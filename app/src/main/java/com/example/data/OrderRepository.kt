package com.example.data

import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrderRepository(
  private val cloudDataSource: CloudOrderDataSource,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val _localOrders = MutableStateFlow<List<ConfirmedOrder>>(emptyList())
  private val _orders = MutableStateFlow<List<ConfirmedOrder>>(emptyList())
  val orders: StateFlow<List<ConfirmedOrder>> = _orders.asStateFlow()

  private var cloudOrders: List<ConfirmedOrder>? = null
  private val pendingLocalOrderNumbers = mutableSetOf<String>()

  init {
    observeCloudOrders()
  }

  private fun observeCloudOrders() {
    scope.launch {
      cloudDataSource.observeOrders().collect { snapshot ->
        when (snapshot) {
          is CloudOrdersSnapshot.Loaded -> {
            val syncedIds = snapshot.orders.map { it.orderNumber }.toSet()
            pendingLocalOrderNumbers.removeAll(syncedIds)
            cloudOrders = snapshot.orders
            publishMergedOrders()
          }
          is CloudOrdersSnapshot.Empty -> {
            cloudOrders = emptyList()
            publishMergedOrders()
          }
          is CloudOrdersSnapshot.Unavailable -> {
            publishMergedOrders()
          }
        }
      }
    }
  }

  private fun publishMergedOrders() {
    val currentCloud = cloudOrders
    val merged =
      if (currentCloud != null) {
        val cloudByNumber = currentCloud.associateBy { it.orderNumber }
        val pendingOnly =
          _localOrders.value.filter { local ->
            local.orderNumber in pendingLocalOrderNumbers && local.orderNumber !in cloudByNumber
          }
        (currentCloud + pendingOnly).sortedByDescending { it.timestamp }
      } else {
        _localOrders.value.sortedByDescending { it.timestamp }
      }
    _orders.value = merged
  }

  /**
   * Records the order locally right away and saves to Firestore in the background.
   * Never throws or blocks checkout even if the cloud write fails.
   */
  suspend fun submitOrder(order: ConfirmedOrder): Result<Unit> {
    pendingLocalOrderNumbers.add(order.orderNumber)
    _localOrders.value =
      (listOf(order) + _localOrders.value.filterNot { it.orderNumber == order.orderNumber })
        .sortedByDescending { it.timestamp }
    publishMergedOrders()

    if (!cloudDataSource.isConfigured) {
      return Result.success(Unit)
    }

    return runCatching {
      cloudDataSource.saveOrder(order).getOrThrow()
    }
  }

  suspend fun updateOrderStatus(
    orderNumber: String,
    newStatus: OrderStatus,
  ): Result<Unit> {
    _localOrders.value =
      _localOrders.value.map { order ->
        if (order.orderNumber == orderNumber) order.copy(status = newStatus) else order
      }
    cloudOrders =
      cloudOrders?.map { order ->
        if (order.orderNumber == orderNumber) order.copy(status = newStatus) else order
      }
    publishMergedOrders()

    if (!cloudDataSource.isConfigured) {
      return Result.success(Unit)
    }

    return runCatching {
      cloudDataSource.updateOrderStatus(orderNumber, newStatus).getOrThrow()
    }
  }
}
