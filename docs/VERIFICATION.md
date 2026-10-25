# 验证记录

更新日期：2026-10-26。机器可读结果见 [results.json](verification/results.json)。

| 验证 | 结果 | 证据 |
|---|---|---|
| 原版首次构建 | 未通过，当时缺少 NDK；未继续原版运行对照 | `I:\DevCache\lees-clash-baseline-build.log` |
| 设计模块及服务依赖 Kotlin | 通过，61 个任务 | `I:\DevCache\lees-clash-design-compile.log` |
| 完整应用 Kotlin | 通过，102 个任务 | `I:\DevCache\lees-clash-app-compile.log` |
| x86_64 Go 原生库 | 通过 | `I:\DevCache\lees-clash-native-build.log` |
| Debug / AndroidTest APK 与 Kotlin 单元测试 | 通过，195 个任务；1 项单元测试 | `I:\DevCache\lees-clash-final-build.log` |
| Go 诊断 | 4 项通过：分页边界、筛选 / 无效输入、64 位统计、关闭已结束连接 | [输出](verification/go-tests.txt) |
| Geo 数据 | 四个资源 SHA-256 与发布者 digest 一致，构建校验通过 | `resources/geodata/manifest.json` |
| MuMu 配置导入 | 1 项通过；创建并选择本机测试配置，读取 native 版本 | [输出](verification/prepare.txt) |
| 导航与主题 | 1 项通过；首页、工具、应用设置、网络、覆写及深色 | [输出](verification/navigation.txt) |
| 本机网络集成 | 1 项通过；VPN、策略切换、规则、DNS、转发、连接关闭、上下行统计 | [输出](verification/native-integration.txt) |
| 私有仓与 Actions | private=true，Actions enabled=false；main 已建立 | `lyq2010/lees-clash` |
| 正式 Release / R8 / 其余 ABI | 尚未执行 | 不以 Debug 结果替代 |

## 自动截图

- [正常前台首页](verification/home-current.png)
- [浅色首页](verification/home-light.png)、[深色首页](verification/home-dark.png)
- [工具](verification/tools-light.png)、[应用设置](verification/settings-light.png)
- [网络设置](verification/network-light.png)、[覆写](verification/overrides-light.png)

截图由自动化生成，截图前等待界面稳定，避免保留切换动画中的暗帧。截图证明实际页面可运行，不代表已经完成 Surge 逐像素或完整视觉验收。

## 回归发现

导航重建初次触发 `Parcel::markForBinder` / SIGABRT。新增诊断接口从异步 Binder 改为后台同步事务后，同一回归用例通过。Kaidl 1.15 源码中存在完成回调写入回复 Parcel 的路径，生命周期取消与回复复用需要继续压力验证，不能推断所有既有异步接口均已消除同类风险。

## 边界

- 仅验证 MuMu Android 15 / x86_64；未验证物理 ARM64 真机、Android 8、真实蜂窝切换、平板和大字体。
- 未验证全部关闭连接、节点测速、后台订阅更新、二维码、外部提供器、完整文件操作及全部覆写字段。
- 连接分页刷新存在已知待修项目，见开发矩阵。
- 没有公开发版。签名属性和密钥未进入 Git，尚未执行正式签名包验收。
- 测试结束后停止本应用服务和本机 fixture，移除 3 条 adb reverse；保留模拟器、应用和测试配置。
