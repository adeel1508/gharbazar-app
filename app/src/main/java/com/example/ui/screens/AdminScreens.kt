package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CatalogSyncStatus
import com.example.data.GHARBAZAR_ADMIN_EMAIL
import com.example.data.ProductCatalog
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.formatPkr
import com.example.security.PasswordActionResult
import com.example.ui.components.CachedProductImage
import com.example.viewmodel.StoreUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminDashboardTab(val label: String) {
  PRODUCTS("Products"),
  ORDERS("Orders"),
}

/**
 * Parses the admin editor's "Photo URLs (one per line)" field: one URL per
 * line, blank lines ignored, duplicates removed, order kept — the first
 * line is the cover photo.
 */
internal fun parseImageUrls(text: String): List<String> =
  text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()

@Composable
fun AdminRouteScreen(
  uiState: StoreUiState,
  onBackToStore: () -> Unit,
  onSetupPassword: (String, String) -> PasswordActionResult,
  onUnlock: (String) -> Boolean,
  onChangePassword: (String, String, String) -> PasswordActionResult,
  onSaveProduct: (
    editingId: String?,
    name: String,
    pricePkr: Int,
    category: String,
    fabric: String,
    size: String,
    description: String,
    imageKey: String,
    imageUri: String?,
    imageUris: List<String>,
    inStock: Boolean,
    stockQuantity: Int,
  ) -> Unit,
  onDeleteProduct: (String) -> Unit,
  onUpdateOrderStatus: (String, OrderStatus) -> Unit,
  onClearNotice: () -> Unit,
  onAdminSignIn: (String) -> Unit,
  onAdminSignOut: () -> Unit,
) {
  BackHandler(onBack = onBackToStore)

  // Security gate (v1.0.8): when a cloud catalogue is configured, the
  // Firebase admin sign-in comes FIRST — before any local password setup
  // or unlock. Otherwise anyone who installs the app could set their own
  // local password and walk into the admin panel. The local password stays
  // as a second, device-level lock after the cloud sign-in.
  if (uiState.isCloudConfigured && !uiState.isAdminSignedIn) {
    AdminCloudSignInScreen(
      onBack = onBackToStore,
      authError = uiState.adminAuthError,
      onSignIn = onAdminSignIn,
    )
  } else if (!uiState.isAdminPasswordSet) {
    AdminPasswordSetupScreen(
      onBack = onBackToStore,
      onSetupPassword = onSetupPassword,
    )
  } else if (!uiState.isAdminUnlocked) {
    AdminPasswordLoginScreen(
      onBack = onBackToStore,
      onUnlock = onUnlock,
    )
  } else {
    AdminDashboardScreen(
      uiState = uiState,
      onExitAdmin = onBackToStore,
      onChangePassword = onChangePassword,
      onSaveProduct = onSaveProduct,
      onDeleteProduct = onDeleteProduct,
      onUpdateOrderStatus = onUpdateOrderStatus,
      onClearNotice = onClearNotice,
      onAdminSignIn = onAdminSignIn,
      onAdminSignOut = onAdminSignOut,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminCloudSignInScreen(
  onBack: () -> Unit,
  authError: String?,
  onSignIn: (String) -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text("Seller / Admin") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier =
          Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("admin_cloud_signin_screen"),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
          ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.CloudDone,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
          }

          Text(
            text = "Cloud Admin Sign In",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )

          Text(
            text =
              "Only the GharBazar account can open the admin panel. Sign in with your account password first — the device admin password comes after.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Text(
            text = "Account: $GHARBAZAR_ADMIN_EMAIL",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )

          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Account Password") },
            singleLine = true,
            isError = authError != null,
            supportingText = authError?.let { msg -> { Text(msg) } },
            visualTransformation =
              if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                  imageVector =
                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = "Toggle password visibility",
                )
              }
            },
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_cloud_signin_password_input"),
          )

          Button(
            onClick = { onSignIn(password) },
            enabled = password.isNotBlank(),
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("admin_cloud_signin_button"),
          ) {
            Text("Sign in to Cloud Admin")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPasswordSetupScreen(
  onBack: () -> Unit,
  onSetupPassword: (String, String) -> PasswordActionResult,
) {
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text("Seller / Admin") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("admin_setup_screen"),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
          ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
          }

          Text(
            text = "Set your admin password",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )

          Text(
            text =
              "Create a private password (minimum 6 characters) to protect your Seller / Admin panel. It is stored securely as an encrypted hash on this device and cannot be recovered if forgotten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          OutlinedTextField(
            value = password,
            onValueChange = {
              password = it
              errorText = null
            },
            label = { Text("New Admin Password") },
            singleLine = true,
            visualTransformation =
              if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                  imageVector =
                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = "Toggle password visibility",
                )
              }
            },
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_setup_password_input"),
          )

          OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
              confirmPassword = it
              errorText = null
            },
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation =
              if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_setup_confirm_input"),
          )

          if (errorText != null) {
            Text(
              text = errorText!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Button(
            onClick = {
              when (val res = onSetupPassword(password, confirmPassword)) {
                PasswordActionResult.Success -> Unit
                is PasswordActionResult.Error -> errorText = res.message
              }
            },
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("admin_setup_save_button"),
          ) {
            Text("Save Password & Open Admin")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPasswordLoginScreen(
  onBack: () -> Unit,
  onUnlock: (String) -> Boolean,
) {
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text("Seller / Admin") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().testTag("admin_login_screen"),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
          ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
          }

          Text(
            text = "Seller / Admin Sign In",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )

          Text(
            text = "Enter your admin password to manage GharBazar products and stock.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          OutlinedTextField(
            value = password,
            onValueChange = {
              password = it
              errorText = null
            },
            label = { Text("Admin Password") },
            singleLine = true,
            isError = errorText != null,
            supportingText = errorText?.let { msg -> { Text(msg) } },
            visualTransformation =
              if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                  imageVector =
                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = "Toggle password visibility",
                )
              }
            },
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_login_password_input"),
          )

          Button(
            onClick = {
              val ok = onUnlock(password)
              if (!ok) {
                errorText = "Incorrect admin password. Admin panel remains locked."
              }
            },
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("admin_login_submit_button"),
          ) {
            Text("Unlock Admin Panel")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminDashboardScreen(
  uiState: StoreUiState,
  onExitAdmin: () -> Unit,
  onChangePassword: (String, String, String) -> PasswordActionResult,
  onSaveProduct: (
    editingId: String?,
    name: String,
    pricePkr: Int,
    category: String,
    fabric: String,
    size: String,
    description: String,
    imageKey: String,
    imageUri: String?,
    imageUris: List<String>,
    inStock: Boolean,
    stockQuantity: Int,
  ) -> Unit,
  onDeleteProduct: (String) -> Unit,
  onUpdateOrderStatus: (String, OrderStatus) -> Unit,
  onClearNotice: () -> Unit,
  onAdminSignIn: (String) -> Unit,
  onAdminSignOut: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTab by remember { mutableStateOf(AdminDashboardTab.PRODUCTS) }
  var showEditorForProduct by remember { mutableStateOf<Product?>(null) }
  var isCreatingNew by remember { mutableStateOf(false) }
  var pendingDeleteProduct by remember { mutableStateOf<Product?>(null) }
  var showChangePasswordDialog by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.adminNotice) {
    val msg = uiState.adminNotice
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      onClearNotice()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      Column {
        TopAppBar(
          title = {
            Column {
              Text("Seller / Admin Panel", style = MaterialTheme.typography.titleLarge)
              Text(
                text =
                  "${uiState.allProducts.size} products • ${uiState.adminOrders.size} orders",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = onExitAdmin,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_exit_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Admin")
            }
          },
          actions = {
            TextButton(
              onClick = { showChangePasswordDialog = true },
              modifier = Modifier.testTag("change_admin_password_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Change Password")
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            ),
        )

        PrimaryTabRow(
          selectedTabIndex = selectedTab.ordinal,
          containerColor = MaterialTheme.colorScheme.background,
        ) {
          AdminDashboardTab.entries.forEach { tab ->
            val count =
              when (tab) {
                AdminDashboardTab.PRODUCTS -> uiState.allProducts.size
                AdminDashboardTab.ORDERS -> uiState.adminOrders.size
              }
            val tabTag =
              when (tab) {
                AdminDashboardTab.PRODUCTS -> "admin_tab_products"
                AdminDashboardTab.ORDERS -> "admin_tab_orders"
              }
            Tab(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              text = { Text("${tab.label} ($count)") },
              modifier = Modifier.testTag(tabTag),
            )
          }
        }
      }
    },
    floatingActionButton = {
      if (selectedTab == AdminDashboardTab.PRODUCTS) {
        FloatingActionButton(
          onClick = {
            showEditorForProduct = null
            isCreatingNew = true
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier =
            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
              .testTag("admin_add_product_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Add Product")
        }
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      AdminDashboardTab.PRODUCTS -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "sync_status") {
            AdminCloudSyncCard(syncStatus = uiState.syncStatus)
          }

          item(key = "admin_auth") {
            AdminFirebaseSignInCard(
              isSignedIn = uiState.isAdminSignedIn,
              authError = uiState.adminAuthError,
              onSignIn = onAdminSignIn,
              onSignOut = onAdminSignOut,
            )
          }

          if (uiState.allProducts.isEmpty()) {
            item(key = "empty_admin") {
              Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                  ),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text("No products in catalogue", style = MaterialTheme.typography.titleMedium)
                  Text(
                    "Tap + to add a product in any category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(items = uiState.allProducts, key = { it.id }) { product ->
              AdminProductRowCard(
                product = product,
                onEdit = {
                  isCreatingNew = false
                  showEditorForProduct = product
                },
                onDelete = { pendingDeleteProduct = product },
              )
            }
          }
        }
      }

      AdminDashboardTab.ORDERS -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "orders_sync_status") {
            AdminCloudSyncCard(syncStatus = uiState.syncStatus)
          }

          item(key = "admin_auth_orders") {
            AdminFirebaseSignInCard(
              isSignedIn = uiState.isAdminSignedIn,
              authError = uiState.adminAuthError,
              onSignIn = onAdminSignIn,
              onSignOut = onAdminSignOut,
            )
          }

          if (uiState.adminOrders.isEmpty()) {
            item(key = "empty_orders") {
              Card(
                modifier = Modifier.fillMaxWidth().testTag("admin_orders_empty"),
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                  ),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(28.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                  )
                  Text("No COD orders yet", style = MaterialTheme.typography.titleMedium)
                  Text(
                    "Guest Cash on Delivery orders placed at checkout sync here automatically (newest first).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          } else {
            items(items = uiState.adminOrders, key = { it.orderNumber }) { order ->
              AdminOrderCard(
                order = order,
                onUpdateStatus = { newStatus ->
                  onUpdateOrderStatus(order.orderNumber, newStatus)
                },
              )
            }
          }
        }
      }
    }
  }

  if (isCreatingNew || showEditorForProduct != null) {
    AdminProductEditorDialog(
      initialProduct = showEditorForProduct,
      onDismiss = {
        isCreatingNew = false
        showEditorForProduct = null
      },
      onSave = { name, price, category, fabric, size, desc, imgKey, imgUri, imgUris, inStock, stockQty ->
        onSaveProduct(
          showEditorForProduct?.id,
          name,
          price,
          category,
          fabric,
          size,
          desc,
          imgKey,
          imgUri,
          imgUris,
          inStock,
          stockQty,
        )
        isCreatingNew = false
        showEditorForProduct = null
      },
    )
  }

  pendingDeleteProduct?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDeleteProduct = null },
      title = { Text("Delete Product?") },
      text = {
        Text(
          "Are you sure you want to delete \"${target.name}\"? This will remove it from the GharBazar store immediately."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteProduct(target.id)
            pendingDeleteProduct = null
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error
            ),
          modifier = Modifier.testTag("confirm_delete_product_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteProduct = null }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showChangePasswordDialog) {
    ChangeAdminPasswordDialog(
      onDismiss = { showChangePasswordDialog = false },
      onChangePassword = onChangePassword,
    )
  }
}

