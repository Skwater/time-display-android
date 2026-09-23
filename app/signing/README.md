# 签名说明

本项目的签名凭据统一从根目录 `keystore.properties` 读取（该文件被 `.gitignore` 排除，绝不上传到 Git/GitHub）；`app/build.gradle` 不写死密码。密钥库文件均被 `/app/signing/*.keystore` 排除。

## Debug 签名（当前版本使用）

- 密钥库：`time-display-debug.keystore`，别名 `androiddebugkey`，来自 0.2.0 原密钥，保证 0.2.0 起所有 Debug APK 可覆盖安装。
- 证书 SHA-256：`9E:2A:7F:51:06:CA:4D:71:91:3F:FF:F1:EC:C2:4A:21:20:88:9E:B0:6A:3E:BA:D2:15:5E:87:E6:0B:AD:07:03`
- 使用 Android 默认 Debug 凭据，仅用于开发期自装验证，不用于正式发布。
- 密钥文件留在本机并被 Git 忽略；请另行安全备份。丢失后新密钥签出的同包名 APK 无法覆盖安装。
- 新检出项目若无此密钥与 `keystore.properties`，构建自动跳过该签名配置，改用 Android 默认 Debug 密钥（签名不同，不能覆盖安装）。

## Release 签名（正式发布版使用）

- 密钥库：`time-display-release.keystore`，别名 `timedisplay`，专用随机凭据，凭据仅存于 `keystore.properties`。
- 证书（主体 CN=Skwater）SHA-256：`38:75:CE:32:45:D4:BB:9D:ED:DC:A5:A9:8E:21:2E:2D:51:76:0B:BE:F1:6D:A3:E2:3D:23:48:34:08:B4:F7:E6`
- Release 签名已就绪，但**尚未用于任何公开版本**；当前各版本（0.3.1–0.9.5）均为 Debug 签名。
- 请另行离线备份密钥库与 `keystore.properties`；Release 密钥用于正式上架，一旦公开泄露，伪造者可冒名签名同包名应用。
