package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.example.model.isValidRemoteImageUrl
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

interface ImageUploadService {
  suspend fun uploadGalleryUri(localUriString: String): Result<String>
}

/**
 * Unsigned Cloudinary uploader (free tier, no secret in code).
 * Downscales picked images to max 1280px, encodes JPEG quality ~85 on Dispatchers.IO,
 * and returns the remote `secure_url`.
 */
class CloudinaryImageUploader(
  context: Context,
) : ImageUploadService {

  private val appContext = context.applicationContext

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(45, TimeUnit.SECONDS)
      .readTimeout(35, TimeUnit.SECONDS)
      .build()
  }

  override suspend fun uploadGalleryUri(localUriString: String): Result<String> {
    return withContext(Dispatchers.IO) {
      runCatching {
        val parsedUri = Uri.parse(localUriString)
        val jpegBytes = decodeAndCompressJpeg(parsedUri)

        val requestBody =
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .addFormDataPart(
              "file",
              "product_${System.currentTimeMillis()}.jpg",
              jpegBytes.toRequestBody("image/jpeg".toMediaType()),
            )
            .build()

        val request =
          Request.Builder()
            .url(CLOUDINARY_UPLOAD_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val apiError =
              runCatching {
                JSONObject(bodyText).optJSONObject("error")?.optString("message")
              }.getOrNull()?.takeIf { it.isNotBlank() }
            throw IllegalStateException(
              apiError ?: "Photo upload failed (HTTP ${response.code})."
            )
          }

          val secureUrl = JSONObject(bodyText).optString("secure_url").trim()
          if (!isValidRemoteImageUrl(secureUrl)) {
            throw IllegalStateException("Upload succeeded without a valid remote image URL.")
          }
          secureUrl
        }
      }.onFailure { err ->
        Log.w(TAG, "Cloudinary upload failed: ${err.message}")
      }
    }
  }

  private fun decodeAndCompressJpeg(uri: Uri): ByteArray {
    val source = ImageDecoder.createSource(appContext.contentResolver, uri)
    val bitmap =
      ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
          val scale =
            if (width >= height) {
              MAX_DIMENSION_PX.toFloat() / width.toFloat()
            } else {
              MAX_DIMENSION_PX.toFloat() / height.toFloat()
            }
          val targetW = (width * scale).roundToInt().coerceAtLeast(1)
          val targetH = (height * scale).roundToInt().coerceAtLeast(1)
          decoder.setTargetSize(targetW, targetH)
        }
      }

    return try {
      ByteArrayOutputStream().use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
          throw IllegalStateException("Could not encode photo as JPEG.")
        }
        out.toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
  }

  companion object {
    const val CLOUDINARY_UPLOAD_URL = "https://api.cloudinary.com/v1_1/mlfzivam/image/upload"
    const val UPLOAD_PRESET = "gharbazar-products"
    private const val MAX_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 85
    private const val TAG = "CloudinaryUploader"
  }
}
