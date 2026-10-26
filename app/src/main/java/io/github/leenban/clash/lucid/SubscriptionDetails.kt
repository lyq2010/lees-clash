package io.github.leenban.clash.lucid

import androidx.compose.runtime.Composable
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.service.model.Profile
import java.text.DateFormat
import java.util.Date

@Composable
fun SubscriptionDetails(profile: Profile) {
    val provided =
        profile.total > 0 || profile.upload > 0 || profile.download > 0 || profile.expire > 0
    LucidSection("订阅信息", footer = if (!provided) "订阅服务未提供流量与到期信息。" else "由订阅服务返回，更新配置后刷新。") {
        if (provided) {
            LucidRow("上传", value = formatBytes(profile.upload))
            LucidRow("下载", value = formatBytes(profile.download))
            if (profile.total > 0) {
                LucidRow("总量", value = formatBytes(profile.total))
                LucidRow(
                    "剩余",
                    value =
                        formatBytes(
                            (profile.total - profile.upload - profile.download).coerceAtLeast(0)
                        ),
                )
            }
            if (profile.expire > 0)
                LucidRow(
                    "到期时间",
                    value =
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(profile.expire)),
                )
        }
    }
}
