package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PageRetentionTest {
    @Test fun leavingHeavyPagesDropsPayloadsButKeepsConnectionListForDetails() {
        val group = ProxyGroup("select", emptyList(), "DIRECT")
        val page = DiagnosticPage(listOf(ConnectionInfo("1")), 1, -1)
        val state = ClientState(groups = listOf(PolicyGroup("Fixture", group)), selectedGroup = group,
            connections = page, rules = DiagnosticPage(listOf(RuleInfo(0, "MATCH", "", "DIRECT"))),
            overrideJson = "large override", extensionJson = "large extension")
        val home = state.retainPageData("home")
        assertTrue(home.groups.isEmpty())
        assertNull(home.selectedGroup)
        assertTrue(home.connections.items.isEmpty())
        assertTrue(home.rules.items.isEmpty())
        assertNull(home.overrideJson)
        assertNull(home.extensionJson)
        assertSame(group, state.retainPageData("nodes/Fixture").selectedGroup)
        assertSame(page, state.retainPageData("connection/1").connections)
    }
}
