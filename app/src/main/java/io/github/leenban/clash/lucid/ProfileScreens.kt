package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.service.model.Profile

@Composable
fun ProfilesScreen(
    state: ClientState,
    vm: ClientViewModel,
    navigate: (String) -> Unit,
    back: () -> Unit,
) {
    val listState = rememberLazyListState()
    var revealed by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.recentProfile, state.profiles.size) {
        val recent = state.recentProfile?.toString()
        if (
            recent != null &&
                recent != revealed &&
                state.profiles.any { it.uuid == state.recentProfile }
        ) {
            listState.scrollToItem(0)
            revealed = recent
        }
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("配置", back, "导入") { navigate("import") }
        LazyColumn(state = listState) {
            item {
                LucidSection("配置文件") {
                    state.profiles
                        .sortedByDescending { it.uuid == state.recentProfile }
                        .forEach { profile ->
                            LucidRow(
                                profile.name,
                                subtitle =
                                    if (!profile.imported) "未完成导入"
                                    else
                                        when (profile.type) {
                                            Profile.Type.Url -> "订阅配置"
                                            Profile.Type.File -> "本地配置"
                                            else -> "外部配置"
                                        },
                                value =
                                    if (profile.uuid in state.updatingProfiles) "更新中"
                                    else if (profile.active) "使用中"
                                    else if (profile.uuid == state.recentProfile) "新添加" else "",
                                selected = profile.active,
                                trailing = {
                                    if (profile.type != Profile.Type.File && profile.imported) {
                                        val updating = profile.uuid in state.updatingProfiles
                                        Box(
                                            Modifier.size(48.dp)
                                                .semantics {
                                                    contentDescription = "更新${profile.name}"
                                                }
                                                .lucidClickable(enabled = !updating) {
                                                    vm.updateProfile(profile)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            LucidIcon(
                                                LucidSymbol.Refresh,
                                                LocalLucidColors.current.blue.copy(
                                                    alpha = if (updating) .35f else 1f
                                                ),
                                                Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                },
                                onDetail = { navigate("profile/${profile.uuid}") },
                                onClick = {
                                    if (!profile.imported) vm.error("该配置尚未完成导入，请先编辑并保存")
                                    else
                                        vm.task(progress = "正在切换配置", success = "配置已切换") {
                                            vm.repository.select(profile)
                                            vm.changed()
                                        }
                                },
                            )
                        }
                }
            }
            if (state.profiles.isEmpty())
                item { LucidEmpty("尚未添加配置", "点按右上角“导入”，从链接、文件或二维码添加 Clash YAML 配置。") }
        }
    }
}

@Composable
fun ImportScreen(
    state: ClientState,
    vm: ClientViewModel,
    back: () -> Unit,
    importFile: () -> Unit,
    scan: () -> Unit,
    external: () -> Unit,
    initialUrl: String = "",
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var secret by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            "导入配置",
            back,
            if (state.importing) "导入中" else "完成",
            actionEnabled = !state.importing,
        ) {
            vm.importProfile(name, url, secret)
        }
        LazyColumn {
            item {
                LucidSection("从 URL 下载") {
                    LucidField("名称", name, { name = it }, "订阅配置", enabled = !state.importing)
                    LucidField(
                        "链接",
                        url,
                        { url = it },
                        "https://example.com/config.yaml",
                        keyboard = KeyboardType.Uri,
                        enabled = !state.importing,
                    )
                    LucidField(
                        "age 私钥（可选）",
                        secret,
                        { secret = it },
                        secret = true,
                        enabled = !state.importing,
                    )
                }
            }
            item {
                LucidSection("其他方式") {
                    LucidRow(
                        "从文件导入",
                        symbol = LucidSymbol.Document,
                        onClick = if (state.importing) null else importFile,
                    )
                    LucidRow(
                        "扫描二维码",
                        symbol = LucidSymbol.Search,
                        onClick = if (state.importing) null else scan,
                    )
                    LucidRow(
                        "来自其他应用",
                        symbol = LucidSymbol.Folder,
                        onClick = if (state.importing) null else external,
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    state: ClientState,
    vm: ClientViewModel,
    navigate: (String) -> Unit,
    back: () -> Unit,
    export: (String, String) -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    val profile = state.editingProfile
    if (profile == null) {
        Column {
            LucidHeader("配置", back)
            LucidEmpty("正在读取配置")
        }
        return
    }
    var name by rememberSaveable(profile.uuid.toString()) { mutableStateOf(profile.name) }
    var source by rememberSaveable(profile.uuid.toString()) { mutableStateOf(profile.source) }
    var interval by
        rememberSaveable(profile.uuid.toString()) {
            mutableStateOf((profile.interval / 60000).toString())
        }
    var secret by
        rememberSaveable(profile.uuid.toString()) { mutableStateOf(profile.ageSecretKey.orEmpty()) }
    var contents by rememberSaveable(profile.uuid.toString()) { mutableStateOf<String?>(null) }
    var originalContents by
        rememberSaveable(profile.uuid.toString()) { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.uuid) {
        vm.task {
            val loaded = vm.repository.readDocument("${profile.uuid}/config.yaml")
            originalContents = loaded
            if (contents == null) contents = loaded
        }
    }
    val dirty =
        name != profile.name ||
            source != profile.source ||
            secret != profile.ageSecretKey.orEmpty() ||
            interval != (profile.interval / 60000).toString() ||
            contents != originalContents
    val leave: () -> Unit = {
        if (dirty)
            confirm("放弃尚未保存的配置修改？") {
                vm.task(done = back) {
                    io.github.leenban.clash.util.withProfile { release(profile.uuid) }
                }
            }
        else back()
    }
    BackHandler(onBack = leave)
    Column(Modifier.fillMaxSize()) {
        LucidHeader("编辑配置", leave, "完成", actionEnabled = !state.busy) {
            vm.task(done = back, progress = "正在保存配置", success = "配置已保存") {
                vm.repository.saveProfile(
                    profile,
                    name,
                    source,
                    (interval.toLongOrNull() ?: error("更新间隔应为整数分钟")) * 60000,
                    secret,
                    if (profile.type == Profile.Type.File && contents != originalContents) contents
                    else null,
                )
                vm.changed()
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (profile.type != Profile.Type.File) item { SubscriptionDetails(profile) }
            item {
                LucidSection("配置属性") {
                    LucidField("名称", name, { name = it })
                    if (profile.type != Profile.Type.File)
                        LucidField("来源链接", source, { source = it }, keyboard = KeyboardType.Uri)
                    LucidField(
                        "自动更新间隔（分钟，0 为关闭）",
                        interval,
                        { interval = it },
                        keyboard = KeyboardType.Number,
                    )
                    LucidField("age 私钥（可选）", secret, { secret = it }, secret = true)
                }
            }
            if (profile.type == Profile.Type.File)
                item {
                    LucidSection("YAML 内容", footer = "保存时由内核验证；验证失败不会替换已导入的有效配置。") {
                        LucidField("", contents.orEmpty(), { contents = it }, multiline = true)
                    }
                }
            item {
                LucidSection("操作") {
                    LucidRow("规则、代理与配置扩展", onClick = { navigate("extension/${profile.uuid}") })
                    LucidRow(
                        "克隆为本地配置",
                        onClick = {
                            vm.task(done = back, progress = "正在克隆配置", success = "已克隆为本地配置") {
                                vm.repository.cloneProfile(profile)
                                vm.changed()
                            }
                        },
                    )
                    LucidRow(
                        "配置与资源文件",
                        onClick = { navigate("files/${Uri.encode(profile.uuid.toString())}") },
                    )
                    LucidRow(
                        "导出 YAML",
                        onClick = { export("${profile.uuid}/config.yaml", "${profile.name}.yaml") },
                    )
                    LucidRow(
                        "删除配置",
                        destructive = true,
                        onClick = {
                            confirm("删除配置“${profile.name}”？此操作无法撤销。") {
                                vm.task(done = back, progress = "正在删除配置", success = "配置已删除") {
                                    vm.repository.delete(profile)
                                    vm.changed()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun FilesScreen(
    id: String,
    state: ClientState,
    vm: ClientViewModel,
    navigate: (String) -> Unit,
    back: () -> Unit,
    importFile: (String) -> Unit,
    export: (String, String) -> Unit,
    confirm: (String, () -> Unit) -> Unit,
    editExternal: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val leave: () -> Unit = { if (renaming != null) renaming = null else if (editing != null) editing = null else back() }
    BackHandler(editing != null || renaming != null, onBack = leave)
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            if (renaming != null) "重命名文件" else if (editing == null) "配置与资源文件" else "编辑文件",
            leave,
            if (editing == null && renaming == null) "导入" else "保存",
            actionEnabled = !state.busy,
        ) {
            if (renaming != null) vm.task(progress = "正在重命名", success = "文件已重命名") {
                require(text.isNotBlank() && text !in setOf(".", "..") && !text.contains('/') && !text.contains('\\')) { "文件名无效" }
                val uuid = java.util.UUID.fromString(id.substringBefore('/'))
                try {
                    vm.repository.files.renameDocument(renaming!!, text)
                    io.github.leenban.clash.util.withProfile { commit(uuid) }
                } finally { io.github.leenban.clash.util.withProfile { release(uuid) } }
                renaming = null
                vm.changed()
            } else if (editing == null) importFile(id)
            else
                vm.task(progress = "正在保存文件", success = "文件已保存") {
                    vm.repository.writeDocument(editing!!, text)
                    val uuid = java.util.UUID.fromString(id.substringBefore('/'))
                    io.github.leenban.clash.util.withProfile {
                        try {
                            commit(uuid)
                        } finally {
                            release(uuid)
                        }
                    }
                    editing = null
                    vm.changed()
                }
        }
        LazyColumn {
            if (renaming != null) item { LucidSection { LucidField("文件名", text, { text = it }) } }
            else if (editing != null)
                item { LucidSection { LucidField("", text, { text = it }, multiline = true) } }
            else
                item {
                    LucidSection("文件") {
                        state.files.forEach { file ->
                            val documentId =
                                if (file.id.startsWith(id.substringBefore('/'))) file.id
                                else "$id/${file.id}"
                            LucidRow(
                                file.name,
                                subtitle = if (file.isDirectory) "文件夹" else formatBytes(file.size),
                                onClick = {
                                    if (file.isDirectory)
                                        navigate("files/${Uri.encode(documentId)}")
                                    else if (documentId.endsWith("/config.yaml"))
                                        navigate("profile/${id.substringBefore('/')}")
                                    else
                                        vm.task {
                                            val loaded = vm.repository.readDocument(documentId)
                                            text = loaded
                                            editing = documentId
                                        }
                                },
                            )
                            if (!file.isDirectory)
                                LucidRow(
                                    "导出 ${file.name}",
                                    onClick = { export(documentId, file.name) },
                                )
                            if (documentId.contains("/providers/"))
                                LucidRow("重命名 ${file.name}", onClick = { text = file.name; renaming = documentId })
                            if (!file.isDirectory) LucidRow("使用外部编辑器", onClick = { editExternal(documentId) })
                            if (documentId.contains("/providers/"))
                                LucidRow(
                                    "删除 ${file.name}",
                                    destructive = true,
                                    onClick = {
                                        confirm("删除资源文件“${file.name}”？") {
                                            vm.task(progress = "正在删除文件", success = "文件已删除") {
                                                vm.repository.files.deleteDocument(documentId)
                                                val uuid =
                                                    java.util.UUID.fromString(
                                                        id.substringBefore('/')
                                                    )
                                                io.github.leenban.clash.util.withProfile {
                                                    try {
                                                        commit(uuid)
                                                    } finally {
                                                        release(uuid)
                                                    }
                                                }
                                                vm.changed()
                                            }
                                        }
                                    },
                                )
                        }
                    }
                }
        }
    }
}
