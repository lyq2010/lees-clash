package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.TunnelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientFormattingTest {
    @Test
    fun byteCountsKeepUnitsAndLargeValues() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("2.0 TB", formatBytes(2L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun subscriptionQuotaSaturatesWithoutOverflow() {
        assertNull(remainingQuota(0, 10, 20))
        assertEquals(70L, remainingQuota(100, 10, 20))
        assertEquals(100L, remainingQuota(100, -1, -1))
        assertEquals(0L, remainingQuota(Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1))
    }

    @Test
    fun serviceDurationHandlesStoppedAndMultiDaySessions() {
        assertEquals("00:00:00", formatRunTime(-1))
        assertEquals("01:01:01", formatRunTime(3661000))
        assertEquals("1 天 01:01:01", formatRunTime(90061000))
        assertEquals("2 天 00:00:00", formatRunTime(172800000))
    }

    @Test
    fun rateDisplayFormatsCompactDotMatrixDigitsAndModeCycle() {
        assertEquals("0.00" to "KB/s", formatRateDisplay(0))
        assertEquals("0.50" to "KB/s", formatRateDisplay(512))
        assertEquals("5.30" to "KB/s", formatRateDisplay(5427))
        assertEquals("42.5" to "KB/s", formatRateDisplay(43520))
        assertEquals("5.30" to "MB/s", formatRateDisplay(5_557_453))
        assertEquals(TunnelState.Mode.Global, nextMode(TunnelState.Mode.Rule))
        assertEquals(TunnelState.Mode.Direct, nextMode(TunnelState.Mode.Global))
        assertEquals(TunnelState.Mode.Rule, nextMode(TunnelState.Mode.Direct))
    }
}
