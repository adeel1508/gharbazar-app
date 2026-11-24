package com.example

import com.example.data.AppUpdateDataSource
import com.example.data.AppUpdateInfo
import com.example.data.CloudOrderDataSource
import com.example.data.CloudOrdersSnapshot
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.data.shouldPromptForUpdate
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.ProductCategory
import com.example.model.SortOption
import com.example.model.formatPkr
import com.example.security.PasswordActionResult
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
  private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}

class StoreViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun catalog_hasExactEightProductsAndExactPkrPrices() {
    val products = ProductCatalog.products
    assertEquals(8, products.size)

    val expectedPrices =
      mapOf(
        "Cozy Fleece Printed Double Bed Blanket" to 1949,
        "Fleece Printed Double Bed Blanket Navy White" to 1949,
        "Stylish Fleece Plain Double Bed Blanket" to 2599,
        "3D Panel Fleece Embossed Double Bed Blanket" to 3599,
        "Men's Unstitched Boski Plain Suit" to 1999,
        "Men's Unstitched Wool Plain Suit" to 2299,
        "Men's Unstitched Wash And Wear Plain Suit" to 2999,
        "Men's Unstitched Cotton Plain Suit" to 3099,
      )

    expectedPrices.forEach { (name, price) ->
      val product = products.find { it.name == name }
      assertNotNull("Missing product: $name", product)
      assertEquals(price, product?.pricePkr)
      assertTrue("Product must have image resource: $name", (product?.imageRes ?: 0) != 0)
      assertTrue("Product must have specs: $name", (product?.specs?.isNotEmpty() == true))
    }
    assertEquals("Rs 1,949", formatPkr(1949))
    assertEquals("Rs 3,599", formatPkr(3599))
  }

  @Test
  fun searchCategoryAndSort_filterAndSortAccurately() {
    val vm = StoreViewModel()

    vm.selectCategory(ProductCategory.BLANKETS.label)
    assertEquals(4, vm.uiState.value.filteredProducts.size)
    assertTrue(vm.uiState.value.filteredProducts.all { it.category == ProductCategory.BLANKETS.label })

    vm.selectCategory(ProductCategory.MENS_SUITS.label)
    assertEquals(4, vm.uiState.value.filteredProducts.size)
    assertTrue(vm.uiState.value.filteredProducts.all { it.category == ProductCategory.MENS_SUITS.label })

    vm.updateSortOption(SortOption.PRICE_HIGH_LOW)
    assertEquals(3099, vm.uiState.value.filteredProducts.first().pricePkr)
    assertEquals(1999, vm.uiState.value.filteredProducts.last().pricePkr)

    vm.updateSortOption(SortOption.PRICE_LOW_HIGH)
    assertEquals(1999, vm.uiState.value.filteredProducts.first().pricePkr)

    vm.selectCategory(ProductCategory.ALL.label)
    vm.updateSearchQuery("Boski")
    assertEquals(1, vm.uiState.value.filteredProducts.size)
    assertEquals("Men's Unstitched Boski Plain Suit", vm.uiState.value.filteredProducts.first().name)

    vm.resetFilters()
    assertEquals(8, vm.uiState.value.filteredProducts.size)
    assertEquals(SortOption.FEATURED, vm.uiState.value.sortOption)
  }

  @Test
  fun cartOperations_updateTotalQuantitiesAndMap() {
    val vm = StoreViewModel()
    val blanket = ProductCatalog.products[0] // Rs 1,949
    val suit = ProductCatalog.products[4] // Rs 1,999

    vm.addToCart(blanket)
    vm.addToCart(suit, quantity = 2)
    assertEquals(3, vm.uiState.value.cartItemCount)
    assertEquals(1949 + (1999 * 2), vm.uiState.value.cartTotalPkr)
    assertEquals(1, vm.uiState.value.cartQuantityByProductId[blanket.id])
    assertEquals(2, vm.uiState.value.cartQuantityByProductId[suit.id])

    vm.increaseQuantity(blanket.id)
    assertEquals(4, vm.uiState.value.cartItemCount)
    assertEquals((1949 * 2) + (1999 * 2), vm.uiState.value.cartTotalPkr)

    vm.decreaseQuantity(suit.id)
    assertEquals(3, vm.uiState.value.cartItemCount)
    assertEquals((1949 * 2) + 1999, vm.uiState.value.cartTotalPkr)

    vm.decreaseQuantity(suit.id)
    assertEquals(2, vm.uiState.value.cartItemCount)
    assertNull(vm.uiState.value.cartQuantityByProductId[suit.id])

    vm.removeFromCart(blanket.id)
    assertEquals(0, vm.uiState.value.cartItemCount)
    assertEquals(0, vm.uiState.value.cartTotalPkr)
  }

  @Test
  fun checkoutValidation_andOrderPlacement_clearsCartAndGeneratesUniqueOrderNumber() {
    val vm = StoreViewModel()

    assertFalse(vm.placeOrder())

    val blanket = ProductCatalog.products[3] // Rs 3,599
    vm.addToCart(blanket)

    assertFalse(vm.placeOrder())
    assertNotNull(vm.uiState.value.checkoutForm.nameError)
    assertNotNull(vm.uiState.value.checkoutForm.phoneError)
    assertNotNull(vm.uiState.value.checkoutForm.addressError)
    assertNotNull(vm.uiState.value.checkoutForm.cityError)
    assertEquals(1, vm.uiState.value.cartItemCount)

    vm.updateCustomerName("Tariq Mahmood")
    vm.updatePhoneNumber("123")
    vm.updateDeliveryAddress("Street 4, F-10/2")
    vm.updateCity("Islamabad")
    assertFalse(vm.placeOrder())
    assertNotNull(vm.uiState.value.checkoutForm.phoneError)

    vm.updatePhoneNumber("03211234567")
    assertTrue(vm.placeOrder())
    assertTrue(vm.uiState.value.cartItems.isEmpty())
    assertEquals(0, vm.uiState.value.cartTotalPkr)

    val firstOrder = vm.uiState.value.lastConfirmedOrder
    assertNotNull(firstOrder)
    assertTrue(firstOrder!!.orderNumber.startsWith("GB-"))
    assertEquals(3599, firstOrder.totalPkr)
    assertEquals(1, firstOrder.items.size)

    vm.addToCart(ProductCatalog.products[0])
    vm.updateCustomerName("Tariq Mahmood")
    vm.updatePhoneNumber("03211234567")
    vm.updateDeliveryAddress("Street 4, F-10/2")
    vm.updateCity("Islamabad")
    assertTrue(vm.placeOrder())

    val secondOrder = vm.uiState.value.lastConfirmedOrder
    assertNotNull(secondOrder)
    assertNotEquals(firstOrder.orderNumber, secondOrder!!.orderNumber)
  }

  @Test
  fun adminAuthAndProductCrud_updatesCustomerCatalogAndCartImmediately() {
    val vm = StoreViewModel()

    // First run: no admin password set
    assertFalse(vm.uiState.value.isAdminPasswordSet)
    assertFalse(vm.uiState.value.isAdminUnlocked)

    // Setup owner password
    assertEquals(
      PasswordActionResult.Success,
      vm.setupAdminPassword("OwnerPass99", "OwnerPass99"),
    )
    assertTrue(vm.uiState.value.isAdminPasswordSet)
    assertTrue(vm.uiState.value.isAdminUnlocked)

    // Lock and test unlock
    vm.lockAdmin()
    assertFalse(vm.uiState.value.isAdminUnlocked)
    assertFalse(vm.unlockAdmin("WrongPassword"))
    assertFalse(vm.uiState.value.isAdminUnlocked)
    assertTrue(vm.unlockAdmin("OwnerPass99"))
    assertTrue(vm.uiState.value.isAdminUnlocked)

    // Admin adds a new product -> appears in customer catalog immediately
    vm.saveAdminProduct(
      editingProductId = null,
      name = "Men's Unstitched Karandi Suit",
      pricePkr = 2750,
      category = ProductCategory.MENS_SUITS.label,
      fabric = "Karandi",
      size = "4.5 meter cutting",
      description = "Warm textured winter Karandi suit.",
      imageKey = "suit_wool_brown",
      imageUri = null,
      inStock = true,
      stockQuantity = 12,
    )
    assertEquals(9, vm.uiState.value.allProducts.size)
    val added = vm.uiState.value.allProducts.find { it.name == "Men's Unstitched Karandi Suit" }
    assertNotNull(added)

    // Add it to cart, then edit its price in Admin -> cart & store update immediately
    vm.addToCart(added!!)
    assertEquals(2750, vm.uiState.value.cartTotalPkr)

    vm.saveAdminProduct(
      editingProductId = added.id,
      name = "Men's Unstitched Karandi Suit",
      pricePkr = 2499,
      category = ProductCategory.MENS_SUITS.label,
      fabric = "Karandi",
      size = "4.5 meter cutting",
      description = "Updated price winter Karandi suit.",
      imageKey = "suit_wool_brown",
      imageUri = null,
      inStock = true,
      stockQuantity = 10,
    )
    assertEquals(2499, vm.getProductById(added.id)?.pricePkr)
    assertEquals(2499, vm.uiState.value.cartTotalPkr)

    // Marking out of stock removes it from cart and blocks adding
    vm.saveAdminProduct(
      editingProductId = added.id,
      name = "Men's Unstitched Karandi Suit",
      pricePkr = 2499,
      category = ProductCategory.MENS_SUITS.label,
      fabric = "Karandi",
      size = "4.5 meter cutting",
      description = "Sold out",
      imageKey = "suit_wool_brown",
      imageUri = null,
      inStock = false,
      stockQuantity = 0,
    )
    assertEquals(0, vm.uiState.value.cartItemCount)
    vm.addToCart(vm.getProductById(added.id)!!)
    assertEquals(0, vm.uiState.value.cartItemCount)

    // Delete product -> returns catalog to 8
    vm.deleteAdminProduct(added.id)
    assertEquals(8, vm.uiState.value.allProducts.size)
    assertNull(vm.getProductById(added.id))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun placeOrder_succeedsEvenWhenFirestoreSaveFails_andAdminCanUpdateOrderStatus() {
    val failingCloudOrders =
      object : CloudOrderDataSource {
        override val isConfigured: Boolean = true
        private val flow = MutableStateFlow<CloudOrdersSnapshot>(CloudOrdersSnapshot.Empty)
        val savedNumbers = mutableListOf<String>()
        val statusUpdates = mutableListOf<Pair<String, OrderStatus>>()

        override fun observeOrders(): Flow<CloudOrdersSnapshot> = flow

        override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
          savedNumbers.add(order.orderNumber)
          return Result.failure(RuntimeException("Network timeout"))
        }

        override suspend fun updateOrderStatus(
          orderNumber: String,
          status: OrderStatus,
        ): Result<Unit> {
          statusUpdates.add(orderNumber to status)
          return Result.success(Unit)
        }
      }

    val orderRepo =
      OrderRepository(
        cloudDataSource = failingCloudOrders,
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
      )
    val vm = StoreViewModel(orderRepository = orderRepo)

    val item = ProductCatalog.products[0]
    vm.addToCart(item, quantity = 2)
    vm.updateCustomerName("Sadia Bibi")
    vm.updatePhoneNumber("03017654321")
    vm.updateDeliveryAddress("House 9, Askari 11")
    vm.updateCity("Rawalpindi")

    // Checkout must succeed locally immediately even when cloud save returns failure
    val placed = vm.placeOrder()
    assertTrue(placed)
    assertTrue(vm.uiState.value.cartItems.isEmpty())

    val confirmed = vm.uiState.value.lastConfirmedOrder
    assertNotNull(confirmed)
    assertEquals(OrderStatus.PLACED, confirmed!!.status)
    assertTrue(confirmed.timestamp > 0L)
    assertEquals(listOf(confirmed.orderNumber), failingCloudOrders.savedNumbers)

    // Order appears in adminOrders and admin can advance its status
    assertEquals(1, vm.uiState.value.adminOrders.size)
    vm.updateAdminOrderStatus(confirmed.orderNumber, OrderStatus.CONFIRMED)
    assertEquals(OrderStatus.CONFIRMED, vm.uiState.value.adminOrders.first().status)
    assertEquals(
      listOf(confirmed.orderNumber to OrderStatus.CONFIRMED),
      failingCloudOrders.statusUpdates,
    )
  }

  @Test
  fun updateCheck_promptsOnlyWhenNewerVersionAndUrlExist_andDismissesCleanly() {
    val newerUpdateSource =
      object : AppUpdateDataSource {
        override suspend fun fetchUpdateInfo(currentVersionCode: Int): AppUpdateInfo {
          return AppUpdateInfo(
            latestVersionCode = 2,
            apkUrl = "https://gharbazar.pk/app-release.apk",
          )
        }
      }

    val vm =
      StoreViewModel(
        appUpdateDataSource = newerUpdateSource,
        currentVersionCode = 1,
      )

    val available = vm.uiState.value.availableUpdate
    assertNotNull(available)
    assertEquals(2, available?.latestVersionCode)
    assertEquals("https://gharbazar.pk/app-release.apk", available?.apkUrl)

    vm.dismissUpdateDialog()
    assertNull(vm.uiState.value.availableUpdate)
  }

  @Test
  fun updateCheck_ignoresSameVersionEmptyUrlMissingOrFailingDoc() {
    // Same version -> no prompt
    assertNull(
      shouldPromptForUpdate(
        AppUpdateInfo(latestVersionCode = 1, apkUrl = "https://gharbazar.pk/app.apk"),
        currentVersionCode = 1,
      )
    )
    // Empty URL -> no prompt
    assertNull(
      shouldPromptForUpdate(
        AppUpdateInfo(latestVersionCode = 5, apkUrl = ""),
        currentVersionCode = 1,
      )
    )

    // Failing data source -> app runs normally with no dialog
    val failingSource =
      object : AppUpdateDataSource {
        override suspend fun fetchUpdateInfo(currentVersionCode: Int): AppUpdateInfo? {
          throw IllegalStateException("Firestore unreachable")
        }
      }
    val vm =
      StoreViewModel(
        appUpdateDataSource = failingSource,
        currentVersionCode = 1,
      )

    assertNull(vm.uiState.value.availableUpdate)
    assertEquals(8, vm.uiState.value.allProducts.size)
  }
}
