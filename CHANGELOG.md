# 更新日志（全部版本）

## 1.0.1-beta.8 — 2026-11-10
- 新增笔记标题与正文的本地全文搜索；搜索仅在解锁后对内存中的解密内容执行，不使用数据库明文 LIKE 查询。
- 增加搜索逻辑单元测试，验证密文不匹配、解密后可搜索及大小写不敏感行为。
- Android versionCode：9。



## 1.0.1-beta.7 — 2026-11-10
- 笔记标题、正文及历史版本改为 AES-GCM 加密后写入 Room 数据库。
- 解锁后自动迁移旧版本的明文笔记与历史记录，保留旧数据读取兼容。
- 新增加密字段模型转换；创建、编辑、快照、恢复均使用密文持久化。
- 增加主密码与加密基础单元测试门禁。
- Android versionCode：8。

本文件记录每一次已提交的项目修改。最新版本置顶。

## 1.0.1-beta.6 — 2026-11-10
- 恢复 Beta 版本的 GitHub Pre-release 标记；正式版本使用普通 Release。
- 将发布流程改为由版本 tag 驱动，自动识别 Beta / Stable。
- Beta 不覆盖正式版 Latest；正式版发布时设为 Latest。
- Android versionCode 从 6 升至 7。

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
