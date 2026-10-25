package io.github.leenban.clash

import android.content.pm.PackageManager
import io.github.leenban.clash.common.compat.getDrawableCompat
import io.github.leenban.clash.common.constants.Metadata
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.design.OverrideSettingsDesign
import io.github.leenban.clash.design.model.AppInfo
import io.github.leenban.clash.design.util.toAppInfo
import io.github.leenban.clash.service.store.ServiceStore
import io.github.leenban.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class OverrideSettingsActivity : BaseActivity<OverrideSettingsDesign>() {
    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }
        val service = ServiceStore(this)

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = OverrideSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        OverrideSettingsDesign.Request.ResetOverride -> {
                            if (design.requestResetConfirm()) {
                                defer {
                                    withClash {
                                        clearOverride(Clash.OverrideSlot.Persist)
                                    }
                                }

                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}