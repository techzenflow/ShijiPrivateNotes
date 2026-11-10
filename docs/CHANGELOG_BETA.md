# 测试版更新日志（Beta）

仅记录 Beta 测试版本。每次 Beta 修改均新增版本号和记录，不覆盖历史条目。Beta Release 在 GitHub 上使用普通 Release（不勾选 Pre-release）。

## 1.0.1-beta.4 — 2026-11-10
- Android Actions 构建串行化，避免并发初始化 Debug 签名密钥缓存。
- 增加 APK 签名验证步骤，验证成功才上传。
- versionCode：5。
- 测试目标：验证安装及覆盖升级；需目标手机实测。

## 1.0.1-beta.3 — 2026-11-10
- 取消 GitHub Pre-release 标记，Beta 版本以普通 Release 显示。
- 将该版本设为 GitHub Latest，方便从仓库主页进入最新版本。
- versionCode：4。
- 说明：普通 Release 展示不代表已通过正式版验收；指纹仍需真机验证。

## 1.0.1-beta.2 — 2026-11-10
- 锁屏页面改为指纹优先布局，主密码保留为备用解锁方式。
- 首页品牌信息卡片和导航栏视觉优化。
- versionCode：3。
- 测试状态：Actions 构建成功；指纹需真机验证。

## 1.0.1-beta.1 — 2026-11-10
- 首个可下载 Beta 包。
- 接入 BiometricPrompt 与 Android Keystore 指纹密钥封装流程。
- 测试状态：尚未完成全面真机验证。

## Beta 发布规则
- 每次代码修改递增 Beta 序号和 Android versionCode。
- 每个 Beta 版本使用独立 tag 和 GitHub 普通 Release，不使用 Pre-release 标记。
- Release 正文必须包含本版本修改、测试状态及已知问题。
