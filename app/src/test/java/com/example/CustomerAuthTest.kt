package com.example

import com.example.data.CloudOrderDataSource
import com.example.data.CustomerAccount
import com.example.data.CustomerAuthDataSource
import com.example.data.CustomerOrdersLoadState
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.data.isAdminAccount
import com.example.data.validateCustomerCredentials
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.viewmodel.OrderSyncState
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerAuthTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  // --- Pure validation / admin-account guard (same functions the Firebase
  // implementation uses, so the guard is tested without needing Firebase) ---

  @Test
  fun isAdminAccount_matchesOnlyTheSellerEmail() {
    assertTrue(isAdminAccount("adeel.arif0786@gmail.com"))
    assertTrue(isAdminAccount("ADEEL.ARIF0786@GMAIL.COM"))
    assertTrue(isAdminAccount("  adeel.arif0786@gmail.com  "))
    assertFalse(isAdminAccount("customer@gmail.com"))
    assertFalse(isAdminAccount("adeel.arif0786@yahoo.com"))
    assertFalse(isAdminAccount(null))
    assertFalse(isAdminAccount(""))
  }

  @Test
  fun validateCustomerCredentials_rejectsBadInputAndAcceptsGoodInput() {
    assertNotNull(validateCustomerCredentials("", "123456"))
    assertNotNull(validateCustomerCredentials("not-an-email", "123456"))
    assertNotNull(validateCustomerCredentials("a@b", "123456"))
    assertNotNull(validateCustomerCredentials("a@b.com", "12345"))
    assertNotNull(validateCustomerCredentials("a@b.com", "123456", requireName = ""))
    assertNotNull(validateCustomerCredentials("a@b.com", "123456", requireName = "   "))
    assertNull(validateCustomerCredentials("a@b.com", "123456"))
    assertNull(validateCustomerCredentials("ayesha@gmail.com", "secret99", requireName = "Ayesha"))
  }

  // --- ViewModel: sign-up / login / logout ---

  @Test
  fun signUpCustomer_setsAccountAndClearsError() {
    val fakeAuth = FakeCustomerAuth()
    val vm = createVm(fakeAuth = fakeAuth)

    vm.signUpCustomer("Ayesha Khan", "ayesha@gmail.com", "secret99")

    val account = vm.uiState.value.customerAccount
    assertNotNull(account)
    assertEquals("ayesha@gmail.com", account!!.email)
    assertEquals("Ayesha Khan", account.displayName)
    assertFalse(vm.uiState.value.isCustomerAuthBusy)
    assertNull(vm.uiState.value.customerAuthError)
  }

  @Test
  fun signInCustomer_failureSetsErrorAndKeepsGuest() {
    val fakeAuth = FakeCustomerAuth(failNext = "Invalid email or password.")
    val vm = createVm(fakeAuth = fakeAuth)

    vm.signInCustomer("ayesha@gmail.com", "wrongpass")

    assertNull(vm.uiState.value.customerAccount)
    assertEquals("Invalid email or password.", vm.uiState.value.customerAuthError)
    assertFalse(vm.uiState.value.isCustomerAuthBusy)
  }

  @Test
  fun signInCustomer_withAdminEmail_isRejectedAsCustomer() {
    val fakeAuth = FakeCustomerAuth()
    val vm = createVm(fakeAuth = fakeAuth)

    vm.signInCustomer("adeel.arif0786@gmail.com", "whatever99")

    assertNull(vm.uiState.value.customerAccount)
    assertNotNull(vm.uiState.value.customerAuthError)
    assertTrue(
      vm.uiState.value.customerAuthError!!.contains("Seller / Admin")
    )
  }

  @Test
  fun signOutCustomer_clearsAccountAndMyOrders() {
    val fakeAuth = FakeCustomerAuth()
    val cloudOrders = FakeCustomerOrders()
    val vm = createVm(fakeAuth = fakeAuth, cloudOrders = cloudOrders)

    vm.signInCustomer("ayesha@gmail.com", "secret99")
    assertNotNull(vm.uiState.value.customerAccount)
    vm.onMyOrdersOpened()
    assertTrue(vm.uiState.value.myOrdersState is CustomerOrdersLoadState.Loaded)

    vm.signOutCustomer()

    assertNull(vm.uiState.value.customerAccount)
    assertEquals(CustomerOrdersLoadState.Idle, vm.uiState.value.myOrdersState)
    assertNull(fakeAuth.currentCustomer())
  }

  @Test
  fun prepareCustomerEntry_restoresSignedInCustomerFromSession() {
    val fakeAuth =
      FakeCustomerAuth(
        signedIn =
          CustomerAccount(
            uid = "uid_ayesha",
            email = "ayesha@gmail.com",
            displayName = "Ayesha Khan",
          )
      )
    val vm = createVm(fakeAuth = fakeAuth)

    vm.prepareCustomerEntry()

    assertEquals("uid_ayesha", vm.uiState.value.customerAccount?.uid)
    assertEquals("ayesha@gmail.com", vm.uiState.value.customerAccount?.email)
  }

  // --- ViewModel: orders link to the signed-in account, guests stay guests ---

  @Test
  fun placeOrder_attachesUserIdWhenSignedIn_andStaysGuestWhenNot() {
    val fakeAuth = FakeCustomerAuth()
    val cloudOrders = FakeCustomerOrders()
    val vm = createVm(fakeAuth = fakeAuth, cloudOrders = cloudOrders)

    vm.signInCustomer("ayesha@gmail.com", "secret99")
    placeFullTestOrder(vm)

    val signedInOrder = vm.uiState.value.lastConfirmedOrder
    assertNotNull(signedInOrder)
    assertEquals("uid_ayesha@gmail.com", signedInOrder!!.userId)
    assertEquals("uid_ayesha@gmail.com", cloudOrders.savedOrders.single().userId)
    assertEquals(OrderSyncState.Saved, vm.uiState.value.orderSyncState)

    // Guest: a fresh session without login places an order with NO userId.
    val guestVm = createVm(cloudOrders = FakeCustomerOrders())
    placeFullTestOrder(guestVm)
    assertNull(guestVm.uiState.value.lastConfirmedOrder!!.userId)
  }

  @Test
  fun myOrders_refreshOnlyWhenSignedIn_andShowsOnlyOwnOrders() {
    val ownOlder =
      ConfirmedOrder(
        orderNumber = "GB-100001",
        customerName = "Ayesha",
        phoneNumber = "03001111111",
        deliveryAddress = "Street 1",
        city = "Lahore",
        items = emptyList(),
        totalPkr = 1949,
        timestampMillis = 1000L,
        status = OrderStatus.PLACED,
        userId = "uid_ayesha@gmail.com",
      )
    val ownNewer =
      ownOlder.copy(
        orderNumber = "GB-200002",
        totalPkr = 1999,
        timestampMillis = 5000L,
        status = OrderStatus.DELIVERED,
      )
    val someoneElses =
      ownOlder.copy(
        orderNumber = "GB-300003",
        customerName = "Bilal",
        timestampMillis = 9000L,
        userId = "uid_bilal",
      )
    val cloudOrders =
      FakeCustomerOrders(storedOrders = mutableListOf(ownOlder, ownNewer, someoneElses))
    val fakeAuth = FakeCustomerAuth()
    val vm = createVm(fakeAuth = fakeAuth, cloudOrders = cloudOrders)

    // Guest: opening My Orders fetches nothing.
    vm.onMyOrdersOpened()
    assertEquals(0, cloudOrders.fetchMyOrdersCount)
    assertEquals(CustomerOrdersLoadState.Idle, vm.uiState.value.myOrdersState)

    // Signed in: one fetch, only the customer's own orders, newest first.
    vm.signInCustomer("ayesha@gmail.com", "secret99")
    vm.onMyOrdersOpened()
    assertEquals(1, cloudOrders.fetchMyOrdersCount)
    val loaded = vm.uiState.value.myOrdersState as CustomerOrdersLoadState.Loaded
    assertEquals(listOf("GB-200002", "GB-100001"), loaded.orders.map { it.orderNumber })

    // Refresh tap fetches again.
    vm.refreshMyOrders()
    assertEquals(2, cloudOrders.fetchMyOrdersCount)
  }

  private fun createVm(
    fakeAuth: CustomerAuthDataSource = FakeCustomerAuth(),
    cloudOrders: FakeCustomerOrders = FakeCustomerOrders(),
  ): StoreViewModel {
    return StoreViewModel(
      orderRepository = OrderRepository(cloudOrders),
      customerAuthDataSource = fakeAuth,
      ioDispatcher = UnconfinedTestDispatcher(),
    )
  }

  private fun placeFullTestOrder(vm: StoreViewModel) {
    vm.addToCart(ProductCatalog.products[0], quantity = 1)
    vm.updateCustomerName("Ayesha Khan")
    vm.updatePhoneNumber("03001234567")
    vm.updateDeliveryAddress("House 3, DHA")
    vm.updateCity("Lahore")
    assertTrue(vm.placeOrder())
  }

  /**
   * In-memory customer auth. Mirrors the real Firebase implementation's
   * contract, including the seller-email guard ([isAdminAccount]) and the
   * shared validation ([validateCustomerCredentials]).
   */
  private class FakeCustomerAuth(
    var signedIn: CustomerAccount? = null,
    var failNext: String? = null,
  ) : CustomerAuthDataSource {
    override fun currentCustomer(): CustomerAccount? = signedIn

    override suspend fun signUp(
      name: String,
      email: String,
      password: String,
    ): Result<CustomerAccount> {
      failNext?.let { return Result.failure(IllegalStateException(it)) }
      val cleanName = name.trim()
      val cleanEmail = email.trim()
      validateCustomerCredentials(cleanEmail, password, requireName = cleanName)?.let {
        return Result.failure(IllegalArgumentException(it))
      }
      if (isAdminAccount(cleanEmail)) {
        return Result.failure(
          IllegalArgumentException(
            "This email is the seller account. Please use the Seller / Admin panel."
          )
        )
      }
      val account =
        CustomerAccount(
          uid = "uid_$cleanEmail",
          email = cleanEmail,
          displayName = cleanName,
        )
      signedIn = account
      return Result.success(account)
    }

    override suspend fun signIn(email: String, password: String): Result<CustomerAccount> {
      failNext?.let { return Result.failure(IllegalStateException(it)) }
      val cleanEmail = email.trim()
      validateCustomerCredentials(cleanEmail, password)?.let {
        return Result.failure(IllegalArgumentException(it))
      }
      if (isAdminAccount(cleanEmail)) {
        return Result.failure(
          IllegalArgumentException(
            "This email is the seller account. Please use the Seller / Admin panel."
          )
        )
      }
      val account =
        CustomerAccount(uid = "uid_$cleanEmail", email = cleanEmail, displayName = null)
      signedIn = account
      return Result.success(account)
    }

    override fun signOut() {
      signedIn = null
    }
  }

  private class FakeCustomerOrders(
    private val storedOrders: MutableList<ConfirmedOrder> = mutableListOf(),
  ) : CloudOrderDataSource {
    override val isConfigured: Boolean = true
    var fetchMyOrdersCount = 0
    val savedOrders = mutableListOf<ConfirmedOrder>()

    override suspend fun fetchOrders(): Result<List<ConfirmedOrder>> =
      Result.success(storedOrders.toList())

    override suspend fun fetchMyOrders(userId: String): Result<List<ConfirmedOrder>> {
      fetchMyOrdersCount++
      return Result.success(
        storedOrders
          .filter { it.userId == userId }
          .sortedByDescending { it.timestampMillis }
      )
    }

    override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
      savedOrders.add(order)
      return Result.success(Unit)
    }

    override suspend fun updateOrderStatus(
      documentId: String,
      status: OrderStatus,
    ): Result<Unit> = Result.success(Unit)
  }
}
