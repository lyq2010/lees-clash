package io.github.leenban.clash.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoAsset(
    val id: String,
    val name: String,
    val file: String,
    val size: Long,
    val updatedAt: Long,
)
