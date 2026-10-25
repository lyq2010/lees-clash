# 更新日志

## 0.1.0（开发中，2026-10-26）

- 固定 CMFA 与 mihomo 源码基线，未导入上游签名密钥。
- 将自有 Kotlin namespace、Android 组件引用及 JNI 符号迁移到 `io.github.leenban.clash`，最低支持 Android 8。
- 引入 Compose，开始实现 Surge iOS Lucid 视觉组件及首页、工具、策略和诊断页面。
- 增加连接、规则、DNS 和独立上传下载统计的 Go / JNI / Binder 接口。
- 增加开发交接文档和功能开发矩阵，明确未完成项目及验证边界。
- 正式构建缺少独立签名配置时直接失败，取消回退 Debug 签名。

本版本尚未完成应用编译、模拟器运行与 Release 验收，不作为可发布版本。
