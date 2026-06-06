#!/system/bin/sh
# ufs_info_fixed.sh - 正确读取 UFS 读写总量（支持大数）

set -u

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

# 检查 root
if [ "$(id -u)" -ne 0 ]; then
    echo -e "${RED}[错误] 需要 root 权限${NC}"
    exit 1
fi

# 查找主块设备
find_block_device() {
    for dev in sda sdb mmcblk0; do
        [ -e "/sys/block/$dev/stat" ] && echo "$dev" && return 0
    done
    # 兜底：遍历排除 loop/ram
    for d in /sys/block/*/stat; do
        d="${d%/stat}"
        d="${d##*/}"
        case "$d" in loop*|ram*) continue ;; esac
        echo "$d"
        return 0
    done
    return 1
}

# 获取扇区大小（默认512）
get_sector_size() {
    local f="/sys/block/$1/queue/hw_sector_size"
    [ -f "$f" ] && cat "$f" 2>/dev/null || echo "512"
}

# 使用 awk 解析 stat 并计算读写字节（大数安全）
get_rw_bytes() {
    local dev="$1"
    local stat_file="/sys/block/$dev/stat"
    [ ! -f "$stat_file" ] && return 1
    local sector_size=$(get_sector_size "$dev")
    awk -v sect="$sector_size" '
        {
            read_sectors = $3
            write_sectors = $7
            read_bytes = read_sectors * sect
            write_bytes = write_sectors * sect
            printf "%.0f %.0f\n", read_bytes, write_bytes
        }
    ' "$stat_file"
}

# 字节转人类可读（支持 TiB, GiB, MiB，使用1024进制）
human_readable() {
    awk -v b="$1" '
        BEGIN {
            if (b < 0) b = 0
            if (b >= 1099511627776) printf "%.2f TiB", b / 1099511627776
            else if (b >= 1073741824) printf "%.2f GiB", b / 1073741824
            else if (b >= 1048576) printf "%.2f MiB", b / 1048576
            else printf "%.0f B", b
        }'
}

# 查找 health_descriptor
find_health_desc() {
    find /sys -name "health_descriptor" -type d 2>/dev/null | head -n1
}

# 解析寿命值 (0x00~0x0B)
parse_life() {
    local val=$(cat "$1" 2>/dev/null | sed 's/0x//i' | tr -d '\n')
    [ -z "$val" ] && echo "不支持" && return
    local dec=$((16#$val))
    case $dec in
        0)  echo "未定义" ;;
        1)  echo "0% - 10%" ;;
        2)  echo "10% - 20%" ;;
        3)  echo "20% - 30%" ;;
        4)  echo "30% - 40%" ;;
        5)  echo "40% - 50%" ;;
        6)  echo "50% - 60%" ;;
        7)  echo "60% - 70%" ;;
        8)  echo "70% - 80%" ;;
        9)  echo "80% - 90%" ;;
        10) echo "90% - 100%" ;;
        11) echo "${YELLOW}已超过设计寿命${NC}" ;;
        *)  echo "未知 (0x$val)" ;;
    esac
}

# 主流程
main() {
    echo "================== UFS/eMMC 存储信息 =================="

    # ---- 读写统计 ----
    dev=$(find_block_device)
    if [ -z "$dev" ]; then
        echo -e "${RED}[失败] 未找到块设备${NC}"
    else
        echo -e "块设备: ${GREEN}$dev${NC}"
        rw=$(get_rw_bytes "$dev")
        if [ $? -eq 0 ] && [ -n "$rw" ]; then
            read_bytes=$(echo "$rw" | awk '{print $1}')
            write_bytes=$(echo "$rw" | awk '{print $2}')
            read_hr=$(human_readable "$read_bytes")
            write_hr=$(human_readable "$write_bytes")
            echo "----------------------------------------"
            echo -e "总读取量: ${YELLOW}$read_hr${NC}"
            echo -e "总写入量: ${YELLOW}$write_hr${NC}"
        else
            echo -e "${RED}[失败] 无法解析 stat${NC}"
        fi
    fi

    # ---- 寿命估算 ----
    hp=$(find_health_desc)
    if [ -n "$hp" ]; then
        echo "----------------------------------------"
        echo -e "健康描述符: ${GREEN}$hp${NC}"
        echo -n "平均磨损寿命: "
        parse_life "$hp/life_time_estimation_a"
        echo -n "最差磨损寿命: "
        parse_life "$hp/life_time_estimation_b"
        if [ -f "$hp/bPreEOLInfo" ]; then
            raw=$(cat "$hp/bPreEOLInfo" 2>/dev/null | tr -d '\n')
            [ -n "$raw" ] && echo "预寿命警告: $raw"
        fi
    else
        echo "----------------------------------------"
        echo -e "${YELLOW}[提示] 未找到 health_descriptor${NC}"
    fi
    echo "======================================================="
}

main "$@"