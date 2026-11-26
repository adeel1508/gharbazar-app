package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AdminAuthDataSource
import com.example.data.AppUpdateDataSource
import com.example.data.AppUpdateInfo
import com.example.data.CatalogSyncStatus
import com.example.data.CloudOrderDataSource
import com.example.data.CloudOrdersSnapshot
import com.example.data.CloudProductDataSource
import com.example.data.CloudProductsSnapshot
import com.example.data.FirebaseAdminAuthDataSource
import com.example.data.FirestoreAppUpdateDataSource
import com.example.data.FirestoreOrderDataSource
import com.example.data.FirestoreProductDataSource
import com.example.data.OrderRepository
import com.example.data.ProductCatalog
import com.example.data.ProductRepository
import com.example.data.friendlyAdminAuthError
import com.example.data.shouldPromptForUpdate
import com.example.model.CartItem
import com.example.model.CheckoutFormState
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductSpec
import com.example.model.SortOption
import com.example.security.AdminPasswordManager
import com.example.security.EncryptedPasswordStorage
import com.example.security.PasswordActionResult
import com.example.security.PasswordStorage
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreUiState(
  val allProducts: List<Product> = ProductCatalog.products,
  val filteredProducts: List<Product> = ProductCatalog.products,
  val searchQuery: String = "",
  /** Display label of the selected category filter; "All" shows everything. */
  val selectedCategory: String = ProductCategory.ALL.label,
  val sortOption: SortOption = SortOption.FEATURED,
  val cartItems: List<CartItem> = emptyList(),
  val checkoutForm: CheckoutFormState = CheckoutFormState(),
  val lastConfirmedOrder: ConfirmedOrder? = null,
  val adminOrders: List<ConfirmedOrder> = emptyList(),
  val syncStatus: CatalogSyncStatus =
    CatalogSyncStatus.LocalFallback("Using built-in 8-product catalogue"),
  val isAdminPasswordSet: Boolean = false,
  val isAdminUnlocked: Boolean = false,
  val adminNotice: String? = null,
  val availableUpdate: AppUpdateInfo? = null,
  /** Firebase Auth state for the admin panel (separate from the local admin password). */
  val isAdminSignedIn: Boolean = false,
  val adminAuthError: String? = null,
  /**
   * True when a cloud Firestore catalogue is configured. The admin panel
   * gate (cloud sign-in first) only applies then; without a cloud the
   * local admin password is the only lock, as before.
   */
  val isCloudConfigured: Boolean = false,
) {
  val cartItemCount: Int
    get() = cartItems.sumOf { it.quantity }

  val cartTotalPkr: Int
    get() = cartItems.sumOf { it.lineTotalPkr }

  val cartQuantityByProductId: Map<String, Int>
    get() = cartItems.associate { it.product.id to it.quantity }
}

