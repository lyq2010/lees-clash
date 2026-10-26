package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.DiagnosticPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the loaded window while refreshing live data. Accessed from the UI dispatcher. */
internal class DiagnosticPager<T>(
    private val key: (T) -> Any,
    private val fetch: suspend (String, Int) -> DiagnosticPage<T>,
) {
    private val mutex = Mutex()
    private var revision = 0
    private var loadedPages = 1
    private var query = ""
    var page = DiagnosticPage<T>()
        private set

    fun reset(query: String) {
        revision++
        this.query = query
        loadedPages = 1
        page = DiagnosticPage()
    }

    suspend fun refresh(): DiagnosticPage<T>? = load(more = false)

    suspend fun more(): DiagnosticPage<T>? = load(more = true)

    private suspend fun load(more: Boolean): DiagnosticPage<T>? {
        val requestedRevision = revision
        return mutex.withLock {
            if (requestedRevision != revision || (more && page.nextOffset < 0)) return@withLock null
            val requestedQuery = query
            val pages = loadedPages + if (more) 1 else 0
            val items = mutableListOf<T>()
            var offset = 0
            var result = DiagnosticPage<T>()
            for (index in 0 until pages) {
                result = fetch(requestedQuery, offset)
                currentCoroutineContext().ensureActive()
                if (requestedRevision != revision) return@withLock null
                items.addAll(result.items)
                if (result.nextOffset < 0) break
                check(result.nextOffset > offset) { "分页游标未向前移动" }
                offset = result.nextOffset
            }
            loadedPages = pages
            result.copy(items = items.distinctBy(key)).also { page = it }
        }
    }
}
