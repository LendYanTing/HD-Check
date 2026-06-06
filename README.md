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

- `crossterm` + `ratatui` — 终端 UI
- `anyhow` — 错误处理

## 构建

### 前置条件

- JDK 17+
- Android SDK（`platforms;android-34`、`build-tools;34.0.0`）
- 环境变量 `ANDROID_HOME` 指向 SDK 路径

### 编译 Android 应用

```bash
cd HD-Check-App

# Debug 版本
./gradlew assembleDebug

# Release 版本（需签名配置）
./gradlew assembleRelease
```

产物位置：`app/build/outputs/apk/<variant>/app-<variant>.apk`

### 编译 Rust TUI（需先创建 Cargo 项目）

```bash
cd io_monitor
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

## 项目结构

```
HD-Check/
├── HD-Check-App/           # Android 应用（主产物）
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/hdcheck/app/
│   │       │   ├── MainActivity.java           # 入口 Activity，含 root 检查
│   │       │   ├── model/
│   │       │   │   ├── AppRow.java             # IO 列表行数据
│   │       │   │   ├── DiskInfo.java           # 硬盘健康结果
│   │       │   │   └── UidIoStat.java          # 单 UID I/O 快照
│   │       │   ├── service/
│   │       │   │   ├── RootShell.java          # libsu shell 封装（日志 + 安全读取）
│   │       │   │   ├── IOMonitorRepository.java # /proc/uid_io/stats 解析
│   │       │   │   └── DiskHealthRepository.java # /sys/block 与 health_descriptor 解析
│   │       │   ├── ui/
│   │       │   │   ├── IOMonitorFragment.java   # IO 监控页面
│   │       │   │   ├── DiskHealthFragment.java  # 硬盘健康页面
│   │       │   │   └── IoRowAdapter.java        # RecyclerView Adapter
│   │       │   ├── util/
│   │       │   │   ├── HumanReadable.java       # 字节格式化 + 寿命值解析
│   │       │   │   ├── PackageResolver.java     # UID → 应用名 映射
│   │       │   │   └── RootChecker.java         # Root 权限检查
│   │       │   └── viewmodel/
│   │       │       ├── IOMonitorViewModel.java   # IO 监控状态管理 + 排序
│   │       │       └── DiskHealthViewModel.java  # 硬盘健康状态管理
│   │       └── res/
│   ├── build.gradle.kts     # 根构建脚本（AGP 8.2.0）
│   ├── gradle/              # Gradle Wrapper（Gradle 8.4）
│   ├── gradlew / gradlew.bat
│   └── settings.gradle.kts
├── io_monitor/              # Rust TUI 参考实现（无 Cargo 项目）
│   ├── main.rs              # 入口：终端 I/O 监控
│   ├── app.rs               # 状态与排序
│   ├── parser.rs            # /proc/uid_io/stats 解析 + delta 计算
│   ├── packages.rs          # UID → 包名映射加载
│   └── ui.rs                # ratatui 终端渲染
├── ufs_check.sh             # Shell 脚本：正确读取 UFS 读写与寿命
├── ufs_check_out/           # 脚本输出 vs 应用输出对比数据
└── icon.png                 # 应用图标（1254×1254）
```

## License

Internal tool.
