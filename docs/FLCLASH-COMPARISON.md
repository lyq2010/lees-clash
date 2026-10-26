# FlClash 功能对照

更新时间：2026-10-27。对照 `chen08209/FlClash` 提交 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`。差距清单不是完成声明。

固定约束：Kotlin / Compose、Android 服务与 mihomo；首页整体布局和风格保持，允许收敛重复入口；低内存作为验收项。WebDAV 不做，仅保留本地备份 / 恢复。未复制 Flutter 页面、字体、图标或运行时。

| 功能 | 源码依据 | 当前差距 |
|---|---|---|
| 配置导入 / 编辑 / 更新 | `lib/views/profiles/` | 已有；本机更新闭环通过；真实订阅因机场开关由用户后测 |
| 订阅信息 | `lib/views/dashboard/widgets/profile_detail.dart` | 已补流量 / 剩余 / 到期，待验收 |
| 策略与测速 | `lib/views/proxies/` | 已有；继续比对筛选 / 排序 / 自动组体验 |
| 连接 / DNS / 流量 / 日志 | `lib/features/connection/`、`lib/views/` | 手动 IP / HTTP 检查、可选20条历史及服务返回的风险字段已补，不虚构质量评分 |
| 本地备份恢复 | `lib/common/backup.dart` | 流式 ZIP、规模 / 路径校验、预览与合并恢复已补；Geo / SAF 完整交互待验收 |
| WebDAV | `lib/common/webdav.dart` | 不做 |
| 自定义规则 / 代理 / 分组 | `lib/views/profiles/custom/` | 结构化编辑与独立扩展已补，更新保留扩展、运行 / 停机生效通过 |
| 配置扩展脚本 | `lib/views/profiles/extend/` | 完整 JSON 扩展与受限 QuickJS 配置脚本已补；异常 / 限时 / 限内存通过 |
| 按需网络规则 | `lib/views/config/on_demand.dart` | SSID / 蜂窝暂停 VPN 与恢复已补；真实切换待验收 |
| 资源与 Geo | `lib/views/config/custom_resources.dart` | 五类 Geo 更新 / 校验已补；完整资源管理待对照 |
| 网络 / TUN / NTP | `lib/views/config/network.dart`、`tun.dart`、`ntp.dart` | 通过完整 JSON 扩展提供原生字段；不宣称全部字段交互已验收 |
| User-Agent 管理 | `lib/views/config/user_agents.dart` | 全局 User-Agent 可经高级扩展配置；专用预设管理页未做 |
| 磁贴 / 自动化 | `README_zh_CN.md`、`android/` | 已有；完整系统入口回归待做 |
| 多屏幕与主题 | `lib/ui/` | 现有浅深色保留；横屏 / 平板 / 全页面大字体待验收 |

验收状态见 [开发矩阵](DEVELOPMENT-MATRIX.md)。内核支持某字段不等于应用已提供完整操作流程。
