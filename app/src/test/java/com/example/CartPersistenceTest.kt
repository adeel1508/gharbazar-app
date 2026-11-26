package com.example

import com.example.data.CartJson
import com.example.data.CartStore
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.data.SavedCartEntry
import com.example.model.Product
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartPersistenceTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  // --- CartJson ---

  @Test
  fun cartJson_roundTrip_preservesIdsQuantitiesAndEscapes() {
    val entries =
      listOf(
        SavedCartEntry("prod_1795627898720", 2),
        SavedCartEntry("suit_boski_plain", 1),
        SavedCartEntry("weird\"id\\with\ttab", 3),
      )
    assertEquals(entries, CartJson.decode(CartJson.encode(entries)))
  }

  @Test
  fun cartJson_encode_emptyList_isEmptyArray() {
    assertEquals("[]", CartJson.encode(emptyList()))
  }

  @Test
  fun cartJson_decode_corruptInput_returnsEmptyList() {
    listOf(
        null,
        "",
        "   ",
        "not json",
        "[{",
        "[{\"id\":1}]",
        "{\"id\":\"x\",\"qty\":1}",
        "[1,2]",
        "[{\"id\":\"x\",\"qty\":}]",
        "[{\"id\":\"x\",\"qty\":1},",
        "[{\"id\":\"x\",\"qty\":1}] trailing",
      )
      .forEach { bad ->
        assertEquals(
          "Corrupt input must decode to an empty cart: $bad",
          emptyList<SavedCartEntry>(),
          CartJson.decode(bad),
        )
      }
  }

  @Test
  fun cartJson_decode_dropsInvalidEntriesAndDedupes() {
    val decoded =
      CartJson.decode(
        "[{\"id\":\"a\",\"qty\":2},{\"id\":\"\",\"qty\":1}," +
          "{\"id\":\"b\",\"qty\":0},{\"id\":\"a\",\"qty\":5},{\"id\":\"c\",\"qty\":-1}]"
      )
    assertEquals(listOf(SavedCartEntry("a", 2)), decoded)
  }

  @Test
  fun cartJson_decode_ignoresUnknownKeys() {
    val decoded =
      CartJson.decode("[{\"id\":\"a\",\"qty\":1,\"price\":999,\"note\":\"x\"}]")
    assertEquals(listOf(SavedCartEntry("a", 1)), decoded)
  }

  // --- StoreViewModel restore ---

  @Test
  fun viewModel_restoresSavedCartFromLiveProducts_withCurrentPrices() {
    val cartStore =
      FakeCartStore(
        savedEntries =
          listOf(
            SavedCartEntry(ProductCatalog.products[0].id, 2), // Rs 1,949
            SavedCartEntry(ProductCatalog.products[4].id, 1), // Rs 1,999
          )
      )
    val vm = createViewModel(cartStore = cartStore)

    assertEquals(3, vm.uiState.value.cartItemCount)
    assertEquals(1949 * 2 + 1999, vm.uiState.value.cartTotalPkr)
    assertEquals(2, vm.uiState.value.cartQuantityByProductId[ProductCatalog.products[0].id])
    assertEquals(1, vm.uiState.value.cartQuantityByProductId[ProductCatalog.products[4].id])
  }

  @Test
  fun viewModel_restore_dropsDeletedOrUnavailableProducts() {
    val cartStore =
      FakeCartStore(
        savedEntries =
          listOf(
            SavedCartEntry("gone_product_id", 3),
            SavedCartEntry(ProductCatalog.products[1].id, 1),
          )
      )
    val vm = createViewModel(cartStore = cartStore)

    assertEquals(1, vm.uiState.value.cartItemCount)
    assertEquals(
      ProductCatalog.products[1].id,
      vm.uiState.value.cartItems.single().product.id,
    )
  }

  @Test
  fun viewModel_restore_corruptSavedData_startsWithEmptyCartAndKeepsWorking() {
    val cartStore = FakeCartStore(throwOnLoad = true)
    val vm = createViewModel(cartStore = cartStore)

    assertTrue(vm.uiState.value.cartItems.isEmpty())

    // A failed restore must never break the cart afterwards.
    vm.addToCart(ProductCatalog.products[0])
    assertEquals(1, vm.uiState.value.cartItemCount)
  }

  @Test
  fun viewModel_cartMutations_persistAndOrderClearsPersistedCart() {
    val cartStore = FakeCartStore()
    val vm = createViewModel(cartStore = cartStore)
    val blanket = ProductCatalog.products[0] // Rs 1,949
    val suit = ProductCatalog.products[4] // Rs 1,999

    vm.addToCart(blanket)
    assertEquals(listOf(SavedCartEntry(blanket.id, 1)), cartStore.savedEntries)

    vm.addToCart(suit, quantity = 2)
    vm.increaseQuantity(blanket.id)
    assertEquals(
      listOf(SavedCartEntry(blanket.id, 2), SavedCartEntry(suit.id, 2)),
      cartStore.savedEntries,
    )

    vm.decreaseQuantity(suit.id)
    assertEquals(
      listOf(SavedCartEntry(blanket.id, 2), SavedCartEntry(suit.id, 1)),
      cartStore.savedEntries,
    )

    vm.removeFromCart(blanket.id)
    assertEquals(listOf(SavedCartEntry(suit.id, 1)), cartStore.savedEntries)

    // Place the order: the live cart AND the persisted cart are both cleared.
    vm.updateCustomerName("Ayesha Khan")
    vm.updatePhoneNumber("03019876543")
    vm.updateDeliveryAddress("House 5, DHA")
    vm.updateCity("Lahore")
    assertTrue(vm.placeOrder())
    assertTrue(vm.uiState.value.cartItems.isEmpty())
    assertTrue(cartStore.savedEntries.isEmpty())
    assertEquals(1, cartStore.clearCalls)
  }

  // --- Helpers ---

  private fun createViewModel(cartStore: CartStore): StoreViewModel {
    val cloud = FakeCartCloudProducts(ProductCatalog.products)
    val repo =
      ProductRepository(
        cloudDataSource = cloud,
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
      )
    return StoreViewModel(
      repository = repo,
      cartStore = cartStore,
      ioDispatcher = UnconfinedTestDispatcher(),
    )
  }

  private class FakeCartCloudProducts(initial: List<Product>) : CloudProductDataSource {
    override val isConfigured: Boolean = true
    private val flow =
      MutableStateFlow<CloudProductsSnapshot>(
        CloudProductsSnapshot.Loaded(initial, isFromCache = false)
      )

    override fun observeProducts(): Flow<CloudProductsSnapshot> = flow

    override suspend fun fetchProductsOnce(): CloudProductsSnapshot = flow.value

    override suspend fun upsertProduct(product: Product): Result<Unit> = Result.success(Unit)

    override suspend fun deleteProduct(productId: String): Result<Unit> = Result.success(Unit)
  }

  private class FakeCartStore(
    var savedEntries: List<SavedCartEntry> = emptyList(),
    var throwOnLoad: Boolean = false,
  ) : CartStore {
    var clearCalls = 0

    override suspend fun loadSavedEntries(): List<SavedCartEntry> {
      if (throwOnLoad) throw RuntimeException("Simulated load failure")
      return savedEntries
    }

    override suspend fun saveEntries(entries: List<SavedCartEntry>) {
      savedEntries = entries
    }

    override suspend fun clearSavedEntries() {
      clearCalls++
      savedEntries = emptyList()
    }
  }
}
