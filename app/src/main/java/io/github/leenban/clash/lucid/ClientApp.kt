package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.design.model.DarkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ClientApp(
    vm: ClientViewModel,
    initialRoute: String = "home",
    toggle: () -> Unit,
    importFile: (String?) -> Unit,
    scan: () -> Unit,
    external: () -> Unit,
    export: (String, String) -> Unit,
    importGeo: (String) -> Unit,
    finish: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var stack by rememberSaveable { mutableStateOf(listOf(initialRoute)) }
    val route = stack.last()
    val kind = route.substringBefore('/')
    var themeRevision by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val confirm: (String, () -> Unit) -> Unit = { message, action ->
        confirmation = message to action
    }
    val navigate: (String) -> Unit = { next -> if (next != route) stack = stack + next }
    val back: () -> Unit = {
        if (stack.size > 1) stack = stack.dropLast(1) else if (initialRoute != "home") finish()
    }
    val owner = LocalLifecycleOwner.current
    val holder = rememberSaveableStateHolder()
    LaunchedEffect(owner, route) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                vm.navigate(route)
                while (true) {
                    vm.refresh()
                    delay(1000)
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) { vm.detach() }
            }
        }
    }
    BackHandler(stack.size > 1 || initialRoute != "home", onBack = back)
    val theme = remember(themeRevision) { vm.repository.ui.darkMode }
    val dark =
        when (theme) {
            DarkMode.Auto -> androidx.compose.foundation.isSystemInDarkTheme()
            DarkMode.ForceLight -> false
            else -> true
        }
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
                .apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
        }
    }
    LucidTheme(dark) {
        val c = LocalLucidColors.current
        Column(
            Modifier.fillMaxSize()
                .background(c.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (state.error != null)
                Row(
                    Modifier.fillMaxWidth()
                        .background(c.red.copy(alpha = .12f))
                        .clickable { vm.clearError() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.error.orEmpty(),
                        Modifier.weight(1f),
                        color = c.red,
                        fontSize = 14.sp,
                    )
                    Text("关闭", Modifier.padding(start = 10.dp), color = c.red, fontSize = 13.sp)
                }
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 600.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide)
                        Column(Modifier.width(290.dp).fillMaxHeight()) {
                            Box(Modifier.weight(1f)) {
                                HomeScreen(state, navigate, toggle, vm::setMode)
                            }
                            TabBar(if (kind == "more") "more" else "home") { stack = listOf(it) }
                        }
                    LucidPageStack(stack, Modifier.weight(1f).fillMaxHeight()) { visibleRoute ->
                        val visibleKind = visibleRoute.substringBefore('/')
                        val argument = Uri.decode(visibleRoute.substringAfter('/', ""))
                        holder.SaveableStateProvider(visibleRoute) {
                            when (visibleKind) {
                                "home" ->
                                    if (wide) ToolsScreen(navigate)
                                    else HomeScreen(state, navigate, toggle, vm::setMode)
                                "tools" -> ToolsScreen(navigate)
                                "more" -> MoreScreen(navigate)
                                "profiles" -> ProfilesScreen(state, vm, navigate, back)
                                "import" ->
                                    ImportScreen(
                                        vm,
                                        back,
                                        { importFile(null) },
                                        scan,
                                        external,
                                        argument,
                                    )
                                "profile" ->
                                    ProfileScreen(state, vm, navigate, back, export, confirm)
                                "files" ->
                                    FilesScreen(
                                        argument,
                                        state,
                                        vm,
                                        navigate,
                                        back,
                                        { importFile(it) },
                                        export,
                                        confirm,
                                    )
                                "groups" -> GroupsScreen(state, navigate, back)
                                "nodes" -> NodesScreen(argument, state, vm, back)
                                "providers" -> ProvidersScreen(state, vm, back)
                                "connections" ->
                                    ConnectionsScreen(state, vm, navigate, back, confirm)
                                "connection" -> ConnectionScreen(state, vm, back, confirm)
                                "rules" -> RulesScreen(state, vm, back)
                                "dns" -> DnsScreen(state, vm, back)
                                "stats" -> StatsScreen(state, back)
                                "logs" -> LogsScreen(state, back)
                                "network" -> NetworkScreen(state, vm, navigate, back)
                                "apps" -> AppsScreen(state, vm, back)
                                "settings" ->
                                    SettingsScreen(vm, state, back, { themeRevision++ }, confirm)
                                "override",
                                "dns-settings" ->
                                    OverrideScreen(
                                        state,
                                        vm,
                                        visibleKind == "dns-settings",
                                        back,
                                        confirm,
                                    )
                                "about" -> AboutScreen(state, back) { navigate("license/$it") }
                                "license" -> AssetTextScreen(argument, vm, back)
                                "help" -> HelpScreen(back)
                                "geodata" -> GeoDataScreen(back, importGeo)
                                "keys" -> KeyScreen(vm, back)
                                "broken",
                                "crashed" ->
                                    Column {
                                        LucidHeader("应用状态", back)
                                        LucidEmpty(
                                            if (visibleKind == "broken") "安装包不完整" else "后台服务意外退出",
                                            "请重新打开应用，或重新安装完整 APK。",
                                        )
                                    }
                            }
                        }
                    }
                }
            }
            if (kind in listOf("home", "tools", "more"))
                BoxWithConstraints { if (maxWidth < 600.dp) TabBar(kind) { stack = listOf(it) } }
        }
        confirmation?.let { (message, action) ->
            Dialog(onDismissRequest = { confirmation = null }) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .padding(top = 20.dp)
                ) {
                    Text(
                        message,
                        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = c.text,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                    )
                    LucidRow(
                        "确认",
                        onClick = {
                            confirmation = null
                            action()
                        },
                    )
                    LucidRow("取消", onClick = { confirmation = null })
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: String, select: (String) -> Unit) {
    val c = LocalLucidColors.current
    Row(Modifier.fillMaxWidth().background(c.surface.copy(alpha = .96f))) {
        listOf(
                Triple("home", "首页", LucidSymbol.Home),
                Triple("tools", "工具", LucidSymbol.Tools),
                Triple("more", "更多", LucidSymbol.More),
            )
            .forEach { (route, title, symbol) ->
                Column(
                    Modifier.weight(1f)
                        .heightIn(min = 58.dp)
                        .lucidClickable { select(route) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val tint by
                        animateColorAsState(
                            if (selected == route) c.blue else c.secondary,
                            tween(160),
                            label = "navigation-tab",
                        )
                    LucidIcon(symbol, tint, Modifier.size(25.dp))
                    Text(title, Modifier.padding(top = 3.dp), color = tint, fontSize = 10.sp)
                }
            }
    }
}

@Composable
private fun AssetTextScreen(name: String, vm: ClientViewModel, back: () -> Unit) {
    var text by remember(name) { mutableStateOf("读取中") }
    LaunchedEffect(name) {
        vm.task {
            text =
                withContext(Dispatchers.IO) {
                    vm.repository.context.assets.open(name).bufferedReader().use { it.readText() }
                }
        }
    }
    LicenseScreen(name, text, back)
}

@Composable
private fun GeoDataScreen(back: () -> Unit, import: (String) -> Unit) {
    Column {
        LucidHeader("Geo 数据", back)
        LucidSection("从文件导入", footer = "选择可信的 Geo 数据文件；导入后重新加载配置使其生效。") {
            listOf(
                    "geoip.metadb" to "GeoIP 数据",
                    "geosite.dat" to "GeoSite 数据",
                    "country.mmdb" to "Country 数据",
                    "ASN.mmdb" to "ASN 数据",
                )
                .forEach { (file, title) ->
                    LucidRow(title, subtitle = file, onClick = { import(file) })
                }
        }
    }
}

@Composable
private fun KeyScreen(vm: ClientViewModel, back: () -> Unit) {
    var key by remember { mutableStateOf<io.github.leenban.clash.core.model.AgeKeyPair?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LucidHeader("加密配置密钥", back)
        LucidSection("生成密钥", footer = "私钥仅保留在当前页面内存中，退出后不保存。请自行安全备份。") {
            LucidRow(
                "生成 X25519 密钥",
                onClick = {
                    vm.task { key = withContext(Dispatchers.IO) { Clash.genX25519KeyPair() } }
                },
            )
            LucidRow(
                "生成混合密钥",
                onClick = {
                    vm.task { key = withContext(Dispatchers.IO) { Clash.genHybridKeyPair() } }
                },
            )
        }
        key?.let { pair ->
            LucidSection("密钥") {
                LucidRow("公钥", subtitle = pair.publicKey)
                LucidRow("私钥", subtitle = pair.secretKey)
                LucidRow(
                    "复制密钥对",
                    onClick = {
                        val manager =
                            vm.repository.context.getSystemService(
                                android.content.ClipboardManager::class.java
                            )
                        val clip =
                            android.content.ClipData.newPlainText(
                                "age 密钥",
                                pair.secretKey + "\n" + pair.publicKey,
                            )
                        if (android.os.Build.VERSION.SDK_INT >= 33)
                            clip.description.extras =
                                android.os.PersistableBundle().apply {
                                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                                }
                        manager.setPrimaryClip(clip)
                    },
                )
            }
        }
    }
}
