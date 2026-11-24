package com.example.model

import androidx.annotation.DrawableRes
import java.text.NumberFormat
import java.util.Locale

enum class ProductCategory(val label: String) {
  ALL("All"),
  BLANKETS("Blankets"),
  MENS_SUITS("Men's Suits");

  companion object {
    /**
     * Canonical display label for a stored category value. Accepts the old
     * enum names ("BLANKETS", "MENS_SUITS") or the labels ("Blankets",
     * "Men's Suits") in any case, and passes any other (custom) category
     * through unchanged. Blank or unknown values fall back to Blankets,
     * matching the previous behaviour for unreadable documents.
     */
    fun fromStored(value: String?): String {
      val trimmed = value?.trim().orEmpty()
      if (trimmed.isEmpty()) return BLANKETS.label
      entries.firstOrNull {
        it != ALL &&
          (it.name.equals(trimmed, ignoreCase = true) ||
            it.label.equals(trimmed, ignoreCase = true))
      }?.let { return it.label }
      return trimmed
    }

    /**
     * Firestore wire value for a category label. The two built-in categories
     * keep their enum names ("BLANKETS" / "MENS_SUITS") because the website
     * home filters match on those exact values; a custom category is stored
     * as its label, so the website shows it under "All" until its filters
     * are derived from the products too.
     */
    fun wireValue(label: String): String {
      val trimmed = label.trim()
      entries.firstOrNull {
        it != ALL && it.label.equals(trimmed, ignoreCase = true)
      }?.let { return it.name }
      return trimmed
    }

    fun isKnownLabel(label: String): Boolean =
      entries.any { it != ALL && it.label.equals(label.trim(), ignoreCase = true) }

    /** True when the category should use a men's-suit fallback image. */
    fun isSuitCategory(label: String): Boolean =
      MENS_SUITS.label.equals(label.trim(), ignoreCase = true)
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

data class Product(
  val id: String,
  val name: String,
  val pricePkr: Int,
  /** Display label of the category, e.g. "Blankets", "Men's Suits" or a custom one. */
  val category: String,
  val fabric: String,
  val size: String,
  val shortSpec: String,
  val specs: List<ProductSpec>,
  val description: String,
  val imageKey: String,
  @DrawableRes val imageRes: Int,
  val imageUri: String? = null,
  val inStock: Boolean = true,
  val stockQuantity: Int = 20,
) {
  val isAvailable: Boolean
    get() = inStock && stockQuantity > 0
}

/**
 * Home-screen filter chips derived from the loaded products, so a category
 * the owner creates in the admin panel appears as a filter automatically.
 * "All" comes first, then the two built-in categories in fixed order, then
 * any custom categories alphabetically. Each entry is (label, product count).
 */
fun categoryFiltersFor(products: List<Product>): List<Pair<String, Int>> {
  val counts = products.groupingBy { it.category }.eachCount()
  val ordered = mutableListOf<Pair<String, Int>>()
  ordered += ProductCategory.ALL.label to products.size
  listOf(ProductCategory.BLANKETS, ProductCategory.MENS_SUITS).forEach { known ->
    counts[known.label]?.let { ordered += known.label to it }
  }
  counts.keys
    .filter { !ProductCategory.isKnownLabel(it) && !it.equals(ProductCategory.ALL.label, ignoreCase = true) }
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .forEach { ordered += it to (counts[it] ?: 0) }
  return ordered
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
        it.wireValue.equals(value, ignoreCase = true) ||
          it.name.equals(value, ignoreCase = true)
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
  val timestamp: Long = System.currentTimeMillis(),
  val status: OrderStatus = OrderStatus.PLACED,
)

fun formatPkr(amount: Int): String {
  val formatter = NumberFormat.getIntegerInstance(Locale.US)
  return "Rs ${formatter.format(amount)}"
}