@Composable
private fun AdminCloudSyncCard(syncStatus: CatalogSyncStatus) {
  val (title, subtitle, isLive) =
    when (syncStatus) {
      CatalogSyncStatus.SyncedLive ->
        Triple(
          "Firestore Cloud Sync Active (Spark Plan)",
          "Realtime updates are synced with Cloud Firestore.",
          true,
        )
      CatalogSyncStatus.OfflineCached ->
        Triple(
          "Offline Cache Active",
          "Showing cached Firestore catalogue; changes sync automatically when online.",
          true,
        )
      is CatalogSyncStatus.LocalFallback ->
        Triple(
          "Local Catalogue Mode (Cloud Sync Not Linked)",
          syncStatus.message,
          false,
        )
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isLive) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
          else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
      ),
    shape = MaterialTheme.shapes.medium,
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = if (isLive) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
        contentDescription = null,
        tint =
          if (isLive) MaterialTheme.colorScheme.tertiary
          else MaterialTheme.colorScheme.secondary,
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Firebase sign-in for cloud admin work. This is separate from the local
 * admin password above it: the password only unlocks this panel on this
 * phone, while this sign-in proves to Firestore that the user is the admin
 * (adeel.arif0786@gmail.com), which the security rules require before any
 * product add/edit/delete or order-status change is accepted. The session
 * persists, so the owner signs in once.
 */
