package io.github.leenban.clash.design.model

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import io.github.leenban.clash.core.model.Provider
import io.github.leenban.clash.design.BR

class ProviderState(
    val provider: Provider,
    updatedAt: Long,
    updating: Boolean,
) : BaseObservable() {
    var updatedAt: Long = updatedAt
        @Bindable get
        set(value) {
            field = value

            notifyPropertyChanged(BR.updatedAt)
        }

    var updating: Boolean = updating
        @Bindable get
        set(value) {
            field = value

            notifyPropertyChanged(BR.updating)
        }
}