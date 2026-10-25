package io.github.leenban.clash.lucid

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientFormattingTest {
    @Test
    fun byteCountsKeepUnitsAndLargeValues() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("2.0 TB", formatBytes(2L * 1024 * 1024 * 1024 * 1024))
    }
}
