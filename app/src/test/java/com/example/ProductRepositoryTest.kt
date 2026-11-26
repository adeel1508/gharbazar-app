package com.example

import com.example.data.CatalogSyncStatus
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.model.Product
import com.example.model.ProductCategories
import com.example.model.ProductSpec
import com.example.model.resolveGalleryImageUrls
import com.example.model.sanitizeProductForCloudSave
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
  var fetchOnceCalls = 0

  override fun observeProducts(): Flow<CloudProductsSnapshot> = snapshotFlow

  override suspend fun fetchProductsOnce(): CloudProductsSnapshot {
    fetchOnceCalls++
    return snapshotFlow.value
  }

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
  fun unavailableCloud_doesNotSeedOrFakeCatalog_andExposesErrorStatus() = runTest {
    val cloud =
      FakeCloudProductDataSource(
        isConfigured = false,
        initialSnapshot = CloudProductsSnapshot.Unavailable("Firebase Firestore is not configured."),
      )
    val repo = ProductRepository(cloud, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))

    assertTrue(repo.products.value.isEmpty())
    assertTrue(cloud.upsertedProducts.isEmpty())
    assertTrue(repo.syncStatus.value is CatalogSyncStatus.Error)
  }

  @Test
  fun emptyCloud_showsSyncedEmpty_thenUpdatesLiveWhenProductsArrive() = runTest {
    val cloud =
      FakeCloudProductDataSource(
        isConfigured = true,
        initialSnapshot = CloudProductsSnapshot.Empty,
      )
    val repo = ProductRepository(cloud, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))

    assertTrue(repo.products.value.isEmpty())
    assertTrue(cloud.upsertedProducts.isEmpty())
    assertEquals(CatalogSyncStatus.SyncedLive, repo.syncStatus.value)

    val cloudProduct =
      Product(
        id = "cloud_suit_1",
        name = "Cloud Silk Suit",
        pricePkr = 4500,
        category = ProductCategories.MENS_SUITS,
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
  fun galleryParsing_prefersImageUris_fallsBackToImageUri_thenPlaceholder() {
    // imageUris wins over imageUri and strips invalid/local entries
    val parsedFromList =
      resolveGalleryImageUrls(
        imageUris =
          listOf(
            "https://res.cloudinary.com/mlfzivam/image/upload/v1/one.jpg",
            "content://media/external/images/media/99",
            "https://res.cloudinary.com/mlfzivam/image/upload/v1/two.jpg",
          ),
        imageUri = "https://example.com/fallback.jpg",
      )
    assertEquals(
      listOf(
        "https://res.cloudinary.com/mlfzivam/image/upload/v1/one.jpg",
        "https://res.cloudinary.com/mlfzivam/image/upload/v1/two.jpg",
      ),
      parsedFromList,
    )

    // Falls back to single imageUri when imageUris is empty
    val parsedFromSingle =
      resolveGalleryImageUrls(
        imageUris = emptyList(),
        imageUri = "https://res.cloudinary.com/mlfzivam/image/upload/v1/single.jpg",
      )
    assertEquals(
      listOf("https://res.cloudinary.com/mlfzivam/image/upload/v1/single.jpg"),
      parsedFromSingle,
    )

    // Empty when both missing or only local content:// URIs -> UI shows placeholder
    val placeholderOnly =
      resolveGalleryImageUrls(
        imageUris = listOf("content://media/external/images/media/42"),
        imageUri = "file:///storage/emulated/0/Download/photo.jpg",
      )
    assertTrue(placeholderOnly.isEmpty())
  }

  @Test
  fun contentUriIsNeverPersistedAsProductImage() = runTest {
    val cloud =
      FakeCloudProductDataSource(
        isConfigured = true,
        initialSnapshot = CloudProductsSnapshot.Empty,
      )
    val repo = ProductRepository(cloud, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))

    val localUriOnlyProduct =
      Product(
        id = "prod_no_local_uri",
        name = "Custom Blanket",
        pricePkr = 3200,
        category = ProductCategories.BLANKETS,
        fabric = "Mink",
        size = "Double Bed",
        shortSpec = "Double Bed • Mink",
        specs = emptyList(),
        description = "Soft blanket",
        imageKey = "blanket_plain_blue",
        imageRes = ProductCatalog.resolveDrawableForKey("blanket_plain_blue"),
        imageUri = "content://media/picker/0/com.android.providers.media.photopicker/media/101",
        imageUris =
          listOf(
            "content://media/picker/0/com.android.providers.media.photopicker/media/101",
            "https://res.cloudinary.com/mlfzivam/image/upload/v1/valid.jpg",
          ),
      )

    val sanitized = sanitizeProductForCloudSave(localUriOnlyProduct)
    assertFalse(sanitized.imageUris.any { it.startsWith("content://") })
    assertEquals(
      listOf("https://res.cloudinary.com/mlfzivam/image/upload/v1/valid.jpg"),
      sanitized.imageUris,
    )

    val pureContentProduct =
      localUriOnlyProduct.copy(
        id = "prod_pure_content",
        imageUris = listOf("content://media/external/images/media/555"),
      )
    assertTrue(repo.addProduct(pureContentProduct).isSuccess)
    val persisted = cloud.upsertedProducts.last()
    assertTrue(persisted.imageUris.isEmpty())
    assertNull(persisted.imageUri)
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
        category = "Shawls",
        fabric = "Velvet Fleece",
        size = "90x92 inches",
        shortSpec = "90x92 inches • Velvet Fleece",
        specs = listOf(ProductSpec("Fabric", "Velvet Fleece")),
        description = "Warm velvet shawl",
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
