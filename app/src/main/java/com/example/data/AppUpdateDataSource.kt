package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.isValidRemoteImageUrl
import kotlinx.coroutines.tasks.await

data class AppUpdateInfo(
  val latestVersionCode: Int,
  val apkUrl: String,
)

sealed interface ManualUpdateCheckResult {
  data class UpdateAvailable(val info: AppUpdateInfo) : ManualUpdateCheckResult
  data object UpToDate : ManualUpdateCheckResult
}

interface AppUpdateDataSource {
  suspend fun checkForUpdate(currentVersionCode: Int): Result<ManualUpdateCheckResult>
}

fun evaluateManualUpdateCheck(
  latestVersionCode: Int?,
  apkUrl: String?,
  currentVersionCode: Int,
): ManualUpdateCheckResult {
  val remoteCode = latestVersionCode ?: return ManualUpdateCheckResult.UpToDate
  val cleanUrl = apkUrl?.trim().orEmpty()
  if (remoteCode > currentVersionCode && isValidRemoteImageUrl(cleanUrl)) {
    return ManualUpdateCheckResult.UpdateAvailable(
      AppUpdateInfo(
        latestVersionCode = remoteCode,
        apkUrl = cleanUrl,
      )
    )
  }
  return ManualUpdateCheckResult.UpToDate
}

/**
 * Manual-only update checker.
 *
 * Crash-safety rules:
 * - Never runs at app launch.
 * - Reads `app_config/version` from Firestore ONLY when admin explicitly taps "Check for Updates".
 * - Never writes or seeds Firestore documents.
 * - Uses the single shared [FirebaseClient] Firestore instance.
 */
class FirestoreAppUpdateDataSource(
  context: Context,
) : AppUpdateDataSource {

  private val appContext = context.applicationContext

  private val firestore
    get() = FirebaseClient.getFirestore(appContext)

  override suspend fun checkForUpdate(
    currentVersionCode: Int,
  ): Result<ManualUpdateCheckResult> {
    val db =
      firestore
        ?: return Result.failure(IllegalStateException("Firebase Firestore is not configured."))

    return runCatching {
      val snapshot =
        db.collection(APP_CONFIG_COLLECTION)
          .document(VERSION_DOCUMENT)
          .get()
          .await()

      if (!snapshot.exists()) {
        return@runCatching ManualUpdateCheckResult.UpToDate
      }

      val latestVersionCode =
        when (val raw = snapshot.get(FIELD_LATEST_VERSION_CODE)) {
          is Number -> raw.toInt()
          is String -> raw.trim().toIntOrNull()
          else -> null
        }
      val apkUrl = snapshot.getString(FIELD_APK_URL)?.trim()

      evaluateManualUpdateCheck(
        latestVersionCode = latestVersionCode,
        apkUrl = apkUrl,
        currentVersionCode = currentVersionCode,
      )
    }.onFailure { err ->
      Log.w(TAG, "Manual update check failed: ${err.message}")
    }
  }

  companion object {
    const val APP_CONFIG_COLLECTION = "app_config"
    const val VERSION_DOCUMENT = "version"
    const val FIELD_LATEST_VERSION_CODE = "latestVersionCode"
    const val FIELD_APK_URL = "apkUrl"
    private const val TAG = "FirestoreUpdateSource"
  }
}
