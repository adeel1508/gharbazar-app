package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.screens.AdminRouteScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CheckoutScreen
import com.example.ui.screens.CustomerAccountScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OrderConfirmationScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.launch

object StoreRoutes {
  const val HOME = "home"
  const val CART = "cart"
  const val CHECKOUT = "checkout"
  const val PRODUCT_DETAIL = "product/{productId}"
  const val CONFIRMATION = "confirmation"
  const val ADMIN = "seller_admin"
  const val ACCOUNT = "account"

  fun productDetail(productId: String): String = "product/$productId"
}

private enum class TopLevelTab(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  HOME(
    route = StoreRoutes.HOME,
    labelRes = R.string.nav_home,
    selectedIcon = Icons.Filled.Storefront,
    unselectedIcon = Icons.Outlined.Storefront,
    testTag = "nav_home",
  ),
  CART(
    route = StoreRoutes.CART,
    labelRes = R.string.nav_cart,
    selectedIcon = Icons.Filled.ShoppingCart,
    unselectedIcon = Icons.Outlined.ShoppingCart,
    testTag = "nav_cart",
  ),
  CHECKOUT(
    route = StoreRoutes.CHECKOUT,
    labelRes = R.string.nav_checkout,
    selectedIcon = Icons.Filled.LocalShipping,
    unselectedIcon = Icons.Outlined.LocalShipping,
    testTag = "nav_checkout",
  ),
}

