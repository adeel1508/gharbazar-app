package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.CartItem
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategories
import com.example.model.ProductSpec
import com.example.model.parseIsoOrEpochMillis
import com.example.model.resolveGalleryImageUrls
import com.example.model.sanitizeProductForCloudSave
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

interface CloudOrderDataSource {
  val isConfigured: Boolean

  suspend fun fetchOrders(): Result<List<ConfirmedOrder>>

  /** Only the orders whose `userId` equals [userId], newest first. */
  suspend fun fetchMyOrders(userId: String): Result<List<ConfirmedOrder>>

  suspend fun saveOrder(order: ConfirmedOrder): Result<Unit>

  suspend fun updateOrderStatus(documentId: String, status: OrderStatus): Result<Unit>
}

/**
 * Firebase Firestore data source for GharBazar's `orders` collection.
 *
 * Crash-safety rules:
 * - Never attaches a listener at app startup or on auth-state changes.
 * - `fetchOrders()` runs ONLY on explicit admin action (opening Orders tab or tapping Refresh).
 * - Uses the single shared [FirebaseClient] Firestore instance.
 * - Defensive parsing handles both String `timestamp` and Firestore `Timestamp` `createdAt`.
 */
class FirestoreOrderDataSource(
  context: Context,
) : CloudOrderDataSource {

  private val appContext = context.applicationContext

  private val firestore
    get() = FirebaseClient.getFirestore(appContext)

  override val isConfigured: Boolean
    get() = firestore != null

  override suspend fun fetchOrders(): Result<List<ConfirmedOrder>> {
    val db =
      firestore
        ?: return Result.failure(IllegalStateException("Firebase Firestore is not configured."))

    return runCatching {
      val snapshot = db.collection(ORDERS_COLLECTION).get().await()
      snapshot.documents
        .mapNotNull { it.toConfirmedOrderSafely() }
        .sortedByDescending { it.timestampMillis }
    }.onFailure { err ->
      Log.w(TAG, "fetchOrders failed: ${err.message}")
    }
  }

  override suspend fun fetchMyOrders(userId: String): Result<List<ConfirmedOrder>> {
    val db =
      firestore
        ?: return Result.failure(IllegalStateException("Firebase Firestore is not configured."))
    val cleanUid = userId.trim()
    if (cleanUid.isEmpty()) {
      return Result.failure(IllegalArgumentException("Missing customer id."))
    }

    return runCatching {
      // Single-field equality only — no composite index needed; sort in memory.
      val snapshot =
        db.collection(ORDERS_COLLECTION).whereEqualTo("userId", cleanUid).get().await()
      snapshot.documents
        .mapNotNull { it.toConfirmedOrderSafely() }
        .sortedByDescending { it.timestampMillis }
    }.onFailure { err ->
      Log.w(TAG, "fetchMyOrders failed: ${err.message}")
    }
  }

  override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
    val db =
      firestore
        ?: return Result.failure(IllegalStateException("Firebase Firestore is not configured."))
    return runCatching {
      val payload = order.toFirestoreOrderMap(createdAtValue = Timestamp.now())
      db.collection(ORDERS_COLLECTION)
        .document(order.orderNumber)
        .set(payload)
        .await()
    }.map { }
  }

  override suspend fun updateOrderStatus(
    documentId: String,
    status: OrderStatus,
  ): Result<Unit> {
    val db =
      firestore
        ?: return Result.failure(IllegalStateException("Firebase Firestore is not configured."))
    val targetId = documentId.trim()
    if (targetId.isEmpty()) {
      return Result.failure(IllegalArgumentException("Missing order document id."))
    }
    return runCatching {
      db.collection(ORDERS_COLLECTION)
        .document(targetId)
        .update("status", status.wireValue)
        .await()
    }.map { }
  }

  private fun DocumentSnapshot.toConfirmedOrderSafely(): ConfirmedOrder? {
    return runCatching {
      val orderNumber = safeString("orderNumber")?.takeIf { it.isNotBlank() } ?: id
      val customerName = safeString("customerName")?.takeIf { it.isNotBlank() } ?: "Customer"
      val phoneNumber = safeString("phoneNumber").orEmpty()
      val deliveryAddress = safeString("deliveryAddress").orEmpty()
      val city = safeString("city").orEmpty()
      val totalPkr = safeInt("totalPkr") ?: 0
      val status = OrderStatus.fromWireValue(safeString("status"))
      val userId = safeString("userId")?.takeIf { it.isNotBlank() }

      val rawTimestamp = get("timestamp")
      val rawCreatedAt = get("createdAt")
      val timestampIso =
        when (rawTimestamp) {
          is String -> rawTimestamp.trim()
          is Timestamp -> rawTimestamp.toDate().toInstant().toString()
          else -> (rawCreatedAt as? Timestamp)?.toDate()?.toInstant()?.toString().orEmpty()
        }
      val timestampMillis =
        resolveOrderTimestampMillis(
          rawTimestamp = rawTimestamp,
          rawCreatedAt = rawCreatedAt,
        )

      val rawItems = get("items") as? List<*>
      val items =
        rawItems?.mapNotNull { entry ->
          runCatching {
            val itemMap = entry as? Map<*, *> ?: return@runCatching null
            val productMap = itemMap["product"] as? Map<*, *> ?: return@runCatching null
            val quantity =
              when (val rawQty = itemMap["quantity"]) {
                is Number -> rawQty.toInt().coerceAtLeast(1)
                is String -> rawQty.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
                else -> 1
              }
            val product = parseProductFromMapSafely(productMap) ?: return@runCatching null
            CartItem(product = product, quantity = quantity)
          }.getOrNull()
        }.orEmpty()

      ConfirmedOrder(
        orderNumber = orderNumber,
        customerName = customerName,
        phoneNumber = phoneNumber,
        deliveryAddress = deliveryAddress,
        city = city,
        items = items,
        totalPkr = totalPkr,
        timestampIso = timestampIso,
        timestampMillis = timestampMillis,
        status = status,
        documentId = id,
        userId = userId,
      )
    }.onFailure { err ->
      Log.w(TAG, "Skipping malformed order document $id: ${err.message}")
    }.getOrNull()
  }

  private fun DocumentSnapshot.safeString(field: String): String? {
    val raw = get(field) ?: return null
    return when (raw) {
      is String -> raw.trim()
      is Number -> raw.toString()
      else -> null
    }
  }

  private fun DocumentSnapshot.safeInt(field: String): Int? {
    return when (val raw = get(field)) {
      is Number -> raw.toInt()
      is String -> raw.trim().toIntOrNull()
      else -> null
    }
  }

  private fun parseProductFromMapSafely(map: Map<*, *>): Product? {
    return runCatching {
      val name = map["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
      val pricePkr =
        when (val rawPrice = map["pricePkr"]) {
          is Number -> rawPrice.toInt()
          is String -> rawPrice.trim().toIntOrNull()
          else -> null
        } ?: 0
      val id = map["id"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: name
      val category = ProductCategories.normalize(map["category"]?.toString())
      val fabric = map["fabric"]?.toString()?.trim().orEmpty().ifBlank { "Standard" }
      val size = map["size"]?.toString()?.trim().orEmpty().ifBlank { "Standard" }
      val description = map["description"]?.toString()?.trim().orEmpty()
      val imageKey = map["imageKey"]?.toString()?.trim().orEmpty()
      val rawImageUris =
        (map["imageUris"] as? List<*>)?.mapNotNull { (it as? String)?.trim() }
      val rawImageUri = map["imageUri"] as? String
      val galleryUrls = resolveGalleryImageUrls(rawImageUris, rawImageUri)
      val inStock = (map["inStock"] as? Boolean) ?: true
      val stockQuantity =
        when (val rawQty = map["stockQuantity"]) {
          is Number -> rawQty.toInt()
          is String -> rawQty.trim().toIntOrNull() ?: 10
          else -> if (inStock) 10 else 0
        }
      val shortSpec =
        map["shortSpec"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
          ?: listOf(size, fabric).filter { it.isNotBlank() }.joinToString(" • ")

      val rawSpecs = map["specs"] as? List<*>
      val parsedSpecs =
        rawSpecs
          ?.mapNotNull { entry ->
            val specMap = entry as? Map<*, *> ?: return@mapNotNull null
            val label = specMap["label"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val value = specMap["value"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ProductSpec(label, value)
          }
          ?.takeIf { it.isNotEmpty() }
          ?: listOf(
            ProductSpec("Category", category),
            ProductSpec("Size / Cutting", size),
            ProductSpec("Fabric", fabric),
          )

      Product(
        id = id,
        name = name,
        pricePkr = pricePkr,
        category = category,
        fabric = fabric,
        size = size,
        shortSpec = shortSpec,
        specs = parsedSpecs,
        description = description,
        imageKey = imageKey,
        imageRes = ProductCatalog.resolveDrawableForKey(imageKey, category),
        imageUri = galleryUrls.firstOrNull(),
        imageUris = galleryUrls,
        inStock = inStock,
        stockQuantity = stockQuantity,
      )
    }.getOrNull()
  }

  companion object {
    const val ORDERS_COLLECTION = "orders"
    private const val TAG = "FirestoreOrderSource"

    fun resolveOrderTimestampMillis(
      rawTimestamp: Any?,
      rawCreatedAt: Any?,
    ): Long {
      val primary =
        when (rawTimestamp) {
          is String -> parseIsoOrEpochMillis(rawTimestamp)
          is Timestamp -> rawTimestamp.toDate().time
          is Number -> {
            val v = rawTimestamp.toLong()
            if (v in 1..9_999_999_999L) v * 1000L else v
          }
          else -> 0L
        }
      if (primary > 0L) return primary

      return when (rawCreatedAt) {
        is Timestamp -> rawCreatedAt.toDate().time
        is Number -> {
          val v = rawCreatedAt.toLong()
          if (v in 1..9_999_999_999L) v * 1000L else v
        }
        is String -> parseIsoOrEpochMillis(rawCreatedAt)
        else -> 0L
      }
    }

    /**
     * Builds the exact Firestore document payload for COD orders.
     * Guest orders contain NO `userId` field; orders placed by a signed-in
     * customer include their `userId` so the record follows their account.
     */
    fun ConfirmedOrder.toFirestoreOrderMap(
      createdAtValue: Any? = null,
    ): Map<String, Any> {
      val map =
        mutableMapOf<String, Any>(
          "orderNumber" to orderNumber,
          "customerName" to customerName,
          "phoneNumber" to phoneNumber,
          "deliveryAddress" to deliveryAddress,
          "city" to city,
          "items" to
            items.map { cartItem ->
              mapOf(
                "product" to sanitizeProductForCloudSave(cartItem.product).toFirestoreProductMap(),
                "quantity" to cartItem.quantity,
              )
            },
          "totalPkr" to totalPkr,
          "timestamp" to timestampIso,
          "status" to status.wireValue,
        )
      order.userId?.trim()?.takeIf { it.isNotEmpty() }?.let { uid ->
        map["userId"] = uid
      }
      if (createdAtValue != null) {
        map["createdAt"] = createdAtValue
      }
      return map
    }

    private fun Product.toFirestoreProductMap(): Map<String, Any> {
      val map =
        mutableMapOf<String, Any>(
          "id" to id,
          "name" to name,
          "pricePkr" to pricePkr,
          "category" to category,
          "fabric" to fabric,
          "size" to size,
          "shortSpec" to shortSpec,
          "specs" to specs.map { mapOf("label" to it.label, "value" to it.value) },
          "description" to description,
          "imageKey" to imageKey,
          "imageUris" to galleryImageUrls,
          "inStock" to inStock,
          "stockQuantity" to stockQuantity,
        )
      coverImageUrl?.let { cover ->
        map["imageUri"] = cover
      }
      return map
    }
  }
}
