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

## 本地 kaidl 运行库补丁

`service/src/main/java/com/github/kr328/kaidl` 保存 Maven `com.github.kr328.kaidl:kaidl-runtime:1.15` 的三个源文件，来源归档 SHA-256 为 `15431dd467e82082c2cd79bb3bd4d6d3ddd8addf3d4b6667976b58aae1eb1837`。保留原包名供 1.15 KSP 生成代码引用，未更换线协议。

本地修改：完成回调先复制到独立 Parcel，成功恢复调用协程后再复制进调用者 reply；取消时释放未交付快照；重复完成、取消及死亡信号用原子状态仲裁。避免取消后由迟到回调改写已经回收的 Parcel。原 AAR 已从 service 运行依赖中移除，不能与这些源文件同时打包。

MIT 许可保存在同目录 LICENSE 并收入根 NOTICE。许可原文来源：[kaidl/LICENSE](https://github.com/kr328/kaidl/blob/master/LICENSE)。

## 配置脚本引擎与视觉参考

QuickJS 固定官方 `2026-06-04` 源码，归档 SHA-256 为 `b376e839b322978313d929fd20663b11ba58b75df5a46c126dd19ea2fa70ad2a`，来源：https://bellard.org/quickjs/quickjs-2026-06-04.tar.xz 。仅纳入引擎及必要头文件，不纳入 CLI、标准库或系统接口；源文件未修改。MIT 原文保存在 `core/src/main/golang/native/script/quickjs/LICENSE` 并收入 NOTICE。自有 CGo 桥接使用临时运行时和显式内存 / 时间限制，不引入常驻 WebView。

FlClash `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 仅用于 Android 功能对照，未复制 Flutter 实现。刷新图标按用户提供的圆弧箭头参考手绘；未复制 Surge、morphicons 或 Lucide 资产，无新增这些图标库的许可依赖。

## 自有项目复用

更新协议、缓存 / 安装校验及发版顺序参考 `lyq2010/lees-music` 的 `d305079`。Clash 使用独立签名；未修改 Music 项目，未复制其凭据。传输改用 Android HTTPS，避免引入额外 HTTP 运行库。
