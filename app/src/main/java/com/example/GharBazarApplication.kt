package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers

class GharBazarApplication : Application(), ImageLoaderFactory {
  override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.25)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(cacheDir.resolve("gharbazar_image_cache"))
          .maxSizeBytes(50L * 1024L * 1024L)
          .build()
      }
      .dispatcher(Dispatchers.IO)
      .crossfade(200)
      .respectCacheHeaders(false)
      .build()
  }
}
