package com.example.data

import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AdminOrdersLoadState {
  data object Idle : AdminOrdersLoadState
  data object Loading : AdminOrdersLoadState
  data class Loaded(val orders: List<ConfirmedOrder>) : AdminOrdersLoadState
  data class Error(val message: String) : AdminOrdersLoadState
}

/**
 * Repository for COD orders.
 *
 * Crash-safety rules:
 * - Does NOTHING in `init`: never attaches an orders listener or refreshes orders at app startup
 *   or on auth-state changes.
 * - `refreshOrders()` is invoked ONLY when the admin opens the Orders tab or taps Refresh.
 */
class OrderRepository(
  private val cloudDataSource: CloudOrderDataSource,
) {
  private val _ordersState = MutableStateFlow<AdminOrdersLoadState>(AdminOrdersLoadState.Idle)
  val ordersState: StateFlow<AdminOrdersLoadState> = _ordersState.asStateFlow()

  suspend fun submitGuestOrder(order: ConfirmedOrder): Result<Unit> {
    if (!cloudDataSource.isConfigured) {
      return Result.failure(IllegalStateException("Cloud order service is not configured."))
    }
    return runCatching {
      cloudDataSource.saveOrder(order).getOrThrow()
    }
  }

  suspend fun refreshOrders(): Result<List<ConfirmedOrder>> {
    _ordersState.value = AdminOrdersLoadState.Loading
    val result = runCatching { cloudDataSource.fetchOrders().getOrThrow() }
    return result
      .onSuccess { fetched ->
        val sorted = fetched.sortedByDescending { it.timestampMillis }
        _ordersState.value = AdminOrdersLoadState.Loaded(sorted)
      }
      .onFailure { err ->
        _ordersState.value =
          AdminOrdersLoadState.Error(
            err.localizedMessage?.takeIf { it.isNotBlank() }
              ?: "Couldn't load orders, tap Refresh."
          )
      }
  }

  fun resetOrdersState() {
    _ordersState.value = AdminOrdersLoadState.Idle
  }

  suspend fun updateOrderStatus(
    documentId: String,
    newStatus: OrderStatus,
  ): Result<Unit> {
    val result = runCatching {
      cloudDataSource.updateOrderStatus(documentId, newStatus).getOrThrow()
    }
    if (result.isSuccess) {
      val current = _ordersState.value
      if (current is AdminOrdersLoadState.Loaded) {
        _ordersState.value =
          AdminOrdersLoadState.Loaded(
            current.orders.map { order ->
              if (order.documentId == documentId || order.orderNumber == documentId) {
                order.copy(status = newStatus)
              } else {
                order
              }
            }
          )
      }
    }
    return result
  }
}
