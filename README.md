# Lee’s Clash

基于 CMFA / mihomo 的 Android 代理客户端，采用 Kotlin 和 Jetpack Compose 重做 Surge iOS Lucid 风格界面。

**开发中：当前快照尚未完成应用编译、模拟器端到端验证和正式发版验收。**

- 应用标识：`io.github.leenban.clash`，最低 Android 8 / API 26。
- 界面：Lucid 彩色首页、工具、更多，统一浅色与深色组件。
- 内核：固定 mihomo 源码快照，保留 Android 服务与原生桥接。
- 新增诊断：实时连接、规则检索、DNS 查询与完整 64 位流量统计（待验证）。

## 开发与交接

- [开发交接](docs/HANDOFF.md)
- [开发矩阵](docs/DEVELOPMENT-MATRIX.md)
- [中文更新日志](CHANGELOG.md)

源码仓为私有仓，GitHub Actions 按项目所有者要求关闭。当前没有正式 APK 发布。

## 来源与许可

本项目基于 [CMFA](https://github.com/MetaCubeX/ClashMetaForAndroid) 修改，使用 [mihomo](https://github.com/MetaCubeX/mihomo) 内核。固定提交和改动记录见交接文档。保留 [GPL-3.0](LICENSE)、[第三方声明](NOTICE) 和原有版权声明。鸣谢不能替代对应源码提供义务，APK-only 分发方案的许可问题尚未解决。
