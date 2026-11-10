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
1. 安装 Android Studio（建议 Iguana 或更新版本）及 Android SDK Platform 35。
2. 打开本项目根目录，等待 Gradle Sync。
3. 使用 JDK 17。
4. 连接 Android 手机并启用 USB 调试，或在 Android Studio 选择 Build > Build APK(s)。
5. APK 通常生成于 `app/build/outputs/apk/debug/app-debug.apk`。

最低系统 Android 8.0（API 26）；目标 API 35。首次运行设置主密码。请牢记主密码：本应用不提供云端找回。

## 安全说明
这是本地优先的个人资料管理器初始完整工程。不要把它当作经过第三方审计的专业密码管理器。主密码派生密钥不会上传；遗忘主密码可能导致私密条目无法解密。建议先用非关键资料测试并定期导出/备份（加密备份功能可在后续版本补充）。


## GitHub Actions 自动编译
仓库已包含 `.github/workflows/android.yml`。上传到 GitHub 的 `main` 或 `master` 分支后会自动编译；也可在仓库的 Actions 页面手动运行 `Android APK`。成功后进入对应运行记录，在 Artifacts 下载 `shiji-debug-apk`，解压得到 `app-debug.apk`。该 APK 是 debug 签名，适合测试安装；正式发布需配置自己的签名密钥。
