package io.github.leenban.clash.service.store

import android.content.Context
import io.github.leenban.clash.common.store.Store
import io.github.leenban.clash.common.store.asStoreProvider
import io.github.leenban.clash.service.PreferenceProvider
import io.github.leenban.clash.service.model.AccessControlMode
import java.util.*

class ServiceStore(context: Context) {
    private val preferences = PreferenceProvider.createSharedPreferencesFromContext(context)
    private val store = Store(preferences.asStoreProvider())

    var activeProfile: UUID?
        get() = preferences.getString("active_profile", null)
            ?.takeIf { it.isNotBlank() }?.let(UUID::fromString)
        set(value) {
            check(preferences.edit().putString("active_profile", value?.toString().orEmpty()).commit()) {
                "无法保存当前配置选择"
            }
        }

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = true
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "mips"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    var onDemandEnabled by store.boolean("on_demand_enabled", false)
    var onDemandSsids by store.stringSet("on_demand_ssids", emptySet())
    var onDemandCellular by store.boolean("on_demand_cellular", false)
}
