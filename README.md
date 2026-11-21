# FocusTracker

一个完全离线的 Android 专注时长记录应用：设定分类、开始计时，用前置摄像头与前台应用分析判断你是否在专注，疑似分心时振动并在全屏弹窗中提醒。

> 所有计算都在设备本地完成，不联网、不上传任何数据。

## 功能

- **专注计时**：前台服务计时，切后台/熄屏不中断，记录总时长与有效专注时长
- **时段分类**：自定义分类（如「写代码」「读论文」），会话按分类归档
- **低头检测**：前置摄像头 + ML Kit 人脸检测，由头部俯仰角判断低头学习
- **分心提醒**：持续分心超过阈值 → 振动 + 全屏弹窗
- **双模式自动切换**：
  - 监护模式（本应用在前台）→ 摄像头判断低头 / 抬头 / 张望 / 离座
  - 设备工作模式（其他应用在前台）→ 仅按前台应用白/黑名单判断，摄像头关闭
- **自适应帧率**：状态稳定时降频，专注时省电

## 技术栈

| 模块 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) |
| 摄像头 | CameraX 1.4.2 |
| 姿态识别 | ML Kit Face Detection 16.1.7（端侧离线） |
| 存储 | Room 2.7.2 |
| 后台运行 | Foreground Service（`camera` 类型） |
| 构建 | Gradle 9.6.0 / AGP 9.5.2 / Kotlin 2.2.10（内置 Kotlin）/ KSP 2.2.10-2.0.2 |

`minSdk 26`，`compileSdk / targetSdk 36`。

## 功耗控制

摄像头管线是耗电主要来源，因此：

- 分析分辨率压到 **480×360**
- `STRATEGY_KEEP_ONLY_LATEST` 背压，只处理最新帧
- 自适应帧率：专注 **4 fps**、状态多变 **10 fps**、无人/离座 **1 fps**
- 设备工作模式下**完全关闭摄像头**，仅保留毫瓦级的 `UsageStatsManager` 轮询

阈值统一在 [`DetectionConfig.kt`](app/src/main/java/com/focustracker/detector/DetectionConfig.kt) 中调整。

## 项目结构

```
app/src/main/java/com/focustracker/
├── data/        # Room：Category、FocusSession、DAO、Database
├── detector/    # 人脸低头检测、应用分类、专注引擎（状态机）
├── service/     # FocusForegroundService：计时与检测编排
├── alert/       # DistractionAlerter：振动 + 全屏通知
├── ui/          # HomeScreen、FocusViewModel、主题
├── FocusTracker.kt              # 全局可观测状态（服务写、UI 读）
├── MainActivity.kt
└── DistractionAlertActivity.kt  # 分心弹窗
```

**判定逻辑**见 [`FocusEngine.kt`](app/src/main/java/com/focustracker/detector/FocusEngine.kt)：
将摄像头姿态与前台应用分类融合为 `[-1, 1]` 的专注度评分，并做 3 秒滞回，避免抖动误报。

## 构建

需要 **JDK 17+** 与 **Android SDK 36**：

```bash
./gradlew assembleDebug
```

安装到设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 权限说明

| 权限 | 用途 | 授予方式 |
| --- | --- | --- |
| `CAMERA` | 前置摄像头检测头部姿态 | 运行时弹窗 |
| `POST_NOTIFICATIONS` | 前台服务通知、分心提醒（API 33+） | 运行时弹窗 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CAMERA` | 后台持续计时与检测 | 安装时授予 |
| `USE_FULL_SCREEN_INTENT` | 分心时弹出全屏提醒 | 安装时授予 |
| `VIBRATE` | 分心振动提醒 | 安装时授予 |
| `PACKAGE_USAGE_STATS` | 读取前台应用以判断专注度 | **需手动**到「设置 → 特殊应用权限 → 使用情况访问」开启 |

未授予「使用情况访问」时，设备工作模式会退化为中性判定，仅监护模式有效。

## 已知限制

- 应用进程被杀后，进行中的会话不会自动恢复（`START_NOT_STICKY`）
- 白/黑名单为内置默认值（微信、抖音、淘宝等），尚无配置界面
- 浏览器类应用只能识别为「中性」，无法区分看课与刷视频
- ML Kit `headEulerAngleX` 的正负方向因设备而异，若低头判定相反，请在 `DetectionConfig` 中对调 `PITCH_DOWN_DEG` / `PITCH_UP_DEG` 的符号
- 本工程尚未在 CI 中编译验证，首次构建请以 Android Studio 的实际报错为准

## Roadmap

- [ ] 白/黑名单可视化配置
- [ ] 人脸丢失时降级 Pose 检测兜底
- [ ] 进程被杀后的会话恢复
- [ ] 每日/每周统计与趋势图
- [ ] 数据导出（CSV / JSON）
- [ ] 「熄屏专注」模式

## License

尚未选定，如需开源请补充 LICENSE 文件（推荐 MIT）。
