# HD-Check

Android 设备存储健康检查与 I/O 监控工具。需要 root 权限。

## 功能

| 功能 | 描述 |
|------|------|
| **检查 IO 占用** | 实时监控各应用（UID）的读写速率与累计 I/O，支持按速率-读/写、累计-读/写四列排序 |
| **检查硬盘健康** | 读取 UFS/eMMC 块设备读写总量与寿命预估（`health_descriptor`），输出等效于 `ufs_check.sh` |

## 依赖

### Android 应用

| 库 | 版本 | 用途 |
|----|------|------|
| `com.google.android.material:material` | 1.11.0 | Material Design 3 组件 |
| `com.github.topjohnwu.libsu:core` | 5.2.2 | Root shell 执行 |
| `androidx.navigation:navigation-fragment` | 2.7.6 | 底部导航 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.7.0 | MVVM 架构 |
| `androidx.recyclerview:recyclerview` | 1.3.2 | 列表展示 |

### Rust TUI（参考实现）
此 rust 代码只作为对监控的可行性做探索，对应用本体无关，仅参考了逻辑实现。

其位置位于/original_cli/io_monitor

- `crossterm` + `ratatui` — 终端 UI
- `anyhow` — 错误处理

### 硬盘健康逻辑
其位置位于/original_cli/ufs_check.sh

仅在ufs4平台上通过测试，不确保其他平台可用

```bash
cd original_cli
chmod +x ufs_check.sh
./ufs_check.sh
```


## 构建

### 前置条件

- JDK 17+
- Android SDK（`platforms;android-34`、`build-tools;34.0.0`）

### 编译 Android 应用

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本（需签名配置）
./gradlew assembleRelease
```

产物位置：`app/build/outputs/apk/<variant>/app-<variant>.apk`

### 编译 Rust TUI（需先创建 Cargo 项目）

```bash
cd original_cli/io_monitor
cargo init
# 将现有 .rs 文件移入 src/，添加依赖后：
cargo build --release
```

## 数据源

### IO 监控

| 路径 | 内容 |
|------|------|
| `/proc/uid_io/stats` | 每行 7 列：uid fg_read fg_write fg_fsync bg_read bg_write bg_fsync |
| `/data/system/packages.list` | UID → 包名映射 |

数据流：两次采样计算 delta → 除以时间间隔得到速率 → 累加得到累计量。

### 硬盘健康

| 路径 | 内容 |
|------|------|
| `/sys/block/<dev>/stat` | 块设备 I/O 统计（第 3 列为读扇区数，第 7 列为写扇区数） |
| `/sys/block/<dev>/queue/hw_sector_size` | 硬件扇区大小（通常 4096） |
| `/sys/devices/platform/soc/*/health_descriptor/` | UFS 寿命信息 |

读写总量 = 扇区数 × 扇区大小。扇区大小读取支持多路径回退（`hw_sector_size` → `logical_block_size` → `physical_block_size` → 默认 512）。

## 日志

所有组件使用 `android.util.Log` 输出，TAG 前缀统一为 `HDCheck.*`。通过 `adb logcat | grep HDCheck` 可查看完整调用链：

```
HDCheck.RootShell     — shell 初始化、每条命令执行
HDCheck.IOMonitor     — stats 解析、采样流程
HDCheck.DiskHealth    — 设备查找、扇区大小、寿命值读取
HDCheck.IOViewModel   — 监控生命周期
HDCheck.DiskViewModel — 健康检查生命周期
HDCheck.IOFragment    — UI 事件
HDCheck.DiskFragment  — UI 事件
```

## License

本项目采用MIT协议进行开源。
