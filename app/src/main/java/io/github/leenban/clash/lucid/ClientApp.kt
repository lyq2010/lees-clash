package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
    exportBackup: () -> Unit,
    restoreBackup: () -> Unit,
    editExternal: (String) -> Unit,
    finish: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var stack by rememberSaveable(initialRoute) { mutableStateOf(listOf(initialRoute)) }
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
    val currentStack by rememberUpdatedState(stack)
    LaunchedEffect(state.navigation?.id) {
        state.navigation?.let { request ->
            vm.consumeNavigation(request.id)
            if (request.route != route)
                stack =
                    if (kind == "import" && request.route.startsWith("import/"))
                        stack.dropLast(1) + request.route
                    else stack + request.route
        }
    }
    LaunchedEffect(state.importedProfile) {
        state.importedProfile?.let { id ->
            vm.consumeImport(id)
            if (kind == "import")
                stack =
                    if (initialRoute == "home") listOf("home", "profiles") else listOf("profiles")
        }
    }
    LaunchedEffect(state.notice?.id) {
        state.notice?.let {
            delay(4000)
            vm.clearNotice(it.id)
        }
    }
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
    val theme = remember(themeRevision, state.settingsRevision) { vm.repository.ui.darkMode }
    val dark =
        when (theme) {
            DarkMode.Auto -> androidx.compose.foundation.isSystemInDarkTheme()
            DarkMode.ForceLight -> false
            else -> true
        }
    val activity = LocalActivity.current
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
        val topRoutes = listOf("home", "groups", "tools", "more")
        val showDock = stack.size == 1 && kind in topRoutes
        Box(
            Modifier.fillMaxSize()
                .background(c.background)
                .drawBehind {
                    val r = size.width.coerceAtLeast(1f) * 0.95f
                    drawRect(
                        Brush.radialGradient(
                            0.0f to c.ambientGlow,
                            1.0f to Color.Transparent,
                            center = Offset(size.width * 0.5f, 0f),
                            radius = r,
                        )
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 600.dp
                    Row(Modifier.fillMaxSize()) {
                        if (wide)
                            Column(Modifier.width(228.dp).fillMaxHeight()) {
                                LucidHeader("Lee's Clash")
                                LucidSection {
                                    listOf(
                                            "home" to "首页",
                                            "groups" to "策略",
                                            "tools" to "工具",
                                            "more" to "更多",
                                        )
                                        .forEach { (destination, title) ->
                                            LucidRow(
                                                title,
                                                selected = stack.first() == destination,
                                                onClick = { stack = listOf(destination) },
                                            )
                                        }
                                }
                            }
                        LucidPageStack(stack, Modifier.weight(1f).fillMaxHeight()) { visibleRoute ->
                            val visibleKind = visibleRoute.substringBefore('/')
                            val argument = Uri.decode(visibleRoute.substringAfter('/', ""))
                            holder.SaveableStateProvider(visibleRoute) {
                                DisposableEffect(visibleRoute) {
                                    onDispose {
                                        vm.releasePage(visibleRoute)
                                        if (visibleRoute !in currentStack) holder.removeState(visibleRoute)
                                    }
                                }
                                when (visibleKind) {
                                    "home" ->
                                        HomeScreen(
                                            state = state,
                                            navigate = navigate,
                                            toggle = toggle,
                                            setMode = vm::setMode,
                                            updateProfile = { state.active?.let(vm::updateProfile) },
                                        )
                                    "tools" -> ToolsScreen(state, vm, navigate, confirm)
                                    "more" -> MoreScreen(navigate, state)
                                    "profiles" -> ProfilesScreen(state, vm, navigate, back)
                                    "import" ->
                                        ImportScreen(
                                            state,
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
                                            editExternal,
                                        )
                                    "groups" ->
                                        GroupsScreen(
                                            state = state,
                                            navigate = navigate,
                                            back = if (currentStack.size > 1) back else null,
                                            ui = vm.repository.ui,
                                        )
                                    "nodes" -> NodesScreen(argument, state, vm, back)
                                    "providers" -> ProvidersScreen(state, vm, back)
                                    "notifications" -> NotificationScreen(state, vm, back)
                                    "connections" ->
                                        ConnectionsScreen(state, vm, navigate, back, confirm)
                                    "connection" -> ConnectionScreen(state, vm, back, confirm)
                                    "rules" -> RulesScreen(state, vm, back)
                                    "dns" -> DnsScreen(state, vm, back)
                                    "stats" -> StatsScreen(state, back)
                                    "logs" -> LogsScreen(state, vm, back)
                                    "network" -> NetworkScreen(state, vm, navigate, back)
                                    "apps" -> AppsScreen(state, vm, back)
                                    "settings" ->
                                        SettingsScreen(
                                            vm,
                                            state,
                                            back,
                                            { themeRevision++ },
                                            confirm,
                                        )
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
                                    "geodata" -> GeoDataScreen(state, vm, back, importGeo)
                                    "extension" ->
                                        ProfileExtensionScreen(argument, state, vm, back, confirm)
                                    "on-demand" -> OnDemandScreen(state, vm, back)
                                    "network-check" -> NetworkProbeScreen(state, vm, back)
                                    "subscription" -> SubscriptionScreen(state, vm, navigate, back)
                                    "lan" -> LanSharingScreen(state, vm, back)
                                    "memory" -> MemoryDetailsScreen(state, vm, back)
                                    "update" -> UpdateScreen(back)
                                    "backup" ->
                                        BackupScreen(state, back, exportBackup, restoreBackup)
                                    "backup-preview" -> BackupPreviewScreen(state, vm, back)
                                    "keys" -> KeyScreen(vm, back)
                                    "broken",
                                    "crashed" ->
                                        Column {
                                            LucidHeader("应用状态", back)
                                            LucidEmpty(
                                                if (visibleKind == "broken") "安装包不完整"
                                                else "后台服务意外退出",
                                                "请重新打开应用，或重新安装完整 APK。",
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
                if (showDock)
                    BoxWithConstraints {
                        if (maxWidth < 600.dp) FloatingDockBar(kind) { stack = listOf(it) }
                    }
            }
            ClientFeedback(state, showDock) { vm.clearError() }
        }
        confirmation?.let { (message, action) ->
            Dialog(onDismissRequest = { confirmation = null }) {
                Column(
                    Modifier.fillMaxWidth()
                        .lucidGlassSurface(22.dp)
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
private fun KeyScreen(vm: ClientViewModel, back: () -> Unit) {
    var key by remember { mutableStateOf<io.github.leenban.clash.core.model.AgeKeyPair?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LucidHeader("加密配置密钥", back)
        LucidSection("生成密钥", footer = "私钥仅保留在当前页面内存中，退出后不保存。请自行安全备份。") {
            LucidRow(
                "生成 X25519 密钥",
                onClick = { vm.task { key = vm.repository.generateKey(false) } },
            )
            LucidRow("生成混合密钥", onClick = { vm.task { key = vm.repository.generateKey(true) } })
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
                        vm.notice("密钥对已复制")
                    },
                )
            }
        }
    }
}
