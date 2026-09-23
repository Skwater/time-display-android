# 时间显示器（Android）

一个可横屏或竖屏使用的全屏时钟。显示 24 小时制的时、分、秒，可切换时区、单独开关时区文字，并可选择显示公历日期、中国农历。支持本地图片、GIF / 动态 WebP、视频背景、背景亮度与位置调节、侧栏透明度、动态按钮颜色、文字阴影和加粗，以及系统字体或用户导入字体。

## 项目状态

已创建可导入 Android Studio 的原生 Java 项目。0.4.1 Debug APK 位于 `releases/time-display-0.4.1-debug.apk`，构建原件位于 `app/build/outputs/apk/debug/app-debug.apk`。构建与签名校验已通过；尚未完成设备实测，验收步骤见 [测试计划](docs/test-plan.md)。

## 运行

1. 用 Android Studio 打开本文件夹。
2. 使用 JDK 17、Android SDK Platform 36，同步 Gradle。
3. 在 Android 9（API 28）及以上的设备或模拟器运行 `app`。
4. 在时钟页**向右滑**打开左侧设置面板；**向左滑**打开右侧自定义面板。反向滑动、点击面板外侧或按返回键可关闭。面板宽度最多为屏幕的 82%，保留时钟可见区域。
5. 左侧面板可调整侧栏背景透明度。右侧面板并排提供“选择图片”和“选择视频”：图片从相册选取，视频从文件选择器选取。选择图片后，点“预览并调整背景位置”：单指平移、双指缩放，再用底部“重置”“保存”“取消”决定是否保留。视频背景暂不支持位置调整。

项目使用 Android Gradle Plugin 8.13.2 与 Gradle 8.13。Gradle wrapper 已包含；首次同步需要获取 Gradle 与插件依赖。本次构建用的 SDK 和 Gradle 放在项目的 `.tooling/`，已由 `.gitignore` 排除。无需网络权限，时间来自设备系统时钟。

本机 Debug 构建使用[项目内的签名密钥](app/signing/README.md)。0.2.0 起的 Debug APK 使用同一证书，SHA-256 为 `9E:2A:7F:51:06:CA:4D:71:91:3F:FF:F1:EC:C2:4A:21:20:88:9E:B0:6A:3E:BA:D2:15:5E:87:E6:0B:AD:07:03`，可在签名相同的前提下覆盖安装。密钥留在本机并被 Git 忽略；从 Git 检出的项目若没有密钥，可正常构建，但默认 Debug 签名无法覆盖安装此前的 APK。

0.4.0 更新：按钮使用 Android 12 及以上的系统动态强调色，旧系统使用固定颜色；新增侧栏透明度、主要时区及当前 UTC 偏移前缀、图片位置预览。

0.4.1 更新：将背景导入拆为并排的图片和视频按钮；图片优先调用系统相册选图界面，视频沿用文件选择器。

## 文档

- [产品需求与验收标准](docs/requirements.md)
- [技术设计与数据流](docs/architecture.md)
- [测试计划与发布检查](docs/test-plan.md)

## Git 版本管理

项目使用 `main` 分支记录已完成的版本。每次功能修改提交代码与文档；构建出的版本化 APK 保存在 `releases/` 并随版本提交。构建缓存、下载的工具和本机签名密钥由 `.gitignore` 排除。推送到远端前，请单独安全备份 `app/signing/time-display-debug.keystore`，以便在其他机器上继续使用相同签名。

## 目录

```text
app/src/main/java/com/example/timedisplay/
  MainActivity.java       时钟页面、双侧抽屉、背景显示、生命周期
  ClockFaceView.java      时间、公历和农历绘制
  SettingsPanel.java      左侧设置与右侧自定义、文件导入
  ClockSettings.java      设置键
  UiPalette.java          动态按钮颜色与旧系统回退色
docs/                    产品与开发文档
```
