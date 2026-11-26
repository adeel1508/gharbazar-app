package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

/**
 * Single shared Firebase provider for GharBazar.
 *
 * Crash-safety rule:
 * Firestore settings are configured EXACTLY ONCE for the whole app on this single shared instance,
 * before any collection or document call occurs. Never call `firestoreSettings = ...` anywhere else.
 */
object FirebaseClient {
  private const val TAG = "FirebaseClient"
  private val lock = Any()

  @Volatile private var initialized = false
  @Volatile private var appInstance: FirebaseApp? = null
  @Volatile private var firestoreInstance: FirebaseFirestore? = null
  @Volatile private var authInstance: FirebaseAuth? = null

  private fun ensureInitialized(context: Context) {
    if (initialized) return
    synchronized(lock) {
      if (initialized) return
      val appContext = context.applicationContext
      runCatching {
        val existingApps = FirebaseApp.getApps(appContext)
        val app =
          if (existingApps.isNotEmpty()) {
            existingApps.first()
          } else {
            FirebaseApp.initializeApp(appContext)
          }
        appInstance = app
        if (app != null) {
          val db = FirebaseFirestore.getInstance(app)
          runCatching {
            db.firestoreSettings = firestoreSettings {
              setLocalCacheSettings(persistentCacheSettings { })
            }
          }.onFailure { err ->
            Log.w(TAG, "Firestore settings already configured or unavailable: ${err.message}")
          }
          firestoreInstance = db
          authInstance =
            runCatching { FirebaseAuth.getInstance(app) }
              .onFailure { err -> Log.w(TAG, "FirebaseAuth unavailable: ${err.message}") }
              .getOrNull()
        }
      }.onFailure { error ->
        Log.w(TAG, "Firebase initialization unavailable: ${error.message}")
      }
      initialized = true
    }
  }

  fun getFirestore(context: Context): FirebaseFirestore? {
    ensureInitialized(context)
    return firestoreInstance
  }

  fun getAuth(context: Context): FirebaseAuth? {
    ensureInitialized(context)
    return authInstance
  }
}
