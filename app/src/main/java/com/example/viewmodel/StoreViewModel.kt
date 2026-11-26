package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AdminAuthDataSource
import com.example.data.AdminOrdersLoadState
import com.example.data.AppUpdateDataSource
import com.example.data.AppUpdateInfo
import com.example.data.CartStore
import com.example.data.CustomerAccount
import com.example.data.CustomerAuthDataSource
import com.example.data.CustomerOrdersLoadState
import com.example.data.FirebaseCustomerAuthDataSource
import com.example.data.CatalogSyncStatus
import com.example.data.CloudOrderDataSource
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.CloudinaryImageUploader
import com.example.data.FirebaseAdminAuthDataSource
import com.example.data.FirestoreAppUpdateDataSource
import com.example.data.FirestoreOrderDataSource
import com.example.data.FirestoreProductDataSource
import com.example.data.ImageUploadService
import com.example.data.ManualUpdateCheckResult
import com.example.data.NoOpCartStore
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.data.SavedCartEntry
import com.example.data.SharedPreferencesCartStore
import com.example.model.CartItem
import com.example.model.CheckoutFormState
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategories
import com.example.model.ProductSpec
import com.example.model.SortOption
import com.example.model.currentIsoTimestamp
import com.example.model.resolveGalleryImageUrls
import com.example.model.sanitizeProductForCloudSave
import com.example.security.AdminPasswordManager
import com.example.security.EncryptedPasswordStorage
import com.example.security.PasswordActionResult
import com.example.security.PasswordStorage
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OrderSyncState {
  data object Idle : OrderSyncState
  data object Saving : OrderSyncState
  data object Saved : OrderSyncState
  data class Failed(val message: String) : OrderSyncState
}

sealed interface PickedPhotoStatus {
  data object Uploading : PickedPhotoStatus
  data class Uploaded(val secureUrl: String) : PickedPhotoStatus
  data class Failed(val errorMessage: String) : PickedPhotoStatus
}

data class PickedPhotoItem(
  val id: String,
  val localUri: String,
  val status: PickedPhotoStatus,
)

sealed interface ManualUpdateDialogState {
  data class Available(val info: AppUpdateInfo) : ManualUpdateDialogState
  data object UpToDate : ManualUpdateDialogState
  data class Failed(val message: String) : ManualUpdateDialogState
}

data class StoreUiState(
  val allProducts: List<Product> = emptyList(),
  val filteredProducts: List<Product> = emptyList(),
  val availableCategories: List<String> =
    listOf(ProductCategories.ALL, ProductCategories.BLANKETS, ProductCategories.MENS_SUITS),
  val searchQuery: String = "",
  val selectedCategory: String = ProductCategories.ALL,
  val sortOption: SortOption = SortOption.FEATURED,
  val cartItems: List<CartItem> = emptyList(),
  val checkoutForm: CheckoutFormState = CheckoutFormState(),
  val lastConfirmedOrder: ConfirmedOrder? = null,
  val orderSyncState: OrderSyncState = OrderSyncState.Idle,
  val syncStatus: CatalogSyncStatus = CatalogSyncStatus.Loading,
  // Admin cloud auth (step 1) & local lock (step 2)
  val cloudAdminEmail: String? = null,
  val isCloudAuthBusy: Boolean = false,
  val cloudAuthError: String? = null,
  val isAdminPasswordSet: Boolean = false,
  val isAdminPasswordChecked: Boolean = false,
  val isAdminUnlocked: Boolean = false,
  // Customer account (optional login; guest checkout always works)
  val customerAccount: CustomerAccount? = null,
  val isCustomerAuthBusy: Boolean = false,
  val customerAuthError: String? = null,
  val myOrdersState: CustomerOrdersLoadState = CustomerOrdersLoadState.Idle,
  // Admin data & dialogs
  val adminOrdersState: AdminOrdersLoadState = AdminOrdersLoadState.Idle,
  val adminNotice: String? = null,
  val isProductSaving: Boolean = false,
  val pickedPhotos: List<PickedPhotoItem> = emptyList(),
  val isCheckingForUpdates: Boolean = false,
  val manualUpdateDialogState: ManualUpdateDialogState? = null,
) {
  val cartItemCount: Int
    get() = cartItems.sumOf { it.quantity }

  val cartTotalPkr: Int
    get() = cartItems.sumOf { it.lineTotalPkr }

  val cartQuantityByProductId: Map<String, Int>
    get() = cartItems.associate { it.product.id to it.quantity }

  val loadedAdminOrdersCount: Int
    get() = (adminOrdersState as? AdminOrdersLoadState.Loaded)?.orders?.size ?: 0
}

