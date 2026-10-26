// Adapted from Lee's Music d305079; independent Lee's Clash signing.
package io.github.leenban.clash.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val version: String,
    val versionCode: Long,
    val packageName: String,
    val apk: String,
    val sha256: String,
    val size: Long,
    val minSdk: Int,
    val notes: String = "",
) {
    fun validate(expectedPackage: String = "io.github.leenban.clash") {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        require(versionCode > 0 && packageName == expectedPackage)
        require(apk == "lees-clash-$version.apk")
        require(version.substringAfterLast('.').toInt() in 0..49)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(size in 1..512L * 1024 * 1024 && minSdk >= 26)
        require(notes.length <= 10000)
    }
}
