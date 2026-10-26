# 验证记录

更新日期：2026-10-26。机器可读结果见 [results.json](verification/results.json)。

| 验证 | 结果 | 证据 |
|---|---|---|
| 原版首次构建 | 未通过，当时缺少 NDK；未继续原版运行对照 | `I:\DevCache\lees-clash-baseline-build.log` |
| 设计模块及服务依赖 Kotlin | 通过，61 个任务 | `I:\DevCache\lees-clash-design-compile.log` |
| 完整应用 Kotlin | 通过，102 个任务 | `I:\DevCache\lees-clash-app-compile.log` |
| x86_64 Go 原生库 | 通过 | `I:\DevCache\lees-clash-native-build.log` |
| 当前 Debug / AndroidTest APK 与 Kotlin 单元测试 | 通过，195 个任务；5 项单元测试 | `I:\DevCache\lees-clash-binder-fix-build.log` |
| Go 诊断 | 此前 4 项通过，本轮未修改 Go：分页边界、筛选 / 无效输入、64 位统计、关闭已结束连接 | [输出](verification/go-tests.txt) |
| Geo 数据 | 四个资源 SHA-256 与发布者 digest 一致，构建校验通过 | `resources/geodata/manifest.json` |
| MuMu 配置导入 | 1 项通过；创建并选择本机测试配置，读取 native 版本 | [输出](verification/prepare.txt) |
| 导航与主题 | 1 项通过；首页、工具、应用设置、网络、覆写及深色 | [输出](verification/navigation.txt) |
| 本机网络集成 | 1 项通过；VPN、策略切换、规则、DNS、转发、连接关闭、上下行统计 | [输出](verification/native-integration.txt) |
| 首页与动效 | 2 项通过：单行配置名、320 / 360dp、1.5 倍字体、转场中间帧及快速反向 | [输出](verification/home-ui.txt) |
| 真实连接分页 | 1 项通过：65 连接加载更多、每秒刷新、搜索、详情结束保留及全部关闭 | [输出](verification/paging.txt) |
| 前后台采样 | 1 项通过：真实 Android 时钟，后台 2.2 秒不采样、恢复后获取新状态 | [输出](verification/lifecycle.txt) |
| Binder 取消竞争 | 2 项通过：取消后迟到回复不写调用方 Parcel、重复完成不二次交付 | [输出](verification/binder-cancellation.txt) |
| 关闭动画导航 | 连续 3 次通过，覆盖页面返回和 Activity 重建，设置恢复为 0.5 | [输出](verification/no-animation.txt) |
| 私有仓与 Actions | private=true，Actions enabled=false；main 已建立 | `lyq2010/lees-clash` |
| 正式 Release / R8 / 其余 ABI | 尚未执行 | 不以 Debug 结果替代 |

当前 MuMu 共有 9 个测试方法通过；关闭动画导航额外重复 3 次。当前 Debug APK 的 SHA-256 为 `4ee00d05276dd0075f275c0a37b4a14887b26c559aebeebbddc864bb838f4fba`。

## 自动截图

- [正常前台首页](verification/home-current.png)
- [浅色首页](verification/home-light.png)、[深色首页](verification/home-dark.png)
- [工具](verification/tools-light.png)、[应用设置](verification/settings-light.png)
- [网络设置](verification/network-light.png)、[覆写](verification/overrides-light.png)

- [320dp 单行配置头部](verification/header-320-font-1.0.png)、[窄屏与 1.5 倍字体](verification/header-narrow-large-font.png)
- [转场中间帧](verification/motion-forward-middle.png)与[结束帧](verification/motion-forward-end.png)：测试面板用于验证插值和可中断行为，不是产品页面。
- [独立图标预览](brand/icon-preview.png)与[SVG 母版](brand/icon.svg)

截图由自动化生成，截图前等待界面稳定，避免保留切换动画中的暗帧。截图证明实际页面可运行，不代表已经完成 Surge 逐像素或完整视觉验收。

## 回归发现

导航重建初次触发 `Parcel::markForBinder` / SIGABRT；只将新增诊断改为同步 Binder 未覆盖既有异步配置接口。关闭系统动画的本轮测试再次复现相同中止：kaidl 1.15 完成回调直接写入调用方 reply，取消后该 Parcel 可已回收并被系统复用。

已纳入本地运行库补丁，改为回调私有快照、恢复后转交和原子完成状态；迟到完成 / 重复回复的确定性测试通过，关闭动画的原导航用例连续 3 次通过。其余既有异步接口共用该补丁；长期并发与服务死亡仍需扩展测试，不将本轮结果等同于全部压力场景。

生命周期回归需要避免用前台 Compose 测试时钟等待已经销毁的测试根；因此前台分页使用 Compose 时钟，后台测试使用 ActivityScenario 与真实 Android 时钟。应用隐藏会解除 UI 服务绑定，测试通过预先取得的 Binder 检查内核变化，没有为后台页面重新绑定服务。

## 边界

- 仅验证 MuMu Android 15 / x86_64；未验证物理 ARM64 真机、Android 8、真实蜂窝切换、平板和大字体。
- 未验证节点测速、后台订阅更新、二维码、外部提供器、完整文件操作及全部覆写字段。
- 分页刷新与基本生命周期回归已通过；长期、高并发与服务进程死亡场景仍待补充。
- 没有公开发版。签名属性和密钥未进入 Git，尚未执行正式签名包验收。
- 测试结束后停止本应用服务和本机 fixture，移除 3 条 adb reverse；保留模拟器、应用和测试配置。
