package io.github.leenban.clash.design.util

import io.github.leenban.clash.design.view.ObservableScrollView

val ObservableScrollView.isTop: Boolean
    get() = scrollX == 0 && scrollY == 0
