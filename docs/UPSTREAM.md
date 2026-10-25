# 来源与固定版本

本项目为 CMFA 的修改版本，自有界面、诊断桥接与工程身份修改始于 2026-10-26。原项目和第三方版权声明继续适用。

| 来源 | 固定版本 | 使用方式 |
|---|---|---|
| MetaCubeX/ClashMetaForAndroid | `1a4f949213fcbb7f0c9e2a4a21138642d84b8f22` | 导入源码快照；未导入签名密钥 |
| MetaCubeX/mihomo | `3f27ef39f8c96bbfbc8b0b9bb10f5c9ef60149d8` | `core/src/foss/golang/clash` 内的完整源码快照 |
| MetaCubeX/meta-rules-dat | Release ID `423062532`，2026-10-25 | 资源随仓保存，发布者 SHA-256 见 `resources/geodata/manifest.json` |
| MetaCubeX/go | Go 1.26.10 的 Android 工具链 | 本地 `I:\DevCache\go1.26`，不改主机 Go 安装 |

构建使用已保存的内核和 Geo 数据；不得自动拉取 Alpha 分支或以新下载的 latest 数据覆盖快照。需要升级时须显式修改锁定记录、校验文件，并重新验证运行行为。

源码中的外部 Go import、依赖坐标、配置关键字与必要版权声明属于真实技术或许可信息，不作为用户可见的上游推广内容清理。

本地验证使用的 Go Windows 归档 SHA-256：`2483b14ce111fa89ac671cdb00073c5b19670198be1334f491a8f3fe36affeb0`。
