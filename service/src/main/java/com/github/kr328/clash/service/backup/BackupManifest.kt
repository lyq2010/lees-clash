package io.github.leenban.clash.service.backup

import io.github.leenban.clash.service.model.Profile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long,
    val profiles: List<Profile>,
    val selections: Map<String, Map<String, String>>,
    val overrides: JsonObject,
    val preferences: JsonObject,
    val ui: JsonObject,
    val geo: List<String>,
)

@Serializable data class BackupPreview(val createdAt: Long, val profiles: Int, val geoFiles: Int)

@Serializable
data class BackupRestoreResult(val restored: Int, val skipped: Int, val ui: JsonObject)
