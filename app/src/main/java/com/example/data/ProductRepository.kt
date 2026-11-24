package com.example.data

import com.example.model.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogSyncStatus {
  data object SyncedLive : CatalogSyncStatus
  data object OfflineCached : CatalogSyncStatus
  data class LocalFallback(val message: String) : CatalogSyncStatus
}

class ProductRepository(
  private val cloudDataSource: CloudProductDataSource,
  private val fallbackCatalog: List<Product> = ProductCatalog.products,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val _localProducts = MutableStateFlow(fallbackCatalog)
  private val _products = MutableStateFlow(fallbackCatalog)
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  /** True when a cloud Firestore catalogue is configured (writes go to the cloud). */
  val isCloudConfigured: Boolean
    get() = cloudDataSource.isConfigured

  private val _syncStatus =
    MutableStateFlow<CatalogSyncStatus>(
      if (cloudDataSource.isConfigured) {
        CatalogSyncStatus.OfflineCached
      } else {
        CatalogSyncStatus.LocalFallback(
          "Cloud sync needs a free Firebase Spark project google-services.json in app/. Using local 8-product catalogue."
        )
      }
    )
  val syncStatus: StateFlow<CatalogSyncStatus> = _syncStatus.asStateFlow()

  private var cloudHasData = false

  init {
    observeCloudCatalog()
  }

  private fun observeCloudCatalog() {
    scope.launch {
      cloudDataSource.observeProducts().collect { snapshot ->
        when (snapshot) {
          is CloudProductsSnapshot.Loaded -> {
            cloudHasData = true
            _localProducts.value = snapshot.products
            _products.value = snapshot.products
            _syncStatus.value =
              if (snapshot.isFromCache) {
                CatalogSyncStatus.OfflineCached
              } else {
                CatalogSyncStatus.SyncedLive
              }
          }
          is CloudProductsSnapshot.Empty -> {
            cloudHasData = false
            _products.value = _localProducts.value
            _syncStatus.value =
              CatalogSyncStatus.LocalFallback(
                "Cloud 'products' collection is empty — displaying built-in 8-product catalogue."
              )
          }
          is CloudProductsSnapshot.Unavailable -> {
            cloudHasData = false
            _products.value = _localProducts.value
            _syncStatus.value = CatalogSyncStatus.LocalFallback(snapshot.reason)
          }
        }
      }
    }
  }

  suspend fun addProduct(product: Product): Result<Unit> {
    val updated = _localProducts.value + product
    _localProducts.value = updated
    _products.value = updated

    if (!cloudDataSource.isConfigured) {
      return Result.success(Unit)
    }

    // If Firestore 'products' was empty when admin adds first item, seed starter items too
    if (!cloudHasData) {
      _localProducts.value.forEach { item ->
        cloudDataSource.upsertProduct(item)
      }
    }
    return cloudDataSource.upsertProduct(product)
  }

  suspend fun updateProduct(product: Product): Result<Unit> {
    val updated =
      _localProducts.value.map { existing ->
        if (existing.id == product.id) product else existing
      }
    _localProducts.value = updated
    _products.value = updated

    if (!cloudDataSource.isConfigured) {
      return Result.success(Unit)
    }

    if (!cloudHasData) {
      updated.forEach { item ->
        cloudDataSource.upsertProduct(item)
      }
    }
    return cloudDataSource.upsertProduct(product)
  }

  suspend fun deleteProduct(productId: String): Result<Unit> {
    val updated = _localProducts.value.filterNot { it.id == productId }
    _localProducts.value = updated
    _products.value = updated

    if (!cloudDataSource.isConfigured) {
      return Result.success(Unit)
    }

    if (!cloudHasData) {
      updated.forEach { item ->
        cloudDataSource.upsertProduct(item)
      }
    }
    return cloudDataSource.deleteProduct(productId)
  }
}
