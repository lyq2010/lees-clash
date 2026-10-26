package io.github.leenban.clash.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.leenban.clash.common.Global
import io.github.leenban.clash.common.constants.Intents
import io.github.leenban.clash.service.util.sendBroadcastSelf

class StatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_PREPARE_START -> {
                stopRequested = false
                Bundle.EMPTY
            }
            METHOD_REQUEST_STOP -> {
                stopRequested = true
                context?.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
                Bundle.EMPTY
            }
            METHOD_CURRENT_PROFILE -> {
                return if (serviceRunning) Bundle().apply { putString("name", currentProfile) }
                else null
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw IllegalArgumentException("Stub!")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        throw IllegalArgumentException("Stub!")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun getType(uri: Uri): String? {
        throw IllegalArgumentException("Stub!")
    }

    override fun onCreate(): Boolean {
        return true
    }

    companion object {
        const val METHOD_CURRENT_PROFILE = "currentProfile"
        const val METHOD_PREPARE_START = "prepareStart"
        const val METHOD_REQUEST_STOP = "requestStop"

        @Volatile var stopRequested: Boolean = false
        @Volatile var networkBypassed: Boolean = false

        private const val CLASH_SERVICE_RUNNING_FILE = "service_running.lock"

        @Volatile
        var serviceRunning: Boolean = false
            set(value) {
                field = value

                shouldStartClashOnBoot = value
            }

        var shouldStartClashOnBoot: Boolean
            get() = Global.application.filesDir.resolve(CLASH_SERVICE_RUNNING_FILE).exists()
            set(value) {
                Global.application.filesDir.resolve(CLASH_SERVICE_RUNNING_FILE).apply {
                    if (value) createNewFile() else delete()
                }
            }

        @Volatile var currentProfile: String? = null
    }
}
