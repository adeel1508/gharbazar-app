package com.example.data

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/** The seller/admin account — it is never a customer account. */
const val ADMIN_EMAIL = "adeel.arif0786@gmail.com"

/** True when this email belongs to the seller/admin account (never a customer). */
fun isAdminAccount(email: String?): Boolean =
  email?.trim().equals(ADMIN_EMAIL, ignoreCase = true)

/**
 * Shared validation for customer email/password.
 * [requireName] is the trimmed full name for sign-up (null for login).
 * Returns an error message, or null when the inputs are acceptable.
 */
fun validateCustomerCredentials(
  email: String,
  password: String,
  requireName: String? = null,
): String? {
  if (requireName != null && requireName.isBlank()) {
    return "Please enter your full name."
  }
  if (email.isBlank() || !email.contains("@") || !email.contains(".")) {
    return "Please enter a valid email address."
  }
  if (password.length < 6) {
    return "Password must be at least 6 characters."
  }
  return null
}

data class CustomerAccount(
  val uid: String,
  val email: String,
  val displayName: String?,
)

interface CustomerAuthDataSource {
  /**
   * Currently signed-in customer, or null when nobody is signed in (guest),
   * when the session belongs to the seller/admin account, or when auth is
   * unavailable. Never throws. Synchronous in-memory read — safe on any thread.
   */
  fun currentCustomer(): CustomerAccount?

  suspend fun signUp(name: String, email: String, password: String): Result<CustomerAccount>

  suspend fun signIn(email: String, password: String): Result<CustomerAccount>

  fun signOut()
}

/**
 * Firebase Auth wrapper for customer sign-up / login.
 *
 * Crash-safety rules (same as [FirebaseAdminAuthDataSource]):
 * - Does NOT register any AuthStateListener and never triggers background data
 *   refreshes or navigation on auth-state changes.
 * - Every call is defensive: a missing Firebase Auth instance or a failed
 *   request returns a failure Result, never a crash.
 * - Uses the single shared [FirebaseClient] auth instance. Customer and admin
 *   sign-in share that one Firebase session, so the seller account can never
 *   appear as a customer account (see [isAdminAccount]).
 */
class FirebaseCustomerAuthDataSource(
  context: Context,
) : CustomerAuthDataSource {

  private val appContext = context.applicationContext

  private val auth
    get() = FirebaseClient.getAuth(appContext)

  override fun currentCustomer(): CustomerAccount? {
    return runCatching { auth?.currentUser?.toCustomerAccount() }.getOrNull()
  }

  override suspend fun signUp(
    name: String,
    email: String,
    password: String,
  ): Result<CustomerAccount> {
    val cleanName = name.trim()
    val cleanEmail = email.trim()
    val validationError =
      validateCustomerCredentials(cleanEmail, password, requireName = cleanName)
    if (validationError != null) {
      return Result.failure(IllegalArgumentException(validationError))
    }
    if (isAdminAccount(cleanEmail)) {
      return Result.failure(
        IllegalArgumentException(
          "This email is the seller account. Please use the Seller / Admin panel."
        )
      )
    }
    val firebaseAuth =
      auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available."))

    return runCatching {
      val authResult =
        firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
      val user =
        authResult.user ?: throw IllegalStateException("Sign-up failed. Please try again.")
      // Best-effort display name: a failed profile update must not fail sign-up.
      runCatching {
        user
          .updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
          )
          .await()
      }
      user.toCustomerAccount()
        ?: throw IllegalStateException("Sign-up failed. Please try again.")
    }
  }

  override suspend fun signIn(email: String, password: String): Result<CustomerAccount> {
    val cleanEmail = email.trim()
    val validationError = validateCustomerCredentials(cleanEmail, password)
    if (validationError != null) {
      return Result.failure(IllegalArgumentException(validationError))
    }
    if (isAdminAccount(cleanEmail)) {
      return Result.failure(
        IllegalArgumentException(
          "This email is the seller account. Please use the Seller / Admin panel."
        )
      )
    }
    val firebaseAuth =
      auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available."))

    return runCatching {
      val authResult =
        firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
      val user =
        authResult.user ?: throw IllegalStateException("Login failed. Please try again.")
      // Defensive: a seller sign-in here must never become a customer session.
      if (isAdminAccount(user.email)) {
        runCatching { firebaseAuth.signOut() }
        throw IllegalStateException(
          "This email is the seller account. Please use the Seller / Admin panel."
        )
      }
      user.toCustomerAccount()
        ?: throw IllegalStateException("Login failed. Please try again.")
    }
  }

  override fun signOut() {
    runCatching { auth?.signOut() }
  }

  private fun FirebaseUser.toCustomerAccount(): CustomerAccount? {
    val uid = uid.takeIf { it.isNotBlank() } ?: return null
    val email = email?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (isAdminAccount(email)) return null
    return CustomerAccount(
      uid = uid,
      email = email,
      displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
    )
  }
}
