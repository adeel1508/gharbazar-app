package com.example

import com.example.data.CloudOrderDataSource
import com.example.data.CloudOrdersSnapshot
import com.example.data.FirestoreOrderDataSource.Companion.toFirestoreOrderMap
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.model.CartItem
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.ProductCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        timestamp = 1764000000000L,
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
    assertEquals(1764000000000L, map["timestamp"])
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
    assertEquals(ProductCategory.wireValue(product.category), productMap["category"])
    assertEquals(product.fabric, productMap["fabric"])
    assertEquals(product.size, productMap["size"])
    assertEquals(product.description, productMap["description"])
  }

  @Test
  fun observeOrders_sortsNewestFirst_andUpdatesStatusProgression() = runTest {
    val olderOrder =
      ConfirmedOrder(
        orderNumber = "GB-100001",
        customerName = "Ali",
        phoneNumber = "03001111111",
        deliveryAddress = "Street 1",
        city = "Karachi",
        items = listOf(CartItem(ProductCatalog.products[0], 1)),
        totalPkr = 1949,
        timestamp = 1000L,
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
        timestamp = 5000L,
        status = OrderStatus.PLACED,
      )

    val fakeCloud = FakeCloudOrderDataSource(initialOrders = listOf(olderOrder, newerOrder))
    val repo = OrderRepository(fakeCloud, scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + UnconfinedTestDispatcher()))

    val observed = repo.orders.value
    assertEquals(listOf("GB-200002", "GB-100001"), observed.map { it.orderNumber })

    val steps =
      listOf(
        OrderStatus.CONFIRMED,
        OrderStatus.SHIPPED,
        OrderStatus.DELIVERED,
      )
    steps.forEach { nextStatus ->
      val result = repo.updateOrderStatus("GB-200002", nextStatus)
      assertTrue(result.isSuccess)
      assertEquals(nextStatus, repo.orders.value.first { it.orderNumber == "GB-200002" }.status)
      assertEquals(nextStatus, fakeCloud.lastUpdatedStatus["GB-200002"])
    }
  }

  @Test
  fun refreshOrders_recoversAfterInitialPermissionDenied() = runTest {
    // Reproduces the live "0 orders" bug: the orders listener attached
    // before the admin signed in failed with PERMISSION_DENIED and the
    // SDK removed it, so the app never saw the orders that exist in
    // Firestore — even after sign-in. Re-subscribing must recover them.
    // NOTE: the re-subscribe is triggered ONLY by opening the Orders tab
    // (or its Refresh button) — never on startup or on auth-state change
    // (that was the v1.0.12 design, which crashed on launch and was
    // reverted). This test covers the repository half; the ViewModel
    // tests lock in that an auth-state change does not refresh.
    val liveOrder =
      ConfirmedOrder(
        orderNumber = "GB-641124",
        customerName = "Farzi Test",
        phoneNumber = "03001234567",
        deliveryAddress = "Test Address, Gulzar Colony",
        city = "Lahore",
        items = listOf(CartItem(ProductCatalog.products[0], 1)),
        totalPkr = 1949,
        timestamp = 1764000000000L,
        status = OrderStatus.PLACED,
      )
    val flakyCloud = FlakyCloudOrderDataSource(liveOrder)
    val repo =
      OrderRepository(
        flakyCloud,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + UnconfinedTestDispatcher()),
      )

    // First subscription died with PERMISSION_DENIED: no orders.
    assertTrue(repo.orders.value.isEmpty())
    assertEquals(1, flakyCloud.subscriptions)

    // Even when the cloud feed later holds the order, the dead listener
    // never delivers it.
    flakyCloud.publishLoaded(listOf(liveOrder))
    assertTrue(repo.orders.value.isEmpty())

    // Re-subscribing after sign-in recovers the orders.
    repo.refreshOrders()
    assertEquals(listOf("GB-641124"), repo.orders.value.map { it.orderNumber })
    assertEquals(2, flakyCloud.subscriptions)
  }

  @Test
  fun submitOrder_handlesCloudFailureGracefullyWithoutThrowing() = runTest {
    val failingCloud =
      FakeCloudOrderDataSource(
        shouldFailSaves = true,
      )
    val repo =
      OrderRepository(
        failingCloud,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + UnconfinedTestDispatcher()),
      )

    val order =
      ConfirmedOrder(
        orderNumber = "GB-777888",
        customerName = "Hamza Khan",
        phoneNumber = "03331234567",
        deliveryAddress = "Sector G-9/3",
        city = "Islamabad",
        items = listOf(CartItem(ProductCatalog.products[2], 1)),
        totalPkr = 2599,
        timestamp = 9000L,
        status = OrderStatus.PLACED,
      )

    val result = repo.submitOrder(order)
    assertTrue(result.isFailure)
    assertEquals(1, repo.orders.value.size)
    assertEquals("GB-777888", repo.orders.value.first().orderNumber)
  }

  /**
   * Simulates the live sign-in race: the FIRST orders subscription fails
   * with PERMISSION_DENIED (listener attached before the admin signed in)
   * and never emits again; later subscriptions get the live feed.
   */
  private class FlakyCloudOrderDataSource(
    private val order: ConfirmedOrder,
  ) : CloudOrderDataSource {
    override val isConfigured: Boolean = true
    var subscriptions = 0
    private val snapshots =
      MutableStateFlow<CloudOrdersSnapshot>(
        CloudOrdersSnapshot.Loaded(listOf(order), false)
      )

    override fun observeOrders(): Flow<CloudOrdersSnapshot> {
      subscriptions++
      return if (subscriptions == 1) {
        flowOf(CloudOrdersSnapshot.Unavailable("PERMISSION_DENIED"))
      } else {
        snapshots
      }
    }

    fun publishLoaded(orders: List<ConfirmedOrder>) {
      snapshots.value = CloudOrdersSnapshot.Loaded(orders, false)
    }

    override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> =
      Result.success(Unit)

    override suspend fun updateOrderStatus(
      orderNumber: String,
      status: OrderStatus,
    ): Result<Unit> = Result.success(Unit)
  }

  private class FakeCloudOrderDataSource(
    initialOrders: List<ConfirmedOrder> = emptyList(),
    private val shouldFailSaves: Boolean = false,
  ) : CloudOrderDataSource {
    override val isConfigured: Boolean = true
    private val snapshotFlow =
      MutableStateFlow<CloudOrdersSnapshot>(
        if (initialOrders.isEmpty()) CloudOrdersSnapshot.Empty
        else CloudOrdersSnapshot.Loaded(initialOrders.sortedByDescending { it.timestamp }, false)
      )
    val lastUpdatedStatus = mutableMapOf<String, OrderStatus>()

    override fun observeOrders(): Flow<CloudOrdersSnapshot> = snapshotFlow

    override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> {
      if (shouldFailSaves) {
        return Result.failure(IllegalStateException("Simulated offline error"))
      }
      val current = (snapshotFlow.value as? CloudOrdersSnapshot.Loaded)?.orders.orEmpty()
      snapshotFlow.value =
        CloudOrdersSnapshot.Loaded(
          orders = (listOf(order) + current).sortedByDescending { it.timestamp },
          isFromCache = false,
        )
      return Result.success(Unit)
    }

    override suspend fun updateOrderStatus(orderNumber: String, status: OrderStatus): Result<Unit> {
      lastUpdatedStatus[orderNumber] = status
      val current = (snapshotFlow.value as? CloudOrdersSnapshot.Loaded)?.orders.orEmpty()
      snapshotFlow.value =
        CloudOrdersSnapshot.Loaded(
          orders =
            current.map {
              if (it.orderNumber == orderNumber) it.copy(status = status) else it
            },
          isFromCache = false,
        )
      return Result.success(Unit)
    }
  }
}
