# SpatialSurvivor

SpatialSurvivor 是一款基于 PICO Spatial SDK 的沉浸式 MR 幸存者游戏，采用官方 Stage 全空间模板、Kotlin 和 Jetpack Compose/SpatialUI 开发。

## 主要功能

- 玩家位置与头显真实空间移动同步，无虚拟摇杆移动
- Scene Mesh 环境网格寻路与空间边界生成
- 普通虫、疾行者、重甲怪、垂降怪和终局 Boss
- 自动攻击、多武器技能、经验晶石与稀有度三选一升级
- 空间 HUD、生命值/经验显示、暂停与胜负结算
- 手部追踪优先，眼动和手柄输入辅助

## 项目信息

- 应用包名：`com.example.spatialsurvivor`
- 模板：PICO Spatial SDK Stage（Mixed MR / Full Space）
- 语言：Kotlin
- UI：Jetpack Compose + PICO SpatialUI
- Spatial SDK：0.13.3

## 构建

请先在 `local.properties` 中配置本机 Android SDK 路径，然后运行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

部署前请确保已安装并配置 PICO Spatial SDK、Android SDK 和 `pico-cli`。真机上的手部追踪、眼动追踪、Scene Mesh 与性能表现需要在兼容的 PICO 头显中验证。
