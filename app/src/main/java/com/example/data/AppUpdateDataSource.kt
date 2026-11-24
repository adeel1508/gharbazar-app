package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.tasks.await

data class AppUpdateInfo(
  val latestVersionCode: Int,
  val apkUrl: String,
)

interface AppUpdateDataSource {
  suspend fun fetchUpdateInfo(currentVersionCode: Int): AppUpdateInfo?
}

fun shouldPromptForUpdate(
  updateInfo: AppUpdateInfo?,
  currentVersionCode: Int,
): AppUpdateInfo? {
  if (updateInfo == null) return null
  val url = updateInfo.apkUrl.trim()
  if (updateInfo.latestVersionCode <= currentVersionCode || url.isEmpty()) {
    return null
  }
  val normalizedUrl =
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
      url
    } else {
      "https://$url"
    }
  return updateInfo.copy(apkUrl = normalizedUrl)
}

/**
 * Reads `app_config/version` from Cloud Firestore on launch.
 * Creates the document with the current `versionCode` and an empty `apkUrl` if it does not exist yet.
 * Never throws or blocks normal app operation if missing or unreadable.
 */
class FirestoreAppUpdateDataSource(
  context: Context,
) : AppUpdateDataSource {

  private val appContext = context.applicationContext

  private val firestore: FirebaseFirestore? by lazy {
    runCatching {
      val apps = FirebaseApp.getApps(appContext)
      val app =
        if (apps.isNotEmpty()) {
          apps.first()
        } else {
          FirebaseApp.initializeApp(appContext)
        }
      if (app == null) {
        null
      } else {
        FirebaseFirestore.getInstance(app).apply {
          firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { })
          }
        }
      }
    }.onFailure { error ->
      Log.w(TAG, "Firebase Firestore unavailable for app update check: ${error.message}")
    }.getOrNull()
  }

  override suspend fun fetchUpdateInfo(currentVersionCode: Int): AppUpdateInfo? {
    val db = firestore ?: return null
    return runCatching {
      val docRef = db.collection(APP_CONFIG_COLLECTION).document(VERSION_DOCUMENT)

      val serverSnapshot = runCatching { docRef.get(Source.SERVER).await() }.getOrNull()
      val snapshot =
        if (serverSnapshot != null) {
          if (!serverSnapshot.exists()) {
            // Seed app_config/version so the remote update mechanism is ready
            runCatching {
              docRef.set(
                mapOf(
                  FIELD_LATEST_VERSION_CODE to currentVersionCode,
                  FIELD_APK_URL to "",
                )
              ).await()
            }.onFailure { err ->
              Log.w(TAG, "Could not seed app_config/version: ${err.message}")
            }
            return@runCatching AppUpdateInfo(
              latestVersionCode = currentVersionCode,
              apkUrl = "",
            )
          }
          serverSnapshot
        } else {
          // Offline fallback to cached document if previously fetched
          runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        }

      if (snapshot == null || !snapshot.exists()) {
        return@runCatching null
      }

      val latestVersionCode =
        (snapshot.get(FIELD_LATEST_VERSION_CODE) as? Number)?.toInt() ?: return@runCatching null
      val apkUrl = snapshot.getString(FIELD_APK_URL).orEmpty().trim()
      AppUpdateInfo(
        latestVersionCode = latestVersionCode,
        apkUrl = apkUrl,
      )
    }.onFailure { error ->
      Log.w(TAG, "Failed to read app_config/version: ${error.message}")
    }.getOrNull()
  }

  companion object {
    const val APP_CONFIG_COLLECTION = "app_config"
    const val VERSION_DOCUMENT = "version"
    const val FIELD_LATEST_VERSION_CODE = "latestVersionCode"
    const val FIELD_APK_URL = "apkUrl"
    private const val TAG = "FirestoreUpdateSource"
  }
}
