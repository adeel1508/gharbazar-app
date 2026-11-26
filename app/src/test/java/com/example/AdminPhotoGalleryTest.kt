package com.example

import com.example.ui.screens.buildAdminGallery
import com.example.ui.screens.isRemotePhotoUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The admin photo form uploads a phone-picked photo to free photo hosting
 * (Cloudinary, unsigned upload) before saving, and only the returned
 * https URL enters the gallery. These tests lock in the other half of
 * that behaviour — only remote (http/https) photo URLs ever reach the
 * saved gallery, so a device-local content:// URI can never be persisted
 * to Firestore and break the product on the website or on other devices.
 */
class AdminPhotoGalleryTest {

  @Test
  fun isRemotePhotoUrl_acceptsOnlyHttpAndHttps() {
    assertTrue(isRemotePhotoUrl("https://example.com/photo.jpg"))
    assertTrue(isRemotePhotoUrl("http://example.com/photo.jpg"))
    assertFalse(isRemotePhotoUrl("content://media/picker/0/com.android.providers.media.photopicker/media/123"))
    assertFalse(isRemotePhotoUrl("content://com.android.providers.media.documents/document/image%3A42"))
    assertFalse(isRemotePhotoUrl("file:///storage/emulated/0/photo.jpg"))
    assertFalse(isRemotePhotoUrl(null))
    assertFalse(isRemotePhotoUrl(""))
    assertFalse(isRemotePhotoUrl("   "))
    assertFalse(isRemotePhotoUrl("ftp://example.com/photo.jpg"))
  }

  @Test
  fun buildAdminGallery_remoteCoverFirstThenTypedUrls() {
    val gallery =
      buildAdminGallery(
        imageUri = "https://static.markaz.app/pakistan/products/1473-94-564232-product-1.webp",
        imageUrlsText =
          "https://static.markaz.app/pakistan/products/1473-94-564232-product-1.webp\n" +
            "https://example.com/second.jpg\n",
      )
    assertEquals(
      listOf(
        "https://static.markaz.app/pakistan/products/1473-94-564232-product-1.webp",
        "https://example.com/second.jpg",
      ),
      gallery,
    )
  }

  @Test
  fun buildAdminGallery_contentUriCoverIsNeverSaved() {
    // A product previously saved by the old form can carry a content://
    // cover; editing and saving it must drop the broken local link.
    val gallery =
      buildAdminGallery(
        imageUri = "content://media/picker/0/com.android.providers.media.photopicker/media/123",
        imageUrlsText = "https://example.com/real-photo.jpg\n",
      )
    assertEquals(listOf("https://example.com/real-photo.jpg"), gallery)
  }

  @Test
  fun buildAdminGallery_contentUriInTypedUrlsIsDropped() {
    val gallery =
      buildAdminGallery(
        imageUri = null,
        imageUrlsText =
          "content://media/picker/0/com.android.providers.media.photopicker/media/123\n" +
            "https://example.com/real-photo.jpg\n",
      )
    assertEquals(listOf("https://example.com/real-photo.jpg"), gallery)
  }

  @Test
  fun buildAdminGallery_onlyPickedPhonePhotoYieldsEmptyGallery() {
    // Phone-picked photo only, no URLs at all: nothing remote to save.
    // (The form uploads the picked photo first and saves only the
    // returned https URL; the gallery builder itself must also never
    // invent a content:// entry.)
    val gallery = buildAdminGallery(imageUri = null, imageUrlsText = "  \n\n")
    assertTrue(gallery.isEmpty())
  }

  @Test
  fun buildAdminGallery_deduplicatesAndKeepsOrder() {
    val gallery =
      buildAdminGallery(
        imageUri = "https://example.com/a.jpg",
        imageUrlsText = "https://example.com/b.jpg\nhttps://example.com/a.jpg\nhttps://example.com/b.jpg\n",
      )
    assertEquals(
      listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
      gallery,
    )
  }
}
