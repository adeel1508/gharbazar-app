package com.example

import com.example.data.AdminAuthDataSource
import com.example.data.AdminOrdersLoadState
import com.example.data.AppUpdateDataSource
import com.example.data.AppUpdateInfo
import com.example.data.CloudOrderDataSource
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.ImageUploadService
import com.example.data.ManualUpdateCheckResult
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.data.evaluateManualUpdateCheck
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategories
import com.example.model.SortOption
import com.example.model.formatPkr
import com.example.security.AdminPasswordManager
import com.example.security.PasswordActionResult
import com.example.security.PasswordStorage
import com.example.viewmodel.ManualUpdateDialogState
import com.example.viewmodel.OrderSyncState
import com.example.viewmodel.PickedPhotoStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun createTestProductRepository(
    products: List<Product> = ProductCatalog.products,
  ): Pair<ProductRepository, FakeCloudProducts> {
    val cloud = FakeCloudProducts(products)
    val repo =
      ProductRepository(
        cloudDataSource = cloud,
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
      )
    return repo to cloud
  }

  @Test
  fun catalogFixtures_haveExpectedPricesAndCategories() {
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
  fun searchCategoryAndSort_filterAndSortAccuratelyIncludingCustomCategories() {
    val customProduct =
      ProductCatalog.products[0].copy(
        id = "custom_shawl_1",
        name = "Pashmina Winter Shawl",
        pricePkr = 3200,
        category = "Winter Shawls",
        fabric = "Pashmina",
      )
    val (repo, _) = createTestProductRepository(ProductCatalog.products + customProduct)
    val vm =
      StoreViewModel(
        repository = repo,
        ioDispatcher = UnconfinedTestDispatcher(),
      )

    assertTrue(vm.uiState.value.availableCategories.contains("Winter Shawls"))

    vm.selectCategory(ProductCategories.BLANKETS)
    assertEquals(4, vm.uiState.value.filteredProducts.size)
    assertTrue(vm.uiState.value.filteredProducts.all { it.category == ProductCategories.BLANKETS })

    vm.selectCategory("Winter Shawls")
    assertEquals(1, vm.uiState.value.filteredProducts.size)
    assertEquals("Pashmina Winter Shawl", vm.uiState.value.filteredProducts.first().name)

    vm.selectCategory(ProductCategories.MENS_SUITS)
    assertEquals(4, vm.uiState.value.filteredProducts.size)
    assertTrue(vm.uiState.value.filteredProducts.all { it.category == ProductCategories.MENS_SUITS })

    vm.updateSortOption(SortOption.PRICE_HIGH_LOW)
    assertEquals(3099, vm.uiState.value.filteredProducts.first().pricePkr)
    assertEquals(1999, vm.uiState.value.filteredProducts.last().pricePkr)

    vm.updateSortOption(SortOption.PRICE_LOW_HIGH)
    assertEquals(1999, vm.uiState.value.filteredProducts.first().pricePkr)

    vm.selectCategory(ProductCategories.ALL)
    vm.updateSearchQuery("Boski")
    assertEquals(1, vm.uiState.value.filteredProducts.size)
    assertEquals("Men's Unstitched Boski Plain Suit", vm.uiState.value.filteredProducts.first().name)

    vm.resetFilters()
    assertEquals(9, vm.uiState.value.filteredProducts.size)
    assertEquals(SortOption.FEATURED, vm.uiState.value.sortOption)
  }

  @Test
  fun cartOperations_updateTotalQuantitiesAndMap() {
    val (repo, _) = createTestProductRepository()
    val vm = StoreViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher())
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
    val (repo, _) = createTestProductRepository()
    val vm = StoreViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher())

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
  fun ordersRefreshOnlyOnOrdersTabOpenOrRefreshTap_neverOnStartupOrAuthChange() {
    val cloudOrders =
      FakeCloudOrders(
        ordersToReturn =
          listOf(
            ConfirmedOrder(
              orderNumber = "GB-641124",
              customerName = "Zainab Tariq",
              phoneNumber = "03001112233",
              deliveryAddress = "House 12, Gulberg",
              city = "Lahore",
              items = emptyList(),
              totalPkr = 1949,
              timestampMillis = 1775044800000L,
            )
          )
      )
    val fakeAuth = FakeAdminAuth()
    val (repo, _) = createTestProductRepository()
    val orderRepo = OrderRepository(cloudOrders)

    val vm =
      StoreViewModel(
        repository = repo,
        passwordManager = AdminPasswordManager(FakePasswordStorage()),
        orderRepository = orderRepo,
        adminAuthDataSource = fakeAuth,
        ioDispatcher = UnconfinedTestDispatcher(),
      )

    // Startup: 0 orders fetches
    assertEquals(0, cloudOrders.fetchCount)
    assertEquals(AdminOrdersLoadState.Idle, vm.uiState.value.adminOrdersState)

    // Opening Admin entry & Cloud Sign-In (step 1): 0 orders fetches
    vm.prepareAdminEntry()
    vm.signInAdminCloud("adeel.arif0786@gmail.com", "secret")
    assertEquals("adeel.arif0786@gmail.com", vm.uiState.value.cloudAdminEmail)
    assertEquals(0, cloudOrders.fetchCount)

    // Setting up / unlocking local password (step 2): STILL 0 orders fetches
    assertEquals(PasswordActionResult.Success, vm.setupAdminPassword("OwnerPass99", "OwnerPass99"))
    assertTrue(vm.uiState.value.isAdminUnlocked)
    assertEquals(0, cloudOrders.fetchCount)

    // Auth sign-out: 0 orders fetches
    vm.signOutAdminCloud()
    assertEquals(0, cloudOrders.fetchCount)

    // Sign back in and unlock
    vm.signInAdminCloud("adeel.arif0786@gmail.com", "secret")
    assertTrue(vm.unlockAdmin("OwnerPass99"))
    assertEquals(0, cloudOrders.fetchCount)

    // Opening Orders tab -> triggers 1 fetch
    vm.onAdminOrdersTabOpened()
    assertEquals(1, cloudOrders.fetchCount)
    assertEquals(1, vm.uiState.value.loadedAdminOrdersCount)

    // Tapping Refresh -> triggers 2nd fetch
    vm.refreshAdminOrders()
    assertEquals(2, cloudOrders.fetchCount)
  }

  @Test
  fun contentUriIsNeverSavedByAdminSaveProduct_andPickedCloudinaryUrlsAreCombinedFirst() {
    val (repo, cloudProducts) = createTestProductRepository()
    val vm =
      StoreViewModel(
        repository = repo,
        imageUploadService =
          object : ImageUploadService {
            override suspend fun uploadGalleryUri(localUriString: String): Result<String> {
              return Result.success("https://res.cloudinary.com/mlfzivam/image/upload/v123/picked.jpg")
            }
          },
        ioDispatcher = UnconfinedTestDispatcher(),
      )

    vm.startPickedPhotoUpload("content://media/external/images/media/44")
    val picked = vm.uiState.value.pickedPhotos.single()
    assertTrue(picked.status is PickedPhotoStatus.Uploaded)
    val uploadedUrl = (picked.status as PickedPhotoStatus.Uploaded).secureUrl

    vm.saveAdminProduct(
      editingProductId = null,
      name = "Luxury Mink Blanket",
      pricePkr = 4500,
      category = "Winter Throws",
      fabric = "Mink",
      size = "Double Bed",
      description = "Soft embossed blanket",
      imageKey = "blanket_plain_blue",
      typedImageUrls =
        listOf(
          "content://media/external/images/media/999", // must be stripped!
          "https://res.cloudinary.com/mlfzivam/image/upload/v123/typed.jpg",
        ),
      uploadedImageUrls = listOf(uploadedUrl),
      inStock = true,
      stockQuantity = 15,
    )

    val saved = cloudProducts.upsertedProducts.last()
    assertEquals("Winter Throws", saved.category)
    assertFalse(saved.imageUris.any { it.startsWith("content://") })
    assertEquals(
      listOf(
        "https://res.cloudinary.com/mlfzivam/image/upload/v123/picked.jpg",
        "https://res.cloudinary.com/mlfzivam/image/upload/v123/typed.jpg",
      ),
      saved.imageUris,
    )
    assertEquals("https://res.cloudinary.com/mlfzivam/image/upload/v123/picked.jpg", saved.imageUri)
  }

  @Test
  fun updateCheck_neverRunsOnLaunch_andRunsOnlyOnManualTap() {
    var checkCallCount = 0
    val manualUpdateSource =
      object : AppUpdateDataSource {
        override suspend fun checkForUpdate(currentVersionCode: Int): Result<ManualUpdateCheckResult> {
          checkCallCount++
          return Result.success(
            ManualUpdateCheckResult.UpdateAvailable(
              AppUpdateInfo(
                latestVersionCode = 5,
                apkUrl = "https://gharbazar.pk/gharbazar-v5.apk",
              )
            )
          )
        }
      }

    val (repo, _) = createTestProductRepository()
    val vm =
      StoreViewModel(
        repository = repo,
        appUpdateDataSource = manualUpdateSource,
        currentVersionCode = 1,
        ioDispatcher = UnconfinedTestDispatcher(),
      )

    // Launch must NOT run update check or show any dialog
    assertEquals(0, checkCallCount)
    assertNull(vm.uiState.value.manualUpdateDialogState)

    // Manual tap inside Admin triggers the check
    vm.checkForUpdatesManually()
    assertEquals(1, checkCallCount)
    val state = vm.uiState.value.manualUpdateDialogState
    assertTrue(state is ManualUpdateDialogState.Available)
    assertEquals(5, (state as ManualUpdateDialogState.Available).info.latestVersionCode)

    vm.dismissManualUpdateDialog()
    assertNull(vm.uiState.value.manualUpdateDialogState)

    // Pure evaluation checks
    assertEquals(
      ManualUpdateCheckResult.UpToDate,
      evaluateManualUpdateCheck(1, "https://gharbazar.pk/app.apk", currentVersionCode = 1),
    )
    assertEquals(
      ManualUpdateCheckResult.UpToDate,
      evaluateManualUpdateCheck(5, "", currentVersionCode = 1),
    )
  }

  @Test
  fun placeOrder_succeedsLocallyEvenWhenCloudSaveFails_andRetryWorks() {
    val cloudOrders = FakeCloudOrders(failNextSave = true)
    val (repo, _) = createTestProductRepository()
    val vm =
      StoreViewModel(
        repository = repo,
        orderRepository = OrderRepository(cloudOrders),
        ioDispatcher = UnconfinedTestDispatcher(),
      )

    vm.addToCart(ProductCatalog.products[0], quantity = 2)
    vm.updateCustomerName("Sadia Bibi")
    vm.updatePhoneNumber("03017654321")
    vm.updateDeliveryAddress("House 9, Askari 11")
    vm.updateCity("Rawalpindi")

    assertTrue(vm.placeOrder())
    assertTrue(vm.uiState.value.cartItems.isEmpty())
    assertNotNull(vm.uiState.value.lastConfirmedOrder)
    assertTrue(vm.uiState.value.orderSyncState is OrderSyncState.Failed)

    // Retry when network recovers
    cloudOrders.failNextSave = false
    vm.retryOrderSync()
    assertEquals(OrderSyncState.Saved, vm.uiState.value.orderSyncState)
  }

  private class FakeCloudProducts(initial: List<Product>) : CloudProductDataSource {
    override val isConfigured: Boolean = true
    private val flow =
      MutableStateFlow<CloudProductsSnapshot>(
        CloudProductsSnapshot.Loaded(initial, isFromCache = false)
      )
    val upsertedProducts = mutableListOf<Product>()

    override fun observeProducts(): Flow<CloudProductsSnapshot> = flow

    override suspend fun fetchProductsOnce(): CloudProductsSnapshot = flow.value

    override suspend fun upsertProduct(product: Product): Result<Unit> {
      upsertedProducts.add(product)
      return Result.success(Unit)
    }

    override suspend fun deleteProduct(productId: String): Result<Unit> = Result.success(Unit)
  }

  private class FakeCloudOrders(
    private val ordersToReturn: List<ConfirmedOrder> = emptyList(),
    var failNextSave: Boolean = false,
  ) : CloudOrderDataSource {
    override val isConfigured: Boolean = true
    var fetchCount = 0

    override suspend fun fetchOrders(): Result<List<ConfirmedOrder>> {
      fetchCount++
      return Result.success(ordersToReturn)
    }

    override suspend fun fetchMyOrders(userId: String): Result<List<ConfirmedOrder>> {
      return Result.success(ordersToReturn.filter { it.userId == userId })
    }

    override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
      return if (failNextSave) {
        Result.failure(RuntimeException("Network timeout"))
      } else {
        Result.success(Unit)
      }
    }

    override suspend fun updateOrderStatus(documentId: String, status: OrderStatus): Result<Unit> {
      return Result.success(Unit)
    }
  }

  private class FakeAdminAuth : AdminAuthDataSource {
    private var email: String? = null
    override fun currentUserEmail(): String? = email

    override suspend fun signIn(email: String, password: String): Result<String> {
      this.email = email.trim()
      return Result.success(email.trim())
    }

    override fun signOut() {
      email = null
    }
  }

  private class FakePasswordStorage : PasswordStorage {
    private var salt: String? = null
    private var hash: String? = null
    override fun hasPassword(): Boolean = !salt.isNullOrBlank() && !hash.isNullOrBlank()
    override fun getSaltBase64(): String? = salt
    override fun getHashBase64(): String? = hash
    override fun saveCredentials(saltBase64: String, hashBase64: String) {
      salt = saltBase64
      hash = hashBase64
    }
  }
}
