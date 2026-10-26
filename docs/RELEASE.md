# 构建与发布

更新日期：2026-10-27。

源码、构建和 GitHub Release 均在私有仓 `lyq2010/lees-clash` 完成，不再创建公开发版仓。Actions 已开启，策略为 selected，仅允许 GitHub 官方动作；四个旧上游工作流已停用。新流程仅支持手动触发，工作流已纳入本次提交，尚未执行远程发布。

## 本地构建

```powershell
.\scripts\Build.ps1
.\scripts\Build.ps1 -Tasks ':app:assembleAlphaRelease' -Abi all
```

PowerShell 7；脚本显式设置 TEMP / TMP、Gradle、Kotlin、Android、Go 缓存到 `I:\DevCache`。工具链锁定见 `tools/release/toolchain.json`：JDK21、SDK35、build-tools35.0.0、NDK29.0.14206865、CMake3.22.1、定制 Go1.26.10。

## 独立签名与备份

当前签名为 2026-10-27 新生成的 Clash RSA4096 密钥，alias `lees-clash`，没有复用其他项目。私钥与属性均被 Git 忽略；缺少正式签名时构建直接失败。

公开证书 SHA-256：`ea3502ded7c00df671f10f197483ab42caa26ce41a9fa61f529537e5503657e6`。

备份目录：`E:\家用资料\恢复码\lees-release-backup\lees-clash\independent-20261027`，P12 / PEM / 恢复属性 / 校验和已逐字节核对。旧密钥另存 previous-standalone-20261027，未删除。密码和私钥不得进入文档、日志或产物。

## 手动发版流程

1. 完成矩阵门禁，更新中文说明、versionName 和单调递增 versionCode。版本补丁号仅 0～49，例如 0.1.49 → 0.2.0。
2. 收到明确推送与发布指令后，再推送 main 和指定 `vMAJOR.MINOR.PATCH` 标签。工作流校验私有仓及 main 祖先关系。
3. 在 Actions 手动选择 `Lee's Clash private release` 并输入标签；签名从独立 Secrets 恢复到临时 runner，构建并测试 Debug 和四 ABI / universal Release，验证 R8 实际代理运行、包名、签名和内核。
4. 将五个 APK、SHA256SUMS、latest-lees-clash.json 发布到本私有仓 Release；同版本完整产物只重试镜像，不覆盖已发布内容。
5. COS 主通道 `https://releases.angelolee.cn` 与 R2 备用 `https://plt-releases.leenbsl.workers.dev` 独立上传、验证包大小及哈希，再发布并验证更新源。
6. 验证成功后仅清理对应存储根目录中更旧的 `lees-clash-版本[-ABI].apk` 与校验文件。完整分页失败、校验失败或更新源失败时不清理；保留其他项目、嵌套目录及新版本。GitHub 历史 Release 不在清理范围。

已配置 Secrets：LEES_CLASH_KEYSTORE_BASE64、LEES_CLASH_STORE_PASSWORD、LEES_CLASH_KEY_PASSWORD、CF_API_TOKEN、TENCENT_COS_BUCKET、TENCENT_COS_REGION、TENCENT_COS_SECRET_ID、TENCENT_COS_SECRET_KEY。COS 来自用户指定的 Toolbox 备份中四个同名文本文件。R2 账户唯一可发现、目标桶及对象列表只读验证通过；COS SDK 未在本机安装，真实写入与清理需远程流程验收。

当前不得将本地通过等同于 CI 已通过。尚未进行真实对象上传 / 删除或正式发版。公开 APK 分发的 GPL 对应源码安排仍未解决，保留 LICENSE / NOTICE，鸣谢不能替代源码义务。
