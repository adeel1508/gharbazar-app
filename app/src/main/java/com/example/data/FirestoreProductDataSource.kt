package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductSpec
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
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
  suspend fun upsertProduct(product: Product): Result<Unit>
  suspend fun deleteProduct(productId: String): Result<Unit>
}

/**
 * Firebase Firestore data source for GharBazar's `products` collection (Free Spark Plan compatible).
 *
 * SETUP REQUIRED FOR MULTI-DEVICE CLOUD SYNC:
 * 1. Create a free Firebase project on the Spark plan at https://console.firebase.google.com
 * 2. Register an Android app with package name `com.aistudio.gharbazar.kmpqrz`
 * 3. Download `google-services.json` and place it at `app/google-services.json`
 * 4. Enable Cloud Firestore in the Firebase Console (no billing / Spark plan is sufficient)
 *
 * Until `app/google-services.json` is added, `FirebaseApp.getApps(context)` is empty, so
 * this data source safely reports `Unavailable` and the app falls back to the built-in
 * 8-product local catalogue without crashing.
 */
class FirestoreProductDataSource(
  context: Context,
) : CloudProductDataSource {

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
      Log.w(TAG, "Firebase Firestore unavailable (likely missing google-services.json): ${error.message}")
    }.getOrNull()
  }

  override val isConfigured: Boolean
    get() = firestore != null

  override fun observeProducts(): Flow<CloudProductsSnapshot> {
    val db = firestore
      ?: return flowOf(
        CloudProductsSnapshot.Unavailable(
          "Firebase google-services.json is not configured yet. Using local catalogue fallback."
        )
      )

    return callbackFlow {
      val registration =
        db.collection(PRODUCTS_COLLECTION)
          .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
              Log.w(TAG, "Firestore snapshot error: ${error.message}")
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

            val parsedProducts = snapshot.documents.mapNotNull { it.toProductOrNull() }
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

  override suspend fun upsertProduct(product: Product): Result<Unit> {
    val db = firestore
      ?: return Result.failure(
        IllegalStateException("Firebase google-services.json not configured")
      )
    return runCatching {
      db.collection(PRODUCTS_COLLECTION)
        .document(product.id)
        .set(product.toFirestoreMap())
        .await()
    }.map { }
  }

  override suspend fun deleteProduct(productId: String): Result<Unit> {
    val db = firestore
      ?: return Result.failure(
        IllegalStateException("Firebase google-services.json not configured")
      )
    return runCatching {
      db.collection(PRODUCTS_COLLECTION)
        .document(productId)
        .delete()
        .await()
    }.map { }
  }

  private fun DocumentSnapshot.toProductOrNull(): Product? {
    val fields =
      mapOf<String, Any?>(
        "name" to getString("name"),
        "pricePkr" to get("pricePkr"),
        "category" to getString("category"),
        "fabric" to getString("fabric"),
        "size" to getString("size"),
        "description" to getString("description"),
        "imageKey" to getString("imageKey"),
        "imageUri" to getString("imageUri"),
        "imageUris" to get("imageUris"),
        "inStock" to getBoolean("inStock"),
        "stockQuantity" to get("stockQuantity"),
        "specs" to get("specs"),
        "shortSpec" to getString("shortSpec"),
      )
    return productFromFirestoreFields(id, fields)
  }

  private fun Product.toFirestoreMap(): Map<String, Any> {
    val map =
      mutableMapOf<String, Any>(
        "name" to name,
        "pricePkr" to pricePkr,
        "category" to ProductCategory.wireValue(category),
        "fabric" to fabric,
        "size" to size,
        "shortSpec" to shortSpec,
        "description" to description,
        "imageKey" to imageKey,
        "inStock" to inStock,
        "stockQuantity" to stockQuantity,
        "specs" to specs.map { mapOf("label" to it.label, "value" to it.value) },
      )
    if (!imageUri.isNullOrBlank()) {
      map["imageUri"] = imageUri
    }
    if (imageUris.isNotEmpty()) {
      map["imageUris"] = imageUris
    }
    return map
  }

  companion object {
    const val PRODUCTS_COLLECTION = "products"
    private const val TAG = "FirestoreProductSource"
  }
}

/**
 * Pure mapping from a Firestore product document's fields to a [Product].
 * Kept separate from [DocumentSnapshot] so the gallery rules are unit
 * testable without Firebase: `imageUris` (array of URL strings) wins when
 * present; otherwise a lone `imageUri` becomes the single-photo gallery;
 * with neither, the gallery is empty and the built-in drawable is used.
 * The cover photo ([Product.imageUri]) is always the first gallery entry.
 */
internal fun productFromFirestoreFields(id: String, fields: Map<String, Any?>): Product? {
  val name = (fields["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
  val pricePkr = (fields["pricePkr"] as? Number)?.toInt() ?: return null
  val category = ProductCategory.fromStored(fields["category"] as? String)

  val fabric = (fields["fabric"] as? String).orEmpty().ifBlank { "Standard" }
  val size = (fields["size"] as? String).orEmpty().ifBlank { "Standard" }
  val description = (fields["description"] as? String).orEmpty()
  val imageKey = (fields["imageKey"] as? String).orEmpty()
  val coverUri = (fields["imageUri"] as? String)?.takeIf { it.isNotBlank() }
  val storedUris =
    (fields["imageUris"] as? List<*>)
      ?.filterIsInstance<String>()
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      .orEmpty()
  val imageUris =
    when {
      storedUris.isNotEmpty() ->
        if (coverUri != null && coverUri !in storedUris) listOf(coverUri) + storedUris
        else storedUris
      coverUri != null -> listOf(coverUri)
      else -> emptyList()
    }
  val imageUri = imageUris.firstOrNull()
  val inStock = (fields["inStock"] as? Boolean) ?: true
  val stockQuantity =
    (fields["stockQuantity"] as? Number)?.toInt() ?: if (inStock) 10 else 0

  val rawSpecs = fields["specs"] as? List<*>
  val parsedSpecs =
    rawSpecs
      ?.mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val label = map["label"] as? String ?: return@mapNotNull null
        val value = map["value"] as? String ?: return@mapNotNull null
        ProductSpec(label, value)
      }
      ?.takeIf { it.isNotEmpty() }
      ?: buildDefaultSpecs(category, fabric, size)

  val shortSpec =
    (fields["shortSpec"] as? String)?.takeIf { it.isNotBlank() }
      ?: listOf(size, fabric).filter { it.isNotBlank() }.joinToString(" • ")

  return Product(
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
    imageUri = imageUri,
    imageUris = imageUris,
    inStock = inStock,
    stockQuantity = stockQuantity,
  )
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
