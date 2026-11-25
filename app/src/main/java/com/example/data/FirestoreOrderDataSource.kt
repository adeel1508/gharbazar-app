package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.CartItem
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

sealed interface CloudOrdersSnapshot {
  data class Loaded(val orders: List<ConfirmedOrder>, val isFromCache: Boolean) :
    CloudOrdersSnapshot

  data object Empty : CloudOrdersSnapshot

  data class Unavailable(val reason: String) : CloudOrdersSnapshot
}

interface CloudOrderDataSource {
  val isConfigured: Boolean

  fun observeOrders(): Flow<CloudOrdersSnapshot>

  suspend fun saveOrder(order: ConfirmedOrder): Result<Unit>

  suspend fun updateOrderStatus(orderNumber: String, status: OrderStatus): Result<Unit>
}

/**
 * Firebase Firestore data source for GharBazar's `orders` collection (guest COD orders, no login).
 */
class FirestoreOrderDataSource(
  context: Context,
) : CloudOrderDataSource {

  private val appContext = context.applicationContext

  private val firestore: FirebaseFirestore? by lazy {
    runCatching {
      val apps = FirebaseApp.getApps(appContext)
      val app =
        if (apps.isNotEmpty()) {
          apps.first()
        } else {
          FirebaseApp.initializeApp(appContext)
        }
      if (app == null) {
        null
      } else {
        FirebaseFirestore.getInstance(app).apply {
          firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { })
          }
        }
      }
    }.onFailure { error ->
      Log.w(TAG, "Firebase Firestore unavailable for orders: ${error.message}")
    }.getOrNull()
  }

  override val isConfigured: Boolean
    get() = firestore != null

  override fun observeOrders(): Flow<CloudOrdersSnapshot> {
    val db =
      firestore
        ?: return flowOf(
          CloudOrdersSnapshot.Unavailable(
            "Firebase google-services.json is not configured."
          )
        )

    return callbackFlow {
      val registration =
        db.collection(ORDERS_COLLECTION)
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
              Log.w(TAG, "Firestore orders snapshot error: ${error.message}")
              trySend(
                CloudOrdersSnapshot.Unavailable(
                  error.localizedMessage ?: "Unable to load orders from Cloud Firestore"
                )
              )
              return@addSnapshotListener
            }

            if (snapshot == null || snapshot.isEmpty) {
              trySend(CloudOrdersSnapshot.Empty)
              return@addSnapshotListener
            }

            val parsedOrders =
              snapshot.documents
                .mapNotNull { it.toConfirmedOrderOrNull() }
                .sortedByDescending { it.timestamp }
            if (parsedOrders.isEmpty()) {
              trySend(CloudOrdersSnapshot.Empty)
            } else {
              trySend(
                CloudOrdersSnapshot.Loaded(
                  orders = parsedOrders,
                  isFromCache = snapshot.metadata.isFromCache,
                )
              )
            }
          }

      awaitClose { registration.remove() }
    }
  }

  override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
    val db =
      firestore
        ?: return Result.failure(
          IllegalStateException("Firebase google-services.json not configured")
        )
    return runCatching {
      db.collection(ORDERS_COLLECTION)
        .document(order.orderNumber)
        .set(order.toFirestoreOrderMap())
        .await()
    }.map { }
  }

  override suspend fun updateOrderStatus(
    orderNumber: String,
    status: OrderStatus,
  ): Result<Unit> {
    val db =
      firestore
        ?: return Result.failure(
          IllegalStateException("Firebase google-services.json not configured")
        )
    return runCatching {
      db.collection(ORDERS_COLLECTION)
        .document(orderNumber)
        .update("status", status.wireValue)
        .await()
    }.map { }
  }

  private fun DocumentSnapshot.toConfirmedOrderOrNull(): ConfirmedOrder? {
    val orderNumber = getString("orderNumber")?.takeIf { it.isNotBlank() } ?: id
    val customerName = getString("customerName")?.takeIf { it.isNotBlank() } ?: return null
    val phoneNumber = getString("phoneNumber").orEmpty()
    val deliveryAddress = getString("deliveryAddress").orEmpty()
    val city = getString("city").orEmpty()
    val totalPkr = (get("totalPkr") as? Number)?.toInt() ?: 0
    val timestamp =
      (get("timestamp") as? Number)?.toLong()
        ?: getTimestamp("timestamp")?.toDate()?.time
        ?: 0L
    val status = OrderStatus.fromWireValue(getString("status"))

    val rawItems = get("items") as? List<*>
    val items =
      rawItems?.mapNotNull { entry ->
        val itemMap = entry as? Map<*, *> ?: return@mapNotNull null
        val productMap = itemMap["product"] as? Map<*, *> ?: return@mapNotNull null
        val quantity = (itemMap["quantity"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
        val product = parseProductFromMap(productMap) ?: return@mapNotNull null
        CartItem(product = product, quantity = quantity)
      }.orEmpty()

    return ConfirmedOrder(
      orderNumber = orderNumber,
      customerName = customerName,
      phoneNumber = phoneNumber,
      deliveryAddress = deliveryAddress,
      city = city,
      items = items,
      totalPkr = totalPkr,
      timestamp = timestamp,
      status = status,
    )
  }

  /**
   * Parses an order item's embedded product snapshot with the same rules as
   * the catalogue ([productFromFirestoreFields]), so a gallery saved with
   * the product survives inside orders too.
   */
  @Suppress("UNCHECKED_CAST")
  private fun parseProductFromMap(map: Map<*, *>): Product? {
    val id =
      (map["id"] as? String)?.takeIf { it.isNotBlank() }
        ?: (map["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    return productFromFirestoreFields(id, map as Map<String, Any?>)
  }

  companion object {
    const val ORDERS_COLLECTION = "orders"
    private const val TAG = "FirestoreOrderSource"

    /**
     * Builds the exact Firestore document payload for guest COD orders.
     * Contains NO userId field.
     */
    fun ConfirmedOrder.toFirestoreOrderMap(): Map<String, Any> {
      return mapOf(
        "orderNumber" to orderNumber,
        "customerName" to customerName,
        "phoneNumber" to phoneNumber,
        "deliveryAddress" to deliveryAddress,
        "city" to city,
        "items" to
          items.map { cartItem ->
            mapOf(
              "product" to cartItem.product.toFirestoreProductMap(),
              "quantity" to cartItem.quantity,
            )
          },
        "totalPkr" to totalPkr,
        "timestamp" to timestamp,
        "status" to status.wireValue,
      )
    }

    private fun Product.toFirestoreProductMap(): Map<String, Any> {
      val map =
        mutableMapOf<String, Any>(
          "id" to id,
          "name" to name,
          "pricePkr" to pricePkr,
          "category" to ProductCategory.wireValue(category),
          "fabric" to fabric,
          "size" to size,
          "shortSpec" to shortSpec,
          "specs" to specs.map { mapOf("label" to it.label, "value" to it.value) },
          "description" to description,
          "imageKey" to imageKey,
          "imageRes" to imageRes,
          "inStock" to inStock,
          "stockQuantity" to stockQuantity,
        )
      if (!imageUri.isNullOrBlank()) {
        map["imageUri"] = imageUri
      }
      if (imageUris.isNotEmpty()) {
        map["imageUris"] = imageUris
      }
      return map
    }
  }
}
