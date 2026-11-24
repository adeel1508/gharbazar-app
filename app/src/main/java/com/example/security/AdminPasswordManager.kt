package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

interface PasswordStorage {
  fun hasPassword(): Boolean
  fun getSaltBase64(): String?
  fun getHashBase64(): String?
  fun saveCredentials(saltBase64: String, hashBase64: String)
}

class EncryptedPasswordStorage(context: Context) : PasswordStorage {
  private val prefs: SharedPreferences by lazy {
    val appContext = context.applicationContext
    val masterKey =
      MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
      appContext,
      PREFS_FILE_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  override fun hasPassword(): Boolean {
    return !prefs.getString(KEY_SALT, null).isNullOrBlank() &&
      !prefs.getString(KEY_HASH, null).isNullOrBlank()
  }

  override fun getSaltBase64(): String? = prefs.getString(KEY_SALT, null)

  override fun getHashBase64(): String? = prefs.getString(KEY_HASH, null)

  override fun saveCredentials(saltBase64: String, hashBase64: String) {
    prefs.edit()
      .putString(KEY_SALT, saltBase64)
      .putString(KEY_HASH, hashBase64)
      .apply()
  }

  companion object {
    private const val PREFS_FILE_NAME = "gharbazar_admin_secure_prefs"
    private const val KEY_SALT = "admin_password_salt"
    private const val KEY_HASH = "admin_password_hash"
  }
}

sealed interface PasswordActionResult {
  data object Success : PasswordActionResult
  data class Error(val message: String) : PasswordActionResult
}

/**
 * Manages the owner-defined Seller / Admin password.
 * Stores only a salted PBKDF2-HMAC-SHA256 hash in EncryptedSharedPreferences.
 * Contains no hardcoded password, PIN, default password, or reset backdoor.
 */
class AdminPasswordManager(
  private val storage: PasswordStorage,
) {
  private val secureRandom = SecureRandom()

  fun isPasswordSet(): Boolean = storage.hasPassword()

  fun setupInitialPassword(password: String, confirmPassword: String): PasswordActionResult {
    if (storage.hasPassword()) {
      return PasswordActionResult.Error("Admin password is already configured.")
    }
    val validationError = validateNewPassword(password, confirmPassword)
    if (validationError != null) {
      return PasswordActionResult.Error(validationError)
    }
    storePasswordHash(password)
    return PasswordActionResult.Success
  }

  fun verifyPassword(password: String): Boolean {
    if (password.isEmpty()) return false
    val saltBase64 = storage.getSaltBase64() ?: return false
    val storedHashBase64 = storage.getHashBase64() ?: return false

    val saltBytes = runCatching { Base64.getDecoder().decode(saltBase64) }.getOrNull() ?: return false
    val storedHashBytes =
      runCatching { Base64.getDecoder().decode(storedHashBase64) }.getOrNull() ?: return false

    val candidateHash = hashPassword(password.toCharArray(), saltBytes)
    return MessageDigest.isEqual(storedHashBytes, candidateHash)
  }

  fun changePassword(
    currentPassword: String,
    newPassword: String,
    confirmNewPassword: String,
  ): PasswordActionResult {
    if (!storage.hasPassword()) {
      return PasswordActionResult.Error("No admin password is set yet.")
    }
    if (!verifyPassword(currentPassword)) {
      return PasswordActionResult.Error("Current password is incorrect.")
    }
    val validationError = validateNewPassword(newPassword, confirmNewPassword)
    if (validationError != null) {
      return PasswordActionResult.Error(validationError)
    }
    storePasswordHash(newPassword)
    return PasswordActionResult.Success
  }

  private fun validateNewPassword(password: String, confirmPassword: String): String? {
    if (password.length < MIN_PASSWORD_LENGTH) {
      return "Password must be at least $MIN_PASSWORD_LENGTH characters."
    }
    if (password != confirmPassword) {
      return "Passwords do not match."
    }
    return null
  }

  private fun storePasswordHash(password: String) {
    val salt = ByteArray(SALT_BYTE_LENGTH)
    secureRandom.nextBytes(salt)
    val hash = hashPassword(password.toCharArray(), salt)
    val encoder = Base64.getEncoder()
    storage.saveCredentials(
      saltBase64 = encoder.encodeToString(salt),
      hashBase64 = encoder.encodeToString(hash),
    )
  }

  private fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, HASH_BIT_LENGTH)
    return try {
      SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  companion object {
    const val MIN_PASSWORD_LENGTH = 6
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_BYTE_LENGTH = 16
    private const val HASH_BIT_LENGTH = 256
  }
}
