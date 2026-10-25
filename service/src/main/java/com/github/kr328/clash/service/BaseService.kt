package io.github.leenban.clash.service

import android.app.Service
import io.github.leenban.clash.service.util.cancelAndJoinBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

abstract class BaseService : Service(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    override fun onDestroy() {
        super.onDestroy()

        cancelAndJoinBlocking()
    }
}