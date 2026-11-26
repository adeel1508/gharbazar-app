package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

/**
 * The one admin identity for GharBazar. This is the same email the Firestore
 * rules and the website admin panel use (`isAdmin()` checks
 * `request.auth.token.email == 'adeel.arif0786@gmail.com'`). It is an
 * identifier, not a credential — the password is always typed by the owner
 * and is never stored anywhere in the app or the code.
 */
const val GHARBAZAR_ADMIN_EMAIL = "adeel.arif0786@gmail.com"

interface AdminAuthDataSource {
  val adminEmail: String
  val isSignedIn: Boolean
  val authState: Flow<Boolean>
  suspend fun signIn(password: String): Result<Unit>
  fun signOut()
}

/**
 * Firebase Auth email/password sign-in for the admin panel.
 *
 * The app previously had no sign-in at all, so every product write was
 * rejected by the Firestore rules ("Missing or insufficient permissions"):
 * products `allow write: if isAdmin()` needs a signed-in user with the
 * admin email. Signing in here also unlocks the Admin Orders tab — the
 * rules let the admin read every order. NOTE: a Firestore listener that
 * was attached BEFORE sign-in and failed with PERMISSION_DENIED does NOT
 * recover when the auth state changes — the SDK removes it. That is why
 * [OrderRepository.refreshOrders] exists: the admin panel calls it only
 * when the admin opens the Orders tab (or taps its Refresh button).
 * Nothing re-subscribes on startup or on auth-state change.
 *
 * The session persists across app restarts (Firebase Auth persistence), so
 * the owner signs in once and stays signed in.
 */
class FirebaseAdminAuthDataSource(
  context: Context,
) : AdminAuthDataSource {

  override val adminEmail: String = GHARBAZAR_ADMIN_EMAIL

  private val auth: FirebaseAuth? by lazy {
    runCatching {
      val apps = FirebaseApp.getApps(context.applicationContext)
      val app =
        if (apps.isNotEmpty()) {
          apps.first()
        } else {
          FirebaseApp.initializeApp(context.applicationContext)
        }
      if (app == null) null else FirebaseAuth.getInstance(app)
    }.onFailure { error ->
      Log.w(TAG, "Firebase Auth unavailable: ${error.message}")
    }.getOrNull()
  }

  private val _authState = MutableStateFlow(false)
  override val authState: Flow<Boolean> = _authState.asStateFlow()

  override val isSignedIn: Boolean
    get() = auth?.currentUser?.email.equals(GHARBAZAR_ADMIN_EMAIL, ignoreCase = true)

  init {
    auth?.addAuthStateListener { _authState.value = isSignedIn }
    _authState.value = isSignedIn
  }

  override suspend fun signIn(password: String): Result<Unit> {
    val firebaseAuth =
      auth ?: return Result.failure(
        IllegalStateException("Firebase Auth is not configured on this device")
      )
    return runCatching {
      firebaseAuth.signInWithEmailAndPassword(GHARBAZAR_ADMIN_EMAIL, password).await()
      if (!isSignedIn) {
        firebaseAuth.signOut()
        throw IllegalStateException("Signed in as a different account")
      }
    }
  }

  override fun signOut() {
    auth?.signOut()
  }

  companion object {
    private const val TAG = "FirebaseAdminAuth"
  }
}

/** Plain-language reason for a failed admin sign-in, for the UI to show. */
internal fun friendlyAdminAuthError(error: Throwable): String =
  when (error) {
    is FirebaseAuthInvalidCredentialsException ->
      "Incorrect password. Please try again."
    is FirebaseAuthInvalidUserException ->
      "No admin account found for $GHARBAZAR_ADMIN_EMAIL."
    is FirebaseNetworkException, is IOException ->
      "Could not reach Firebase. Check your connection and try again."
    else -> "Sign-in failed (${error.localizedMessage ?: "unknown error"})."
  }
