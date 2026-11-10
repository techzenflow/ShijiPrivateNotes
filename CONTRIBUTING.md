# 开发与贡献说明

## 开发环境

- Android Studio
- JDK 17
- Android SDK Platform 35
- Gradle Wrapper（以仓库配置为准）

## 本地构建

在项目根目录执行：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

APK 输出位置：

`app/build/outputs/apk/debug/app-debug.apk`

## 提交前检查

- 确认 Gradle 构建通过。
- 不要提交主密码、真实账号密码、密钥、签名文件或本地配置。
- 涉及加密、主密码和生物识别的修改，应补充测试并检查异常处理。
- 不要将未经安全验证的版本用于保存真实高敏感资料。
