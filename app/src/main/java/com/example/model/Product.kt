package com.example.model

import androidx.annotation.DrawableRes
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object ProductCategories {
  const val ALL = "All"
  const val BLANKETS = "Blankets"
  const val MENS_SUITS = "Men's Suits"

  fun normalize(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return BLANKETS
    return when (trimmed.uppercase(Locale.ROOT)) {
      "ALL" -> ALL
      "BLANKETS", "BLANKET" -> BLANKETS
      "MENS_SUITS", "MEN'S SUITS", "MENS SUITS", "MEN SUITS" -> MENS_SUITS
      else -> trimmed
    }
  }
}

enum class SortOption(val label: String) {
  FEATURED("Featured"),
  PRICE_LOW_HIGH("Price: Low to High"),
  PRICE_HIGH_LOW("Price: High to Low"),
}

data class ProductSpec(
  val label: String,
  val value: String,
)

data class ProductPhotoOption(
  val key: String,
  val label: String,
  @DrawableRes val imageRes: Int,
)

fun isValidRemoteImageUrl(url: String?): Boolean {
  val trimmed = url?.trim().orEmpty()
  return trimmed.startsWith("https://", ignoreCase = true) ||
    trimmed.startsWith("http://", ignoreCase = true)
}

/**
 * Gallery parsing priority:
 * 1. Valid remote URLs in `imageUris` (first entry is cover photo)
 * 2. Fallback to single remote `imageUri`
 * 3. Empty list -> UI displays local placeholder drawable
 *
 * Phone-local `content://` or `file://` URIs are strictly excluded.
 */
fun resolveGalleryImageUrls(
  imageUris: List<String>?,
  imageUri: String?,
): List<String> {
  val fromList =
    imageUris
      .orEmpty()
      .map { it.trim() }
      .filter { isValidRemoteImageUrl(it) }
      .distinct()
  if (fromList.isNotEmpty()) {
    return fromList
  }
  val single = imageUri?.trim().orEmpty()
  if (isValidRemoteImageUrl(single)) {
    return listOf(single)
  }
  return emptyList()
}

/**
 * Strips any phone-local URI (`content://`, `file://`, etc.) so only valid http/https URLs
 * can ever be persisted to Firestore.
 */
fun sanitizeProductForCloudSave(product: Product): Product {
  val cleanUris = resolveGalleryImageUrls(product.imageUris, product.imageUri)
  return product.copy(
    category = ProductCategories.normalize(product.category),
    imageUris = cleanUris,
    imageUri = cleanUris.firstOrNull(),
  )
}

data class Product(
  val id: String,
  val name: String,
  val pricePkr: Int,
  val category: String,
  val fabric: String,
  val size: String,
  val shortSpec: String,
  val specs: List<ProductSpec>,
  val description: String,
  val imageKey: String,
  @DrawableRes val imageRes: Int,
  val imageUri: String? = null,
  val imageUris: List<String> = emptyList(),
  val inStock: Boolean = true,
  val stockQuantity: Int = 20,
) {
  val isAvailable: Boolean
    get() = inStock && stockQuantity > 0

  val galleryImageUrls: List<String>
    get() = resolveGalleryImageUrls(imageUris, imageUri)

  val coverImageUrl: String?
    get() = galleryImageUrls.firstOrNull()
}

data class CartItem(
  val product: Product,
  val quantity: Int,
) {
  val lineTotalPkr: Int
    get() = product.pricePkr * quantity
}

data class CheckoutFormState(
  val customerName: String = "",
  val phoneNumber: String = "",
  val deliveryAddress: String = "",
  val city: String = "",
  val nameError: String? = null,
  val phoneError: String? = null,
  val addressError: String? = null,
  val cityError: String? = null,
)

enum class OrderStatus(val wireValue: String, val label: String) {
  PLACED("placed", "Placed"),
  CONFIRMED("confirmed", "Confirmed"),
  SHIPPED("shipped", "Shipped"),
  DELIVERED("delivered", "Delivered");

  companion object {
    fun fromWireValue(value: String?): OrderStatus {
      return entries.firstOrNull {
        it.wireValue.equals(value?.trim(), ignoreCase = true) ||
          it.name.equals(value?.trim(), ignoreCase = true)
      } ?: PLACED
    }
  }
}

data class ConfirmedOrder(
  val orderNumber: String,
  val customerName: String,
  val phoneNumber: String,
  val deliveryAddress: String,
  val city: String,
  val items: List<CartItem>,
  val totalPkr: Int,
  val timestampIso: String = currentIsoTimestamp(),
  val timestampMillis: Long = System.currentTimeMillis(),
  val status: OrderStatus = OrderStatus.PLACED,
  val documentId: String = orderNumber,
  /**
   * Firebase uid of the signed-in customer who placed the order.
   * Null for guest orders — guest orders are never linked to an account.
   */
  val userId: String? = null,
)

fun currentIsoTimestamp(): String = Instant.now().toString()

fun parseIsoOrEpochMillis(rawString: String?): Long {
  val trimmed = rawString?.trim().orEmpty()
  if (trimmed.isEmpty()) return 0L

  trimmed.toLongOrNull()?.let { numeric ->
    return if (numeric in 1..9_999_999_999L) numeric * 1000L else numeric
  }

  runCatching { return Instant.parse(trimmed).toEpochMilli() }
  runCatching { return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }

  val fallbackPatterns =
    listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      "yyyy-MM-dd'T'HH:mm:ss'Z'",
      "yyyy-MM-dd HH:mm:ss",
      "yyyy-MM-dd",
    )
  for (pattern in fallbackPatterns) {
    runCatching {
      val sdf = SimpleDateFormat(pattern, Locale.US)
      val date = sdf.parse(trimmed)
      if (date != null) return date.time
    }
  }
  return 0L
}

fun formatOrderTimeForDisplay(timestampIso: String?, timestampMillis: Long): String {
  if (timestampMillis > 0L) {
    return runCatching {
      val formatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault())
          .withZone(ZoneId.systemDefault())
      formatter.format(Instant.ofEpochMilli(timestampMillis))
    }.getOrElse {
      SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestampMillis))
    }
  }
  val cleaned = timestampIso?.trim().orEmpty()
  return cleaned.ifEmpty { "Time unavailable" }
}

fun formatPkr(amount: Int): String {
  val formatter = NumberFormat.getIntegerInstance(Locale.US)
  return "Rs ${formatter.format(amount)}"
}
