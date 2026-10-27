# 构建与发布

更新日期：2026-10-27。版本由根`version.properties`维护：本次 **1.0.0 / code3**，补丁号范围0～49；后续版本名按该范围进位，versionCode必须单调递增。原0.1.0 / code1和0.0.1 / code2均为未发布开发构建。

## 本机工具与签名

Mac使用`/Volumes/DISK/Applications/lees-clash-toolchain`，共享缓存 / 下载 / AVD / 临时状态放`/Volumes/DISK/DevCache/lees-clash`。未安装全局Homebrew包或修改系统JDK、Python包、shell启动文件；入口只设置当前命令环境。项目构建目录在工作区，发版收尾按授权清理。

| 工具 | 固定版本 |
|---|---|
| JDK / Gradle | Temurin21.0.13+9 / Wrapper8.10.2 |
| Go | MetaCubeX Go1.26.10；按.github/patch检查所需补丁 |
| Android | SDK / Build Tools35，NDK29.0.14206865，CMake3.22.1 |
| Emulator | 37.2.12；API35 ARM64、API37.0 google_apis_ps16k ARM64 |
| 附加测试 | 外挂卷python venv，qrcode8.2 / Pillow13.0.0 / grpcio及grpcio-tools1.85.0；Markor2026.10.0只装测试AVD |

归档校验值：

