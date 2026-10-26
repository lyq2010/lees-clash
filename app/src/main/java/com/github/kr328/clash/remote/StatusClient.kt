package io.github.leenban.clash.remote

import android.content.Context
import android.net.Uri
import io.github.leenban.clash.common.constants.Authorities
import io.github.leenban.clash.common.log.Log
import io.github.leenban.clash.service.StatusProvider

class StatusClient(private val context: Context) {
    private val uri: Uri
        get() {
            return Uri.Builder().scheme("content").authority(Authorities.STATUS_PROVIDER).build()
        }

    fun currentProfile(): String? {
        return try {
            val result =
                context.contentResolver.call(uri, StatusProvider.METHOD_CURRENT_PROFILE, null, null)

            result?.getString("name")
        } catch (e: Exception) {
            Log.w("Query current profile: $e", e)

            null
        }
    }

    fun prepareStart() {
        context.contentResolver.call(uri, StatusProvider.METHOD_PREPARE_START, null, null)
    }

    fun requestStop() {
        context.contentResolver.call(uri, StatusProvider.METHOD_REQUEST_STOP, null, null)
    }
}
