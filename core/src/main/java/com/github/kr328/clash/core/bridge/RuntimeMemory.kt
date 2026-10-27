package io.github.leenban.clash.core.bridge

import io.github.leenban.clash.common.log.Log
import org.json.JSONObject

// A shared Android Go library has no startup environment. The policy must be
// embedded by the Go build task; check its metadata after the library is ready.
internal fun verifyRuntimeMemory(diagnostic: String) {
    val configured = runCatching { JSONObject(diagnostic).optBoolean("immediatePageRelease") }.getOrDefault(false)
    if (!configured) Log.w("Native immediate memory page release is not configured")
}
