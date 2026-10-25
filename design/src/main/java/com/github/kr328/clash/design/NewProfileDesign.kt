package io.github.leenban.clash.design

import android.content.Context
import android.view.View
import io.github.leenban.clash.design.adapter.ProfileProviderAdapter
import io.github.leenban.clash.design.databinding.DesignNewProfileBinding
import io.github.leenban.clash.design.model.ProfileProvider
import io.github.leenban.clash.design.util.*

class NewProfileDesign(context: Context) : Design<NewProfileDesign.Request>(context) {
    sealed class Request {
        data class Create(val provider: ProfileProvider) : Request()
        data class OpenDetail(val provider: ProfileProvider.External) : Request()
        data class LaunchScanner(val provider: ProfileProvider.QR) : Request()
    }

    private val binding = DesignNewProfileBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = ProfileProviderAdapter(context, this::requestCreate, this::requestDetail)

    override val root: View
        get() = binding.root

    suspend fun patchProviders(providers: List<ProfileProvider>) {
        adapter.apply {
            patchDataSet(this::providers, providers)
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
    }

    private fun requestCreate(provider: ProfileProvider) {
        if (provider is ProfileProvider.QR) {
            requests.trySend(Request.LaunchScanner(provider))
        } else {
            requests.trySend(Request.Create(provider))
        }

    }

    private fun requestDetail(provider: ProfileProvider): Boolean {
        if (provider !is ProfileProvider.External) return false

        requests.trySend(Request.OpenDetail(provider))

        return true
    }
}