@Composable
private fun AdminFirebaseSignInCard(
  isSignedIn: Boolean,
  authError: String?,
  onSignIn: (String) -> Unit,
  onSignOut: () -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }

  LaunchedEffect(isSignedIn) {
    if (isSignedIn) {
      password = ""
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("admin_firebase_signin_card"),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = if (isSignedIn) Icons.Filled.CloudDone else Icons.Filled.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              if (isSignedIn) "Signed in to Cloud Admin" else "Cloud Admin Sign In",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (isSignedIn) {
                "Signed in as $GHARBAZAR_ADMIN_EMAIL — product and order changes are unlocked."
              } else {
                "Sign in once with your GharBazar account to add, edit or delete products and to see orders here."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (isSignedIn) {
          TextButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("admin_firebase_signout_button"),
          ) {
            Text("Sign out")
          }
        }
      }

      if (!isSignedIn) {
        Text(
          text = "Account: $GHARBAZAR_ADMIN_EMAIL",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
          },
          label = { Text("Account Password") },
          singleLine = true,
          isError = authError != null,
          supportingText = authError?.let { msg -> { Text(msg) } },
          visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(
                imageVector =
                  if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = "Toggle password visibility",
              )
            }
          },
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done,
            ),
          modifier = Modifier.fillMaxWidth().testTag("admin_firebase_password_input"),
        )
        Button(
          onClick = { onSignIn(password) },
          enabled = password.isNotBlank(),
          modifier =
            Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag("admin_firebase_signin_button"),
        ) {
          Text("Sign in to Cloud Admin")
        }
      }
    }
  }
}

