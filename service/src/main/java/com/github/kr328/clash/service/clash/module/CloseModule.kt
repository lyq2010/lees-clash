package io.github.leenban.clash.service.clash.module

import android.app.Service
import io.github.leenban.clash.common.constants.Intents
import io.github.leenban.clash.common.log.Log
import io.github.leenban.clash.service.StatusProvider

class CloseModule(service: Service) : Module<CloseModule.RequestClose>(service) {
    object RequestClose

    override suspend fun run() {
        val broadcasts = receiveBroadcast { addAction(Intents.ACTION_CLASH_REQUEST_STOP) }

        if (!StatusProvider.stopRequested) broadcasts.receive()

        Log.d("User request close")

        return enqueueEvent(RequestClose)
    }
}
