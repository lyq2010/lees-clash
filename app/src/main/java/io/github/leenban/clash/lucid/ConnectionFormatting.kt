package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.ConnectionInfo

internal fun ConnectionInfo.applicationTitle(): String =
    appName.ifBlank { process.ifBlank { if (uid >= 0) "UID $uid" else "未知应用" } }