class StoreViewModel(
  private val repository: ProductRepository =
    ProductRepository(cloudDataSource = NoOpCloudProductDataSource),
  private val passwordManager: AdminPasswordManager =
    AdminPasswordManager(InMemoryPasswordStorage()),
  private val orderRepository: OrderRepository =
    OrderRepository(cloudDataSource = NoOpCloudOrderDataSource),
  private val adminAuthDataSource: AdminAuthDataSource = NoOpAdminAuthDataSource,
  private val customerAuthDataSource: CustomerAuthDataSource = NoOpCustomerAuthDataSource,
  private val cartStore: CartStore = NoOpCartStore,
  private val imageUploadService: ImageUploadService = NoOpImageUploadService,
  private val appUpdateDataSource: AppUpdateDataSource = NoOpAppUpdateDataSource,
  private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow(
      StoreUiState(
        allProducts = repository.products.value,
        filteredProducts = repository.products.value,
        availableCategories = buildCategoryList(repository.products.value),
        syncStatus = repository.syncStatus.value,
        adminOrdersState = orderRepository.ordersState.value,
      )
    )
  val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

  private val usedOrderNumbers = mutableSetOf<String>()

  // True once the saved cart has been restored (or the restore was attempted).
  private var cartRestored = false

  init {
    // Crash-safety rule #1: At startup, load ONLY the product list.
    // No orders listener, no auth-triggered work, no update check, no dialogs.
    viewModelScope.launch {
      repository.products.collect { latestProducts ->
        // Cart restore (once, when the first real product list arrives):
        // rebuild the saved cart from live products only. Deleted or
        // out-of-stock products are silently dropped and prices always come
        // from Firestore. A corrupt or unreadable save can never crash
        // startup — it just restores to an empty cart.
        var restoredCart: List<CartItem>? = null
        if (!cartRestored && latestProducts.isNotEmpty()) {
          val saved =
            runCatching { cartStore.loadSavedEntries() }.getOrElse { emptyList() }
          cartRestored = true
          restoredCart =
            saved.mapNotNull { entry ->
              val product = latestProducts.find { it.id == entry.productId }
              if (product == null || !product.isAvailable) {
                null
              } else {
                CartItem(product = product, quantity = entry.quantity.coerceAtLeast(1))
              }
            }
        }
        _uiState.update { state ->
          val baseCart =
            if (restoredCart != null && state.cartItems.isEmpty()) restoredCart
            else state.cartItems
          val categories = buildCategoryList(latestProducts)
          val activeCategory =
            if (categories.any { it.equals(state.selectedCategory, ignoreCase = true) }) {
              state.selectedCategory
            } else {
              ProductCategories.ALL
            }
          val syncedCart =
            baseCart.mapNotNull { cartItem ->
              val updatedProduct = latestProducts.find { it.id == cartItem.product.id }
              if (updatedProduct == null || !updatedProduct.isAvailable) {
                null
              } else {
                cartItem.copy(product = updatedProduct)
              }
            }
          state.copy(
            allProducts = latestProducts,
            availableCategories = categories,
            selectedCategory = activeCategory,
            filteredProducts =
              applyFiltersAndSort(
                latestProducts,
                state.searchQuery,
                activeCategory,
                state.sortOption,
              ),
            cartItems = syncedCart,
          )
        }
      }
    }

    viewModelScope.launch {
      repository.syncStatus.collect { status ->
        _uiState.update { it.copy(syncStatus = status) }
      }
    }

    viewModelScope.launch {
      orderRepository.ordersState.collect { ordersState ->
        _uiState.update { it.copy(adminOrdersState = ordersState) }
      }
    }

    viewModelScope.launch {
      orderRepository.myOrdersState.collect { myOrdersState ->
        _uiState.update { it.copy(myOrdersState = myOrdersState) }
      }
    }
  }

  fun retryLoadProducts() {
    repository.refreshProducts()
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { state ->
      state.copy(
        searchQuery = query,
        filteredProducts =
          applyFiltersAndSort(
            state.allProducts,
            query,
            state.selectedCategory,
            state.sortOption,
          ),
      )
    }
  }

  fun selectCategory(category: String) {
    val normalized = ProductCategories.normalize(category)
    _uiState.update { state ->
      state.copy(
        selectedCategory = normalized,
        filteredProducts =
          applyFiltersAndSort(
            state.allProducts,
            state.searchQuery,
            normalized,
            state.sortOption,
          ),
      )
    }
  }

  fun updateSortOption(option: SortOption) {
    _uiState.update { state ->
      state.copy(
        sortOption = option,
        filteredProducts =
          applyFiltersAndSort(
            state.allProducts,
            state.searchQuery,
            state.selectedCategory,
            option,
          ),
      )
    }
  }

  fun resetFilters() {
    _uiState.update { state ->
      state.copy(
        searchQuery = "",
        selectedCategory = ProductCategories.ALL,
        sortOption = SortOption.FEATURED,
        filteredProducts = state.allProducts,
      )
    }
  }

  fun getProductById(productId: String): Product? {
    return _uiState.value.allProducts.find { it.id == productId }
  }

  fun addToCart(product: Product, quantity: Int = 1) {
    if (quantity <= 0 || !product.isAvailable) return
    _uiState.update { state ->
      val existingIndex = state.cartItems.indexOfFirst { it.product.id == product.id }
      val updatedCart =
        if (existingIndex >= 0) {
          state.cartItems.toMutableList().apply {
            val current = this[existingIndex]
            this[existingIndex] = current.copy(quantity = current.quantity + quantity)
          }
        } else {
          state.cartItems + CartItem(product = product, quantity = quantity)
        }
      state.copy(cartItems = updatedCart)
    }
    persistCart()
  }

  fun increaseQuantity(productId: String) {
    _uiState.update { state ->
      val updatedCart =
        state.cartItems.map { item ->
          if (item.product.id == productId) {
            item.copy(quantity = item.quantity + 1)
          } else {
            item
          }
        }
      state.copy(cartItems = updatedCart)
    }
    persistCart()
  }

  fun decreaseQuantity(productId: String) {
    _uiState.update { state ->
      val updatedCart =
        state.cartItems.mapNotNull { item ->
          if (item.product.id == productId) {
            if (item.quantity > 1) item.copy(quantity = item.quantity - 1) else null
          } else {
            item
          }
        }
      state.copy(cartItems = updatedCart)
    }
    persistCart()
  }

  fun removeFromCart(productId: String) {
    _uiState.update { state ->
      state.copy(cartItems = state.cartItems.filterNot { it.product.id == productId })
    }
    persistCart()
  }

  // --- Local cart persistence (guest checkout: no login, no Firestore cart) ---

  private fun persistCart() {
    val entries =
      _uiState.value.cartItems.map { SavedCartEntry(it.product.id, it.quantity) }
    viewModelScope.launch {
      runCatching { cartStore.saveEntries(entries) }
    }
  }

  private fun clearPersistedCart() {
    viewModelScope.launch {
      runCatching { cartStore.clearSavedEntries() }
    }
  }

  fun updateCustomerName(name: String) {
    _uiState.update { state ->
      state.copy(checkoutForm = state.checkoutForm.copy(customerName = name, nameError = null))
    }
  }

  fun updatePhoneNumber(phone: String) {
    _uiState.update { state ->
      state.copy(checkoutForm = state.checkoutForm.copy(phoneNumber = phone, phoneError = null))
    }
  }

  fun updateDeliveryAddress(address: String) {
    _uiState.update { state ->
      state.copy(
        checkoutForm = state.checkoutForm.copy(deliveryAddress = address, addressError = null)
      )
    }
  }

  fun updateCity(city: String) {
    _uiState.update { state ->
      state.copy(checkoutForm = state.checkoutForm.copy(city = city, cityError = null))
    }
  }

  fun placeOrder(): Boolean {
    val currentState = _uiState.value
    if (currentState.cartItems.isEmpty()) return false

    val form = currentState.checkoutForm
    val trimmedName = form.customerName.trim()
    val trimmedPhone = form.phoneNumber.trim()
    val trimmedAddress = form.deliveryAddress.trim()
    val trimmedCity = form.city.trim()

    val nameError = if (trimmedName.isEmpty()) "Please enter your full name" else null
    val digitCount = trimmedPhone.count { it.isDigit() }
    val phoneError =
      if (trimmedPhone.isEmpty() || digitCount < 7) {
        "Please enter a valid phone number"
      } else {
        null
      }
    val addressError =
      if (trimmedAddress.isEmpty()) "Please enter your complete delivery address" else null
    val cityError = if (trimmedCity.isEmpty()) "Please enter your delivery city" else null

    if (nameError != null || phoneError != null || addressError != null || cityError != null) {
      _uiState.update { state ->
        state.copy(
          checkoutForm =
            state.checkoutForm.copy(
              nameError = nameError,
              phoneError = phoneError,
              addressError = addressError,
              cityError = cityError,
            )
        )
      }
      return false
    }

    val orderNumber = generateUniqueOrderNumber()
    val nowMillis = System.currentTimeMillis()
    // Signed-in customer? Link the order to their account so the record
    // survives reinstalls and shows on the website. Guest orders keep
    // userId null — login is never required to order.
    val signedInCustomer =
      runCatching { customerAuthDataSource.currentCustomer() }.getOrNull()
    val confirmedOrder =
      ConfirmedOrder(
        orderNumber = orderNumber,
        customerName = trimmedName,
        phoneNumber = trimmedPhone,
        deliveryAddress = trimmedAddress,
        city = trimmedCity,
        items = currentState.cartItems,
        totalPkr = currentState.cartTotalPkr,
        timestampIso = currentIsoTimestamp(),
        timestampMillis = nowMillis,
        status = OrderStatus.PLACED,
        documentId = orderNumber,
        userId = signedInCustomer?.uid,
      )

    _uiState.update { state ->
      state.copy(
        cartItems = emptyList(),
        checkoutForm = CheckoutFormState(),
        lastConfirmedOrder = confirmedOrder,
        orderSyncState = OrderSyncState.Saving,
      )
    }
    // The order is placed: the saved cart goes away with the live one.
    clearPersistedCart()

    // Never block UI while saving guest order; show error state on failure, never crash.
    submitOrderInBackground(confirmedOrder)
    return true
  }

  fun retryOrderSync() {
    val order = _uiState.value.lastConfirmedOrder ?: return
    _uiState.update { it.copy(orderSyncState = OrderSyncState.Saving) }
    submitOrderInBackground(order)
  }

  private fun submitOrderInBackground(order: ConfirmedOrder) {
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { orderRepository.submitOrder(order) }
            .getOrElse { Result.failure(it) }
        }
      _uiState.update { state ->
        if (state.lastConfirmedOrder?.orderNumber != order.orderNumber) {
          state
        } else {
          state.copy(
            orderSyncState =
              if (result.isSuccess) {
                OrderSyncState.Saved
              } else {
                OrderSyncState.Failed(
                  result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not sync order right now. Please tap Retry."
                )
              }
          )
        }
      }
    }
  }

  // --- Customer Account (optional login; guest checkout always works) ---

  /** Refreshes the signed-in customer from the shared Firebase session. */
  fun prepareCustomerEntry() {
    viewModelScope.launch {
      val account =
        withContext(ioDispatcher) {
          runCatching { customerAuthDataSource.currentCustomer() }.getOrNull()
        }
      _uiState.update { it.copy(customerAccount = account, customerAuthError = null) }
    }
  }

  fun signUpCustomer(name: String, email: String, password: String) {
    if (_uiState.value.isCustomerAuthBusy) return
    _uiState.update { it.copy(isCustomerAuthBusy = true, customerAuthError = null) }

    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { customerAuthDataSource.signUp(name, email, password) }
            .getOrElse { Result.failure(it) }
        }
      // Crash-safety rule #2: signing in updates UI state ONLY; never triggers
      // data refresh or navigation.
      _uiState.update { state ->
        result.fold(
          onSuccess = { account ->
            state.copy(
              customerAccount = account,
              isCustomerAuthBusy = false,
              customerAuthError = null,
            )
          },
          onFailure = { error ->
            state.copy(
              isCustomerAuthBusy = false,
              customerAuthError =
                error.localizedMessage?.takeIf { it.isNotBlank() }
                  ?: "Sign-up failed. Please try again.",
            )
          },
        )
      }
    }
  }

  fun signInCustomer(email: String, password: String) {
    if (_uiState.value.isCustomerAuthBusy) return
    _uiState.update { it.copy(isCustomerAuthBusy = true, customerAuthError = null) }

    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { customerAuthDataSource.signIn(email, password) }
            .getOrElse { Result.failure(it) }
        }
      _uiState.update { state ->
        result.fold(
          onSuccess = { account ->
            state.copy(
              customerAccount = account,
              isCustomerAuthBusy = false,
              customerAuthError = null,
            )
          },
          onFailure = { error ->
            state.copy(
              isCustomerAuthBusy = false,
              customerAuthError =
                error.localizedMessage?.takeIf { it.isNotBlank() }
                  ?: "Login failed. Check your email and password.",
            )
          },
        )
      }
    }
  }

  fun signOutCustomer() {
    customerAuthDataSource.signOut()
    orderRepository.resetMyOrdersState()
    // Crash-safety rule #2: signing out updates UI state ONLY.
    _uiState.update {
      it.copy(
        customerAccount = null,
        isCustomerAuthBusy = false,
        customerAuthError = null,
        myOrdersState = CustomerOrdersLoadState.Idle,
      )
    }
  }

  fun clearCustomerAuthError() {
    _uiState.update { it.copy(customerAuthError = null) }
  }

  // --- Customer My Orders (Refresh ONLY on My Orders open or Refresh tap) ---

  fun onMyOrdersOpened() {
    val account =
      runCatching { customerAuthDataSource.currentCustomer() }.getOrNull() ?: return
    refreshMyOrders(account.uid)
  }

  fun refreshMyOrders() {
    val account =
      runCatching { customerAuthDataSource.currentCustomer() }.getOrNull() ?: return
    refreshMyOrders(account.uid)
  }

  private fun refreshMyOrders(userId: String) {
    viewModelScope.launch {
      withContext(ioDispatcher) {
        runCatching { orderRepository.refreshMyOrders(userId) }
      }
    }
  }

  // --- Admin Entry, Cloud Sign-In (Step 1), and Local Password (Step 2) ---

  fun prepareAdminEntry() {
    _uiState.update {
      it.copy(
        isAdminUnlocked = false,
        cloudAuthError = null,
        adminNotice = null,
      )
    }
    viewModelScope.launch {
      val (email, hasLocalPw) =
        withContext(ioDispatcher) {
          val currentEmail = adminAuthDataSource.currentUserEmail()
          val pwSet = passwordManager.isPasswordSet()
          currentEmail to pwSet
        }
      _uiState.update {
        it.copy(
          cloudAdminEmail = email,
          isAdminPasswordSet = hasLocalPw,
          isAdminPasswordChecked = true,
        )
      }
    }
  }

  fun signInAdminCloud(email: String, password: String) {
    if (_uiState.value.isCloudAuthBusy) return
    _uiState.update { it.copy(isCloudAuthBusy = true, cloudAuthError = null) }

    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { adminAuthDataSource.signIn(email, password) }
            .getOrElse { Result.failure(it) }
        }
      val hasLocalPw = withContext(ioDispatcher) { passwordManager.isPasswordSet() }

      // Crash-safety rule #2: Signing in updates UI state ONLY; never triggers data refresh or listener.
      _uiState.update { state ->
        result.fold(
          onSuccess = { signedInEmail ->
            state.copy(
              cloudAdminEmail = signedInEmail,
              isCloudAuthBusy = false,
              cloudAuthError = null,
              isAdminPasswordSet = hasLocalPw,
              isAdminPasswordChecked = true,
            )
          },
          onFailure = { error ->
            state.copy(
              isCloudAuthBusy = false,
              cloudAuthError =
                error.localizedMessage?.takeIf { it.isNotBlank() }
                  ?: "Cloud sign-in failed. Check your email and password.",
            )
          },
        )
      }
    }
  }

  fun signOutAdminCloud() {
    adminAuthDataSource.signOut()
    orderRepository.resetOrdersState()
    // Crash-safety rule #2: Signing out updates UI state ONLY.
    _uiState.update {
      it.copy(
        cloudAdminEmail = null,
        isCloudAuthBusy = false,
        cloudAuthError = null,
        isAdminUnlocked = false,
        adminNotice = null,
        manualUpdateDialogState = null,
      )
    }
  }

  fun setupAdminPassword(password: String, confirmPassword: String): PasswordActionResult {
    val result = passwordManager.setupInitialPassword(password, confirmPassword)
    if (result is PasswordActionResult.Success) {
      _uiState.update {
        it.copy(
          isAdminPasswordSet = true,
          isAdminPasswordChecked = true,
          isAdminUnlocked = true,
          adminNotice = null,
        )
      }
    }
    return result
  }

  fun unlockAdmin(password: String): Boolean {
    val verified = passwordManager.verifyPassword(password)
    if (verified) {
      _uiState.update { it.copy(isAdminUnlocked = true, adminNotice = null) }
    }
    return verified
  }

  fun lockAdmin() {
    _uiState.update {
      it.copy(
        isAdminUnlocked = false,
        adminNotice = null,
        manualUpdateDialogState = null,
      )
    }
  }

  fun changeAdminPassword(
    currentPassword: String,
    newPassword: String,
    confirmNewPassword: String,
  ): PasswordActionResult {
    return passwordManager.changePassword(currentPassword, newPassword, confirmNewPassword)
  }

  fun clearAdminNotice() {
    _uiState.update { it.copy(adminNotice = null) }
  }

  // --- Admin Orders (Refresh ONLY on Orders tab open or Refresh tap) ---

  fun onAdminOrdersTabOpened() {
    if (_uiState.value.cloudAdminEmail.isNullOrBlank() || !_uiState.value.isAdminUnlocked) {
      return
    }
    refreshAdminOrders()
  }

  fun refreshAdminOrders() {
    if (_uiState.value.cloudAdminEmail.isNullOrBlank() || !_uiState.value.isAdminUnlocked) {
      return
    }
    viewModelScope.launch {
      withContext(ioDispatcher) {
        runCatching { orderRepository.refreshOrders() }
      }
    }
  }

  fun updateAdminOrderStatus(documentId: String, newStatus: OrderStatus) {
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { orderRepository.updateOrderStatus(documentId, newStatus) }
            .getOrElse { Result.failure(it) }
        }
      _uiState.update {
        it.copy(
          adminNotice =
            if (result.isSuccess) {
              "Order status marked as ${newStatus.label}."
            } else {
              "Could not update order status: ${result.exceptionOrNull()?.localizedMessage ?: "try again"}"
            }
        )
      }
    }
  }

  // --- Admin Cloudinary Photo Picking & Upload ---

  fun initPickedPhotosForProduct(product: Product?) {
    val existing =
      product?.galleryImageUrls.orEmpty().mapIndexed { idx, url ->
        PickedPhotoItem(
          id = "existing_$idx",
          localUri = url,
          status = PickedPhotoStatus.Uploaded(url),
        )
      }
    _uiState.update { it.copy(pickedPhotos = existing) }
  }

  fun startPickedPhotoUpload(localUriString: String) {
    val id = "pick_${UUID.randomUUID().toString().take(8)}"
    val newItem =
      PickedPhotoItem(
        id = id,
        localUri = localUriString,
        status = PickedPhotoStatus.Uploading,
      )
    _uiState.update { it.copy(pickedPhotos = it.pickedPhotos + newItem) }
    uploadPhotoItem(id, localUriString)
  }

  fun retryPickedPhotoUpload(id: String) {
    val target = _uiState.value.pickedPhotos.find { it.id == id } ?: return
    _uiState.update { state ->
      state.copy(
        pickedPhotos =
          state.pickedPhotos.map { item ->
            if (item.id == id) item.copy(status = PickedPhotoStatus.Uploading) else item
          }
      )
    }
    uploadPhotoItem(id, target.localUri)
  }

  fun removePickedPhoto(id: String) {
    _uiState.update { state ->
      state.copy(pickedPhotos = state.pickedPhotos.filterNot { it.id == id })
    }
  }

  fun clearPickedPhotos() {
    _uiState.update { it.copy(pickedPhotos = emptyList()) }
  }

  private fun uploadPhotoItem(id: String, localUriString: String) {
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { imageUploadService.uploadGalleryUri(localUriString) }
            .getOrElse { Result.failure(it) }
        }
      _uiState.update { state ->
        state.copy(
          pickedPhotos =
            state.pickedPhotos.map { item ->
              if (item.id != id) {
                item
              } else {
                result.fold(
                  onSuccess = { url ->
                    item.copy(status = PickedPhotoStatus.Uploaded(url))
                  },
                  onFailure = { err ->
                    item.copy(
                      status =
                        PickedPhotoStatus.Failed(
                          err.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Photo upload failed. Remove or retry."
                        )
                    )
                  },
                )
              }
            }
        )
      }
    }
  }

  // --- Admin Product CRUD ---

  fun saveAdminProduct(
    editingProductId: String?,
    name: String,
    pricePkr: Int,
    category: String,
    fabric: String,
    size: String,
    description: String,
    imageKey: String,
    typedImageUrls: List<String>,
    uploadedImageUrls: List<String>,
    inStock: Boolean,
    stockQuantity: Int,
    onResult: (Result<Unit>) -> Unit = {},
  ) {
    val normalizedCategory = ProductCategories.normalize(category)
    val cleanName = name.trim()
    val cleanFabric = fabric.trim()
    val cleanSize = size.trim()
    val cleanDescription = description.trim()
    val cleanQty = if (inStock) stockQuantity.coerceAtLeast(1) else 0
    val resolvedRes = ProductCatalog.resolveDrawableForKey(imageKey, normalizedCategory)

    // Picked Cloudinary uploads first, then typed Photo URLs; strictly strip non-http(s) URIs
    val combinedImageUrls =
      resolveGalleryImageUrls(
        imageUris = uploadedImageUrls + typedImageUrls,
        imageUri = null,
      )

    val specs =
      buildList {
        add(ProductSpec("Category", normalizedCategory))
        if (cleanSize.isNotEmpty()) add(ProductSpec("Size / Cutting", cleanSize))
        if (cleanFabric.isNotEmpty()) add(ProductSpec("Fabric", cleanFabric))
      }

    val shortSpec =
      listOf(cleanSize, cleanFabric)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
        .ifBlank { normalizedCategory }

    val product =
      sanitizeProductForCloudSave(
        Product(
          id = editingProductId ?: "prod_${UUID.randomUUID().toString().take(8)}",
          name = cleanName,
          pricePkr = pricePkr,
          category = normalizedCategory,
          fabric = cleanFabric,
          size = cleanSize,
          shortSpec = shortSpec,
          specs = specs,
          description = cleanDescription,
          imageKey = imageKey,
          imageRes = resolvedRes,
          imageUri = combinedImageUrls.firstOrNull(),
          imageUris = combinedImageUrls,
          inStock = inStock,
          stockQuantity = cleanQty,
        )
      )

    _uiState.update { it.copy(isProductSaving = true) }
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching {
            if (editingProductId == null) {
              repository.addProduct(product)
            } else {
              repository.updateProduct(product)
            }
          }.getOrElse { Result.failure(it) }
        }

      _uiState.update {
        it.copy(
          isProductSaving = false,
          adminNotice =
            if (result.isSuccess) {
              if (editingProductId == null) "Product added to Firestore." else "Product updated."
            } else {
              "Save failed: ${result.exceptionOrNull()?.localizedMessage ?: "check admin sign-in"}"
            },
        )
      }
      onResult(result)
    }
  }

  fun deleteAdminProduct(productId: String) {
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { repository.deleteProduct(productId) }
            .getOrElse { Result.failure(it) }
        }
      _uiState.update {
        it.copy(
          adminNotice =
            if (result.isSuccess) {
              "Product deleted from Firestore."
            } else {
              "Delete failed: ${result.exceptionOrNull()?.localizedMessage ?: "try again"}"
            }
        )
      }
    }
  }

  // --- Manual-Only Update Check ---

  fun checkForUpdatesManually() {
    if (_uiState.value.isCheckingForUpdates) return
    _uiState.update { it.copy(isCheckingForUpdates = true, manualUpdateDialogState = null) }

    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          runCatching { appUpdateDataSource.checkForUpdate(currentVersionCode) }
            .getOrElse { Result.failure(it) }
        }
      _uiState.update { state ->
        val dialogState =
          result.fold(
            onSuccess = { outcome ->
              when (outcome) {
                is ManualUpdateCheckResult.UpdateAvailable ->
                  ManualUpdateDialogState.Available(outcome.info)
                ManualUpdateCheckResult.UpToDate ->
                  ManualUpdateDialogState.UpToDate
              }
            },
            onFailure = { err ->
              ManualUpdateDialogState.Failed(
                err.localizedMessage?.takeIf { it.isNotBlank() }
                  ?: "Could not check for updates. Please try again."
              )
            },
          )
        state.copy(
          isCheckingForUpdates = false,
          manualUpdateDialogState = dialogState,
        )
      }
    }
  }

  fun dismissManualUpdateDialog() {
    _uiState.update { it.copy(manualUpdateDialogState = null) }
  }

  private fun generateUniqueOrderNumber(): String {
    var candidate: String
    do {
      val code = 100000 + Random.nextInt(900000)
      candidate = "GB-$code"
    } while (!usedOrderNumbers.add(candidate))
    return candidate
  }

  private fun buildCategoryList(products: List<Product>): List<String> {
    val base =
      mutableListOf(
        ProductCategories.ALL,
        ProductCategories.BLANKETS,
        ProductCategories.MENS_SUITS,
      )
    products.forEach { product ->
      val normalized = ProductCategories.normalize(product.category)
      if (normalized.isNotBlank() && base.none { it.equals(normalized, ignoreCase = true) }) {
        base.add(normalized)
      }
    }
    return base
  }

  private fun applyFiltersAndSort(
    products: List<Product>,
    query: String,
    category: String,
    sortOption: SortOption,
  ): List<Product> {
    val normalizedQuery = query.trim()
    val filtered =
      products.filter { product ->
        val matchesCategory =
          category.equals(ProductCategories.ALL, ignoreCase = true) ||
            product.category.equals(category, ignoreCase = true)
        val matchesQuery =
          normalizedQuery.isEmpty() ||
            product.name.contains(normalizedQuery, ignoreCase = true) ||
            product.fabric.contains(normalizedQuery, ignoreCase = true) ||
            product.category.contains(normalizedQuery, ignoreCase = true)
        matchesCategory && matchesQuery
      }
    return when (sortOption) {
      SortOption.FEATURED -> filtered
      SortOption.PRICE_LOW_HIGH -> filtered.sortedBy { it.pricePkr }
      SortOption.PRICE_HIGH_LOW -> filtered.sortedByDescending { it.pricePkr }
    }
  }

  companion object {
    fun factory(context: Context): ViewModelProvider.Factory {
      val appContext = context.applicationContext
      return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          val repository =
            ProductRepository(
              cloudDataSource = FirestoreProductDataSource(appContext)
            )
          val orderRepository =
            OrderRepository(
              cloudDataSource = FirestoreOrderDataSource(appContext)
            )
          val passwordManager =
            AdminPasswordManager(
              storage = EncryptedPasswordStorage(appContext)
            )
          val adminAuthDataSource = FirebaseAdminAuthDataSource(appContext)
          val customerAuthDataSource = FirebaseCustomerAuthDataSource(appContext)
          val cartStore = SharedPreferencesCartStore(appContext)
          val imageUploadService = CloudinaryImageUploader(appContext)
          val appUpdateDataSource = FirestoreAppUpdateDataSource(appContext)
          return StoreViewModel(
            repository = repository,
            passwordManager = passwordManager,
            orderRepository = orderRepository,
            adminAuthDataSource = adminAuthDataSource,
            customerAuthDataSource = customerAuthDataSource,
            cartStore = cartStore,
            imageUploadService = imageUploadService,
            appUpdateDataSource = appUpdateDataSource,
            currentVersionCode = BuildConfig.VERSION_CODE,
          ) as T
        }
      }
    }
  }
}

