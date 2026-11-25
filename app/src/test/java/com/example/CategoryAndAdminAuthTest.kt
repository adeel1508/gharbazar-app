package com.example

import com.example.data.AdminAuthDataSource
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.GHARBAZAR_ADMIN_EMAIL
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.data.friendlyAdminAuthError
import com.example.model.OrderStatus
import com.example.model.ProductCategory
import com.example.model.categoryFiltersFor
import com.example.viewmodel.StoreViewModel
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryAndAdminAuthTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun categoryFromStored_mapsEnumNamesAndLabelsToCanonicalLabels() {
    assertEquals("Blankets", ProductCategory.fromStored("BLANKETS"))
    assertEquals("Blankets", ProductCategory.fromStored("blankets"))
    assertEquals("Blankets", ProductCategory.fromStored("Blankets"))
    assertEquals("Men's Suits", ProductCategory.fromStored("MENS_SUITS"))
    assertEquals("Men's Suits", ProductCategory.fromStored("mens_suits"))
    assertEquals("Men's Suits", ProductCategory.fromStored("Men's Suits"))
    assertEquals("Blankets", ProductCategory.fromStored(null))
    assertEquals("Blankets", ProductCategory.fromStored(""))
    assertEquals("Blankets", ProductCategory.fromStored("   "))
  }

  @Test
  fun categoryFromStored_passesCustomCategoriesThroughTrimmed() {
    assertEquals("Bedsheets", ProductCategory.fromStored("Bedsheets"))
    assertEquals("Bedsheets", ProductCategory.fromStored("  Bedsheets  "))
    assertEquals("Curtains", ProductCategory.fromStored("Curtains"))
  }

  @Test
  fun categoryWireValue_keepsEnumNamesForBuiltInsAndLabelForCustom() {
    assertEquals("BLANKETS", ProductCategory.wireValue("Blankets"))
    assertEquals("MENS_SUITS", ProductCategory.wireValue("Men's Suits"))
    assertEquals("BLANKETS", ProductCategory.wireValue("blankets"))
    assertEquals("Bedsheets", ProductCategory.wireValue("Bedsheets"))
    assertEquals("Bedsheets", ProductCategory.wireValue("  Bedsheets "))
  }

  @Test
  fun categoryFiltersFor_builtInCatalogueHasAllBlanketsAndSuits() {
    val filters = categoryFiltersFor(ProductCatalog.products)
    assertEquals(
      listOf("All" to 8, "Blankets" to 4, "Men's Suits" to 4),
      filters,
    )
  }

  @Test
  fun categoryFiltersFor_customCategoriesAppearAlphabeticallyAfterBuiltIns() {
    val products =
      ProductCatalog.products +
        ProductCatalog.products[0].copy(id = "custom_1", name = "Bedsheet A", category = "Bedsheets") +
        ProductCatalog.products[0].copy(id = "custom_2", name = "Bedsheet B", category = "Bedsheets") +
        ProductCatalog.products[0].copy(id = "custom_3", name = "Curtain A", category = "Curtains")
    assertEquals(
      listOf(
        "All" to 11,
        "Blankets" to 4,
        "Men's Suits" to 4,
        "Bedsheets" to 2,
        "Curtains" to 1,
      ),
      categoryFiltersFor(products),
    )
  }

  @Test
  fun categoryFiltersFor_emptyCatalogueHasOnlyAllWithZero() {
    assertEquals(listOf("All" to 0), categoryFiltersFor(emptyList()))
  }

  @Test
  fun customCategory_saveAndFilter_roundTripsThroughViewModel() {
    val vm = StoreViewModel()

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Handloom Bedsheet Set",
      pricePkr = 3200,
      category = "Bedsheets",
      fabric = "Cotton",
      size = "King Size",
      description = "Soft cotton bedsheet set.",
      imageKey = "blanket_plain_blue",
      imageUri = null,
      inStock = true,
      stockQuantity = 15,
    )

    val added = vm.uiState.value.allProducts.find { it.name == "Handloom Bedsheet Set" }
    assertNotNull(added)
    assertEquals("Bedsheets", added?.category)
    assertEquals("Bedsheets", added?.specs?.find { it.label == "Category" }?.value)

    vm.selectCategory("Bedsheets")
    assertEquals(1, vm.uiState.value.filteredProducts.size)
    assertEquals("Bedsheets", vm.uiState.value.selectedCategory)

    val filters = categoryFiltersFor(vm.uiState.value.allProducts)
    assertEquals("All" to 9, filters.first())
    assertTrue(filters.contains("Bedsheets" to 1))
  }

  @Test
  fun cloudWriteBlockedUntilAdminSignsIn() {
    val vm = StoreViewModel(repository = ProductRepository(FakeConfiguredCloudProducts()))

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Should Not Save",
      pricePkr = 1000,
      category = "Blankets",
      fabric = "Cotton",
      size = "Size",
      description = "Desc",
      imageKey = "blanket_plain_blue",
      imageUri = null,
      inStock = true,
      stockQuantity = 5,
    )

    assertEquals(8, vm.uiState.value.allProducts.size)
    assertTrue(vm.uiState.value.adminNotice?.contains("Sign in") == true)

    vm.deleteAdminProduct(ProductCatalog.products[0].id)
    assertEquals(8, vm.uiState.value.allProducts.size)
  }

  @Test
  fun adminPanelGate_staysLockedWithoutCloudSignIn() {
    // The AdminRouteScreen gate is (isCloudConfigured && !isAdminSignedIn):
    // with a configured cloud the panel must stay locked until the cloud
    // sign-in succeeds, and every cloud write stays blocked meanwhile.
    val failingAuth =
      FakeAdminAuth(signInResult = Result.failure(RuntimeException("bad password")))
    val vm =
      StoreViewModel(
        repository = ProductRepository(FakeConfiguredCloudProducts()),
        adminAuth = failingAuth,
      )
    assertTrue(vm.uiState.value.isCloudConfigured)
    assertFalse(vm.uiState.value.isAdminSignedIn)

    vm.signInAdmin("wrong-password")
    assertFalse(vm.uiState.value.isAdminSignedIn)
    assertNotNull(vm.uiState.value.adminAuthError)

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Should Not Save",
      pricePkr = 1000,
      category = "Blankets",
      fabric = "Cotton",
      size = "Size",
      description = "Desc",
      imageKey = "blanket_plain_blue",
      imageUri = null,
      inStock = true,
      stockQuantity = 5,
    )
    vm.deleteAdminProduct(ProductCatalog.products[0].id)
    vm.updateAdminOrderStatus("GB-000000", OrderStatus.CONFIRMED)
    assertEquals(8, vm.uiState.value.allProducts.size)
    assertTrue(vm.uiState.value.adminNotice?.contains("Sign in") == true)
  }

  @Test
  fun adminPanelGate_skippedWhenCloudNotConfigured() {
    // Without a cloud catalogue the local admin password is the only lock,
    // as before — the cloud sign-in gate does not apply.
    val vm = StoreViewModel()
    assertFalse(vm.uiState.value.isCloudConfigured)
    assertFalse(vm.uiState.value.isAdminSignedIn)
  }

  @Test
  fun cloudWriteAllowedWhenAdminSignedIn() {
    val fakeAuth = FakeAdminAuth(isSignedIn = true)
    val vm =
      StoreViewModel(
        repository = ProductRepository(FakeConfiguredCloudProducts()),
        adminAuth = fakeAuth,
      )

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Signed In Product",
      pricePkr = 1000,
      category = "Curtains",
      fabric = "Cotton",
      size = "Size",
      description = "Desc",
      imageKey = "blanket_plain_blue",
      imageUri = null,
      inStock = true,
      stockQuantity = 5,
    )

    assertEquals(9, vm.uiState.value.allProducts.size)
    assertEquals("Curtains", vm.uiState.value.allProducts.find { it.name == "Signed In Product" }?.category)
  }

  @Test
  fun signInAdmin_successSetsSignedInState_andFailureShowsPlainError() {
    val failingAuth =
      FakeAdminAuth(signInResult = Result.failure(RuntimeException("bad password")))
    val vm = StoreViewModel(adminAuth = failingAuth)

    vm.signInAdmin("wrong-password")
    assertFalse(vm.uiState.value.isAdminSignedIn)
    assertEquals("wrong-password", failingAuth.lastPassword)
    assertTrue(vm.uiState.value.adminAuthError?.contains("bad password") == true)

    val okAuth = FakeAdminAuth()
    val vm2 = StoreViewModel(adminAuth = okAuth)
    vm2.signInAdmin("correct-password")
    assertTrue(vm2.uiState.value.isAdminSignedIn)
    assertNull(vm2.uiState.value.adminAuthError)
    assertEquals(GHARBAZAR_ADMIN_EMAIL, okAuth.adminEmail)
  }

  @Test
  fun friendlyAdminAuthError_mapsNetworkAndUnknownFailures() {
    assertEquals(
      "Could not reach Firebase. Check your connection and try again.",
      friendlyAdminAuthError(IOException("timeout")),
    )
    assertEquals(
      "Sign-in failed (boom).",
      friendlyAdminAuthError(RuntimeException("boom")),
    )
  }
}

private class FakeConfiguredCloudProducts : CloudProductDataSource {
  override val isConfigured: Boolean = true
  override fun observeProducts(): Flow<CloudProductsSnapshot> = emptyFlow()
  override suspend fun upsertProduct(product: com.example.model.Product): Result<Unit> =
    Result.success(Unit)
  override suspend fun deleteProduct(productId: String): Result<Unit> = Result.success(Unit)
}

private class FakeAdminAuth(
  override var isSignedIn: Boolean = false,
  var signInResult: Result<Unit> = Result.success(Unit),
) : AdminAuthDataSource {
  override val adminEmail: String = GHARBAZAR_ADMIN_EMAIL
  private val _authState = MutableStateFlow(isSignedIn)
  override val authState: Flow<Boolean> = _authState
  var lastPassword: String? = null

  override suspend fun signIn(password: String): Result<Unit> {
    lastPassword = password
    if (signInResult.isSuccess) {
      isSignedIn = true
      _authState.value = true
    }
    return signInResult
  }

  override fun signOut() {
    isSignedIn = false
    _authState.value = false
  }
}
