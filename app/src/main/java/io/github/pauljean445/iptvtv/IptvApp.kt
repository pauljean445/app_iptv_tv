package io.github.pauljean445.iptvtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Chargeur d'images (affiches, logos) réglé pour une TV : peu de mémoire et peu de stockage libre. */
class IptvApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(30L * 1024 * 1024).build() }
        // Pas de RGB_565 : les logos de chaînes (PNG à palette transparente) y perdent leur transparence
        .respectCacheHeaders(false) // les affiches ne changent pas : on garde ce qui est en cache
        .crossfade(false)
        .build()
}
