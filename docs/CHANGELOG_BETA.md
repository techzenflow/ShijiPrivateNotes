# 测试版更新日志（Beta）

仅记录 Beta 测试版本。每次 Beta 修改均新增版本号和记录，不覆盖历史条目。

## 1.0.1-beta.6 — 2026-11-10
- 恢复 GitHub Pre-release 标记。
- 发布由版本 tag 驱动；Beta 不覆盖正式版 Latest。
- Android versionCode：7。

## 1.0.1-beta.5 — 2026-11-10
- 发布文件采用统一格式：`ShijiPrivateNotes-{version}-Android-{arch}.apk`。
- 当前 APK 标记为 `universal`；每个 APK 附带 SHA-256 校验文件。
- versionCode：6。

## 1.0.1-beta.4 — 2026-11-10
- Android Actions 构建串行化，避免并发初始化 Debug 签名密钥缓存。
- 增加 APK 签名验证步骤，验证成功才上传。
- versionCode：5。
- 测试目标：验证安装及覆盖升级；需目标手机实测。

## 1.0.1-beta.3 — 2026-11-10
- 取消 GitHub Pre-release 标记，Beta 版本以普通 Release 显示。
- 将该版本设为 GitHub Latest。
- versionCode：4。

## 1.0.1-beta.2 — 2026-11-10
- 锁屏页面改为指纹优先布局，主密码保留为备用解锁方式。
- 首页品牌信息卡片和导航栏视觉优化。
- versionCode：3。

## 1.0.1-beta.1 — 2026-11-10
- 首个可下载 Beta 包。
- 接入 BiometricPrompt 与 Android Keystore 指纹密钥封装流程。

## Beta 发布规则
- 每次应用代码修改递增 Beta 序号和 versionCode。
- Beta Release 必须标记 Pre-release；正式版不得标记 Pre-release。
- 文件名包含项目名、版本号、平台、架构；附 SHA-256 校验文件。
- Release 正文写明变更、文件说明、测试状态及已知问题。
