<p align="center">
  <img src="app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png" width="96" alt="屏幕时钟图标" />
</p>

<h1 align="center">屏幕时钟（Android）</h1>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3" /></a>
</p>

一个可横屏或竖屏使用的全屏时钟。显示 24 小时制的时、分、秒，可切换时区、单独开关时区文字，并可选择显示公历日期、中国农历。支持本地图片、GIF / 动态 WebP、视频背景、背景亮度与位置调节、侧栏透明度、动态按钮颜色、文字阴影和加粗、多字体导入管理，以及字体颜色调节。

本项目的创意来源于作者曾经用过的一款时钟 App，在此致谢。项目从零重写，独立实现全部功能。

## 功能

- 全屏时钟：默认 12 小时制并显示 AM/PM，可独立隐藏 AM/PM 或切换 24 小时制；可显示秒数、公历与农历日期
- 时区搜索与切换：按 UTC 偏移、城市、时区 ID 或本地名称检索，独立显示时区文字
- 背景：本地图片、GIF / 动态 WebP、视频，亮度与位置调节
- 播放列表：批量添加、排序、随机 / 顺序、循环、图片停留时间及画面交叠过渡
- 字体：多字体导入管理，50%–200% 大小调节，颜色调节，整组文字位置预览与拖动
- 界面：侧栏透明度、动态按钮颜色（Monet）、文字阴影与加粗、中英文语言
- 省电：关闭秒数后按分钟刷新；静态背景按屏幕采样解码，动图保留动画

## 运行

用 Android Studio 打开本文件夹，在 Android 9（API 28）及以上设备或模拟器运行 `app`。项目使用 Android Gradle Plugin 8.13.2 与 Gradle 8.13，无需网络权限。

## 构建与校验

在项目根目录运行 `./tools/build-verify.ps1`；需要校验预期版本时可加 `-ExpectedVersion 0.10.2`。脚本离线构建 Debug APK，检查包名、版本、应用名、固定签名和 APK 对齐，再将文件复制到 `releases/`。终端只输出摘要；完整日志写在 `.tmp/`。脚本要求本机保留 `keystore.properties` 和固定 Debug 密钥，缺失时会中止，防止生成无法覆盖安装的 APK。

## 目录

```text
app/src/main/java/com/example/timedisplay/
  MainActivity.java       时钟页面、双侧抽屉、背景显示、生命周期
  ClockFaceView.java      时间、公历和农历绘制
  SettingsPanel.java      左侧设置与右侧自定义、文件导入
  ClockSettings.java      设置键
  FontLibrary.java        字体文件副本、名称读取、选择与删除
  UiPalette.java          动态按钮颜色与旧系统回退色
  BackgroundImageView.java 图片与动图平铺绘制
  L10n.java               应用语言选择与中英文文案
  PlaylistStore.java      背景播放列表文件和顺序管理
  PlaylistFolderImporter.java 文件夹扫描与媒体导入
tools/build-verify.ps1    离线构建、固定签名和 APK 元数据校验
docs/                     产品与开发文档（仅本地保留，不上传）
.tmp/                     临时文件（不上传）
.trash/                   垃圾回收区（不上传）
```
