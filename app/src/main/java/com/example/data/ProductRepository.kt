package com.example.data

import com.example.model.Product
import com.example.model.sanitizeProductForCloudSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogSyncStatus {
  data object Loading : CatalogSyncStatus
  data object SyncedLive : CatalogSyncStatus
  data object OfflineCached : CatalogSyncStatus
  data class Error(val message: String) : CatalogSyncStatus
}

class ProductRepository(
  private val cloudDataSource: CloudProductDataSource,
  initialProducts: List<Product> = emptyList(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val _products = MutableStateFlow(initialProducts)
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _syncStatus =
    MutableStateFlow<CatalogSyncStatus>(
      if (initialProducts.isNotEmpty()) {
        CatalogSyncStatus.SyncedLive
      } else if (cloudDataSource.isConfigured) {
        CatalogSyncStatus.Loading
      } else {
        CatalogSyncStatus.Error("Firebase Firestore is not configured.")
      }
    )
  val syncStatus: StateFlow<CatalogSyncStatus> = _syncStatus.asStateFlow()

  private var observeJob: Job? = null

  init {
    observeCloudCatalog()
  }

  private fun observeCloudCatalog() {
    observeJob?.cancel()
    observeJob =
      scope.launch {
        cloudDataSource.observeProducts().collect { snapshot ->
          applySnapshot(snapshot)
        }
      }
  }

  fun refreshProducts() {
    _syncStatus.value = CatalogSyncStatus.Loading
    scope.launch {
      val snapshot =
        runCatching { cloudDataSource.fetchProductsOnce() }
          .getOrElse { err ->
            CloudProductsSnapshot.Unavailable(err.localizedMessage ?: "Unable to load products.")
          }
      applySnapshot(snapshot)
      observeCloudCatalog()
    }
  }

  private fun applySnapshot(snapshot: CloudProductsSnapshot) {
    when (snapshot) {
      is CloudProductsSnapshot.Loaded -> {
        _products.value = snapshot.products
        _syncStatus.value =
          if (snapshot.isFromCache) {
            CatalogSyncStatus.OfflineCached
          } else {
            CatalogSyncStatus.SyncedLive
          }
      }
      is CloudProductsSnapshot.Empty -> {
        _products.value = emptyList()
        _syncStatus.value = CatalogSyncStatus.SyncedLive
      }
      is CloudProductsSnapshot.Unavailable -> {
        _syncStatus.value = CatalogSyncStatus.Error(snapshot.reason)
      }
    }
  }

  suspend fun addProduct(product: Product): Result<Unit> {
    val sanitized = sanitizeProductForCloudSave(product)
    val result = cloudDataSource.upsertProduct(sanitized)
    if (result.isSuccess) {
      _products.value =
        _products.value.filterNot { it.id == sanitized.id } + sanitized
    }
    return result
  }

  suspend fun updateProduct(product: Product): Result<Unit> {
    val sanitized = sanitizeProductForCloudSave(product)
    val result = cloudDataSource.upsertProduct(sanitized)
    if (result.isSuccess) {
      val exists = _products.value.any { it.id == sanitized.id }
      _products.value =
        if (exists) {
          _products.value.map { if (it.id == sanitized.id) sanitized else it }
        } else {
          _products.value + sanitized
        }
    }
    return result
  }

  suspend fun deleteProduct(productId: String): Result<Unit> {
    val result = cloudDataSource.deleteProduct(productId)
    if (result.isSuccess) {
      _products.value = _products.value.filterNot { it.id == productId }
    }
    return result
  }
}
