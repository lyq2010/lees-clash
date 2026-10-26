package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.DiagnosticPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiagnosticPagerTest {
    private fun page(items: List<Int>, offset: Int): DiagnosticPage<Int> {
        val end = (offset + 50).coerceAtMost(items.size)
        return DiagnosticPage(
            items.drop(offset).take(50),
            items.size,
            if (end < items.size) end else -1,
        )
    }

    @Test
    fun refreshPreservesLoadedWindowAndRemovesEndedConnections() = runBlocking {
        var live = (1..130).toList()
        val pager = DiagnosticPager<Int>({ it }) { _, offset -> page(live, offset) }
        assertEquals(50, pager.refresh()!!.items.size)
        assertEquals(100, pager.more()!!.items.size)
        live = (2..131).toList()
        val refreshed = pager.refresh()!!
        assertEquals((2..101).toList(), refreshed.items)
        assertEquals(100, refreshed.nextOffset)
        assertEquals(130, pager.more()!!.items.size)
        live = (2..12).toList()
        assertEquals(live, pager.refresh()!!.items)
        live = (2..131).toList()
        assertEquals(live, pager.refresh()!!.items)
    }

    @Test
    fun searchInvalidatesInFlightAndQueuedResults() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val pager =
            DiagnosticPager<Int>({ it }) { query, offset ->
                if (query.isEmpty()) release.await()
                page(if (query.isEmpty()) (1..120).toList() else listOf(900), offset)
            }
        val old = async(start = CoroutineStart.UNDISPATCHED) { pager.refresh() }
        val queued = async(start = CoroutineStart.UNDISPATCHED) { pager.refresh() }
        pager.reset("new")
        release.complete(Unit)
        assertNull(old.await())
        assertNull(queued.await())
        assertTrue(pager.page.items.isEmpty())
        assertEquals(listOf(900), pager.refresh()!!.items)
        assertNull(pager.more())
    }

    @Test
    fun canceledAndFailedLoadsKeepLastSuccessfulWindow() = runBlocking {
        var blocked = false
        var fail = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val offsets = mutableListOf<Int>()
        val pager =
            DiagnosticPager<Int>({ it }) { _, offset ->
                offsets += offset
                if (blocked) {
                    entered.complete(Unit)
                    release.await()
                }
                check(!fail || offset == 0) { "Service unavailable" }
                page((1..160).toList(), offset)
            }
        val original = pager.refresh()
        blocked = true
        val pending = async { pager.more() }
        entered.await()
        pending.cancelAndJoin()
        assertEquals(original, pager.page)
        blocked = false
        fail = true
        assertTrue(runCatching { pager.more() }.isFailure)
        assertEquals(original, pager.page)
        fail = false
        offsets.clear()
        pager.refresh()
        assertEquals(listOf(0), offsets)
    }

    @Test
    fun concurrentRefreshAndMoreSerializeWithoutShrinkingOrDuplicateKeys() = runBlocking {
        var hold = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pager =
            DiagnosticPager<Int>({ it }) { _, offset ->
                if (hold) {
                    entered.complete(Unit)
                    release.await()
                }
                // A live list can move between two Binder transactions.
                if (offset == 50) DiagnosticPage((50..99).toList(), 120, 100)
                else page((1..120).toList(), offset)
            }
        pager.refresh()
        hold = true
        val refreshing = async { pager.refresh() }
        entered.await()
        val more = async(start = CoroutineStart.UNDISPATCHED) { pager.more() }
        release.complete(Unit)
        refreshing.await()
        val expanded = more.await()!!
        assertEquals(99, expanded.items.size)
        assertEquals(99, expanded.items.distinct().size)
        assertEquals(100, expanded.nextOffset)
        assertEquals(expanded, pager.refresh())
    }
}
