package io.github.leenban.clash.util

import android.os.DeadObjectException
import io.github.leenban.clash.common.log.Log
import io.github.leenban.clash.remote.Remote
import io.github.leenban.clash.service.remote.IClashManager
import io.github.leenban.clash.service.remote.IProfileManager
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> withClash(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IClashManager.() -> T,
): T {
    while (true) {
        val remote = Remote.service.remote.get()

        try {
            val client = remote.clash()
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            Log.w("Remote services panic")

            Remote.service.remote.reset(remote)
        }
    }
}

suspend fun <T> withProfile(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IProfileManager.() -> T,
): T {
    while (true) {
        val remote = Remote.service.remote.get()

        try {
            val client = remote.profile()
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            Log.w("Remote services panic")

            Remote.service.remote.reset(remote)
        }
    }
}