@Composable
fun GharBazarApp() {
  val context = LocalContext.current
  val viewModel: StoreViewModel = viewModel(factory = StoreViewModel.factory(context))
  val navController = rememberNavController()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val currentRoute = currentDestination?.route
  val showTopLevelNav =
    currentRoute in listOf(StoreRoutes.HOME, StoreRoutes.CART, StoreRoutes.CHECKOUT)

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun showAddedSnackbar(productName: String) {
    coroutineScope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      val result =
        snackbarHostState.showSnackbar(
          message = "Added $productName to cart",
          actionLabel = "View Cart",
          duration = SnackbarDuration.Short,
        )
      if (result == SnackbarResult.ActionPerformed) {
        navigateToTab(StoreRoutes.CART)
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (showTopLevelNav && useNavRail) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
          TopLevelTab.entries.forEach { tab ->
            val selected =
              currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationRailItem(
              selected = selected,
              onClick = { navigateToTab(tab.route) },
              icon = {
                TabIcon(
                  tab = tab,
                  selected = selected,
                  cartCount = uiState.cartItemCount,
                )
              },
              label = { Text(stringResource(tab.labelRes)) },
              modifier = Modifier.testTag(tab.testTag),
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f).fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
          if (showTopLevelNav && !useNavRail) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              windowInsets = WindowInsets.navigationBars,
            ) {
              TopLevelTab.entries.forEach { tab ->
                val selected =
                  currentDestination?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTab(tab.route) },
                  icon = {
                    TabIcon(
                      tab = tab,
                      selected = selected,
                      cartCount = uiState.cartItemCount,
                    )
                  },
                  label = { Text(stringResource(tab.labelRes)) },
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = StoreRoutes.HOME,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
          composable(StoreRoutes.HOME) {
            HomeScreen(
              uiState = uiState,
              onSearchQueryChange = viewModel::updateSearchQuery,
              onSelectCategory = viewModel::selectCategory,
              onSelectSortOption = viewModel::updateSortOption,
              onResetFilters = viewModel::resetFilters,
              onRetryProducts = viewModel::retryLoadProducts,
              onProductClick = { product ->
                navController.navigate(StoreRoutes.productDetail(product.id))
              },
              onQuickAddToCart = { product ->
                viewModel.addToCart(product, 1)
                showAddedSnackbar(product.name)
              },
              onOpenCart = { navigateToTab(StoreRoutes.CART) },
              onOpenSellerAdmin = {
                viewModel.prepareAdminEntry()
                navController.navigate(StoreRoutes.ADMIN)
              },
              onOpenAccount = {
                viewModel.prepareCustomerEntry()
                navController.navigate(StoreRoutes.ACCOUNT)
              },
            )
          }

          composable(StoreRoutes.ACCOUNT) {
            CustomerAccountScreen(
              uiState = uiState,
              onSignUp = viewModel::signUpCustomer,
              onSignIn = viewModel::signInCustomer,
              onSignOut = viewModel::signOutCustomer,
              onMyOrdersOpened = viewModel::onMyOrdersOpened,
              onRefreshMyOrders = viewModel::refreshMyOrders,
              onClearAuthError = viewModel::clearCustomerAuthError,
              onBack = { navController.popBackStack() },
              onBrowseProducts = { navigateToTab(StoreRoutes.HOME) },
            )
          }

          composable(
            route = StoreRoutes.PRODUCT_DETAIL,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
          ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId").orEmpty()
            val product = viewModel.getProductById(productId)
            ProductDetailScreen(
              product = product,
              cartItemCount = uiState.cartItemCount,
              onBack = { navController.popBackStack() },
              onOpenCart = { navigateToTab(StoreRoutes.CART) },
              onAddToCart = { selectedProduct, qty ->
                viewModel.addToCart(selectedProduct, qty)
                showAddedSnackbar(selectedProduct.name)
              },
              onBuyNowCod = { selectedProduct, qty ->
                viewModel.addToCart(selectedProduct, qty)
                navigateToTab(StoreRoutes.CHECKOUT)
              },
            )
          }

          composable(StoreRoutes.CART) {
            CartScreen(
              uiState = uiState,
              onIncreaseQuantity = viewModel::increaseQuantity,
              onDecreaseQuantity = viewModel::decreaseQuantity,
              onRemoveItem = viewModel::removeFromCart,
              onProductClick = { product ->
                navController.navigate(StoreRoutes.productDetail(product.id))
              },
              onProceedToCheckout = { navigateToTab(StoreRoutes.CHECKOUT) },
              onBrowseProducts = { navigateToTab(StoreRoutes.HOME) },
            )
          }

          composable(StoreRoutes.CHECKOUT) {
            CheckoutScreen(
              uiState = uiState,
              isCustomerSignedIn = uiState.customerAccount != null,
              customerEmail = uiState.customerAccount?.email,
              onOpenAccount = {
                viewModel.prepareCustomerEntry()
                navController.navigate(StoreRoutes.ACCOUNT)
              },
              onNameChange = viewModel::updateCustomerName,
              onPhoneChange = viewModel::updatePhoneNumber,
              onAddressChange = viewModel::updateDeliveryAddress,
              onCityChange = viewModel::updateCity,
              onPlaceOrder = {
                if (viewModel.placeOrder()) {
                  navController.navigate(StoreRoutes.CONFIRMATION) {
                    popUpTo(StoreRoutes.HOME) { inclusive = false }
                  }
                }
              },
              onBrowseProducts = { navigateToTab(StoreRoutes.HOME) },
            )
          }

          composable(StoreRoutes.CONFIRMATION) {
            OrderConfirmationScreen(
              order = uiState.lastConfirmedOrder,
              orderSyncState = uiState.orderSyncState,
              onRetryOrderSync = viewModel::retryOrderSync,
              onContinueShopping = {
                navController.navigate(StoreRoutes.HOME) {
                  popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                  }
                  launchSingleTop = true
                }
              },
            )
          }

          composable(StoreRoutes.ADMIN) {
            AdminRouteScreen(
              uiState = uiState,
              onBackToStore = {
                viewModel.lockAdmin()
                navController.popBackStack()
              },
              onSignInCloud = viewModel::signInAdminCloud,
              onSignOutCloud = viewModel::signOutAdminCloud,
              onSetupPassword = viewModel::setupAdminPassword,
              onUnlock = viewModel::unlockAdmin,
              onChangePassword = viewModel::changeAdminPassword,
              onStartPickedPhotoUpload = viewModel::startPickedPhotoUpload,
              onRetryPickedPhotoUpload = viewModel::retryPickedPhotoUpload,
              onRemovePickedPhoto = viewModel::removePickedPhoto,
              onClearPickedPhotos = viewModel::clearPickedPhotos,
              onSaveProduct = viewModel::saveAdminProduct,
              onDeleteProduct = viewModel::deleteAdminProduct,
              onOrdersTabOpened = viewModel::onAdminOrdersTabOpened,
              onRefreshOrders = viewModel::refreshAdminOrders,
              onUpdateOrderStatus = viewModel::updateAdminOrderStatus,
              onCheckForUpdates = viewModel::checkForUpdatesManually,
              onDismissUpdateDialog = viewModel::dismissManualUpdateDialog,
              onClearNotice = viewModel::clearAdminNotice,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TabIcon(
  tab: TopLevelTab,
  selected: Boolean,
  cartCount: Int,
) {
  val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
  val label = stringResource(tab.labelRes)
  if (tab == TopLevelTab.CART && cartCount > 0) {
    BadgedBox(
      badge = {
        Badge(modifier = Modifier.testTag("cart_badge")) {
          Text(text = cartCount.toString())
        }
      }
    ) {
      Icon(imageVector = icon, contentDescription = label)
    }
  } else {
    Icon(imageVector = icon, contentDescription = label)
  }
}
