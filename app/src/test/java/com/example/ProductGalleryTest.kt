package com.example

import com.example.data.AdminAuthDataSource
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.GHARBAZAR_ADMIN_EMAIL
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.data.productFromFirestoreFields
import com.example.model.ProductCategory
import com.example.ui.screens.parseImageUrls
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductGalleryTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun baseFields(): MutableMap<String, Any?> =
    mutableMapOf(
      "name" to "Test Blanket",
      "pricePkr" to 1999,
      "category" to "BLANKETS",
      "fabric" to "Fleece",
      "size" to "Double Bed",
      "description" to "Warm blanket",
      "imageKey" to "blanket_plain_blue",
      "inStock" to true,
      "stockQuantity" to 10,
    )

  @Test
  fun productFromFirestoreFields_imageUrisArrayWinsAndCoverIsFirst() {
    val fields =
      baseFields().apply {
        put("imageUri", "https://example.com/cover.jpg")
        put("imageUris", listOf("https://example.com/1.jpg", "https://example.com/2.jpg"))
      }
    val product = productFromFirestoreFields("p1", fields)!!
    assertEquals("https://example.com/cover.jpg", product.imageUri)
    assertEquals(
      listOf(
        "https://example.com/cover.jpg",
        "https://example.com/1.jpg",
        "https://example.com/2.jpg",
      ),
      product.imageUris,
    )
    assertEquals(product.imageUris, product.galleryPhotos())
  }

  @Test
  fun productFromFirestoreFields_imageUriOnlyBecomesSinglePhotoGallery() {
    val fields = baseFields().apply { put("imageUri", "https://example.com/only.jpg") }
    val product = productFromFirestoreFields("p1", fields)!!
    assertEquals("https://example.com/only.jpg", product.imageUri)
    assertEquals(listOf("https://example.com/only.jpg"), product.imageUris)
    assertEquals(listOf("https://example.com/only.jpg"), product.galleryPhotos())
  }

  @Test
  fun productFromFirestoreFields_noPhotosUsesBuiltInDrawableFallback() {
    val product = productFromFirestoreFields("p1", baseFields())!!
    assertNull(product.imageUri)
    assertEquals(emptyList<String>(), product.imageUris)
    assertEquals(listOf<String?>(null), product.galleryPhotos())
    assertEquals(
      ProductCatalog.resolveDrawableForKey("blanket_plain_blue"),
      product.imageRes,
    )
  }

  @Test
  fun productFromFirestoreFields_blankAndNonStringUrisAreDropped() {
    val fields =
      baseFields().apply {
        put("imageUris", listOf("  ", "https://example.com/1.jpg", 123, "", "https://example.com/2.jpg"))
      }
    val product = productFromFirestoreFields("p1", fields)!!
    assertEquals(
      listOf("https://example.com/1.jpg", "https://example.com/2.jpg"),
      product.imageUris,
    )
    assertEquals("https://example.com/1.jpg", product.imageUri)
  }

  @Test
  fun productFromFirestoreFields_coverPrependedWhenMissingFromUris() {
    val fields =
      baseFields().apply {
        put("imageUri", "https://example.com/cover.jpg")
        put("imageUris", listOf("https://example.com/1.jpg"))
      }
    val product = productFromFirestoreFields("p1", fields)!!
    assertEquals(
      listOf("https://example.com/cover.jpg", "https://example.com/1.jpg"),
      product.imageUris,
    )
  }

  @Test
  fun productFromFirestoreFields_missingNameOrPriceReturnsNull() {
    val noName = baseFields().apply { remove("name") }
    assertNull(productFromFirestoreFields("p1", noName))
    val noPrice = baseFields().apply { remove("pricePkr") }
    assertNull(productFromFirestoreFields("p1", noPrice))
    val blankName = baseFields().apply { put("name", "   ") }
    assertNull(productFromFirestoreFields("p1", blankName))
  }

  @Test
  fun galleryPhotos_builtInCatalogueProductYieldsNullPlaceholder() {
    val builtIn = ProductCatalog.products.first()
    assertNull(builtIn.imageUri)
    assertEquals(emptyList<String>(), builtIn.imageUris)
    assertEquals(listOf<String?>(null), builtIn.galleryPhotos())
  }

  @Test
  fun parseImageUrls_splitsLinesTrimsAndDedupes() {
    assertEquals(
      listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
      parseImageUrls("https://example.com/a.jpg\n\n  https://example.com/b.jpg  \nhttps://example.com/a.jpg\n"),
    )
    assertEquals(emptyList<String>(), parseImageUrls("   \n\n  "))
    assertEquals(
      listOf("https://example.com/only.jpg"),
      parseImageUrls("https://example.com/only.jpg"),
    )
  }

  @Test
  fun saveAdminProduct_withImageUris_savesGalleryAndCoverFirst() {
    val vm =
      StoreViewModel(
        repository = ProductRepository(FakeSignedInCloudProducts()),
        adminAuth = FakeSignedInAdminAuth(),
      )

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Gallery Blanket",
      pricePkr = 2100,
      category = ProductCategory.BLANKETS.label,
      fabric = "Fleece",
      size = "Double Bed",
      description = "Blanket with two photos.",
      imageKey = "blanket_plain_blue",
      imageUri = "https://example.com/cover.jpg",
      inStock = true,
      stockQuantity = 5,
      imageUris = listOf("https://example.com/cover.jpg", "https://example.com/2.jpg"),
    )

    val added = vm.uiState.value.allProducts.find { it.name == "Gallery Blanket" }
    assertTrue(added != null)
    assertEquals("https://example.com/cover.jpg", added?.imageUri)
    assertEquals(
      listOf("https://example.com/cover.jpg", "https://example.com/2.jpg"),
      added?.imageUris,
    )
    assertEquals(added?.imageUris, added?.galleryPhotos())
  }

  @Test
  fun saveAdminProduct_withoutImageUris_keepsSingleCoverBehaviour() {
    val vm =
      StoreViewModel(
        repository = ProductRepository(FakeSignedInCloudProducts()),
        adminAuth = FakeSignedInAdminAuth(),
      )

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Single Photo Suit",
      pricePkr = 1500,
      category = ProductCategory.MENS_SUITS.label,
      fabric = "Boski",
      size = "4m",
      description = "One photo only.",
      imageKey = "suit_boski",
      imageUri = "https://example.com/suit.jpg",
      inStock = true,
      stockQuantity = 3,
    )

    val added = vm.uiState.value.allProducts.find { it.name == "Single Photo Suit" }
    assertEquals("https://example.com/suit.jpg", added?.imageUri)
    assertEquals(listOf("https://example.com/suit.jpg"), added?.imageUris)
  }
}

private class FakeSignedInCloudProducts : CloudProductDataSource {
  override val isConfigured: Boolean = true
  override fun observeProducts(): Flow<CloudProductsSnapshot> = emptyFlow()
  override suspend fun upsertProduct(product: com.example.model.Product): Result<Unit> =
    Result.success(Unit)
  override suspend fun deleteProduct(productId: String): Result<Unit> = Result.success(Unit)
}

private class FakeSignedInAdminAuth : AdminAuthDataSource {
  override val adminEmail: String = GHARBAZAR_ADMIN_EMAIL
  override val isSignedIn: Boolean = true
  private val _authState = MutableStateFlow(true)
  override val authState: Flow<Boolean> = _authState
  override suspend fun signIn(password: String): Result<Unit> = Result.success(Unit)
  override fun signOut() = Unit
}
