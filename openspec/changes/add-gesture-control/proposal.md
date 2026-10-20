# Proposal

## Why

在小米 14（HyperOS，Android 14/15）上，希望不接触屏幕、不使用 root，仅靠前置摄像头识别手势来操作其他 App 的页面：挥手滚动、左右挥返回、握拳回桌面。现有的手势方案要么需要 root 注入触摸事件，要么只能在自家 App 内生效，无法覆盖浏览器、阅读器等日常应用。

本变更从零建立一个原生 Android 应用，用无障碍服务（`AccessibilityService`）替代 root 完成跨应用操作，并通过应用白名单把识别严格限制在用户选定的 App 内。

## What Changes

- 新增一个原生 Android 应用（Kotlin + Jetpack Compose），通过前置摄像头实时识别手势
- 使用 MediaPipe 手部关键点模型提取 21 个关节点，滑动方向由掌心轨迹计算，静态手势由内置分类器识别
- 通过 `AccessibilityService` 在**任意已勾选 App** 中执行动作：节点滚动、合成滑动、返回、回主页
- 引入**手势 → 动作**两层映射，支持全局默认映射与按 App 覆盖；左右挥可配置为返回或横向翻页
- 引入**生效应用白名单**：只有白名单内的 App 在前台时才启用摄像头识别；桌面与未勾选 App（含游戏）下相机完全关闭
- 前台服务常驻但相机按需挂载，避免后台启动摄像头的平台限制，同时消除桌面的隐私绿点和耗电
- 提供悬浮窗状态提示（识别中 / 未启用 / 暂停 / 准备中）与暂停开关
- 提供权限向导与设置页：灵敏度、手势映射、白名单、方向反转，全部持久化

## Capabilities

### New Capabilities

- `gesture-recognition`: 前置摄像头采集、手部关键点跟踪、滑动方向与静态手势判定，输出与目标应用无关的手势事件
- `gesture-action-dispatch`: 将手势事件按「全局默认 + 按 App 覆盖」解析为动作，并通过无障碍服务执行滚动、返回、回主页与横向翻页
- `app-activation-scope`: 依据前台应用白名单控制识别管线的启停，包括相机按需挂载、熄屏暂停与状态上报
- `gesture-setup-ui`: 权限引导、生效应用与手势映射配置、悬浮窗状态展示与暂停控制

### Modified Capabilities

无。本项目此前没有任何既有规格。

## Impact

- **新增工程**：完整的 Android Gradle 项目（application 模块、Compose UI、两个 Service、纯 Kotlin 领域层）
- **依赖**：MediaPipe Tasks Vision（手势识别）、CameraX（相机采集）、AndroidX Lifecycle / DataStore / Compose
- **系统权限**：`CAMERA`、`POST_NOTIFICATIONS`、`SYSTEM_ALERT_WINDOW`（悬浮窗）、`FOREGROUND_SERVICE_CAMERA`，以及用户手动开启的无障碍服务
- **平台约束**：Android 14+ 摄像头属 while-in-use 类型，不能从后台启动前台服务；HyperOS 对后台进程和无障碍服务有额外限制，需要引导用户配置
- **分发**：因使用无障碍服务操作其他应用，预计以侧载方式分发
- **不影响**：不需要 root，不修改系统分区，不申请 `QUERY_ALL_PACKAGES`
