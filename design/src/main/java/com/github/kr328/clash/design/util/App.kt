package io.github.leenban.clash.design.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.leenban.clash.common.compat.foreground
import io.github.leenban.clash.design.model.AppInfo

fun PackageInfo.toAppInfo(pm: PackageManager): AppInfo {
    return AppInfo(
        packageName = packageName,
        icon = applicationInfo!!.loadIcon(pm).foreground(),
        label = applicationInfo!!.loadLabel(pm).toString(),
        installTime = firstInstallTime,
        updateDate = lastUpdateTime,
    )
}