class StoreViewModel(
  private val repository: ProductRepository =
    ProductRepository(cloudDataSource = NoOpCloudProductDataSource),
  private val passwordManager: AdminPasswordManager =
    AdminPasswordManager(InMemoryPasswordStorage()),
  private val orderRepository: OrderRepository =
    OrderRepository(cloudDataSource = NoOpCloudOrderDataSource),
  private val appUpdateDataSource: AppUpdateDataSource = NoOpAppUpdateDataSource,
  private val adminAuth: AdminAuthDataSource = NoOpAdminAuthDataSource,
  private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow(
      StoreUiState(
        allProducts = repository.products.value,
        filteredProducts = repository.products.value,
        adminOrders = orderRepository.orders.value,
        syncStatus = repository.syncStatus.value,
        isAdminPasswordSet = passwordManager.isPasswordSet(),
        isAdminUnlocked = false,
        isAdminSignedIn = adminAuth.isSignedIn,
        isCloudConfigured = repository.isCloudConfigured,
      )
    )
  val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

  private val usedOrderNumbers = mutableSetOf<String>()

  init {
    viewModelScope.launch {
      repository.products.collect { latestProducts ->
        _uiState.update { state ->
          val syncedCart =
            state.cartItems.mapNotNull { cartItem ->
              val updatedProduct = latestProducts.find { it.id == cartItem.product.id }
              if (updatedProduct == null || !updatedProduct.isAvailable) {
                null
              } else {
                cartItem.copy(product = updatedProduct)
              }
            }
          state.copy(
            allProducts = latestProducts,
            filteredProducts =
              applyFiltersAndSort(
                latestProducts,
                state.searchQuery,
                state.selectedCategory,
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
      orderRepository.orders.collect { latestOrders ->
        _uiState.update { it.copy(adminOrders = latestOrders) }
      }
    }

    viewModelScope.launch {
      val info = runCatching { appUpdateDataSource.fetchUpdateInfo(currentVersionCode) }.getOrNull()
      val prompt = shouldPromptForUpdate(info, currentVersionCode)
      if (prompt != null) {
        _uiState.update { it.copy(availableUpdate = prompt) }
      }
    }

    viewModelScope.launch {
      adminAuth.authState.collect { signedIn ->
        _uiState.update {
          it.copy(
            isAdminSignedIn = signedIn,
            adminAuthError = if (signedIn) null else it.adminAuthError,
          )
        }
      }
    }
  }

  fun dismissUpdateDialog() {
    _uiState.update { it.copy(availableUpdate = null) }
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
    _uiState.update { state ->
      state.copy(
        selectedCategory = category,
        filteredProducts =
          applyFiltersAndSort(
            state.allProducts,
            state.searchQuery,
            category,
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
        selectedCategory = ProductCategory.ALL.label,
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
  }

  fun removeFromCart(productId: String) {
    _uiState.update { state ->
      state.copy(cartItems = state.cartItems.filterNot { it.product.id == productId })
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
    val confirmedOrder =
      ConfirmedOrder(
        orderNumber = orderNumber,
        customerName = trimmedName,
        phoneNumber = trimmedPhone,
        deliveryAddress = trimmedAddress,
        city = trimmedCity,
        items = currentState.cartItems,
        totalPkr = currentState.cartTotalPkr,
        timestamp = System.currentTimeMillis(),
        status = OrderStatus.PLACED,
      )

    _uiState.update { state ->
      state.copy(
        cartItems = emptyList(),
        checkoutForm = CheckoutFormState(),
        lastConfirmedOrder = confirmedOrder,
      )
    }

    // Non-blocking background sync to Firestore; checkout succeeds even if save fails.
    viewModelScope.launch {
      runCatching { orderRepository.submitOrder(confirmedOrder) }
    }
    return true
  }

  // --- Seller / Admin Security & Catalog Management ---

  fun setupAdminPassword(password: String, confirmPassword: String): PasswordActionResult {
    val result = passwordManager.setupInitialPassword(password, confirmPassword)
    if (result is PasswordActionResult.Success) {
      _uiState.update {
        it.copy(
          isAdminPasswordSet = true,
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
        isAdminPasswordSet = passwordManager.isPasswordSet(),
        adminNotice = null,
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

  /** Firebase sign-in for the admin panel (email is fixed to the admin account). */
  fun signInAdmin(password: String) {
    viewModelScope.launch {
      val result = adminAuth.signIn(password)
      _uiState.update {
        it.copy(
          isAdminSignedIn = adminAuth.isSignedIn,
          adminAuthError =
            if (result.isSuccess) null else friendlyAdminAuthError(result.exceptionOrNull()!!),
        )
      }
    }
  }

  fun signOutAdmin() {
    adminAuth.signOut()
    _uiState.update { it.copy(isAdminSignedIn = false, adminAuthError = null) }
  }

  fun clearAdminAuthError() {
    _uiState.update { it.copy(adminAuthError = null) }
  }

  /**
   * Cloud product writes need the admin to be signed in: the Firestore rules
   * only allow product writes for the signed-in admin email. Without this
   * check the write would fail with PERMISSION_DENIED after the product had
   * already been added locally. Local-only catalogues (no cloud configured)
   * are unaffected.
   */
  private fun requireAdminSignedInForCloudWrite(action: String): Boolean {
    if (repository.isCloudConfigured && !adminAuth.isSignedIn) {
      _uiState.update {
        it.copy(
          adminNotice =
            "Sign in with your GharBazar account in the admin panel to $action."
        )
      }
      return false
    }
    return true
  }

  fun saveAdminProduct(
    editingProductId: String?,
    name: String,
    pricePkr: Int,
    category: String,
    fabric: String,
    size: String,
    description: String,
    imageKey: String,
    imageUri: String?,
    imageUris: List<String> = emptyList(),
    inStock: Boolean,
    stockQuantity: Int,
  ) {
    if (!requireAdminSignedInForCloudWrite("save products to the cloud")) return
    val normalizedCategory =
      ProductCategory.fromStored(category).takeIf { it != ProductCategory.ALL.label }
        ?: ProductCategory.BLANKETS.label
    val cleanName = name.trim()
    val cleanFabric = fabric.trim()
    val cleanSize = size.trim()
    val cleanDescription = description.trim()
    val cleanQty = if (inStock) stockQuantity.coerceAtLeast(1) else 0
    val resolvedRes = ProductCatalog.resolveDrawableForKey(imageKey, normalizedCategory)
    // The gallery is the cleaned URL list; the cover is the first gallery
    // photo (or the single imageUri when no list was given).
    val cleanImageUris =
      imageUris.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val coverUri = imageUri?.takeIf { it.isNotBlank() } ?: cleanImageUris.firstOrNull()
    val galleryUris =
      if (coverUri != null && coverUri !in cleanImageUris) listOf(coverUri) + cleanImageUris
      else cleanImageUris

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
        imageUri = coverUri,
        imageUris = galleryUris,
        inStock = inStock,
        stockQuantity = cleanQty,
      )

    viewModelScope.launch {
      val result =
        if (editingProductId == null) {
          repository.addProduct(product)
        } else {
          repository.updateProduct(product)
        }
      _uiState.update {
        it.copy(
          adminNotice =
            if (result.isSuccess) {
              if (editingProductId == null) "Product added to catalogue." else "Product updated."
            } else {
              "Updated locally; cloud sync pending (${result.exceptionOrNull()?.message ?: "offline"})."
            }
        )
      }
    }
  }

  fun deleteAdminProduct(productId: String) {
    if (!requireAdminSignedInForCloudWrite("delete products from the cloud")) return
    viewModelScope.launch {
      val result = repository.deleteProduct(productId)
      _uiState.update {
        it.copy(
          adminNotice =
            if (result.isSuccess) {
              "Product deleted from catalogue."
            } else {
              "Removed locally; cloud sync pending (${result.exceptionOrNull()?.message ?: "offline"})."
            }
        )
      }
    }
  }

  fun updateAdminOrderStatus(orderNumber: String, newStatus: OrderStatus) {
    if (!requireAdminSignedInForCloudWrite("change order status in the cloud")) return
    viewModelScope.launch {
      val result = orderRepository.updateOrderStatus(orderNumber, newStatus)
      _uiState.update {
        it.copy(
          adminNotice =
            if (result.isSuccess) {
              "Order $orderNumber marked as ${newStatus.label}."
            } else {
              "Updated locally; cloud sync pending (${result.exceptionOrNull()?.message ?: "offline"})."
            }
        )
      }
    }
  }

  private fun generateUniqueOrderNumber(): String {
    var candidate: String
    do {
      val code = 100000 + Random.nextInt(900000)
      candidate = "GB-$code"
    } while (!usedOrderNumbers.add(candidate))
    return candidate
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
          category == ProductCategory.ALL.label || product.category == category
        val matchesQuery =
          normalizedQuery.isEmpty() ||
            product.name.contains(normalizedQuery, ignoreCase = true)
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
          val appUpdateDataSource = FirestoreAppUpdateDataSource(appContext)
          val adminAuth = FirebaseAdminAuthDataSource(appContext)
          return StoreViewModel(
            repository = repository,
            passwordManager = passwordManager,
            orderRepository = orderRepository,
            appUpdateDataSource = appUpdateDataSource,
            adminAuth = adminAuth,
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
  override suspend fun upsertProduct(product: Product): Result<Unit> = Result.success(Unit)
  override suspend fun deleteProduct(productId: String): Result<Unit> = Result.success(Unit)
}

private object NoOpCloudOrderDataSource : CloudOrderDataSource {
  override val isConfigured: Boolean = false
  override fun observeOrders(): Flow<CloudOrdersSnapshot> = emptyFlow()
  override suspend fun saveOrder(order: ConfirmedOrder): Result<Unit> = Result.success(Unit)
  override suspend fun updateOrderStatus(orderNumber: String, status: OrderStatus): Result<Unit> =
    Result.success(Unit)
}

private object NoOpAppUpdateDataSource : AppUpdateDataSource {
  override suspend fun fetchUpdateInfo(currentVersionCode: Int): AppUpdateInfo? = null
}

private object NoOpAdminAuthDataSource : AdminAuthDataSource {
  override val adminEmail: String = ""
  override val isSignedIn: Boolean = false
  override val authState: Flow<Boolean> = emptyFlow()
  override suspend fun signIn(password: String): Result<Unit> =
    Result.failure(IllegalStateException("Firebase Auth is not configured"))
  override fun signOut() = Unit
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
