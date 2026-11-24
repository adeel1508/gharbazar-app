package com.example

import com.example.security.AdminPasswordManager
import com.example.security.PasswordActionResult
import com.example.security.PasswordStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePasswordStorage : PasswordStorage {
  var storedSalt: String? = null
  var storedHash: String? = null

  override fun hasPassword(): Boolean = !storedSalt.isNullOrBlank() && !storedHash.isNullOrBlank()

  override fun getSaltBase64(): String? = storedSalt

  override fun getHashBase64(): String? = storedHash

  override fun saveCredentials(saltBase64: String, hashBase64: String) {
    storedSalt = saltBase64
    storedHash = hashBase64
  }
}

class AdminPasswordManagerTest {

  @Test
  fun firstRunSetup_requiresMinSixCharsAndConfirmMatch_andNeverStoresPlaintext() {
    val storage = FakePasswordStorage()
    val manager = AdminPasswordManager(storage)

    assertFalse(manager.isPasswordSet())
    assertFalse(manager.verifyPassword("anything"))

    val tooShort = manager.setupInitialPassword("12345", "12345")
    assertTrue(tooShort is PasswordActionResult.Error)
    assertFalse(manager.isPasswordSet())

    val mismatch = manager.setupInitialPassword("ownerSecret99", "different99")
    assertTrue(mismatch is PasswordActionResult.Error)
    assertFalse(manager.isPasswordSet())

    val secretInput = "BazarOwner#2026"
    val ok = manager.setupInitialPassword(secretInput, secretInput)
    assertEquals(PasswordActionResult.Success, ok)
    assertTrue(manager.isPasswordSet())

    // Plaintext password must never be stored
    assertNotNull(storage.storedSalt)
    assertNotNull(storage.storedHash)
    assertNotEquals(secretInput, storage.storedHash)
    assertFalse(storage.storedHash!!.contains(secretInput))
  }

  @Test
  fun verifyPassword_acceptsCorrectPasswordAndRejectsWrongPassword() {
    val storage = FakePasswordStorage()
    val manager = AdminPasswordManager(storage)
    val ownerPassword = "MyShopPass!42"

    assertEquals(
      PasswordActionResult.Success,
      manager.setupInitialPassword(ownerPassword, ownerPassword),
    )

    assertTrue(manager.verifyPassword(ownerPassword))
    assertFalse(manager.verifyPassword("WrongPass!42"))
    assertFalse(manager.verifyPassword("myshoppass!42"))
    assertFalse(manager.verifyPassword(""))
  }

  @Test
  fun changePassword_requiresCurrentPasswordBeforeUpdatingHash() {
    val storage = FakePasswordStorage()
    val manager = AdminPasswordManager(storage)
    val initialPw = "FirstPass123"
    val updatedPw = "SecondPass456"

    assertEquals(
      PasswordActionResult.Success,
      manager.setupInitialPassword(initialPw, initialPw),
    )

    // Wrong current password rejected
    val wrongCurrent = manager.changePassword("WrongCurrent", updatedPw, updatedPw)
    assertTrue(wrongCurrent is PasswordActionResult.Error)
    assertTrue(manager.verifyPassword(initialPw))
    assertFalse(manager.verifyPassword(updatedPw))

    // Valid current password updates stored hash
    val changed = manager.changePassword(initialPw, updatedPw, updatedPw)
    assertEquals(PasswordActionResult.Success, changed)
    assertFalse(manager.verifyPassword(initialPw))
    assertTrue(manager.verifyPassword(updatedPw))
  }
}
