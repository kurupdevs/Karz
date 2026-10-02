package com.kurupdevs.karz

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.google.firebase.FirebaseApp
import com.kurupdevs.karz.di.ServiceLocator
import okio.Path.Companion.toPath

/**
 * Application entry point.
 *
 * Coil 3 singleton: 25% memory cache, 2% disk cache, 200ms crossfade
 * (SPEC section 5). Firebase initializes only when google-services.json
 * is present; without it the app runs fully offline-capable without
 * crashing (FirebaseInitProvider auto-init is removed in the manifest).
 */
class KarzApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        initFirebaseIfConfigured()
        ServiceLocator.init(this)
    }

    private fun initFirebaseIfConfigured() {
        val hasConfig = resources.getIdentifier("google_app_id", "string", packageName) != 0
        if (hasConfig) {
            FirebaseApp.initializeApp(this)
            firebaseEnabled = true
        } else {
            Log.w(TAG, "google-services.json not found; running without Firebase")
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_image_cache").absolutePath.toPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(200)
            .build()
    }

    companion object {
        private const val TAG = "KarzApp"

        /** True once FirebaseApp was initialized; data layer checks this. */
        @Volatile
        var firebaseEnabled: Boolean = false
            private set
    }
}
