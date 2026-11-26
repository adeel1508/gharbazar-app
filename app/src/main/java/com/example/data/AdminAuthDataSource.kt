package com.example.data

import android.content.Context
import kotlinx.coroutines.tasks.await

interface AdminAuthDataSource {
  fun currentUserEmail(): String?
  suspend fun signIn(email: String, password: String): Result<String>
  fun signOut()
}

/**
 * Firebase Auth wrapper for admin cloud sign-in.
 *
 * Crash-safety rule:
 * Does NOT register any AuthStateListener and never triggers background data refreshes
 * or navigation on auth-state changes.
 */
class FirebaseAdminAuthDataSource(
  context: Context,
) : AdminAuthDataSource {

  private val appContext = context.applicationContext

  private val auth
    get() = FirebaseClient.getAuth(appContext)

  override fun currentUserEmail(): String? {
    return runCatching { auth?.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() } }
      .getOrNull()
  }

  override suspend fun signIn(email: String, password: String): Result<String> {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty()) {
      return Result.failure(IllegalArgumentException("Please enter your admin email."))
    }
    if (password.isEmpty()) {
      return Result.failure(IllegalArgumentException("Please enter your password."))
    }
    val firebaseAuth =
      auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available."))

    return runCatching {
      val authResult = firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
      val signedInEmail =
        authResult.user?.email?.trim()?.takeIf { it.isNotEmpty() } ?: cleanEmail
      signedInEmail
    }
  }

  override fun signOut() {
    runCatching { auth?.signOut() }
  }
}
