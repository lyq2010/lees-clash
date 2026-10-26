package io.github.leenban.clash.lucid

import android.content.Context
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.service.model.Profile
import io.github.leenban.clash.util.stopClashService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

// Restore only the active selection. Test profiles and user data are not deleted.
internal suspend fun ClientRepository.restoreSelection(context: Context, original: Profile?) {
    context.stopClashService()
    try {
        withTimeout(10000) { while (StatusClient(context).currentProfile() != null) delay(100) }
    } finally {
        if (original != null) select(original)
    }
}

internal suspend fun awaitRunningProfile(context: Context) {
    withTimeout(15000) { while (StatusClient(context).currentProfile() == null) delay(100) }
}
