package com.example.model

import androidx.annotation.DrawableRes
import java.text.NumberFormat
import java.util.Locale

enum class ProductCategory(val label: String) {
  ALL("All"),
  BLANKETS("Blankets"),
  MENS_SUITS("Men's Suits"),
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
  val category: ProductCategory,
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
