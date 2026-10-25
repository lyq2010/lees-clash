package io.github.leenban.clash

import io.github.leenban.clash.common.util.uuid

class PropertiesActivity : io.github.leenban.clash.lucid.LucidActivity() {
    override fun initialRoute(): String {
        val uuid = intent.uuid ?: return super.initialRoute()
        return "profile/$uuid"
    }
}
