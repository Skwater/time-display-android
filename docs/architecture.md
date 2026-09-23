# 技术设计

## 1. 技术选型

| 项 | 选择 | 理由 |
| --- | --- | --- |
| UI | 原生 Android View，Java | 不依赖第三方 UI 库，适合单屏时钟。 |
| 最低系统 | Android 9 / API 28 | 可直接使用 `ImageDecoder` 播放 GIF / 动态 WebP。 |
| 构建 | AGP 8.13.2、Gradle 8.13、JDK 17、compileSdk 36 | 采用兼容组合。 |
| 日期时间 | `java.time`、IANA `ZoneId` | 用系统时区规则处理夏令时。 |
| 中国农历 | Android ICU `ChineseCalendar` | 系统 API，无需外部数据表。 |
| 本地数据 | `SharedPreferences` | 设置项少且无需数据库查询。 |
| 媒体选择 | 图片：`ACTION_PICK_IMAGES` / `ACTION_PICK`；视频：`ACTION_OPEN_DOCUMENT` | 图片优先打开相册并复制到应用目录；视频使用文件选择器和持久读取授权。 |

Android 官方资料：[AGP 8.13 兼容要求](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[ChineseCalendar API](https://developer.android.com/reference/android/icu/util/ChineseCalendar)、[ImageDecoder API](https://developer.android.com/reference/android/graphics/ImageDecoder)、[ACTION_OPEN_DOCUMENT](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT)。

## 2. 模块职责

```text
SettingsPanel ──写入──> SharedPreferences
       │                         │
       └──系统文件选择器           ├──> MainActivity：方向和背景
                                 └──> ClockFaceView：时间、日期、字体

设备系统时钟 ──每秒读取──> ClockFaceView
```

- `MainActivity` 创建背景层、可调暗度层、时钟层和双侧抽屉；暗度默认为 0。在 `onResume` 装载设置，在 `onPause` 停止计时回调与动画。水平手势控制面板，面板宽度上限为屏幕的 82%。全屏标志在创建、恢复和重新获得焦点时应用，以覆盖语言切换后的页面重建。
- 双侧面板外的点击拦截层保持透明，打开面板时不会改变背景亮度。亮度滑杆触摸期间暂停抽屉水平手势，松手后恢复。
- `ClockFaceView` 在每次重绘时读取设置，将当前瞬间映射到选定时区，再绘制时间、公历、农历。
- `SettingsPanel` 负责左侧时间设置、右侧外观自定义与媒体导入。单个背景图片沿用应用内副本；播放列表新导入图片和视频保留系统 URI 与读取授权，图片另存小尺寸缩略图；字体菜单提供导入项选择和删除。
- `FontLibrary` 将每个 TTF / OTF 复制为 `files/fonts/` 中独立的 UUID 文件，校验大小和可加载性，优先读取字体内部 family 名称；旧版单字体设置在首次启动时迁移。元数据保存在 `SharedPreferences`，删除条目时同时删除副本。
- `ClockSettings` 集中定义键名，避免散落的字符串。
- `L10n` 按独立设置选择中文、英文或跟随系统；界面文案与时钟日期格式据此切换。改变语言时重建页面，保留已保存的设置。
- `BackgroundImageView` 在平铺模式重复绘制静态图片或动图；普通模式沿用矩阵变换。
- `PlaylistStore` 用应用私有 SQLite 数据库存储多个命名列表及有序项目；首次建库时将旧版 JSON 单列表迁入默认列表。新导入图片和视频保存原 URI；图片缩略图写入 `files/playlist-thumbnails/`，以 URI 摘要命名并由多个列表共用。旧版图片副本留在 `files/playlist-images/` 直到最后一个引用被删除。另存为复制条目记录并复用缩略图和授权；仅在所有列表都不再引用时清理缩略图、旧图片副本或释放授权。列表无固定项目上限，管理页每页读取 50 项；播放时读取当前列表的项目顺序。
- `PlaylistFolderImporter` 通过 `ACTION_OPEN_DOCUMENT_TREE` 的持久读取授权查询子文档，递归扫描文件夹，按 MIME 类型及常见扩展名识别图片和视频。先在后台扫描计数；如果导入使当前列表首次达到或超过 100 项，则在导入前请求确认；随后在后台再次扫描并导入，不设固定扫描数量上限。图片和视频均依赖所选文件夹的长期读取授权；删除最后一个相关条目时释放授权。
- `UiPalette` 在 Android 12 及以上读取系统动态强调色，旧系统提供回退色。按钮、开关和滑杆使用同一颜色来源。

## 3. 时间正确性

应用每次刷新都调用 `System.currentTimeMillis()`，并将下一次刷新安排在下一个整秒附近。这样从后台返回、系统手动改时、网络自动校时、跨日和夏令时切换时，都重新按当前时刻计算；不使用“上一次时间 + 1 秒”累加。UI 主线程仅在时钟页活跃时更新。

公历使用 `Instant → ZoneId → ZonedDateTime`。农历使用同一个毫秒时间戳和所选时区生成 `ChineseCalendar`。`ChineseCalendar` 自身的天文计算遵循 Android ICU 的中国历法规则，不能将其他历法来源与此混用。

## 4. 背景与字体

| 类型 | 读取方式 | 运行方式 |
| --- | --- | --- |
| 静态图片 | 相册 URI → 应用私有文件 → `ImageDecoder` | `ImageView`。 |
| GIF / 动态 WebP | 相册 URI → 应用私有文件 → `AnimatedImageDrawable` | 页面可见时播放。 |
| 视频 | 文档 URI → `VideoView` | 静音循环；退出页面停止。 |
| TTF / OTF | 文档 URI → 校验并复制到 `files/fonts/` | 多字体列表按名称选择；`Typeface.createFromFile`。 |

背景布局提供填充、适应、拉伸、平铺。填充取覆盖屏幕的较大等比缩放，适应取完整显示的较小等比缩放，拉伸分别按宽高缩放。平铺时，静态位图用重复 `BitmapShader`，动图重复绘制 `Drawable`；预览中缩放和平移控制平铺单元尺寸与位置。视频在解码信息可用后计算容器尺寸，平铺选项按适应方式显示。不同设备的 `VideoView` / `SurfaceView` 渲染行为仍需真机确认。

背景亮度通过背景与文字之间的黑色视图控制，默认透明；滑杆将暗度设为 0%–70%，对应背景亮度 100%–30%。文字阴影和加粗分别由独立的布尔设置控制。

普通图片背景使用 `ImageView.ScaleType.MATRIX`：按所选布局计算基础矩阵，再叠加用户缩放与按屏幕宽高归一化的平移。非平铺布局下，平移限制在图片边缘以内；适应模式允许背景色留白。预览触摸由 `ScaleGestureDetector` 和单指位移处理；保存时写入缩放及两个平移值，取消时恢复持久化值。视频保持 `VideoView` 显示，不进入手势预览。

应用语言设置写入 `SharedPreferences`，值为 `system`、`zh` 或 `en`。跟随系统时使用当前设备语言；非中文系统语言显示英文。应用内菜单、提示、时间日期和农历说明跟随此项；系统相册和文件选择器的语言由系统及对应应用控制。桌面应用名称固定为“屏幕时钟”。启动图标为自适应图标，前景资源包含小圆角黑底和白色“T:me”。

播放列表与单个背景由 `BACKGROUND_SOURCE` 切换。列表图片模式、视频模式、亮度、停留时间、淡出淡入、随机和循环各有独立设置键。列表显示时缩放固定为所选模式的基础比例、平移为零；单个背景原有位置预览和设置不参与列表。图片与动图到时切换，视频播放完毕切换。切换时用背景层上方的黑色遮罩先淡出再淡入；视频首帧渲染后撤去遮罩，超时也会撤去以防画面卡黑。播放失败时跳过条目，全部失效时停止并提示。

侧栏透明度只修改两个面板的背景颜色 alpha；文字及面板外的背景保持原值。时区选择项的前缀在创建面板时依据当前时区规则与当前时刻计算，因此包含夏令时偏移。

字体颜色用四个整数设置保存透明度、HSV 明度、色相及饱和度，默认组成不透明白色。`ClockFaceView` 用 `Color.HSVToColor` 计算文字色，文字阴影沿用相同透明度。右侧的颜色区默认折叠，四条滑条使用相同宽度的标签列及剩余轨道宽度；轨道分别显示透明度、明度、色相与饱和度的渐变。

## 5. 状态与权限

无需互联网、相机或全盘存储权限。用户主动选择图片后，应用复制图片到私有目录，避免依赖相册 URI 的短期授权；视频则保留所选文档的读取 URI 授权。字体复制进应用私有目录。设置保存在应用数据中；清除应用数据会重置设置。`FLAG_KEEP_SCREEN_ON` 只作用于时钟页窗口。

## 6. 质量关注点

- **可读性**：暗度默认 0%，文字阴影可关闭；极亮或高频动图可用背景亮度滑杆调节。
- **签名**：Debug APK 使用 `app/signing/time-display-debug.keystore`，0.2.0、0.3.0 与 0.3.1 的证书指纹已核对一致。该密钥仅用于测试，不作为发布签名。
- **大图内存**：当前实现直接解码原图，超大图可能占用大量内存；发布前应加入按屏幕尺寸采样。
- **功耗**：秒级更新、常亮和视频播放持续耗电；可关闭秒数，但首版刷新调度仍为每秒。后续可在秒数关闭时改为分钟级刷新。
- **系统差异**：背景视频编码、字体格式和文件提供方的持久授权需覆盖不同设备测试。
- **备份**：系统备份可能恢复设置字符串但不能恢复外部文档授权；恢复后应回退默认背景。
