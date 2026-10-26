# Lee’s Clash 开发交接

更新日期：2026-10-26。当前为可构建、可运行的开发初版，未达到完整产品与正式发版验收标准。逐项状态见 [开发矩阵](DEVELOPMENT-MATRIX.md)，证据见 [验证记录](VERIFICATION.md)。

## 当前工程

- 工作目录：`D:\Desktop\codex\lees-clash`；私有远程：`https://github.com/lyq2010/lees-clash`；分支 `main`；GitHub Actions 关闭。
- 应用名称 Lee’s Clash，applicationId / 自有 Kotlin namespace：`io.github.leenban.clash`；最低 API 26，target API 35，版本 `0.1.0`，versionCode `1`。
- Kotlin / Compose 应用层、Go 内核、C/JNI 桥接；自有 Go module 已改为 `lees`。
- CMFA `1a4f949213fcbb7f0c9e2a4a21138642d84b8f22`；mihomo `3f27ef39f8c96bbfbc8b0b9bb10f5c9ef60149d8`。完整内核作为源码快照纳入仓库，不依赖实际 Git 子模块检出。来源见 [UPSTREAM.md](UPSTREAM.md)。
- Geo 数据随仓保存，构建校验发布者 SHA-256，不再下载浮动 latest；更新应用时保留用户自行导入的数据。
- 未导入上游签名密钥；本地独立密钥在 `.secrets/release.p12`，属性在 `signing.properties`，均被 Git 忽略。必须另行离线备份，凭据不得提交。

## 已实现的开发代码

- 统一 Lucid 浅深色组件、矢量图标、首页 / 工具 / 更多、配置、策略、连接、规则、DNS、设置、覆写、文件、日志和关于页。
- Manifest 中的页面入口均接入 Compose；原 Design/XML 等旧实现仍保留，尚未进行文件删除。
- Repository / ViewModel / StateFlow 接入原服务；新增诊断接口经 Go → JNI → Binder 返回有界分页 JSON，流量使用完整 64 位值。
- 新增诊断 Binder 方法使用同步事务，调用侧通过 `withClash(Dispatchers.IO)` 在后台执行。
- 关闭动画时重现既有异步 Binder 的 Parcel/SIGABRT。已将 kaidl 1.15 的三个运行库源文件纳入 service，修复取消后迟到回调写入已回收 reply 的路径；KSP 编译器仍用 1.15。来源及 MIT 许可见 UPSTREAM / NOTICE；禁止再同时引入原版运行库 AAR。
- 首页直接显示单行配置名，长名称省略；使用独立矢量箭头，不添加“当前配置”标签。新增统一短动效与自有猫形信号图标，规范见 UI-DESIGN。
- 连接与规则分页由 DiagnosticPager 串行管理；刷新保留已加载范围，搜索使旧请求失效。详情与返回列表保留对应状态，后台停止轮询，恢复后重读当前页面。
- 原生版本信息改为固定来源元数据，避免依赖不存在的子模块 Git 分支。

## 本地构建

PowerShell 7 执行：

```powershell
.\scripts\Build.ps1 -Tasks ':app:assembleAlphaDebug',':app:assembleAlphaDebugAndroidTest',':app:testAlphaDebugUnitTest'
```

脚本在启动工具前设置当前会话 TEMP/TMP、Gradle、Android 和 Go 缓存路径，不修改机器或用户的持久环境。

- JDK 21：`I:\DevCache\jdk21`；定制 Go 1.26.10：`I:\DevCache\go1.26`。
- SDK：`I:\DevCache\android-sdk`，API 35 / build-tools 35.0.0 / NDK 29.0.14206865 / CMake 3.22.1。
- Build.ps1 已将后续 Gradle 项目缓存指向 I 盘；先前的 `.gradle`、`.kotlin` 目录未删除。Kotlin 项目持久缓存位置仍需统一核实。
- 单 ABI Debug APK 在 `app/build/intermediates/apk/alpha/debug/`；是 testOnly 包，安装需要 `adb install -r -t`。
- `ndk.dir` 是当前 Go 构建插件初始化的兼容项；移除它需要先处理插件取得 NDK 的时序，不能直接删除配置。

