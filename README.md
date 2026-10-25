# Lee’s Clash

基于 CMFA / mihomo 的 Android 代理客户端，使用 Kotlin 和 Jetpack Compose 实现 Surge iOS Lucid 风格界面。

**开发初版：x86_64 Debug 可构建运行，当前自动化测试通过；完整功能与正式发版验收尚未完成。**

- 应用标识 `io.github.leenban.clash`，最低 Android 8 / API 26。
- 首页 / 工具 / 更多，统一 Lucid 彩色卡片、分组列表、浅色与深色界面。
- 保留 Go 代理内核与 Android 服务，新增连接、规则、DNS 和上下行流量诊断。
- 固定内核与 Geo 数据快照，不在构建时跟踪浮动分支或 latest 数据。

## 开发资料

- [交接文档](docs/HANDOFF.md)
- [开发矩阵与未完成项](docs/DEVELOPMENT-MATRIX.md)
- [验证结果与截图](docs/VERIFICATION.md)
- [构建与发布](docs/RELEASE.md)
- [来源版本](docs/UPSTREAM.md)
- [中文更新日志](CHANGELOG.md)

源码仓为私有仓，GitHub Actions 关闭。当前没有正式 APK 发布。单 ABI Debug 包是 testOnly 构建，安装使用 `adb install -r -t`。

## 来源与许可

基于 [CMFA](https://github.com/MetaCubeX/ClashMetaForAndroid) 修改，使用 [mihomo](https://github.com/MetaCubeX/mihomo) 内核。保留 [GPL-3.0](LICENSE)、[第三方声明](NOTICE) 和原有版权声明。APK-only 公开分发方案的源码提供义务尚未解决，鸣谢不能替代履约。
