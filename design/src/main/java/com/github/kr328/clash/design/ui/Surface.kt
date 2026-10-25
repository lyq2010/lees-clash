package io.github.leenban.clash.design.ui

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import io.github.leenban.clash.design.BR

class Surface : BaseObservable() {
    var insets: Insets = Insets.EMPTY
        @Bindable get
        set(value) {
            field = value

            notifyPropertyChanged(BR.insets)
        }
}