## 已执行验证

- 设计模块、完整应用 Kotlin、x86_64 原生内核、Debug APK 和 AndroidTest APK 构建通过。
- Go 诊断此前 4 项测试通过，本轮未修改 Go；当前 Kotlin 单元测试 5 项通过（格式化 1 项、分页 4 项）。
- 当前 MuMu 9 项测试通过：Binder 取消 / 重复回复 2 项，首页与动效 2 项，65 连接分页 1 项，真实生命周期 1 项，配置 / 原生网络 2 项，导航截图 1 项。
- 关闭 Android 动画时长后的导航与重建连续 3 次通过；结束后恢复原值 0.5。
- 网络用例覆盖 VPN 启动、选择策略、规则检索、DNS / TTL、SOCKS 转发、活动连接、单连接关闭和上下行统计。测试代码不使用真实订阅或外部代理服务器。
- 六张自动化截图与正常前台首页截图存于 `docs/verification/`。正常首页已确认显示 `Local integration test`，服务已停止。
- 测试服务与本轮端口映射均已关闭；MuMu、应用、测试 APK 和本地测试配置保留。

## 验证设备与复现

MuMu 实例 3“开发测试”，ADB `127.0.0.1:16480`，Android 15 / API 35，ABI `x86_64,arm64-v8a,x86`，1080×1920。

复现网络测试需要先启动 `scripts/local-fixture.py`，并为该实例建立 TCP 18080 / 18081 / 13053 的 adb reverse。三个服务只监听主机 127.0.0.1。测试应用需要 VPN 与通知权限。

测试类：`BinderCancellationTest`、`HomeUiTest`、`DiagnosticPagingTest`、`DiagnosticLifecycleTest`、`NativeIntegrationTest`、`NavigationSmokeTest`；通过 `androidx.test.runner.AndroidJUnitRunner` 执行。每次测试前同样设置 TEMP/TMP 为 I 盘。

```powershell
$env:TEMP='I:\DevCache\Temp'; $env:TMP=$env:TEMP
& 'I:\Android\Sdk\platform-tools\adb.exe' -s 127.0.0.1:16480 shell am instrument -w -e class io.github.leenban.clash.lucid.DiagnosticPagingTest io.github.leenban.clash.test/androidx.test.runner.AndroidJUnitRunner
```

`DiagnosticPagingTest` 使用 Compose 测试时钟推进前台定时刷新；`DiagnosticLifecycleTest` 使用真实 Android 时钟，并保留已取得的 Binder 来检查后台内核。应用隐藏时主动解除 UI 服务绑定，后台测试不能通过 Repository 等待重新绑定，否则会改变或阻塞被测流程。

## 下一阶段优先事项

1. 扩展长期运行、服务进程死亡及高并发分页压力验证；已有 65 连接分页、取消竞争与前后台恢复回归通过。
2. 补齐文件重命名、Geo 数据格式校验及高级功能完整映射；实测二维码、外部提供器、覆写保存、日志与加密配置。
3. 将 UI 参考逐屏对照推进到视觉验收，补平板、横屏、大字体、Android 8 和异常 / 权限拒绝矩阵。
4. 完成签名 Release、R8/JNI 和其余 ABI 构建与运行验证；不能用 Debug 结果替代。
5. 形成旧实现、资源、工作流与品牌元数据的精确删除清单；取得授权前不删除。重写隐私 / 贡献等尚未迁移的文档。
6. 公开发版仓、发布流水线尚未建立；Actions 维持关闭。APK-only 分发的 GPL 源码提供义务仍未解决，保留 LICENSE / NOTICE。

原版仅进行了首次构建尝试，因当时缺少 NDK 失败。当前 NDK 已安装、改版已成功构建；首次缺 NDK 的问题已经解除，B02 状态为待验证。原版尚未重新进行干净构建、安装及运行对照，是否存在其他构建兼容问题需实际重试确认；不能声称已验证与原版行为完全等价。
