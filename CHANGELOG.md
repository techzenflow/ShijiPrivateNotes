# 更新日志（全部版本）

本文件记录每一次已提交的项目修改。最新版本置顶。

## 1.0.1-beta.3 — 2026-11-10
- 根据项目发布策略，取消 Beta Release 的 GitHub Pre-release 标记，Beta 包也作为普通 Release 显示。
- GitHub Release 标记为 Latest，便于从仓库主页 Releases 入口直接看到最新 Beta 包。
- Android versionCode 从 3 升至 4。
- 注意：普通 Release 展示不代表该 Beta 已通过正式版质量验收。

## 1.0.1-beta.2 — 2026-11-10
- 优化锁屏页面：已启用指纹时优先展示指纹解锁按钮，主密码作为备用入口。
- 优化首页品牌信息卡片、导航栏底板及选中态样式。
- Android versionCode 从 2 升至 3。
- 状态：构建成功；指纹行为仍需真机测试。

## 1.0.1-beta.1 — 2026-11-10
- 建立首个可下载的 Android Beta Release。
- 接入 Android Keystore 生物识别密钥封装与 BiometricPrompt 解锁流程（待设备实测）。
- 状态：已发布；指纹和设备兼容性尚未完成全面验证。

## 后续版本记录规则
每次代码修改必须新增一个版本小节，不覆盖或删除旧记录。
