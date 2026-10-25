# 构建与发布

## 当前策略

源码仓 `lyq2010/lees-clash` 为私有仓，GitHub Actions 按所有者要求关闭。本地验证不依赖 CI；禁止为了执行工作流擅自开启 Actions。

公开产物仓尚未创建。对外发布前需要明确目标仓、产物版本和许可安排；当前开发快照不能作为已验收版本公开发布。

## Windows 本地构建

使用 PowerShell 7：

```powershell
.\scripts\Build.ps1
.\scripts\Build.ps1 -Tasks ':app:assembleAlphaRelease' -Abi all
```

脚本仅设置当前进程环境，TEMP / TMP / Gradle / Android / Go 缓存使用 `I:\DevCache`；需要 JDK 21、SDK 35、build-tools 35.0.0、NDK 29.0.14206865、CMake 3.22.1 和固定的 Android Go 工具链。

## 签名

独立密钥存放在项目 `.secrets/release.p12`，签名属性为根目录 `signing.properties`，两者均被 Git 忽略。首次生成的密钥必须离线备份，丢失后不能使用同一签名升级已安装版本。密码不得写入日志、文档或提交。

正式构建缺少签名属性时立即失败；不得退回 Debug 签名。版本默认 `0.1.0`，补丁号达到 50 时进位；Android versionCode 独立递增。

## 待发布包

正式输出应包括 APK、SHA256SUMS、中文 CHANGELOG 和签名证书指纹。发布前核对 applicationId、minSdk、targetSdk、ABI 与真实提交。验证记录见 [VERIFICATION.md](VERIFICATION.md)。

保留 GPL / NOTICE。APK-only 公开分发的源码提供义务尚未解决，不能将关于页鸣谢写成替代履约方案。
