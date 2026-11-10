# 更新日志（全部版本）

本文件记录每一次已提交的项目修改。最新版本置顶。

## 1.0.1-beta.5 — 2026-11-10
- 统一发布资产命名：`ShijiPrivateNotes-{version}-Android-{arch}.apk`。
- 当前单一 Android APK 标记为 `universal`，避免误标为单一 CPU 架构。
- 每个 APK 同步提供 SHA-256 校验文件。
- Android versionCode 从 5 升至 6。

## 1.0.1-beta.4 — 2026-11-10
- 修复 GitHub Actions 并发构建可能导致 Debug 签名密钥缓存竞争的风险：Android 发布构建统一串行执行。
- 增加 APK 签名校验，校验失败时不上传 Release APK。
- Android versionCode 从 4 升至 5。
- 目的：排查并修复不同 Beta APK 签名不一致导致的覆盖安装失败；仍需在目标手机验证升级安装。

## 1.0.1-beta.3 — 2026-11-10
- 根据项目发布策略，取消 Beta Release 的 GitHub Pre-release 标记，Beta 包也作为普通 Release 显示。
- GitHub Release 标记为 Latest，便于从仓库主页 Releases 入口直接看到最新 Beta 包。
- Android versionCode 从 3 升至 4。

## 1.0.1-beta.2 — 2026-11-10
- 优化锁屏页面：已启用指纹时优先展示指纹解锁按钮，主密码作为备用入口。
- 优化首页品牌信息卡片、导航栏底板及选中态样式。
- Android versionCode 从 2 升至 3。

## 1.0.1-beta.1 — 2026-11-10
- 建立首个可下载的 Android Beta Release。
- 接入 Android Keystore 生物识别密钥封装与 BiometricPrompt 解锁流程（待设备实测）。

## 后续版本记录规则
每次代码修改必须新增一个版本小节，不覆盖或删除旧记录。
