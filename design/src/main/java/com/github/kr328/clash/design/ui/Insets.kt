package io.github.leenban.clash.design.ui

data class Insets(val start: Int, val top: Int, val end: Int, val bottom: Int) {
    companion object {
        val EMPTY = Insets(0, 0, 0, 0)
    }
}