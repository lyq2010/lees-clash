# 验证记录

更新日期：2026-10-27。结果仅覆盖表中场景，未完成项见 DEVELOPMENT-MATRIX。原始日志、截图和安装包已按清理范围移除；测试源码与固定工具链保留，可重新运行。

| 验证 | 结果与范围 |
|---|---|
| 固定原版 CMFA 基线 B02 | 独立构建、安装、导入、VPN、本机实际代理转发通过；未改原版源码 |
| 最终 Debug / Release | 构建通过；四 ABI 与 universal、R8、独立签名 / 包名 / minSDK / 内核检查通过 |
| 签名 Release 实际运行 | 独立 Android17 x86_64 模拟器，导入配置、VPN、本机 HTTP 实际转发通过 |
| MuMu API35 | 完整24项通过；最后安全区底色调整后，首页 / 反馈 / 导入重试 / 导航7项再验证通过 |
| JVM | 17项通过：格式1、分页4、诊断1、更新协议6、更新缓存5 |
| Go / QuickJS | 宿主10项通过；Android脚本转换 / 限时 / 限内存 / 非法输出 / 宿主访问拒绝通过；非2xx订阅响应测试通过 |
| 发布辅助脚本 | 11项通过；覆盖缺失签名、四ABI完整性、哈希、先包后更新源、失败不清理、旧版本边界及完整分页；Python / Bash语法通过 |
| Android8 API26 | 核心9项通过：网络、设备设置、扩展、备份 |
| Android17 API37 | 核心10项通过；Compose UI 用例被 Espresso 旧 InputManager 反射接口阻塞 |
| 真实配置 | DNS A/AAAA、HTTPS、策略与规则、测速、25个提供器及5类Geo更新通过；机场订阅开关导致更新无法可靠验收，由用户后测 |
| 大数据 / 生命周期 | 5000节点、5001规则、12组摘要 / 分页；65连接、后台停止采样、服务死亡恢复通过 |
| 内存 | UI 不映射第二份Go内核；Debug暖样本 UI93085KiB / 后台113443KiB；Release本机fixture样本 UI48110KiB / 后台63858KiB PSS。设备 / 配置 / 热度不同，不作为固定峰值或降幅 |
| 远端配置 | private仓、Actions开启且仅允许GitHub官方动作；4个旧工作流停用，8项Secrets配置成功；R2桶及对象列表只读通过 |

## 最终包身份

- applicationId `io.github.leenban.clash`；versionName `0.1.0`；versionCode `1`；minSDK26 / target35。
- 独立证书 SHA-256：`ea3502ded7c00df671f10f197483ab42caa26ce41a9fa61f529537e5503657e6`。
- universal Release APK SHA-256：`c2b7a7ad0a1f3638594c1010c08fcfff0a5e739d17aaf940d8f1ab8b2b077c80`。
- x86_64 Debug APK SHA-256：`80464992b798da3010dcc8a1c9717be85267db619b8dd4009a45dec4d28e8bd3`。
- 本地7个发布资产的完整性已核对；没有打标签、上传云对象或执行正式发布。

## 复现与保留范围

Windows 使用 scripts/Build.ps1；Android用例在 app/src/androidTest，JVM用例在 app/src/test，发版测试为 `python -B -m unittest discover -s tools/release/tests -v`。本机网络用例需要 scripts/local-fixture.py 及ADB reverse 18080 / 18081 / 13053，不使用机场订阅。

清理结束保留 MuMu开发包、用户配置与数据、源码测试、独立密钥和E盘备份、共享SDK及依赖缓存。停止本轮fixture与自建模拟器，撤销本轮端口映射；移除构建目录、原始测试产物和截图。旧源码 / 资源未删除；精确候选见 CLEANUP-CANDIDATES。

完整横屏 / 平板、按需真实网络切换、Geo成功恢复 / SAF、相机扫码 / 外部编辑器、加密订阅解密及全部系统入口仍待验收；真实蜂窝与非x86运行缺对应条件。远程发布门禁尚未实际执行，不能用本地结果代替。
