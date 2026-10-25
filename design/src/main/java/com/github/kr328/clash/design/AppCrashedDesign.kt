package io.github.leenban.clash.design

import android.content.Context
import android.view.View
import io.github.leenban.clash.design.databinding.DesignAppCrashedBinding
import io.github.leenban.clash.design.util.applyFrom
import io.github.leenban.clash.design.util.bindAppBarElevation
import io.github.leenban.clash.design.util.layoutInflater
import io.github.leenban.clash.design.util.root

class AppCrashedDesign(context: Context) : Design<Unit>(context) {
    private val binding = DesignAppCrashedBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    fun setAppLogs(logs: String) {
        binding.logsView.text = logs
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
    }
}