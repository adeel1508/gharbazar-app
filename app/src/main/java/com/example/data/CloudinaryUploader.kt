package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Uploads product photos picked from the phone gallery to Cloudinary's
 * free image hosting (unsigned upload — no API key or secret is needed,
 * and none is embedded in the app; only the public cloud name and the
 * unsigned preset name below are used). The returned `secure_url` is a
 * normal https link that the app and the website can both show, so a
 * Markaz product can be added from the phone gallery with no photo URL
 * at all. No billing is involved (Cloudinary free plan).
 *
 * A photo picked from the phone is a device-local content:// URI. It must
 * NEVER be saved to Firestore as-is: it is broken on the website and on
 * every other device, and it stops working on this phone after a restart.
 * The admin form uploads it first and saves only the returned https URL.
 */
object CloudinaryUploader {

  /** Public Cloudinary cloud name (shown on the Cloudinary dashboard). */
  const val CLOUD_NAME = "mlfzivam"

  /** Unsigned upload preset (dashboard: Settings → Upload → Upload presets). */
  const val UPLOAD_PRESET = "gharbazar-products"

  /** Photos are downscaled so the longest side is at most this many pixels. */
  const val MAX_DIMENSION_PX = 1280

  /** JPEG quality used when compressing the photo before upload. */
  const val JPEG_QUALITY = 85

  fun uploadUrl(cloudName: String = CLOUD_NAME): String =
    "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

  /**
   * Pulls the `secure_url` out of a Cloudinary upload response, or null
   * when the response has none (e.g. an error payload).
   */
  fun parseSecureUrl(responseJson: String): String? =
    runCatching {
        JSONObject(responseJson).optString("secure_url").takeIf { it.isNotBlank() }
      }
      .getOrNull()

  /**
   * Scales (width, height) down so the longest side is at most
   * [maxDimension], keeping the aspect ratio. Small photos are unchanged.
   */
  fun scaledDimensions(
    width: Int,
    height: Int,
    maxDimension: Int = MAX_DIMENSION_PX,
  ): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxDimension || longest <= 0) return width to height
    val scale = maxDimension.toDouble() / longest
    return (width * scale).toInt().coerceAtLeast(1) to
      (height * scale).toInt().coerceAtLeast(1)
  }

  /**
   * Reads the photo at [uri], downscales/compresses it to JPEG, uploads it
   * to Cloudinary, and returns the public https URL on success.
   */
  suspend fun uploadPhoto(context: Context, uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val jpegBytes = readScaledJpeg(context, uri)
        postToCloudinary(jpegBytes)
      }
    }

  private fun readScaledJpeg(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bounds =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DIMENSION_PX) {
      sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded =
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalStateException("Could not read the picked photo.")
    val (targetWidth, targetHeight) = scaledDimensions(decoded.width, decoded.height)
    val scaled =
      if (targetWidth != decoded.width || targetHeight != decoded.height) {
        Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
      } else {
        decoded
      }
    val out = ByteArrayOutputStream()
    check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
      "Could not compress the picked photo."
    }
    return out.toByteArray()
  }

  private fun postToCloudinary(jpegBytes: ByteArray): String {
    val boundary = "----GharBazar${System.currentTimeMillis()}"
    val connection =
      (URL(uploadUrl()).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 60_000
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
      }
    try {
      connection.outputStream.use { out ->
        writeTextPart(out, boundary, "upload_preset", UPLOAD_PRESET)
        writeFilePart(out, boundary, "file", "photo.jpg", "image/jpeg", jpegBytes)
        out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
      }
      val code = connection.responseCode
      val body =
        (if (code in 200..299) connection.inputStream else connection.errorStream)
          ?.bufferedReader()
          .use { it?.readText().orEmpty() }
      if (code !in 200..299) {
        throw IllegalStateException(
          "Photo upload failed (HTTP $code): ${body.take(200)}"
        )
      }
      return parseSecureUrl(body)
        ?: throw IllegalStateException(
          "Photo upload failed: no photo link in the response."
        )
    } finally {
      connection.disconnect()
    }
  }

  private fun writeTextPart(out: OutputStream, boundary: String, name: String, value: String) {
    out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(Charsets.UTF_8))
    out.write("$value\r\n".toByteArray(Charsets.UTF_8))
  }

  private fun writeFilePart(
    out: OutputStream,
    boundary: String,
    name: String,
    fileName: String,
    contentType: String,
    bytes: ByteArray,
  ) {
    out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
    out.write(
      "Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n"
        .toByteArray(Charsets.UTF_8)
    )
    out.write("Content-Type: $contentType\r\n\r\n".toByteArray(Charsets.UTF_8))
    out.write(bytes)
    out.write("\r\n".toByteArray(Charsets.UTF_8))
  }
}
