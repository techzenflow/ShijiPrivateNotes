# 时迹 · 私人笔记（Shiji Private Notes）

Android 原生离线笔记与密码库项目。技术栈：Kotlin、Jetpack Compose、Room、Android Keystore、BiometricPrompt。

## 功能
- 本地笔记：创建、编辑、删除、搜索。
- 版本时间线：每次内容变更保存不可变快照；按时间查看历史版本。
- 版本对比：逐行差异展示新增/删除/未变化行。
- 历史恢复：恢复会创建一个新版本，不覆盖旧版本。
- 私密条目：账号/网站/用户名/密码/备注加密后存储。
- 主密码：PBKDF2-HMAC-SHA256 验证；敏感字段使用 AES-256-GCM。
- 生物识别：Android BiometricPrompt；需设备已设置安全锁屏/指纹。
- 数据仅保存在应用私有目录，不请求网络权限。

## 打开与构建
1. 安装 Android Studio 及 Android SDK Platform 35。
2. 打开本项目根目录，等待 Gradle Sync。
3. 使用 JDK 17。
4. 连接 Android 手机并启用 USB 调试，或在 Android Studio 选择 Build > Build APK(s)。
5. APK 通常生成于 `app/build/outputs/apk/debug/app-debug.apk`。

最低系统 Android 8.0（API 26）；目标 API 35。首次运行设置主密码。请牢记主密码：本应用不提供云端找回。

## 发布文件命名规范
统一格式：`{Project}-{Version}-{Platform}-{Architecture}.{Extension}`。

示例：
- `ShijiPrivateNotes-1.0.1-beta.5-Android-universal.apk`
- `ShijiPrivateNotes-1.0.1-beta.5-Android-arm64-v8a.apk`（仅在实际构建对应 ABI 专用包时使用）
- 每个安装包同时提供同名 `.sha256` 文件。

架构字段必须反映实际 APK 内容：通用包使用 `universal`；仅含 ARM64 原生库时才标记 `arm64-v8a`；不得把通用包误标成单一架构。文件名顺序固定，便于用户和下载工具识别。

## 版本与变更日志规则
- 每一次代码/功能/界面/修复修改，都必须递增 `versionCode` 和 `versionName`，不得复用已发布版本号。
- 测试版本使用 `1.0.x-beta.n`，Git tag 使用 `v1.0.x-beta.n`；每次 Beta 修改递增 n。
- 正式版本使用稳定语义版本号（例如 `1.0.0`）。
- 所有 Beta 和正式版 GitHub Release 均使用普通 Release，不勾选 Pre-release。Beta 仍须在标题和日志中明确标注 Beta。
- 每次修改都要更新 `CHANGELOG.md` 和 `docs/CHANGELOG_BETA.md`；正式发布时还必须更新 `docs/CHANGELOG_STABLE.md`。
- GitHub Release 使用唯一版本 tag，并在正文写明变更、文件说明、测试状态和已知问题。
- APK 的 `applicationId` 保持 `com.shiji.notes`。覆盖安装还要求新旧 APK 使用同一签名密钥。
- Android 发布构建串行执行，APK 上传前验证签名并生成 SHA-256 校验文件。

## 变更日志
- [完整变更记录](CHANGELOG.md)
- [测试版变更记录](docs/CHANGELOG_BETA.md)
- [正式版变更记录](docs/CHANGELOG_STABLE.md)

## GitHub Actions
推送到 main/master 后自动构建 APK，并按版本号创建 GitHub 普通 Release（不使用 Pre-release 标记）。最新 Beta 也会作为 Latest 显示。
