package io.github.leenban.clash.service.remote

import io.github.leenban.clash.core.model.LogMessage
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
interface ILogObserver {
    fun newItem(log: LogMessage)
}