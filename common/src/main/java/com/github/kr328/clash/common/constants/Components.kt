package io.github.leenban.clash.common.constants

import android.content.ComponentName
import io.github.leenban.clash.common.util.packageName

object Components {
    private const val componentsPackageName = "io.github.leenban.clash"

    val MAIN_ACTIVITY = ComponentName(packageName, "$componentsPackageName.MainActivity")
    val PROPERTIES_ACTIVITY = ComponentName(packageName, "$componentsPackageName.PropertiesActivity")
}