@Composable
private fun AdminProductRowCard(
  product: Product,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("admin_product_row_${product.id}"),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.medium,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CachedProductImage(
        imageRes = product.imageRes,
        imageUri = product.imageUri,
        contentDescription = product.name,
        cacheKey = product.id,
        modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small),
        contentScale = ContentScale.Crop,
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = product.name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${formatPkr(product.pricePkr)} • ${product.category}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "${product.fabric} • ${product.size}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Surface(
          color =
            if (product.isAvailable) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.errorContainer,
          shape = CircleShape,
        ) {
          Text(
            text =
              if (product.isAvailable) "In Stock (${product.stockQuantity})"
              else "Out of Stock",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (product.isAvailable) MaterialTheme.colorScheme.onTertiaryContainer
              else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        IconButton(
          onClick = onEdit,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("admin_edit_${product.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit ${product.name}",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onDelete,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("admin_delete_${product.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete ${product.name}",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun AdminOrderCard(
  order: ConfirmedOrder,
  onUpdateStatus: (OrderStatus) -> Unit,
) {
  val formattedTime =
    remember(order.timestamp) {
      if (order.timestamp <= 0L) {
        "Time unavailable"
      } else {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
      }
    }

  val (badgeBg, badgeFg) =
    when (order.status) {
      OrderStatus.PLACED ->
        MaterialTheme.colorScheme.secondaryContainer to
          MaterialTheme.colorScheme.onSecondaryContainer
      OrderStatus.CONFIRMED ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      OrderStatus.SHIPPED ->
        MaterialTheme.colorScheme.tertiaryContainer to
          MaterialTheme.colorScheme.onTertiaryContainer
      OrderStatus.DELIVERED ->
        MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("admin_order_card_${order.orderNumber}"),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = order.orderNumber,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = formattedTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Surface(
          color = badgeBg,
          contentColor = badgeFg,
          shape = CircleShape,
        ) {
          Text(
            text = order.status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = order.customerName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Phone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = order.phoneNumber,
            style = MaterialTheme.typography.bodyMedium,
          )
        }

        Row(
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
          )
          Text(
            text = listOf(order.deliveryAddress, order.city).filter { it.isNotBlank() }.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Items",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        order.items.forEach { item ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "${item.quantity}x ${item.product.name}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${formatPkr(item.product.pricePkr)} each",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = formatPkr(item.lineTotalPkr),
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Total (COD)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = formatPkr(order.totalPkr),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Order Status",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OrderStatus.entries.forEach { status ->
            val selected = order.status == status
            FilterChip(
              selected = selected,
              onClick = {
                if (!selected) {
                  onUpdateStatus(status)
                }
              },
              leadingIcon =
                if (selected) {
                  {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                } else {
                  null
                },
              label = { Text(status.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("order_status_${order.orderNumber}_${status.wireValue}"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AdminProductEditorDialog(
  initialProduct: Product?,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    pricePkr: Int,
    category: String,
    fabric: String,
    size: String,
    description: String,
    imageKey: String,
    imageUri: String?,
    imageUris: List<String>,
    inStock: Boolean,
    stockQuantity: Int,
  ) -> Unit,
) {
  val context = LocalContext.current
  var name by remember(initialProduct) { mutableStateOf(initialProduct?.name.orEmpty()) }
  var priceText by remember(initialProduct) {
    mutableStateOf(initialProduct?.pricePkr?.toString().orEmpty())
  }
  var categoryLabel by remember(initialProduct) {
    mutableStateOf(
      initialProduct?.category?.takeIf { ProductCategory.isKnownLabel(it) }
        ?: ProductCategory.BLANKETS.label
    )
  }
  var useCustomCategory by remember(initialProduct) {
    mutableStateOf(
      initialProduct != null && !ProductCategory.isKnownLabel(initialProduct.category)
    )
  }
  var customCategory by remember(initialProduct) {
    mutableStateOf(
      if (initialProduct != null && !ProductCategory.isKnownLabel(initialProduct.category)) {
        initialProduct.category
      } else {
        ""
      }
    )
  }
  var fabric by remember(initialProduct) { mutableStateOf(initialProduct?.fabric.orEmpty()) }
  var size by remember(initialProduct) { mutableStateOf(initialProduct?.size.orEmpty()) }
  var description by remember(initialProduct) {
    mutableStateOf(initialProduct?.description.orEmpty())
  }
  var imageKey by remember(initialProduct) {
    mutableStateOf(initialProduct?.imageKey ?: ProductCatalog.photoOptions.first().key)
  }
  var imageUri by remember(initialProduct) { mutableStateOf(initialProduct?.imageUri) }
  var imageUrlsText by remember(initialProduct) {
    mutableStateOf(
      (initialProduct?.imageUris?.ifEmpty { listOfNotNull(initialProduct.imageUri) })
        ?.joinToString("\n")
        .orEmpty()
    )
  }
  var inStock by remember(initialProduct) { mutableStateOf(initialProduct?.inStock ?: true) }
  var stockQtyText by remember(initialProduct) {
    mutableStateOf((initialProduct?.stockQuantity ?: 20).toString())
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri ->
        if (uri != null) {
          runCatching {
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          }
          imageUri = uri.toString()
        }
      },
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (initialProduct == null) "Add Product" else "Edit Product",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            errorText = null
          },
          label = { Text("Product Name") },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.Words,
              imeAction = ImeAction.Next,
            ),
          modifier = Modifier.fillMaxWidth().testTag("admin_product_name_input"),
        )

        OutlinedTextField(
          value = priceText,
          onValueChange = { input ->
            priceText = input.filter { it.isDigit() }
            errorText = null
          },
          label = { Text("Price (PKR)") },
          prefix = { Text("Rs ") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth().testTag("admin_product_price_input"),
        )

        Text(
          text = "Category",
          style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(ProductCategory.BLANKETS, ProductCategory.MENS_SUITS).forEach { cat ->
            FilterChip(
              selected = !useCustomCategory && categoryLabel == cat.label,
              onClick = {
                useCustomCategory = false
                categoryLabel = cat.label
                errorText = null
              },
              label = { Text(cat.label) },
              modifier = Modifier.testTag("admin_category_chip_${cat.name.lowercase()}"),
            )
          }
          FilterChip(
            selected = useCustomCategory,
            onClick = {
              useCustomCategory = true
              errorText = null
            },
            label = { Text("Other…") },
            modifier = Modifier.testTag("admin_category_chip_other"),
          )
        }

        if (useCustomCategory) {
          OutlinedTextField(
            value = customCategory,
            onValueChange = {
              customCategory = it
              errorText = null
            },
            label = { Text("Custom Category (e.g. Bedsheets, Curtains)") },
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_product_custom_category_input"),
          )
        }

        OutlinedTextField(
          value = fabric,
          onValueChange = {
            fabric = it
            errorText = null
          },
          label = { Text("Fabric (e.g. Fleece, Boski, Wool, Cotton)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_product_fabric_input"),
        )

        OutlinedTextField(
          value = size,
          onValueChange = {
            size = it
            errorText = null
          },
          label = { Text("Size / Cutting (e.g. Double Bed 89x90 in, 4m)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_product_size_input"),
        )

        OutlinedTextField(
          value = description,
          onValueChange = {
            description = it
            errorText = null
          },
          label = { Text("Full Description") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth().testTag("admin_product_desc_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Availability", style = MaterialTheme.typography.labelLarge)
            Text(
              text = if (inStock) "In Stock" else "Out of Stock",
              style = MaterialTheme.typography.bodySmall,
              color =
                if (inStock) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
            )
          }
          Switch(
            checked = inStock,
            onCheckedChange = { inStock = it },
            modifier = Modifier.testTag("admin_product_instock_switch"),
          )
        }

        if (inStock) {
          OutlinedTextField(
            value = stockQtyText,
            onValueChange = { input -> stockQtyText = input.filter { it.isDigit() } },
            label = { Text("Stock Quantity") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("admin_product_qty_input"),
          )
        }

        Text("Product Photo", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ProductCatalog.photoOptions.forEach { option ->
            val isSelected = imageUri == null && imageKey == option.key
            Box(
              modifier =
                Modifier.size(60.dp)
                  .clip(MaterialTheme.shapes.small)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color =
                      if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.small,
                  )
                  .clickable {
                    imageUri = null
                    imageKey = option.key
                  },
            ) {
              CachedProductImage(
                imageRes = option.imageRes,
                contentDescription = option.label,
                cacheKey = "picker_${option.key}",
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }

        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (imageUri != null) "Custom Photo Selected (Change)"
            else "Choose Photo from Gallery"
          )
        }

        if (imageUri != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
              text = "Custom gallery image attached",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { imageUri = null }) {
              Text("Reset")
            }
          }
        }

        OutlinedTextField(
          value = imageUrlsText,
          onValueChange = {
            imageUrlsText = it
            errorText = null
          },
          label = { Text("Photo URLs (one per line)") },
          placeholder = { Text("https://... (first URL is the cover photo)") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth().testTag("admin_product_image_urls_input"),
        )
        Text(
          text =
            "Add up to 4 photo links, one per line — the detail screen shows them as a swipeable gallery. A built-in photo above is used only when no photo URL is set.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedPrice = priceText.toIntOrNull()
          val parsedQty = stockQtyText.toIntOrNull() ?: 0
          val effectiveCategory =
            if (useCustomCategory) customCategory.trim() else categoryLabel
          when {
            name.isBlank() -> errorText = "Product name is required."
            parsedPrice == null || parsedPrice <= 0 ->
              errorText = "Enter a valid price in PKR."
            useCustomCategory && effectiveCategory.isBlank() ->
              errorText = "Enter a custom category name."
            effectiveCategory.equals(ProductCategory.ALL.label, ignoreCase = true) ->
              errorText = "\"All\" cannot be used as a category name."
            effectiveCategory.length > 40 ->
              errorText = "Category name must be 40 characters or fewer."
            fabric.isBlank() -> errorText = "Fabric detail is required."
            size.isBlank() -> errorText = "Size / cutting detail is required."
            description.isBlank() -> errorText = "Full description is required."
            inStock && parsedQty <= 0 -> errorText = "Enter stock quantity greater than 0."
            else -> {
              // Gallery: a photo picked from the device first, then the
              // URLs from the text field. First entry is the cover photo.
              val gallery =
                buildList {
                  imageUri?.takeIf { it.isNotBlank() }?.let { add(it) }
                  addAll(parseImageUrls(imageUrlsText))
                }.distinct()
              onSave(
                name,
                parsedPrice,
                effectiveCategory,
                fabric,
                size,
                description,
                imageKey,
                gallery.firstOrNull(),
                gallery,
                inStock,
                parsedQty,
              )
            }
          }
        },
        modifier = Modifier.testTag("admin_save_product_button"),
      ) {
        Text("Save Product")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun ChangeAdminPasswordDialog(
  onDismiss: () -> Unit,
  onChangePassword: (String, String, String) -> PasswordActionResult,
) {
  var currentPassword by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Change admin password") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = currentPassword,
          onValueChange = {
            currentPassword = it
            errorText = null
          },
          label = { Text("Current Password") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("change_pw_current_input"),
        )
        OutlinedTextField(
          value = newPassword,
          onValueChange = {
            newPassword = it
            errorText = null
          },
          label = { Text("New Password (min 6 chars)") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("change_pw_new_input"),
        )
        OutlinedTextField(
          value = confirmPassword,
          onValueChange = {
            confirmPassword = it
            errorText = null
          },
          label = { Text("Confirm New Password") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("change_pw_confirm_input"),
        )
        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when (val res = onChangePassword(currentPassword, newPassword, confirmPassword)) {
            PasswordActionResult.Success -> onDismiss()
            is PasswordActionResult.Error -> errorText = res.message
          }
        },
        modifier = Modifier.testTag("change_pw_save_button"),
      ) {
        Text("Update Password")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
