# Debug APK 签名

`time-display-debug.keystore` 是本项目固定使用的 **Debug 专用**密钥，来自 0.2.0 APK 使用的原密钥。文件存在时，`app/build.gradle` 会使用它，避免构建机器或 Android 用户目录变化导致签名变化。

- 别名：`androiddebugkey`
- 证书 SHA-256：`9E:2A:7F:51:06:CA:4D:71:91:3F:FF:F1:EC:C2:4A:21:20:88:9E:B0:6A:3E:BA:D2:15:5E:87:E6:0B:AD:07:03`
- 密钥文件留在项目目录，但由 `.gitignore` 排除。请另行安全备份；丢失后新密钥签出的同包名 APK 无法直接覆盖安装。
- 新检出的项目若没有此文件，仍可用 Android 的默认 Debug 密钥构建，但签名与当前 APK 不同，不能覆盖安装。
- 这把密钥使用 Android 默认 Debug 凭据，仅用于测试 APK，不用于正式发布。正式发布应另建发布密钥与签名配置。
