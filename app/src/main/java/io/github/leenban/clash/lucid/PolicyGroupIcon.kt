package io.github.leenban.clash.lucid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.SvgDecoder
import coil.memory.MemoryCache
import io.github.leenban.clash.design.lucid.*
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

// One shared loader keeps memory/disk caches across group pages.
internal object PolicyIconLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: android.content.Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache { MemoryCache.Builder(context.applicationContext).maxSizeBytes(4 * 1024 * 1024).build() }
            .components { add(SvgDecoder.Factory()) }
            .okHttpClient {
                OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
            }
            .build().also { instance = it }
    }

    fun clearMemory() { instance?.memoryCache?.clear() }
}

internal fun remotePolicyIcon(value: String): String? = runCatching {
    val uri = URI(value.trim())
    value.trim().takeIf {
        uri.scheme?.lowercase() in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}.getOrNull()

@Composable
internal fun PolicyGroupIcon(icon: String) {
    val context = LocalContext.current
    val url = remember(icon) { remotePolicyIcon(icon) }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.size(29.dp).clip(RoundedCornerShape(7.dp))
            .background(if (loaded) Color.Transparent else Color(0xFF5856D6)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) LucidIcon(
            LucidSymbol.Network, Color.White,
            Modifier.size(22.dp).testTag("policy-icon-default"),
        )
        if (url != null) AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).size(128).build() },
            imageLoader = PolicyIconLoader.get(context),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(29.dp).testTag("policy-icon-remote"),
            onSuccess = { loaded = true },
            onError = { loaded = false },
        )
    }
}
