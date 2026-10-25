# Lee’s Clash 开发交接

更新日期：2026-10-26。本文记录已验证的工程事实，任务状态见 [开发矩阵](DEVELOPMENT-MATRIX.md)。

## 已确定的产品约束

- Kotlin / Jetpack Compose 重做全部用户界面；Go mihomo 内核及 C/JNI 桥接保留。
- 应用 Lee’s Clash，applicationId / 自有 namespace 为 `io.github.leenban.clash`，最低 API 26。
- Surge iOS Lucid 彩色首页，首页 / 工具 / 更多导航；不混用默认 Material 外观。
- 保留现有配置、服务及高级功能，增加连接、规则、DNS 诊断；不承诺 HTTP 内容抓包或 Surge 配置兼容。
- 私有开发仓、公开产物仓；发布源码义务尚未解决，鸣谢不能替代 GPL 要求。保留许可证、版权和第三方声明。
- 删除文件必须先提交精确清单并取得用户确认；尚未进行删除。导入时没有引入上游 release.keystore。
- 用户指定使用已打开的 MuMu“开发测试”实例验证；不得重置、清空或关闭用户模拟器。

## 源码与环境

- 工作目录：`D:\Desktop\codex\lees-clash`，起始为空目录，已导入源码并初始化本地 Git；私有远程仓为 `lyq2010/lees-clash`，Actions 按用户要求禁用。首次推送为开发中快照。
- CMFA 固定提交：`1a4f949213fcbb7f0c9e2a4a21138642d84b8f22`。
- mihomo 固定提交：`3f27ef39f8c96bbfbc8b0b9bb10f5c9ef60149d8`，以源码快照导入 `core/src/foss/golang/clash`；构建不得自动追踪 Alpha 分支。
- 原始源码归档位于 `I:\DevCache\lees-clash-cmfa-1a4f949.tar.gz` 和 `I:\DevCache\lees-clash-mihomo-3f27ef3.tar.gz`，可用于逐文件比较，不能用来覆盖后续修改。
- JDK 21：`I:\DevCache\jdk21`；Android SDK 验证目录：`I:\DevCache\android-sdk`；已有 SDK：`I:\Android\Sdk`。
- 每次构建设置 TEMP / TMP 为 `I:\DevCache\Temp`，GRADLE_USER_HOME 为 `I:\DevCache\gradle`，Android 用户缓存与 AVD 也使用 I 盘。
- 主机 Go 为 1.27.0；上游 CI 使用定制 Go 1.26 及 Android 补丁，不能据此推断原版工具链可直接替换。
- MuMu 安装路径：`D:\Program Files (x86)\Netease\MuMuPlayer`；“开发测试”实例 index 3，ADB `127.0.0.1:16480`；Android 15 / API 35，ABI `x86_64,arm64-v8a,x86`，1080×1920。

## 当前验证与阻塞

- 原版首次构建未通过：NDK 尚未配置。日志 `I:\DevCache\lees-clash-baseline-build.log`。
- SDK 下载最初因默认 `.android/cache` 不可写失败，已将 `ANDROID_USER_HOME` 改到 I 盘；随后改用官方 SDK 归档安装 API 35 / build-tools 35.0.0 / CMake 3.22.1 / NDK 29；均在 I 盘。
- 尚未完成原版 APK 安装、VPN 或代理功能验收，不得标记基线通过。
- 原工程 CMake 通过 Git 子模块推导版本；源码快照构建需要改为读取固定的来源元数据。

## 继续工作的顺序

1. 核对设计模块编译结果，继续完整应用集成，保留原版构建失败的真实证据。
2. 实现统一 Compose 组件及可交互原型，生成与官方 Lucid 参考截图的对照。
3. 接通服务 Repository / ViewModel 及 Go→JNI→Binder 诊断链，迁移所有功能入口。
4. 完成身份、签名和双仓流水线，列明可删除文件；授权前不删除。
5. 验证 Debug / Release / ABI / 模拟器流程，回填矩阵和中文 CHANGELOG。

## 交接维护规则

每项状态必须附可复核的文件、命令、日志或截图。区分“已实现”“构建通过”“运行通过”“待验证”，不将生成文件、按钮存在或模拟数据当作端到端完成。

## 本次已落地的开发中代码

- 新命名空间与 JNI 符号已替换；原目录尚未搬移或删除。
- `design/.../lucid` 新增主题、矢量图标和通用组件。
- `app/.../lucid` 新增 Repository、ViewModel、首页 / 工具 / 更多 / 策略 / 诊断页面。主入口接入与完整设置页面仍待完成。
- `core/.../native/diagnostics` 与 JNI / Binder 诊断入口已实现，尚未进行 Go 编译及运行验证。
- `:design:compileAlphaDebugKotlin` 已通过（61 个任务，3 分 17 秒）；日志 `I:\DevCache\lees-clash-design-compile.log`。这仅验证设计模块及其依赖，不等于应用或原生内核构建通过。
