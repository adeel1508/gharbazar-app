package com.example

import com.example.data.CloudinaryUploader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure, network-free checks for the free photo-hosting upload used by the
 * admin product form: the upload endpoint, the secure_url parsing, and the
 * downscale maths. The actual HTTP upload is exercised live on the phone
 * (Cloudinary unsigned preset `gharbazar-products`, cloud `mlfzivam`) —
 * no secret is involved, so nothing here needs a credential.
 */
class CloudinaryUploaderTest {

  @Test
  fun uploadUrl_pointsAtCloudinaryWithOurCloudName() {
    assertEquals(
      "https://api.cloudinary.com/v1_1/mlfzivam/image/upload",
      CloudinaryUploader.uploadUrl(),
    )
  }

  @Test
  fun parseSecureUrl_returnsSecureUrl() {
    val response =
      """{"asset_id":"abc","secure_url":"https://res.cloudinary.com/mlfzivam/image/upload/v1/photo.jpg","url":"http://res.cloudinary.com/mlfzivam/image/upload/v1/photo.jpg"}"""
    assertEquals(
      "https://res.cloudinary.com/mlfzivam/image/upload/v1/photo.jpg",
      CloudinaryUploader.parseSecureUrl(response),
    )
  }

  @Test
  fun parseSecureUrl_returnsNullWhenMissingOrGarbage() {
    assertNull(CloudinaryUploader.parseSecureUrl("""{"error":{"message":"Unknown API key"}}"""))
    assertNull(CloudinaryUploader.parseSecureUrl("{}"))
    assertNull(CloudinaryUploader.parseSecureUrl("not json at all"))
    assertNull(CloudinaryUploader.parseSecureUrl(""))
  }

  @Test
  fun scaledDimensions_keepsSmallPhotosUnchanged() {
    assertEquals(800 to 600, CloudinaryUploader.scaledDimensions(800, 600))
    assertEquals(1280 to 960, CloudinaryUploader.scaledDimensions(1280, 960))
  }

  @Test
  fun scaledDimensions_downscalesWidePhotoToMaxSide() {
    assertEquals(1280 to 640, CloudinaryUploader.scaledDimensions(4000, 2000))
  }

  @Test
  fun scaledDimensions_downscalesTallPhotoToMaxSide() {
    assertEquals(426 to 1280, CloudinaryUploader.scaledDimensions(1000, 3000))
  }

  @Test
  fun uploadPreset_isUnsignedPresetName() {
    // The app only ever sends the public preset name; no API secret or
    // key is embedded anywhere.
    assertEquals("gharbazar-products", CloudinaryUploader.UPLOAD_PRESET)
    assertTrue(CloudinaryUploader.CLOUD_NAME.isNotBlank())
  }
}
