package com.example

import com.example.data.AdminOrdersLoadState
import com.example.data.CloudOrderDataSource
import com.example.data.CustomerOrdersLoadState
import com.example.data.FirestoreOrderDataSource.Companion.resolveOrderTimestampMillis
import com.example.data.FirestoreOrderDataSource.Companion.toFirestoreOrderMap
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.model.CartItem
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.ProductCategories
import com.google.firebase.Timestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderRepositoryTest {

  @Test
  fun firestoreOrderPayload_hasExactRequiredFieldsAndNoUserId() {
    val product = ProductCatalog.products[0]
    val order =
      ConfirmedOrder(
        orderNumber = "GB-654321",
        customerName = "Usman Raza",
        phoneNumber = "03009876543",
        deliveryAddress = "House 14, Block C, Model Town",
        city = "Lahore",
        items = listOf(CartItem(product = product, quantity = 2)),
        totalPkr = product.pricePkr * 2,
        timestampIso = "2026-04-01T12:00:00Z",
        timestampMillis = 1775044800000L,
        status = OrderStatus.PLACED,
      )

    val map = order.toFirestoreOrderMap()

    assertEquals(
      setOf(
        "orderNumber",
        "customerName",
        "phoneNumber",
        "deliveryAddress",
        "city",
        "items",
        "totalPkr",
        "timestamp",
        "status",
      ),
      map.keys,
    )
    assertFalse("Guest orders must NOT include userId", map.containsKey("userId"))
    assertEquals("GB-654321", map["orderNumber"])
    assertEquals("Usman Raza", map["customerName"])
    assertEquals("03009876543", map["phoneNumber"])
    assertEquals("House 14, Block C, Model Town", map["deliveryAddress"])
    assertEquals("Lahore", map["city"])
    assertEquals(product.pricePkr * 2, map["totalPkr"])
    assertEquals("2026-04-01T12:00:00Z", map["timestamp"])
    assertEquals("placed", map["status"])

    @Suppress("UNCHECKED_CAST")
    val itemsList = map["items"] as List<Map<String, Any>>
    assertEquals(1, itemsList.size)
    assertEquals(2, itemsList.first()["quantity"])
    @Suppress("UNCHECKED_CAST")
    val productMap = itemsList.first()["product"] as Map<String, Any>
    assertEquals(product.id, productMap["id"])
    assertEquals(product.name, productMap["name"])
    assertEquals(product.pricePkr, productMap["pricePkr"])
    assertEquals(ProductCategories.BLANKETS, productMap["category"])
    assertEquals(product.fabric, productMap["fabric"])
    assertEquals(product.size, productMap["size"])
    assertEquals(product.description, productMap["description"])
  }

  @Test
  fun firestoreOrderPayload_includesUserIdWhenSignedIn() {
    val product = ProductCatalog.products[0]
    val order =
      ConfirmedOrder(
        orderNumber = "GB-777777",
        customerName = "Ayesha Khan",
        phoneNumber = "03001234567",
        deliveryAddress = "House 3, DHA",
        city = "Lahore",
        items = listOf(CartItem(product = product, quantity = 1)),
        totalPkr = product.pricePkr,
        timestampIso = "2026-04-01T12:00:00Z",
        timestampMillis = 1775044800000L,
        status = OrderStatus.PLACED,
        userId = "uid_customer_1",
      )

    val map = order.toFirestoreOrderMap()

    assertEquals("uid_customer_1", map["userId"])
    assertEquals("GB-777777", map["orderNumber"])
  }

  @Test
  fun refreshMyOrders_returnsOnlyOwnOrdersNewestFirstAndNeverFetchesOnInit() = runTest {
    val ownOlder =
      ConfirmedOrder(
        orderNumber = "GB-100001",
        customerName = "Ayesha",
        phoneNumber = "03001111111",
        deliveryAddress = "Street 1",
        city = "Lahore",
        items = listOf(CartItem(ProductCatalog.products[0], 1)),
        totalPkr = 1949,
        timestampMillis = 1000L,
        status = OrderStatus.PLACED,
        userId = "uid_ayesha",
      )
    val ownNewer =
      ConfirmedOrder(
        orderNumber = "GB-200002",
        customerName = "Ayesha",
        phoneNumber = "03001111111",
        deliveryAddress = "Street 1",
        city = "Lahore",
        items = listOf(CartItem(ProductCatalog.products[4], 1)),
        totalPkr = 1999,
        timestampMillis = 5000L,
        status = OrderStatus.DELIVERED,
        userId = "uid_ayesha",
      )
    val someoneElses =
      ConfirmedOrder(
        orderNumber = "GB-300003",
        customerName = "Bilal",
        phoneNumber = "03002222222",
        deliveryAddress = "Street 2",
        city = "Karachi",
        items = listOf(CartItem(ProductCatalog.products[0], 1)),
        totalPkr = 1949,
        timestampMillis = 9000L,
        status = OrderStatus.PLACED,
        userId = "uid_bilal",
      )
    val guestOrder =
      ConfirmedOrder(
        orderNumber = "GB-400004",
        customerName = "Guest",
        phoneNumber = "03003333333",
        deliveryAddress = "Street 3",
        city = "Islamabad",
        items = listOf(CartItem(ProductCatalog.products[0], 1)),
        totalPkr = 1949,
        timestampMillis = 8000L,
        status = OrderStatus.PLACED,
        userId = null,
      )

    val fakeCloud =
      FakeCloudOrderDataSource(
        storedOrders = mutableListOf(ownOlder, ownNewer, someoneElses, guestOrder)
      )
    val repo = OrderRepository(fakeCloud)

    // Never fetches on init — neither admin nor customer orders.
    assertEquals(0, fakeCloud.fetchCallCount)
    assertEquals(0, fakeCloud.fetchMyOrdersCallCount)
    assertEquals(CustomerOrdersLoadState.Idle, repo.myOrdersState.value)

    val refreshResult = repo.refreshMyOrders("uid_ayesha")
    assertTrue(refreshResult.isSuccess)
    assertEquals(1, fakeCloud.fetchMyOrdersCallCount)
    assertEquals(0, fakeCloud.fetchCallCount)
    val loaded = (repo.myOrdersState.value as CustomerOrdersLoadState.Loaded).orders
    assertEquals(listOf("GB-200002", "GB-100001"), loaded.map { it.orderNumber })
  }

  @Test
  fun refreshMyOrders_exposesErrorStateOnFailure() = runTest {
    val failingCloud = FakeCloudOrderDataSource(shouldFailFetches = true)
    val repo = OrderRepository(failingCloud)

    val result = repo.refreshMyOrders("uid_ayesha")
    assertTrue(result.isFailure)
    assertTrue(repo.myOrdersState.value is CustomerOrdersLoadState.Error)
  }

  @Test
  fun timestampParser_handlesIsoStringAndFirestoreTimestampDefensively() {
    val isoMillis = resolveOrderTimestampMillis("2026-04-01T12:00:00Z", null)
    assertTrue(isoMillis > 0L)

    val firestoreTs = Timestamp(1775044800L, 0)
    val fallbackFromCreatedAt = resolveOrderTimestampMillis("not-a-date", firestoreTs)
    assertEquals(1775044800L * 1000L, fallbackFromCreatedAt)

    val missingBoth = resolveOrderTimestampMillis(null, null)
    assertEquals(0L, missingBoth)
  }

  @Test
  fun refreshOrders_doesNotRunInInit_andSortsNewestFirstAndUpdatesStatus() = runTest {
    val olderOrder =
      ConfirmedOrder(
        orderNumber = "GB-100001",
        customerName = "Ali",
        phoneNumber = "03001111111",
        deliveryAddress = "Street 1",
        city = "Karachi",
        items = listOf(CartItem(ProductCatalog.products[0], 1)),
        totalPkr = 1949,
        timestampMillis = 1000L,
        status = OrderStatus.PLACED,
      )
    val newerOrder =
      ConfirmedOrder(
        orderNumber = "GB-200002",
        customerName = "Bilal",
        phoneNumber = "03002222222",
        deliveryAddress = "Street 2",
        city = "Islamabad",
        items = listOf(CartItem(ProductCatalog.products[4], 1)),
        totalPkr = 1999,
        timestampMillis = 5000L,
        status = OrderStatus.PLACED,
      )

    val fakeCloud = FakeCloudOrderDataSource(storedOrders = mutableListOf(olderOrder, newerOrder))
    val repo = OrderRepository(fakeCloud)

    // Never fetches on init
    assertEquals(0, fakeCloud.fetchCallCount)
    assertEquals(AdminOrdersLoadState.Idle, repo.ordersState.value)

    // Explicit manual refresh loads and sorts newest first
    val refreshResult = repo.refreshOrders()
    assertTrue(refreshResult.isSuccess)
    assertEquals(1, fakeCloud.fetchCallCount)
    val loaded = (repo.ordersState.value as AdminOrdersLoadState.Loaded).orders
    assertEquals(listOf("GB-200002", "GB-100001"), loaded.map { it.orderNumber })

    val steps = listOf(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
    steps.forEach { nextStatus ->
      val result = repo.updateOrderStatus("GB-200002", nextStatus)
      assertTrue(result.isSuccess)
      val updatedOrders = (repo.ordersState.value as AdminOrdersLoadState.Loaded).orders
      assertEquals(nextStatus, updatedOrders.first { it.orderNumber == "GB-200002" }.status)
      assertEquals(nextStatus, fakeCloud.lastUpdatedStatus["GB-200002"])
    }
  }

  @Test
  fun refreshOrders_exposesErrorStateOnFailure_insteadOfSilentlyShowingZero() = runTest {
    val failingCloud = FakeCloudOrderDataSource(shouldFailFetches = true)
    val repo = OrderRepository(failingCloud)

    val result = repo.refreshOrders()
    assertTrue(result.isFailure)
    assertTrue(repo.ordersState.value is AdminOrdersLoadState.Error)
  }

  private class FakeCloudOrderDataSource(
    private val storedOrders: MutableList<ConfirmedOrder> = mutableListOf(),
    private val shouldFailFetches: Boolean = false,
    private val shouldFailSaves: Boolean = false,
  ) : CloudOrderDataSource {
    override val isConfigured: Boolean = true
    var fetchCallCount = 0
    var fetchMyOrdersCallCount = 0
    val lastUpdatedStatus = mutableMapOf<String, OrderStatus>()

    override suspend fun fetchOrders(): Result<List<ConfirmedOrder>> {
      fetchCallCount++
      if (shouldFailFetches) {
        return Result.failure(IllegalStateException("Permission denied or offline"))
      }
      return Result.success(storedOrders.sortedByDescending { it.timestampMillis })
    }

    override suspend fun fetchMyOrders(userId: String): Result<List<ConfirmedOrder>> {
      fetchMyOrdersCallCount++
      if (shouldFailFetches) {
        return Result.failure(IllegalStateException("Permission denied or offline"))
      }
      return Result.success(
        storedOrders
          .filter { it.userId == userId }
          .sortedByDescending { it.timestampMillis }
      )
    }

    override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
      if (shouldFailSaves) {
        return Result.failure(IllegalStateException("Simulated offline error"))
      }
      storedOrders.add(order)
      return Result.success(Unit)
    }

    override suspend fun updateOrderStatus(documentId: String, status: OrderStatus): Result<Unit> {
      lastUpdatedStatus[documentId] = status
      for (i in storedOrders.indices) {
        val current = storedOrders[i]
        if (current.documentId == documentId || current.orderNumber == documentId) {
          storedOrders[i] = current.copy(status = status)
        }
      }
      return Result.success(Unit)
    }
  }
}
