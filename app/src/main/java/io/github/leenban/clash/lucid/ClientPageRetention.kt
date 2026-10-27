package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.DiagnosticPage

// Release data only after the outgoing page finishes its transition.
internal fun ClientState.retainPageData(route: String): ClientState {
    val page = route.substringBefore('/')
    val connectionPage = page == "connections" || page == "connection"
    return copy(
        groups = if (page == "groups") groups else emptyList(),
        selectedGroup = if (page == "nodes") selectedGroup else null,
        connections = if (connectionPage) connections else DiagnosticPage(),
        connection = if (page == "connection") connection else null,
        rules = if (page == "rules") rules else DiagnosticPage(),
        providers = if (page == "providers") providers else emptyList(),
        geoAssets = if (page == "geodata") geoAssets else emptyList(),
        apps = if (page == "apps") apps else emptyList(),
        logs = if (page == "logs") logs else emptyList(),
        files = if (page == "files") files else emptyList(),
        overrideJson = if (page == "override" || page == "dns-settings") overrideJson else null,
        extensionJson = if (page == "extension") extensionJson else null,
    )
}
