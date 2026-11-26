package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Product
import com.example.model.ProductCategories
import com.example.model.ProductSpec
import com.example.model.resolveGalleryImageUrls
import com.example.model.sanitizeProductForCloudSave
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

sealed interface CloudProductsSnapshot {
  data class Loaded(val products: List<Product>, val isFromCache: Boolean) : CloudProductsSnapshot
  data object Empty : CloudProductsSnapshot
  data class Unavailable(val reason: String) : CloudProductsSnapshot
}

interface CloudProductDataSource {
  val isConfigured: Boolean
  fun observeProducts(): Flow<CloudProductsSnapshot>
  suspend fun fetchProductsOnce(): CloudProductsSnapshot
  suspend fun upsertProduct(product: Product): Result<Unit>
  suspend fun deleteProduct(productId: String): Result<Unit>
}

/**
 * Firebase Firestore data source for GharBazar's `products` collection.
 * Uses the single shared [FirebaseClient] instance and parses every document defensively.
 * Never seeds, modifies, or deletes existing Firestore documents automatically.
 */
class FirestoreProductDataSource(
  context: Context,
) : CloudProductDataSource {

  private val appContext = context.applicationContext

  private val firestore
    get() = FirebaseClient.getFirestore(appContext)

  override val isConfigured: Boolean
    get() = firestore != null

  override fun observeProducts(): Flow<CloudProductsSnapshot> {
    val db =
      firestore
        ?: return flowOf(
          CloudProductsSnapshot.Unavailable(
            "Unable to initialize Firebase Firestore."
          )
        )

    return callbackFlow {
      val registration =
        db.collection(PRODUCTS_COLLECTION)
          .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
              Log.w(TAG, "Firestore products snapshot error: ${error.message}")
              trySend(
                CloudProductsSnapshot.Unavailable(
                  error.localizedMessage ?: "Unable to reach Cloud Firestore"
                )
              )
              return@addSnapshotListener
            }

            if (snapshot == null || snapshot.isEmpty) {
              trySend(CloudProductsSnapshot.Empty)
              return@addSnapshotListener
            }

            val parsedProducts = snapshot.documents.mapNotNull { it.toProductSafely() }
            if (parsedProducts.isEmpty()) {
              trySend(CloudProductsSnapshot.Empty)
            } else {
              trySend(
                CloudProductsSnapshot.Loaded(
                  products = parsedProducts,
                  isFromCache = snapshot.metadata.isFromCache,
                )
              )
            }
          }

      awaitClose { registration.remove() }
    }
  }

  override suspend fun fetchProductsOnce(): CloudProductsSnapshot {
    val db =
      firestore
        ?: return CloudProductsSnapshot.Unavailable("Unable to initialize Firebase Firestore.")
    return runCatching {
      val snapshot = db.collection(PRODUCTS_COLLECTION).get().await()
      if (snapshot == null || snapshot.isEmpty) {
        CloudProductsSnapshot.Empty
      } else {
        val parsed = snapshot.documents.mapNotNull { it.toProductSafely() }
        if (parsed.isEmpty()) {
          CloudProductsSnapshot.Empty
        } else {
          CloudProductsSnapshot.Loaded(
            products = parsed,
            isFromCache = snapshot.metadata.isFromCache,
          )
        }
      }
    }.getOrElse { err ->
      Log.w(TAG, "fetchProductsOnce failed: ${err.message}")
      CloudProductsSnapshot.Unavailable(err.localizedMessage ?: "Failed to load products.")
    }
  }

  override suspend fun upsertProduct(product: Product): Result<Unit> {
    val db =
      firestore
        ?: return Result.failure(
          IllegalStateException("Firebase Firestore is not configured.")
        )
    val sanitized = sanitizeProductForCloudSave(product)
    return runCatching {
      db.collection(PRODUCTS_COLLECTION)
        .document(sanitized.id)
        .set(sanitized.toFirestoreMap())
        .await()
    }.map { }
  }

  override suspend fun deleteProduct(productId: String): Result<Unit> {
    val db =
      firestore
        ?: return Result.failure(
          IllegalStateException("Firebase Firestore is not configured.")
        )
    return runCatching {
      db.collection(PRODUCTS_COLLECTION)
        .document(productId)
        .delete()
        .await()
    }.map { }
  }

  private fun DocumentSnapshot.toProductSafely(): Product? {
    return runCatching {
      val name = safeString("name")?.takeIf { it.isNotBlank() } ?: return null
      val pricePkr = safeInt("pricePkr") ?: return null
      val productId = safeString("id")?.takeIf { it.isNotBlank() } ?: id
      val category = ProductCategories.normalize(safeString("category"))
      val fabric = safeString("fabric").orEmpty().ifBlank { "Standard" }
      val size = safeString("size").orEmpty().ifBlank { "Standard" }
      val description = safeString("description").orEmpty()
      val imageKey = safeString("imageKey").orEmpty()

      val rawImageUris =
        (get("imageUris") as? List<*>)
          ?.mapNotNull { (it as? String)?.trim() }
      val rawImageUri = safeString("imageUri")
      val galleryUrls = resolveGalleryImageUrls(rawImageUris, rawImageUri)

      val inStock = (get("inStock") as? Boolean) ?: true
      val stockQuantity = safeInt("stockQuantity") ?: if (inStock) 10 else 0

      val rawSpecs = get("specs") as? List<*>
      val parsedSpecs =
        rawSpecs
          ?.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val label = map["label"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val value = map["value"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ProductSpec(label, value)
          }
          ?.takeIf { it.isNotEmpty() }
          ?: buildDefaultSpecs(category, fabric, size)

      val shortSpec =
        safeString("shortSpec")?.takeIf { it.isNotBlank() }
          ?: listOf(size, fabric).filter { it.isNotBlank() }.joinToString(" • ")

      Product(
        id = productId,
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
    }.onFailure { err ->
      Log.w(TAG, "Skipping malformed product document $id: ${err.message}")
    }.getOrNull()
  }

  private fun DocumentSnapshot.safeString(field: String): String? {
    val raw = get(field) ?: return null
    return (raw as? String)?.trim()
  }

  private fun DocumentSnapshot.safeInt(field: String): Int? {
    return when (val raw = get(field)) {
      is Number -> raw.toInt()
      is String -> raw.trim().toIntOrNull()
      else -> null
    }
  }

  private fun buildDefaultSpecs(
    category: String,
    fabric: String,
    size: String,
  ): List<ProductSpec> {
    return listOf(
      ProductSpec("Category", category),
      ProductSpec("Size / Cutting", size),
      ProductSpec("Fabric", fabric),
    )
  }

  companion object {
    const val PRODUCTS_COLLECTION = "products"
    private const val TAG = "FirestoreProductSource"

    fun Product.toFirestoreMap(): Map<String, Any> {
      val sanitized = sanitizeProductForCloudSave(this)
      val map =
        mutableMapOf<String, Any>(
          "id" to sanitized.id,
          "name" to sanitized.name,
          "pricePkr" to sanitized.pricePkr,
          "category" to sanitized.category,
          "fabric" to sanitized.fabric,
          "size" to sanitized.size,
          "shortSpec" to sanitized.shortSpec,
          "description" to sanitized.description,
          "imageKey" to sanitized.imageKey,
          "imageUris" to sanitized.galleryImageUrls,
          "inStock" to sanitized.inStock,
          "stockQuantity" to sanitized.stockQuantity,
          "specs" to sanitized.specs.map { mapOf("label" to it.label, "value" to it.value) },
        )
      sanitized.coverImageUrl?.let { cover ->
        map["imageUri"] = cover
      }
      return map
    }
  }
}
