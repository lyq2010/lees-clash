# 更新日志

## 0.1.0（开发中，2026-10-26）

- 固定 CMFA 与 mihomo 源码快照，保留许可证和来源记录，未导入上游签名。
- 自有 Kotlin namespace、组件、JNI 符号迁移到 `io.github.leenban.clash`，Go module 改为 `lees`，最低 Android 8。
- 实现 Compose Lucid 浅深色组件及主要页面，原 Manifest 页面入口接入新界面。
- 接入配置、策略、网络、覆写、文件、日志与关于等开发页面。
- 新增 Go / JNI / Binder 连接、规则、DNS、关闭连接及完整 64 位上传下载统计。
- 新增诊断改用后台同步 Binder 事务，导航重建回归通过。
- Geo 数据固定随仓保存并校验 SHA-256，升级时保留自定义数据。
- 新增独立图标与签名配置，正式构建禁止回退 Debug 签名；密钥和属性不入库。
- 新增本机网络 fixture、原生集成与 Compose 导航截图测试；Debug / x86_64 构建及当前测试通过。
- 建立交接、开发矩阵、验证记录和截图；私有 main 分支开发，GitHub Actions 关闭。

当前为开发初版。连接分页刷新、完整高级功能回归、Release / R8、多 ABI 及兼容矩阵仍未完成，未公开发版。