- [Temurin JDK](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13_9.tar.gz)：`bf036b35b071bfb2f54f2c0af8ee8d88afa95ee6eb3dce02c0f94cfc2524d04a`。
- [MetaCubeX Go](https://github.com/MetaCubeX/go/releases/download/build/go1.26.darwin-arm64.tar.gz)：`18ec7eefd48747a8e4b0bcbdb5303c3ba6d5ae4469ae9de3a65e58c6557ebf89`，并核对go/VERSION。可变URL哈希不符时停止，不能直接跟随latest。
- [Android命令行工具](https://dl.google.com/android/repository/commandlinetools-mac_arm64-16111833_latest.zip)：`58da8f215781cc0208743e88b65ae259780c55e7eb40e50373d377cf369232d6`。
- [Markor APK](https://github.com/gsantner/markor/releases/download/v2026.10.0/net.gsantner.markor-v164-2026.10.0-flavorDefault-release.apk)：`c105bf5cc9530f7486b984323a185c17086b459a56ec936a5990ceb8da3d8440`。

使用用户提供的`independent-20261027`原P12备份，未另造密钥。证书SHA-256为`ea3502ded7c00df671f10f197483ab42caa26ce41a9fa61f529537e5503657e6`，与`tools/release/signing-certificate.sha256`锁定一致。原P12 / PEM / 恢复资料 / 校验和保持原外挂卷位置；旧签名备份保留。

工作区`signing.properties`是Git忽略链接，指向外挂卷`signing/signing.properties`，目录700 / 文件600；Java属性转义中文路径，密码不进入命令行或日志。缺签名构建直接失败，不回退Debug证书。

## 构建命令

```bash
bash scripts/Build.sh  # 默认ARM64 Debug
bash scripts/Build.sh :app:testAlphaDebugUnitTest :app:assembleAlphaDebug :app:assembleAlphaDebugAndroidTest -Pandroid.injected.build.abi=arm64-v8a --max-workers=4
# 不传注入ABI，得到一份包含四架构的通用Release：
GOMAXPROCS=4 bash scripts/Build.sh :app:assembleAlphaRelease --max-workers=4
source scripts/mac-env.sh
"$LEES_CLASH_TOOLCHAIN/python/bin/python" -B -m unittest discover -s tools/release/tests -v
```

Windows使用PowerShell7：`pwsh -File scripts/Build.ps1`，全Release为`./scripts/Build.ps1 -Tasks ':app:assembleAlphaRelease' -Abi all`，共享状态放I盘。Linux发版工具版本及下载哈希锁定在`tools/release/toolchain.json`。

注入单ABI的Debug / 测试包在`app/build/intermediates/apk/`，安装需`adb install -r -t`；常规通用包在`app/build/outputs/apk/alpha/{debug,release}/`，不可与旧intermediates产物混用。R8映射在`app/build/outputs/mapping/alphaRelease/`。Debug签名不能替代原签名Release验收。

## 本地复验

```bash
source scripts/mac-env.sh
# 缺组件时，仅安装到外挂卷：
sdkmanager --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'ndk;29.0.14206865' 'cmake;3.22.1' 'emulator' 'system-images;android-35;google_apis;arm64-v8a' 'system-images;android-37.0;google_apis_ps16k;arm64-v8a'
printf 'no\n' | avdmanager create avd -n lees-clash-api35-arm64 -k 'system-images;android-35;google_apis;arm64-v8a' -d pixel_5
emulator -avd lees-clash-api35-arm64 -port 5580 -no-window -no-snapshot -no-audio -gpu swiftshader -memory 2048
```

另一终端启动`"$LEES_CLASH_TOOLCHAIN/python/bin/python" -B scripts/local-fixture.py`，仅监听回环HTTP18080 / SOCKS18081 / DNS13053。安装包后为所选专用模拟器准备：

```bash
source scripts/mac-env.sh
serial=emulator-5580
adb -s "$serial" shell appops set io.github.leenban.clash ACTIVATE_VPN allow
adb -s "$serial" shell pm grant io.github.leenban.clash android.permission.POST_NOTIFICATIONS
for port in 18080 18081 13053; do adb -s "$serial" reverse tcp:$port tcp:$port; done
adb -s "$serial" shell am instrument -w -e class 'io.github.leenban.clash.lucid.NativeIntegrationTest#prepareLocalProfile' io.github.leenban.clash.test/androidx.test.runner.AndroidJUnitRunner
bash scripts/Verify-Overrides.sh "$serial"
bash scripts/Verify-Layouts.sh "$serial"
```

JUnit不保证方法顺序，必须单独执行准备方法并检查`OK (N tests)`，不能只看进程退出码。Release用独立AVD，避免Debug / 正式证书不一致而覆盖测试数据。签名包运行入口`"$LEES_CLASH_TOOLCHAIN/python/bin/python" -B tools/release/release_smoke.py --serial emulator-5582`，须先准备fixture和同三端口reverse。

- `OverrideCombinationsVerificationTest`共享70叶字段JSON夹具；Android Go测试用真实标签foss,with_gvisor,cmfa，不为Darwin增加平台桩。订阅 / DNS测试均用合成输入，不请求真实机场。
- `SystemDocumentWorkflowTest`需英文系统UI，保存到Downloads；按文档ID与精确名称查内置提供器，避免媒体索引延迟。全新AVD缺DAT时先导入测试DAT。
- Markor首次引导完成后测试外部编辑及合成日志分享；日志测试不传真实运行内容。模拟Wi-Fi的定位拒绝 / 允许两场景分别运行，不等同于物理网络。
- 相机：在外挂卷生成包含`http://127.0.0.1:18080/config`的RGB二维码；AVD加`-camera-back virtualscene -virtualscene-poster wall="$LEES_CLASH_CACHE/qr-import-rgb.png" -virtualscene-poster table="$LEES_CLASH_CACHE/qr-import-rgb.png" -grpc 8556 -grpc-use-token`，运行`Prepare-Camera.py`及相机测试。真实采集而非注入结果；gRPC令牌不得输出。
- 结束撤销本轮reverse并停止专用AVD / fixture。复验产物按当次清理授权处理，不能删除用户配置或签名恢复资料。

## 安装包体积

用户选择优先小APK并保留全部功能。仅Release设置`variant.packaging.jniLibs.useLegacyPackaging.set(true)`，原生库在APK压缩、安装时提取；行为见[Android Gradle API](https://developer.android.com/reference/tools/gradle-api/8.8/com/android/build/api/variant/JniLibsApkPackaging)与[Android打包说明](https://developer.android.com/guide/practices/page-sizes)。

| 架构 | 未压缩基线 MiB | 压缩后约 MiB | 减少 |
|---|---:|---:|---:|
| arm64-v8a | 89.63 | 46.27 | 48.38% |
| armeabi-v7a | 83.71 | 46.26 | 44.74% |
| x86 | 87.43 | 48.39 | 44.65% |
| x86_64 | 95.10 | 48.15 | 49.37% |
| universal | 286.43 | 119.59 | 58.25% |

以上为历史单ABI / 通用多包基线在相同源码下的精简对照，当前只发行通用APK；本轮首页功能增加少量代码，最终通用包的精确字节和哈希以本次发行记录为准。ZIP条目集合相同，全部.so、DEX和assets的SHA-256相同，唯一原始内容变化是Manifest提取标志。协议、扫码、QuickJS、加密、Geo及四ABI均保留；R8、资源裁剪和符号剥离原已启用，未删除运行时表。

历史ARM64单ABI APK＋原生库目录占用89.65→112.76 MiB（增加23.11）；通用包286.45→186.08 MiB（减少100.37），只提取当前ABI。此值不含ART、用户数据或全部文件系统开销，也不是内存测量。最终精确字节及哈希由Release资产和SHA256SUMS提供。

## 发版流程

参考[Music发行工作流](https://github.com/lyq2010/lees-music/blob/cbb5965b60d8b3c1f709e663a610e489f800a315/.github/workflows/release.yml)的顺序，保留本项目Go内核、四ABI通用包、独立签名与16KB门禁。

1. 更新version.properties、CHANGELOG及交接 / 矩阵，完成本地验证；发布说明从CHANGELOG对应章节提取。
2. 提交并推送main及`vMAJOR.MINOR.PATCH`标签，标签推送自动触发发行；手动指定已发布标签可重试镜像。流程先读取main的发行工具，校验私有仓和标签属于main历史，再判断是否已有完整不可变Release。
3. 新发行检出标签源码，移除临时构建机不用的预装.NET / PowerShell及NDK / CMake，确认至少20GiB可用后安装锁定工具；本机工具链不受影响。仅用锁定的GitHub官方Actions。构建缓存支持前缀恢复，并在测试失败时保存可复用依赖及Go编译缓存，避免每次失败重新冷编译。
4. 先执行Go宿主测试、单ABI Debug构建及限定回环夹具的隔离设备回归；通过后才恢复正式签名，执行JVM单测、完整Lint及通用Release构建，再实际运行原签名R8包。CMake依赖绑定到当前变体，单ABI Debug只编译一份Alpha内核，通用Release的四ABI各编译一次，不再附带Meta编译。
5. 审核R8映射、包名 / 版本、原证书、四ABI真实ELF CPU、64位LOAD至少16KB、APK zipalign及原生库压缩。只暂存并发布`lees-clash-<版本>.apk`、SHA256SUMS和latest-lees-clash.json，共三项资产；不生成或发布分架构APK。已有完整Release不覆盖。
6. 发布后COS / R2并行同步，镜像工具从main读取，便于修复后重试且保持发行标签不变。**发版验收禁止下载COS对象正文，包括APK、校验表和更新源**：上传启用`EnableMD5=True`，由服务端核对Content-MD5；之后只用HEAD核对大小、上传响应ETag和SHA-256 / versionCode元数据。ETag仅与上传响应比较，不当作MD5。R2继续完整回读校验SHA-256。COS已有更新源缺版本元数据或同版本摘要冲突时停止，不下载正文。
7. 新镜像验证完成后，按精确Clash文件名和旧版本清理旧包；分页信息缺失 / 非法、哈希或feed检查失败时不清理，不碰其他项目 / 嵌套对象 / GitHub历史Release。随后按授权直接删除本地临时、构建 / 测试 / 截图 / 验收和Android产物，保留签名、工具链与可复用依赖下载。

八项Secrets已配置：LEES_CLASH_KEYSTORE_BASE64、LEES_CLASH_STORE_PASSWORD、LEES_CLASH_KEY_PASSWORD、CF_API_TOKEN及四项TENCENT_COS配置。不得把值写入源码 / 日志。镜像流程使用锁定COS SDK和Wrangler，不安装到本机系统环境。

## 本次发行

- 版本：**1.0.0 / versionCode3**，标签`v1.0.0`；[私有Release](https://github.com/lyq2010/lees-clash/releases/tag/v1.0.0)。
- 范围：用户明确选择私有Release＋公开COS / R2镜像，不附源码包；仓库保持私有，LICENSE / NOTICE及原有许可继续保留。
- 本地验收：当前单通用包119.61MiB，原证书 / 四ABI CPU / 压缩 / 16KB审核通过；JVM19项、完整Lint零错误（62项非阻断警告）、发版脚本26项通过。API35 ARM64当前正式R8包验证导入、VPN实际转发、LAN端口、内存和DNS清理；分页主动加入额外连接后连续两次通过，生命周期回归通过。既有Go宿主12项、API37 / 16KB正式包证据保留。
- 远端状态：用户已明确恢复1.0.0提交与发版，本轮执行完整构建门禁和镜像校验，成功后登记结果与清理位置。
- 旧0.0.1仅为本地验收基线；1.0.0最终APK、更新源和SHA256SUMS由本轮发行产物提供，不使用旧包哈希。


## 流程同步修正

本次在资产发布前停止首轮任务，补齐了远端ELF / 16KB / 压缩打包门禁与R8映射检查，Go禁止自动切换工具链。COS与R2列表必须显式给出有效分页结束状态；信息缺失或分页不完整时不删除旧对象。新增构造ELF、缺失分页及COS禁止下载 / 元数据损坏 / 回滚 / 同版重试回归，发版辅助测试共26项；既有原签名基线也通过同一审核脚本。

首次完整Linux构建因磁盘剩余50MB而在复制原生库时失败，未发布资产；失败附件上传另受Actions存储额度限制。已增加临时构建机空间准备与20GiB门禁，并将失败诊断改为运行日志输出。该修正不删除其他仓库的Actions附件，也不要求扩大账户存储。

用户随后明确禁止COS下载，已在镜像上传前取消当轮任务；COS没有发生对象正文下载。上传摘要校验与HEAD元数据用法见[腾讯云MD5校验](https://cloud.tencent.com/document/product/436/36427)及[HEAD元数据查询](https://cloud.tencent.com/document/product/436/65828)。本机独立发行复核也只对COS发HEAD请求；完整APK重新下载与签名审核使用GitHub资产，不消耗COS对象下载流量。

Cloudflare分页结构见[官方List Objects](https://developers.cloudflare.com/api/resources/r2/subresources/buckets/subresources/objects/methods/list/)，COS分页语义见[官方列出对象说明](https://cloud.tencent.cn/document/product/436/65647)。以上修正不改变应用功能或签名；1.0.0标签在发布前对齐流程修正提交，已有完整Release保持不可覆盖。

构建耗时修复：原先CMake任务名前缀不含flavor，却同时附加Alpha / Meta依赖，导致Alpha Release执行8个Go编译、单ABI Debug执行2个。实际Gradle任务图现分别为4个 / 1个，Meta仍可独立构建；Go先于CMake执行。设备回归调整到正式包构建之前，分页 / 生命周期测试使用独立夹具域名，并主动加入额外连接验证隔离，避免设备背景连接数影响断言。

完整Lint补验发现并修正ClientApp将LocalContext强转Activity的问题，现使用现有Activity Compose依赖提供的LocalActivity；未屏蔽Lint错误或批量升级依赖。
