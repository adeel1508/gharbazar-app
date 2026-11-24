package com.example

import com.example.data.CatalogSyncStatus
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeCloudProductDataSource(
  override var isConfigured: Boolean,
  initialSnapshot: CloudProductsSnapshot,
) : CloudProductDataSource {

  val snapshotFlow = MutableStateFlow(initialSnapshot)
  val upsertedProducts = mutableListOf<Product>()
  val deletedIds = mutableListOf<String>()

  override fun observeProducts(): Flow<CloudProductsSnapshot> = snapshotFlow

  override suspend fun upsertProduct(product: Product): Result<Unit> {
    upsertedProducts.add(product)
    return Result.success(Unit)
  }

  override suspend fun deleteProduct(productId: String): Result<Unit> {
    deletedIds.add(productId)
    return Result.success(Unit)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductRepositoryTest {

  @Test
  fun unavailableCloud_fallsBackToBuiltInEightProductCatalog() = runTest {
    val cloud =
      FakeCloudProductDataSource(
        isConfigured = false,
        initialSnapshot = CloudProductsSnapshot.Unavailable("No google-services.json"),
      )
    val repo = ProductRepository(cloud, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))

    assertEquals(8, repo.products.value.size)
    assertEquals(ProductCatalog.products.map { it.id }, repo.products.value.map { it.id })
    assertTrue(repo.syncStatus.value is CatalogSyncStatus.LocalFallback)
  }

  @Test
  fun emptyCloud_fallsBackToBuiltInCatalog_thenSwitchesToCloudWhenLoaded() = runTest {
    val cloud =
      FakeCloudProductDataSource(
        isConfigured = true,
        initialSnapshot = CloudProductsSnapshot.Empty,
      )
    val repo = ProductRepository(cloud, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))

    // Empty cloud falls back to the 8 built-in products
    assertEquals(8, repo.products.value.size)
    assertTrue(repo.syncStatus.value is CatalogSyncStatus.LocalFallback)

    // Realtime cloud emission updates catalog immediately
    val cloudProduct =
      Product(
        id = "cloud_suit_1",
        name = "Cloud Silk Suit",
        pricePkr = 4500,
        category = ProductCategory.MENS_SUITS.label,
        fabric = "Silk",
        size = "4 meter",
        shortSpec = "4 meter • Silk",
        specs = listOf(ProductSpec("Fabric", "Silk")),
        description = "Synced from Firestore",
        imageKey = "suit_boski",
        imageRes = ProductCatalog.resolveDrawableForKey("suit_boski"),
      )
    cloud.snapshotFlow.value =
      CloudProductsSnapshot.Loaded(products = listOf(cloudProduct), isFromCache = false)

    assertEquals(1, repo.products.value.size)
    assertEquals("Cloud Silk Suit", repo.products.value.first().name)
    assertEquals(CatalogSyncStatus.SyncedLive, repo.syncStatus.value)
  }

  @Test
  fun adminCrudWrites_updateLocalAndCloudCatalogImmediately() = runTest {
    val cloud =
      FakeCloudProductDataSource(
        isConfigured = true,
        initialSnapshot =
          CloudProductsSnapshot.Loaded(
            products = ProductCatalog.products,
            isFromCache = true,
          ),
      )
    val repo = ProductRepository(cloud, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))
    assertEquals(CatalogSyncStatus.OfflineCached, repo.syncStatus.value)

    val newProduct =
      Product(
        id = "new_blanket_9",
        name = "Royal Velvet Double Blanket",
        pricePkr = 4200,
        category = ProductCategory.BLANKETS.label,
        fabric = "Velvet Fleece",
        size = "90x92 inches",
        shortSpec = "90x92 inches • Velvet Fleece",
        specs = listOf(ProductSpec("Fabric", "Velvet Fleece")),
        description = "Warm velvet blanket",
        imageKey = "blanket_embossed_red",
        imageRes = ProductCatalog.resolveDrawableForKey("blanket_embossed_red"),
      )

    assertTrue(repo.addProduct(newProduct).isSuccess)
    assertEquals(9, repo.products.value.size)
    assertTrue(cloud.upsertedProducts.any { it.id == "new_blanket_9" })

    val edited = newProduct.copy(pricePkr = 3999)
    assertTrue(repo.updateProduct(edited).isSuccess)
    assertEquals(3999, repo.products.value.find { it.id == "new_blanket_9" }?.pricePkr)

    assertTrue(repo.deleteProduct("new_blanket_9").isSuccess)
    assertEquals(8, repo.products.value.size)
    assertTrue(cloud.deletedIds.contains("new_blanket_9"))
  }
}
