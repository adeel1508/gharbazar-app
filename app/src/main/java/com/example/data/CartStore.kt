package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Local persistence for the guest cart (no login, no Firestore cart). */
interface CartStore {
  /** Saved lines, in saved order. Never throws — failure yields an empty list. */
  suspend fun loadSavedEntries(): List<SavedCartEntry>

  /** Overwrites the whole saved cart. Never throws. */
  suspend fun saveEntries(entries: List<SavedCartEntry>)

  /** Removes the saved cart entirely. Never throws. */
  suspend fun clearSavedEntries()
}

/**
 * SharedPreferences-backed [CartStore].
 *
 * The cart is one JSON string ([CartJson]) in plain SharedPreferences — the
 * cart holds no sensitive data. All disk access runs on Dispatchers.IO and
 * every call is defensive, so a storage problem can never crash the app or
 * block startup (see crash-safety rules).
 */
class SharedPreferencesCartStore(context: Context) : CartStore {
  private val appContext = context.applicationContext

  // Serializes writes so rapid cart taps can never land out of order
  // (a stale write overwriting a newer one would lose items on restart).
  private val writeMutex = Mutex()

  // Lazy so the prefs file is first touched on an IO thread, never on Main.
  private val prefs: SharedPreferences? by lazy {
    runCatching {
      appContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }
      .onFailure { err -> Log.w(TAG, "Cart prefs unavailable: ${err.message}") }
      .getOrNull()
  }

  override suspend fun loadSavedEntries(): List<SavedCartEntry> =
    withContext(Dispatchers.IO) {
      runCatching { CartJson.decode(prefs?.getString(KEY_CART_JSON, null)) }
        .getOrElse { emptyList() }
    }

  override suspend fun saveEntries(entries: List<SavedCartEntry>) {
    writeMutex.withLock {
      withContext(Dispatchers.IO) {
        runCatching {
          prefs?.edit()?.putString(KEY_CART_JSON, CartJson.encode(entries))?.apply()
        }.onFailure { err -> Log.w(TAG, "Failed to save cart: ${err.message}") }
      }
    }
  }

  override suspend fun clearSavedEntries() {
    writeMutex.withLock {
      withContext(Dispatchers.IO) {
        runCatching { prefs?.edit()?.remove(KEY_CART_JSON)?.apply() }
          .onFailure { err -> Log.w(TAG, "Failed to clear saved cart: ${err.message}") }
      }
    }
  }

  companion object {
    private const val TAG = "CartStore"
    private const val PREFS_FILE_NAME = "gharbazar_cart_prefs"
    private const val KEY_CART_JSON = "saved_cart_json"
  }
}

/** In-memory no-op store (tests and unconfigured builds). */
object NoOpCartStore : CartStore {
  override suspend fun loadSavedEntries(): List<SavedCartEntry> = emptyList()
  override suspend fun saveEntries(entries: List<SavedCartEntry>) = Unit
  override suspend fun clearSavedEntries() = Unit
}
