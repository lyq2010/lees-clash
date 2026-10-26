# Lee’s Clash 开发交接

更新日期：2026-10-27。当前为开发版，完整产品与正式发版尚未全部验收；逐项见 [开发矩阵](DEVELOPMENT-MATRIX.md)。

## 工程与协作边界

- 当前目录 `D:\Desktop\codex\lees-clash`，私有远程 `lyq2010/lees-clash`，main。
- Kotlin / Compose 应用层、Go 内核、少量 C/JNI；自有标识 `io.github.leenban.clash`，最低 API26、target35，0.1.0 / versionCode1。
- CMFA `1a4f949213fcbb7f0c9e2a4a21138642d84b8f22`，mihomo `3f27ef39f8c96bbfbc8b0b9bb10f5c9ef60149d8`，源码快照与固定 Geo 随仓保存。来源 / 许可见 UPSTREAM、NOTICE。
- 首页布局与 Lucid 风格保持；配置名单行，三角紧跟标题，长名截断；不加长按提示。成功用底部短 Toast，错误 / 延迟进度用不改变布局的顶部浮层，长错误可展开模态详情；危险操作二次确认。
- **推送、标签和发布分别按明确授权执行；未授权不删除文件。** 私有源码与发版现均在本仓；不再建立公开发版仓。
- 真实机场更新受订阅开关影响，由用户后测；禁止继续用该接口做自动回归。WebDAV 不做。

## 本轮实现

- 配置导入 / 更新 / 编辑的真实终态反馈，停机策略与规则浏览，选择预存；修复 kaidl 取消后迟到 Parcel 回复（本地 MIT 源码补丁）。
- 五类 Geo 单项 / 全部在线更新及文件导入，流式下载、格式校验、失败保留原文件。
- 本地流式 ZIP 备份 / 预览 / 合并恢复；规模、路径、配置和 Geo 校验，重复配置跳过，失败回滚受影响内容。备份不加密，含订阅链接与私钥。
- 独立配置扩展、结构化规则 / 代理 / 分组、高级 JSON 和 QuickJS 配置转换。脚本 VM 临时创建、32MB 堆 / 500ms，不提供宿主网络与文件能力。
- 默认关闭的按需网络；手动 IP / HTTP / 强制直连检查及可选本地20条历史；订阅流量 / 到期展示；文件重命名与外部编辑返回校验。
- 版本 / age 密钥调用转至服务，UI 无第二份 Go 映射；分组摘要不加载所有节点，进入组才读节点；页面可见采样、流式 / 有界读入。
- 修复 TUN 关闭锁等待、启动 / 停止广播竞争、网络事件覆盖和服务死亡重连。服务停止通过 stop latch 协调，不能简单替换为 stopService。
- Music 协议的更新器已移植，使用平台 HTTPS、有界更新源、流式 APK、镜像一致性 / SHA / 包名 / 版本 / 签名验证及用户安装授权；手动检查，不启动自动请求。

## 本地构建与验证

```powershell
.\scripts\Build.ps1 -Tasks ':app:assembleAlphaDebug',':app:assembleAlphaDebugAndroidTest',':app:testAlphaDebugUnitTest'
.\scripts\Build.ps1 -Tasks ':app:assembleAlphaRelease' -Abi all
```

脚本显式设置 TEMP / TMP、Gradle / Kotlin / Android / Go 缓存到 I 盘。JDK21、SDK35、NDK29.0.14206865、CMake3.22.1、定制 Go1.26.10。单 ABI Debug 是 testOnly，使用 `adb install -r -t`。

- MuMu 实例3，`127.0.0.1:16480`，API35 x86_64；emulator-5560 是同一实例，不能当成第二设备。
- 官方无窗口 API26 / API37 AVD 用于兼容验证，数据在 I 盘。用户接受 MuMu 作为本机设备；不冒称已验证物理 ARM 或真实蜂窝。
- 主机 fixture `scripts/local-fixture.py` 只监听回环，HTTP18080 / SOCKS18081 / DNS TCP13053；ADB reverse 同三端口。测试在 finally 恢复选中配置与设置，保留测试配置。
- MuMu 完整24项通过，含 UI 无第二内核映射；安全区调整后7项再验证通过；JVM17项、发版脚本11项、Go宿主10项、Android脚本限制测试通过。
- API26核心9项通过；API37核心10项通过，Compose页面用例因 Espresso InputManager 反射不兼容失败。最终 UI 调整、四 ABI / R8 / 签名和实际转发均通过，包哈希见 VERIFICATION。
- B02 已解除：固定原版独立构建、安装、导入、VPN与本机实际转发均通过。原版包保留但停止。
- Debug 单次暖启动样本 UI93085KiB、后台113443KiB PSS；仅作样本，不声称固定峰值或严格改善百分比。

## 签名与 Actions

2026-10-27 重新生成 Clash 独立 RSA4096，alias lees-clash；没有复用 Music / Toolbox 密钥。当前 `.secrets/lees-clash-release-20261027.p12` 与 signing.properties 已忽略。公开证书指纹见 tools/release/signing-certificate.sha256。

备份 `E:\家用资料\恢复码\lees-release-backup\lees-clash\independent-20261027` 已逐字节核对；旧签名单独备份并保留。八项 Secrets 已获授权并配置成功。Actions 已开启、仅允许 GitHub 官方动作，四个旧工作流已停用；新工作流已纳入本次提交，尚未运行。细节及手动流程见 RELEASE。

## 尚未完成

真实机场更新由用户后测；完整平板 / 横屏 / 大字体、按需真实切换、Geo成功恢复与SAF、相机扫码 / 外部编辑器、加密配置解密、磁贴 / 快捷方式仍保留待验证。真实蜂窝 / 非x86运行需要相应条件。远程完整发版与镜像写入清理等待正式发布授权；公开分发 GPL 对应源码安排仍未解决。

旧文件未删除；四个已停用工作流候选见 CLEANUP-CANDIDATES，XML / 资源有编译引用，不能直接目录删除。测试前私有数据备份 `I:\DevCache\lees-clash-private-preverification-20261026.tar`，不纳入 Git；不要删除用户配置。临时 fixture / reverse / 自建模拟器与原始测试产物已清理，验证JSON摘要保留，见 VERIFICATION。
