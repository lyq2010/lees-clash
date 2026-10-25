package io.github.leenban.clash.service.util

import io.github.leenban.clash.service.data.ImportedDao
import io.github.leenban.clash.service.data.PendingDao
import java.util.*

suspend fun generateProfileUUID(): UUID {
    var result = UUID.randomUUID()

    while (ImportedDao().exists(result) || PendingDao().exists(result)) {
        result = UUID.randomUUID()
    }

    return result
}