private object NoOpCloudProductDataSource : CloudProductDataSource {
  override val isConfigured: Boolean = false
  override fun observeProducts(): Flow<CloudProductsSnapshot> = emptyFlow()
  override suspend fun fetchProductsOnce(): CloudProductsSnapshot = CloudProductsSnapshot.Empty
  override suspend fun upsertProduct(product: Product): Result<Unit> = Result.success(Unit)
  override suspend fun deleteProduct(productId: String): Result<Unit> = Result.success(Unit)
}

private object NoOpCloudOrderDataSource : CloudOrderDataSource {
  override val isConfigured: Boolean = false
  override suspend fun fetchOrders(): Result<List<ConfirmedOrder>> = Result.success(emptyList())
  override suspend fun fetchMyOrders(userId: String): Result<List<ConfirmedOrder>> =
    Result.success(emptyList())
  override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> = Result.success(Unit)
  override suspend fun updateOrderStatus(documentId: String, status: OrderStatus): Result<Unit> =
    Result.success(Unit)
}

private object NoOpAdminAuthDataSource : AdminAuthDataSource {
  override fun currentUserEmail(): String? = null
  override suspend fun signIn(email: String, password: String): Result<String> =
    Result.failure(IllegalStateException("Auth not configured"))
  override fun signOut() = Unit
}

object NoOpCustomerAuthDataSource : CustomerAuthDataSource {
  override fun currentCustomer(): CustomerAccount? = null
  override suspend fun signUp(
    name: String,
    email: String,
    password: String,
  ): Result<CustomerAccount> =
    Result.failure(IllegalStateException("Auth not configured"))
  override suspend fun signIn(email: String, password: String): Result<CustomerAccount> =
    Result.failure(IllegalStateException("Auth not configured"))
  override fun signOut() = Unit
}

private object NoOpImageUploadService : ImageUploadService {
  override suspend fun uploadGalleryUri(localUriString: String): Result<String> =
    Result.failure(IllegalStateException("Uploader not configured"))
}

private object NoOpAppUpdateDataSource : AppUpdateDataSource {
  override suspend fun checkForUpdate(currentVersionCode: Int): Result<ManualUpdateCheckResult> =
    Result.success(ManualUpdateCheckResult.UpToDate)
}

private class InMemoryPasswordStorage : PasswordStorage {
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
