# 构建与发布

更新日期：2026-10-27。版本由根`version.properties`维护：本次 **0.0.1 / code2**，补丁号范围0～49；后续版本名按该范围进位，versionCode必须单调递增。原未发布0.1.0 / code1仅为开发构建。

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
# 不传注入ABI，得到四架构与通用Release：
GOMAXPROCS=4 bash scripts/Build.sh :app:assembleAlphaRelease --max-workers=4
source scripts/mac-env.sh
"$LEES_CLASH_TOOLCHAIN/python/bin/python" -B -m unittest discover -s tools/release/tests -v
```

Windows使用PowerShell7：`pwsh -File scripts/Build.ps1`，全Release为`./scripts/Build.ps1 -Tasks ':app:assembleAlphaRelease' -Abi all`，共享状态放I盘。Linux发版工具版本及下载哈希锁定在`tools/release/toolchain.json`。

单ABI Debug / 测试包在`app/build/intermediates/apk/`，安装需`adb install -r -t`；普通多ABI包在`app/build/outputs/apk/alpha/{debug,release}/`，不可与旧intermediates产物混用。R8映射在`app/build/outputs/mapping/alphaRelease/`。Debug签名不能替代原签名Release验收。

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

JUnit不保证方法顺序，必须单独执行准备方法并检查`OK (N tests)`，不能只看进程退出码。Release用独立AVD，避免Debug / 正式证书不一致而覆盖测试数据。签名包运行入口`python3 -B tools/release/release_smoke.py --serial emulator-5582`，须先准备fixture和同三端口reverse。

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

以上为首次精简时相同源码R8结果的对照；本轮首页功能增加少量代码，最终精确字节和哈希见下表。ZIP条目集合相同，全部.so、DEX和assets的SHA-256相同，唯一原始内容变化是Manifest提取标志。协议、扫码、QuickJS、加密、Geo及四ABI均保留；R8、资源裁剪和符号剥离原已启用，未删除运行时表。

ARM64 AVD的APK＋原生库目录占用89.65→112.76 MiB（增加23.11）；通用包286.45→186.08 MiB（减少100.37），只提取当前ABI。此值不含ART、用户数据或全部文件系统开销，也不是内存测量。最终精确字节及哈希由Release资产和SHA256SUMS提供。

## 发版流程

1. 更新version.properties、CHANGELOG及交接 / 矩阵，完成本地门禁；发版说明从CHANGELOG匹配版本章节提取，不维护重复副本。
2. 按明确授权提交并推送main，创建`vMAJOR.MINOR.PATCH`标签；工作流校验私有仓和main祖先关系。首次0.0.1已解除旧脚本0.1.0下限。
3. 手动触发`.github/workflows/lees-clash-release.yml`并传tag。仅允许GitHub官方Actions；从独立Secrets恢复签名，固定Go / Android工具，验证JVM、Go、Android流程、R8实际转发和四ABI身份 / 签名。
4. 发布五APK、SHA256SUMS和latest-lees-clash.json到本私有仓Release。已有完整版本不可覆盖，只允许重试镜像。
5. COS主`https://releases.angelolee.cn`、R2备`https://plt-releases.leenbsl.workers.dev`先上传APK与校验表，实际验证大小 / SHA后发布更新源，再验证更新源。
6. 仅在新镜像完全通过后按精确Clash文件名和旧版本筛选清理旧包；分页失败、哈希失败或feed失败则不清理，不碰其他项目 / 嵌套对象 / GitHub历史Release。
7. 远端成功并校验后，按本次授权移走本地临时文件、构建 / 测试产物、截图和测试AVD；保留签名、工具链与可复用依赖下载。

八项Secrets已配置：LEES_CLASH_KEYSTORE_BASE64、LEES_CLASH_STORE_PASSWORD、LEES_CLASH_KEY_PASSWORD、CF_API_TOKEN及四项TENCENT_COS配置。不得把值写入源码 / 日志。镜像流程使用锁定COS SDK和Wrangler，不安装到本机系统环境。

## 本次发行

- 版本：**0.0.1 / versionCode2**，标签`v0.0.1`；[私有Release](https://github.com/lyq2010/lees-clash/releases/tag/v0.0.1)。
- 范围：用户明确选择私有Release＋公开COS / R2镜像，不附源码包；仓库保持私有，LICENSE / NOTICE及原有许可继续保留。
- 本地验收：新增首页与工具功能后，四ABI / 通用原签名R8重建3m33s通过；JVM19项、Go宿主12项、发版脚本13项通过。五包原证书 / CPU / 16KB对齐通过；API35 ARM64与API37 / 16KB正式包均验证导入、VPN实际转发、LAN端口、内存和DNS清理。
- 远端状态：用户要求暂缓发版后，已按全部授权实现首页入口和工具页补齐；仍未推送 / 打标签 / 触发Actions / 发布。当前正式包保留供验看，远端成功后的最终产物清理尚未执行。

### 首页补齐后的本地包

最新五包暂存：`/Volumes/DISK/DevCache/lees-clash/release-001-verified-20261027-31b94b2c`。原证书和16KB ZIP / 64位ELF对齐全部通过，R8映射SHA-256：`f965383b6e2b8f070fb317df5d9a28777cb99c9a2be5700b03d739577376ec6c`。

| 架构 | MiB | SHA-256 |
|---|---:|---|
| arm64-v8a | 46.29 | `0d11ac92b8f7f56e0ff64bc930eafca25e5048dbe445ac658023c74df9032d85` |
| armeabi-v7a | 46.28 | `754792e346ab1f4d68f944ddf56d0bffe94047f4d93da62cdd050b7f3e1d57d9` |
| universal | 119.61 | `6ade09ad024a39d7d293c39ebeae7bda67e888db2c68a011605bfab13b452343` |
| x86 | 48.41 | `d35d0dfb9172f99ae8dbc0d1408bbb0a2633f61f17da37908a69551097b81e78` |
| x86_64 | 48.17 | `dd5f0d9f20bec3d32ecbc172b36efcafaf839fb1870440fb86351e267769cc68` |

构建和复验日志在外挂卷：`build-home-release.log`、`home-release-api35.log`、`home-release-api37.log`；新R8设备复验包含导入 / VPN实际转发、LAN真实端口、内存详情与DNS缓存清理。用户恢复发版前不要触发远端流程。
