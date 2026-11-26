package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.AdminOrdersLoadState
import com.example.data.CatalogSyncStatus
import com.example.data.ProductCatalog
import com.example.model.ConfirmedOrder
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategories
import com.example.model.formatOrderTimeForDisplay
import com.example.model.formatPkr
import com.example.model.isValidRemoteImageUrl
import com.example.security.PasswordActionResult
import com.example.ui.components.CachedProductImage
import com.example.viewmodel.ManualUpdateDialogState
import com.example.viewmodel.PickedPhotoItem
import com.example.viewmodel.PickedPhotoStatus
import com.example.viewmodel.StoreUiState

private const val DEFAULT_ADMIN_EMAIL = "adeel.arif0786@gmail.com"

private enum class AdminDashboardTab(val label: String) {
  PRODUCTS("Products"),
  ORDERS("Orders"),
}

@Composable
fun AdminRouteScreen(
  uiState: StoreUiState,
  onBackToStore: () -> Unit,
  onSignInCloud: (email: String, password: String) -> Unit,
  onSignOutCloud: () -> Unit,
  onSetupPassword: (String, String) -> PasswordActionResult,
  onUnlock: (String) -> Boolean,
  onChangePassword: (String, String, String) -> PasswordActionResult,
  onStartPickedPhotoUpload: (String) -> Unit,
  onRetryPickedPhotoUpload: (String) -> Unit,
  onRemovePickedPhoto: (String) -> Unit,
  onClearPickedPhotos: () -> Unit,
  onSaveProduct: (
    editingId: String?,
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
    onResult: (Result<Unit>) -> Unit,
  ) -> Unit,
  onDeleteProduct: (String) -> Unit,
  onOrdersTabOpened: () -> Unit,
  onRefreshOrders: () -> Unit,
  onUpdateOrderStatus: (String, OrderStatus) -> Unit,
  onCheckForUpdates: () -> Unit,
  onDismissUpdateDialog: () -> Unit,
  onClearNotice: () -> Unit,
) {
  BackHandler(onBack = onBackToStore)

  // Two-step Admin Gate:
  // Step 1: Firebase Cloud Sign-In FIRST (required for Firestore admin writes & orders reads)
  // Step 2: Device-local owner password SECOND
  when {
    uiState.cloudAdminEmail.isNullOrBlank() -> {
      AdminCloudSignInScreen(
        isBusy = uiState.isCloudAuthBusy,
        errorMessage = uiState.cloudAuthError,
        onBack = onBackToStore,
        onSignIn = onSignInCloud,
      )
    }
    !uiState.isAdminPasswordChecked -> {
      AdminCheckingGateScreen(onBack = onBackToStore)
    }
    !uiState.isAdminPasswordSet -> {
      AdminPasswordSetupScreen(
        cloudEmail = uiState.cloudAdminEmail,
        onBack = onBackToStore,
        onSignOutCloud = onSignOutCloud,
        onSetupPassword = onSetupPassword,
      )
    }
    !uiState.isAdminUnlocked -> {
      AdminPasswordLoginScreen(
        cloudEmail = uiState.cloudAdminEmail,
        onBack = onBackToStore,
        onSignOutCloud = onSignOutCloud,
        onUnlock = onUnlock,
      )
    }
    else -> {
      AdminDashboardScreen(
        uiState = uiState,
        onExitAdmin = onBackToStore,
        onSignOutCloud = onSignOutCloud,
        onChangePassword = onChangePassword,
        onStartPickedPhotoUpload = onStartPickedPhotoUpload,
        onRetryPickedPhotoUpload = onRetryPickedPhotoUpload,
        onRemovePickedPhoto = onRemovePickedPhoto,
        onClearPickedPhotos = onClearPickedPhotos,
        onSaveProduct = onSaveProduct,
        onDeleteProduct = onDeleteProduct,
        onOrdersTabOpened = onOrdersTabOpened,
        onRefreshOrders = onRefreshOrders,
        onUpdateOrderStatus = onUpdateOrderStatus,
        onCheckForUpdates = onCheckForUpdates,
        onDismissUpdateDialog = onDismissUpdateDialog,
        onClearNotice = onClearNotice,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminCheckingGateScreen(onBack: () -> Unit) {
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
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminCloudSignInScreen(
  isBusy: Boolean,
  errorMessage: String?,
  onBack: () -> Unit,
  onSignIn: (email: String, password: String) -> Unit,
) {
  var email by remember { mutableStateOf(DEFAULT_ADMIN_EMAIL) }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }

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
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(20.dp)
          .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier =
          Modifier.widthIn(max = 480.dp)
            .fillMaxWidth()
            .testTag("admin_cloud_signin_screen"),
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
              imageVector = Icons.Filled.Email,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
          }

          Text(
            text = "Step 1: Admin Cloud Sign In",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )

          Text(
            text =
              "Sign in with your GharBazar Firebase admin account to manage live products and view customer orders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          OutlinedTextField(
            value = email,
            onValueChange = {
              email = it
              localError = null
            },
            label = { Text("Admin Email") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
              ),
            modifier = Modifier.fillMaxWidth().testTag("admin_cloud_email_input"),
          )

          OutlinedTextField(
            value = password,
            onValueChange = {
              password = it
              localError = null
            },
            label = { Text("Firebase Account Password") },
            singleLine = true,
            enabled = !isBusy,
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
            modifier = Modifier.fillMaxWidth().testTag("admin_cloud_password_input"),
          )

          val combinedError = localError ?: errorMessage
          if (!combinedError.isNullOrBlank()) {
            Text(
              text = combinedError,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Button(
            onClick = {
              when {
                email.trim().isEmpty() -> localError = "Please enter your admin email."
                password.isEmpty() -> localError = "Please enter your Firebase password."
                else -> onSignIn(email, password)
              }
            },
            enabled = !isBusy,
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("admin_cloud_signin_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Signing In…")
            } else {
              Text("Sign In to Cloud")
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPasswordSetupScreen(
  cloudEmail: String,
  onBack: () -> Unit,
  onSignOutCloud: () -> Unit,
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
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(20.dp)
          .verticalScroll(rememberScrollState()),
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
            text = "Step 2: Set your admin password",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )

          Text(
            text =
              "Signed in to cloud as $cloudEmail. Create a private local password (minimum 6 characters) to lock this Seller / Admin panel on your phone.",
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

          TextButton(onClick = onSignOutCloud) {
            Text("Sign out of $cloudEmail")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPasswordLoginScreen(
  cloudEmail: String,
  onBack: () -> Unit,
  onSignOutCloud: () -> Unit,
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
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(20.dp)
          .verticalScroll(rememberScrollState()),
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
            text = "Step 2: Unlock Admin Panel",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )

          Text(
            text = "Cloud account $cloudEmail signed in. Enter your device admin password to continue.",
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

          TextButton(onClick = onSignOutCloud) {
            Text("Sign out of $cloudEmail")
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
  onSignOutCloud: () -> Unit,
  onChangePassword: (String, String, String) -> PasswordActionResult,
  onStartPickedPhotoUpload: (String) -> Unit,
  onRetryPickedPhotoUpload: (String) -> Unit,
  onRemovePickedPhoto: (String) -> Unit,
  onClearPickedPhotos: () -> Unit,
  onSaveProduct: (
    editingId: String?,
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
    onResult: (Result<Unit>) -> Unit,
  ) -> Unit,
  onDeleteProduct: (String) -> Unit,
  onOrdersTabOpened: () -> Unit,
  onRefreshOrders: () -> Unit,
  onUpdateOrderStatus: (String, OrderStatus) -> Unit,
  onCheckForUpdates: () -> Unit,
  onDismissUpdateDialog: () -> Unit,
  onClearNotice: () -> Unit,
) {
  val context = LocalContext.current
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

  // Manual/on-demand orders loading: fetch ONLY when the Admin opens the Orders tab.
  LaunchedEffect(selectedTab) {
    if (selectedTab == AdminDashboardTab.ORDERS) {
      onOrdersTabOpened()
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
                  "${uiState.allProducts.size} products • ${uiState.loadedAdminOrdersCount} orders",
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
              Text("Password")
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
                AdminDashboardTab.ORDERS -> uiState.loadedAdminOrdersCount
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
            onClearPickedPhotos()
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
            AdminCloudSyncCard(
              syncStatus = uiState.syncStatus,
              cloudAdminEmail = uiState.cloudAdminEmail.orEmpty(),
              isCheckingForUpdates = uiState.isCheckingForUpdates,
              onCheckForUpdates = onCheckForUpdates,
              onSignOut = onSignOutCloud,
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
                    "Tap + to add a blanket, men's unstitched suit, or any custom category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          } else {
            items(items = uiState.allProducts, key = { it.id }) { product ->
              AdminProductRowCard(
                product = product,
                onEdit = {
                  onClearPickedPhotos()
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
          item(key = "orders_header_controls") {
            AdminOrdersHeaderRow(
              ordersState = uiState.adminOrdersState,
              onRefresh = onRefreshOrders,
            )
          }

          when (val ordersState = uiState.adminOrdersState) {
            AdminOrdersLoadState.Idle,
            AdminOrdersLoadState.Loading -> {
              item(key = "orders_loading") {
                Card(
                  modifier = Modifier.fillMaxWidth().testTag("admin_orders_loading"),
                  colors =
                    CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    CircularProgressIndicator()
                    Text(
                      text = "Loading COD orders from Firestore…",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            is AdminOrdersLoadState.Error -> {
              item(key = "orders_error") {
                Card(
                  modifier = Modifier.fillMaxWidth().testTag("admin_orders_error"),
                  colors =
                    CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CloudOff,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(36.dp),
                    )
                    Text(
                      text = "Couldn't load orders, tap Refresh",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      textAlign = TextAlign.Center,
                    )
                    Text(
                      text = ordersState.message,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                      textAlign = TextAlign.Center,
                    )
                    Button(
                      onClick = onRefreshOrders,
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("admin_orders_error_retry_button"),
                    ) {
                      Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Refresh")
                    }
                  }
                }
              }
            }

            is AdminOrdersLoadState.Loaded -> {
              if (ordersState.orders.isEmpty()) {
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
                        "Tap Refresh anytime to fetch new Cash on Delivery orders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                      )
                    }
                  }
                }
              } else {
                items(
                  items = ordersState.orders,
                  key = { it.documentId.ifBlank { it.orderNumber } },
                ) { order ->
                  AdminOrderCard(
                    order = order,
                    onUpdateStatus = { newStatus ->
                      onUpdateOrderStatus(order.documentId.ifBlank { order.orderNumber }, newStatus)
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (isCreatingNew || showEditorForProduct != null) {
    AdminProductEditorDialog(
      initialProduct = showEditorForProduct,
      pickedPhotos = uiState.pickedPhotos,
      isSaving = uiState.isProductSaving,
      onStartPickedPhotoUpload = onStartPickedPhotoUpload,
      onRetryPickedPhotoUpload = onRetryPickedPhotoUpload,
      onRemovePickedPhoto = onRemovePickedPhoto,
      onDismiss = {
        onClearPickedPhotos()
        isCreatingNew = false
        showEditorForProduct = null
      },
      onSave = {
        name,
        price,
        category,
        fabric,
        size,
        desc,
        imgKey,
        typedUrls,
        uploadedUrls,
        inStock,
        stockQty,
        onSaveDone ->
        onSaveProduct(
          showEditorForProduct?.id,
          name,
          price,
          category,
          fabric,
          size,
          desc,
          imgKey,
          typedUrls,
          uploadedUrls,
          inStock,
          stockQty,
        ) { result ->
          if (result.isSuccess) {
            onClearPickedPhotos()
            isCreatingNew = false
            showEditorForProduct = null
          }
          onSaveDone(result)
        }
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

  uiState.manualUpdateDialogState?.let { dialogState ->
    ManualUpdateResultDialog(
      state = dialogState,
      onOpenUrl = { url ->
        runCatching {
          val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          context.startActivity(intent)
        }
        onDismissUpdateDialog()
      },
      onDismiss = onDismissUpdateDialog,
    )
  }
}

@Composable
private fun AdminCloudSyncCard(
  syncStatus: CatalogSyncStatus,
  cloudAdminEmail: String,
  isCheckingForUpdates: Boolean,
  onCheckForUpdates: () -> Unit,
  onSignOut: () -> Unit,
) {
  val (title, subtitle, isLive) =
    when (syncStatus) {
      CatalogSyncStatus.Loading ->
        Triple(
          "Connecting to Firestore…",
          "Syncing product catalogue with GharBazar cloud.",
          true,
        )
      CatalogSyncStatus.SyncedLive ->
        Triple(
          "Firestore Cloud Sync Active",
          "Realtime product updates are live.",
          true,
        )
      CatalogSyncStatus.OfflineCached ->
        Triple(
          "Offline Cache Active",
          "Showing cached Firestore catalogue; changes sync when online.",
          true,
        )
      is CatalogSyncStatus.Error ->
        Triple(
          "Cloud Sync Error",
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
          else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
      ),
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
          imageVector = if (isLive) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
          contentDescription = null,
          tint =
            if (isLive) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
          if (cloudAdminEmail.isNotBlank()) {
            Text(
              text = "Signed in: $cloudAdminEmail",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onCheckForUpdates,
          enabled = !isCheckingForUpdates,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("admin_check_updates_button"),
        ) {
          if (isCheckingForUpdates) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Checking…", maxLines = 1)
          } else {
            Icon(
              imageVector = Icons.Filled.SystemUpdate,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Check for Updates", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }

        OutlinedButton(
          onClick = onSignOut,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("admin_sign_out_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Sign out", maxLines = 1)
        }
      }
    }
  }
}

@Composable
private fun AdminOrdersHeaderRow(
  ordersState: AdminOrdersLoadState,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Customer COD Orders",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Newest first • Manual refresh",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    OutlinedButton(
      onClick = onRefresh,
      enabled = ordersState !is AdminOrdersLoadState.Loading,
      modifier =
        Modifier.minimumInteractiveComponentSize().testTag("admin_refresh_orders_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = "Refresh orders",
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text("Refresh")
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
        imageUri = product.coverImageUrl,
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
          if (product.galleryImageUrls.size > 1) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = CircleShape,
            ) {
              Text(
                text = "${product.galleryImageUrls.size} photos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
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
    remember(order.timestampIso, order.timestampMillis) {
      formatOrderTimeForDisplay(order.timestampIso, order.timestampMillis)
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
  pickedPhotos: List<PickedPhotoItem>,
  isSaving: Boolean,
  onStartPickedPhotoUpload: (String) -> Unit,
  onRetryPickedPhotoUpload: (String) -> Unit,
  onRemovePickedPhoto: (String) -> Unit,
  onDismiss: () -> Unit,
  onSave: (
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
    onResult: (Result<Unit>) -> Unit,
  ) -> Unit,
) {
  var name by remember(initialProduct) { mutableStateOf(initialProduct?.name.orEmpty()) }
  var priceText by remember(initialProduct) {
    mutableStateOf(initialProduct?.pricePkr?.toString().orEmpty())
  }
  var categoryText by remember(initialProduct) {
    mutableStateOf(initialProduct?.category ?: ProductCategories.BLANKETS)
  }
  var fabric by remember(initialProduct) { mutableStateOf(initialProduct?.fabric.orEmpty()) }
  var size by remember(initialProduct) { mutableStateOf(initialProduct?.size.orEmpty()) }
  var description by remember(initialProduct) {
    mutableStateOf(initialProduct?.description.orEmpty())
  }
  var imageKey by remember(initialProduct) {
    mutableStateOf(initialProduct?.imageKey ?: ProductCatalog.photoOptions.first().key)
  }
  var photoUrlsText by remember(initialProduct) {
    mutableStateOf(initialProduct?.galleryImageUrls?.joinToString("\n").orEmpty())
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
          errorText = null
          onStartPickedPhotoUpload(uri.toString())
        }
      },
    )

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
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
          listOf(ProductCategories.BLANKETS, ProductCategories.MENS_SUITS).forEach { cat ->
            FilterChip(
              selected = categoryText.equals(cat, ignoreCase = true),
              onClick = {
                categoryText = cat
                errorText = null
              },
              label = { Text(cat) },
            )
          }
        }
        OutlinedTextField(
          value = categoryText,
          onValueChange = {
            categoryText = it
            errorText = null
          },
          label = { Text("Category Name (or type custom)") },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.Words,
              imeAction = ImeAction.Next,
            ),
          modifier = Modifier.fillMaxWidth().testTag("admin_product_category_input"),
        )

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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text("Product Photos", style = MaterialTheme.typography.labelLarge)

        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.fillMaxWidth().testTag("admin_pick_photo_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Choose Photo from Gallery")
        }

        if (pickedPhotos.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pickedPhotos.forEach { item ->
              PickedPhotoStatusRow(
                item = item,
                fallbackImageRes =
                  ProductCatalog.resolveDrawableForKey(imageKey, categoryText),
                onRetry = { onRetryPickedPhotoUpload(item.id) },
                onRemove = { onRemovePickedPhoto(item.id) },
              )
            }
          }
        }

        OutlinedTextField(
          value = photoUrlsText,
          onValueChange = {
            photoUrlsText = it
            errorText = null
          },
          label = { Text("Photo URLs (one per line)") },
          placeholder = { Text("https://res.cloudinary.com/...") },
          minLines = 2,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth().testTag("admin_product_photo_urls_input"),
        )

        Text(
          text = "Fallback Placeholder Icon",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ProductCatalog.photoOptions.forEach { option ->
            val isSelected = imageKey == option.key
            Box(
              modifier =
                Modifier.size(56.dp)
                  .clip(MaterialTheme.shapes.small)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color =
                      if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.small,
                  )
                  .clickable { imageKey = option.key },
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
        enabled = !isSaving,
        onClick = {
          val parsedPrice = priceText.toIntOrNull()
          val parsedQty = stockQtyText.toIntOrNull() ?: 0
          val typedLines =
            photoUrlsText
              .lines()
              .map { it.trim() }
              .filter { it.isNotEmpty() }
          val hasUploadingPhoto = pickedPhotos.any { it.status is PickedPhotoStatus.Uploading }
          val hasFailedPhoto = pickedPhotos.any { it.status is PickedPhotoStatus.Failed }
          val hasLocalUri =
            typedLines.any {
              it.startsWith("content://", ignoreCase = true) ||
                it.startsWith("file://", ignoreCase = true)
            }
          val hasInvalidUrl = typedLines.any { !isValidRemoteImageUrl(it) }

          when {
            name.isBlank() -> errorText = "Product name is required."
            parsedPrice == null || parsedPrice <= 0 ->
              errorText = "Enter a valid price in PKR."
            categoryText.isBlank() -> errorText = "Category is required."
            fabric.isBlank() -> errorText = "Fabric detail is required."
            size.isBlank() -> errorText = "Size / cutting detail is required."
            description.isBlank() -> errorText = "Full description is required."
            inStock && parsedQty <= 0 -> errorText = "Enter stock quantity greater than 0."
            hasUploadingPhoto -> errorText = "Uploading photo… please wait before saving."
            hasFailedPhoto ->
              errorText = "A picked photo failed to upload. Retry or remove it before saving."
            hasLocalUri ->
              errorText =
                "Local content:// or file:// URIs cannot be saved. Choose from Gallery to upload or use https:// URLs."
            hasInvalidUrl ->
              errorText = "Every Photo URL must start with https:// or http://."
            else -> {
              val uploadedUrls =
                pickedPhotos.mapNotNull { (it.status as? PickedPhotoStatus.Uploaded)?.secureUrl }
              onSave(
                name,
                parsedPrice,
                categoryText,
                fabric,
                size,
                description,
                imageKey,
                typedLines,
                uploadedUrls,
                inStock,
                parsedQty,
              ) { result ->
                if (result.isFailure) {
                  errorText =
                    result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                      ?: "Could not save product."
                }
              }
            }
          }
        },
        modifier = Modifier.testTag("admin_save_product_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Saving…")
        } else {
          Text("Save Product")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSaving) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun PickedPhotoStatusRow(
  item: PickedPhotoItem,
  fallbackImageRes: Int,
  onRetry: () -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth().testTag("picked_photo_${item.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      CachedProductImage(
        imageRes = fallbackImageRes,
        imageUri =
          when (val status = item.status) {
            is PickedPhotoStatus.Uploaded -> status.secureUrl
            else -> item.localUri
          },
        contentDescription = "Selected product photo",
        cacheKey = "picked_${item.id}",
        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
      )

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (val status = item.status) {
          PickedPhotoStatus.Uploading -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
              Text(
                text = "Uploading photo…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          is PickedPhotoStatus.Uploaded -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Uploaded to cloud",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          is PickedPhotoStatus.Failed -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = status.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      if (item.status is PickedPhotoStatus.Failed) {
        IconButton(
          onClick = onRetry,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Retry upload",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      IconButton(
        onClick = onRemove,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("remove_picked_photo_${item.id}"),
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Remove photo",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ManualUpdateResultDialog(
  state: ManualUpdateDialogState,
  onOpenUrl: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  when (state) {
    is ManualUpdateDialogState.Available -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
          Icon(
            imageVector = Icons.Filled.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        title = { Text("Update available") },
        text = {
          Text(
            "Version ${state.info.latestVersionCode} of GharBazar is available. Tap Update to open the download page in your browser, or Later to dismiss."
          )
        },
        confirmButton = {
          Button(
            onClick = { onOpenUrl(state.info.apkUrl) },
            modifier = Modifier.testTag("update_dialog_update_button"),
          ) {
            Text("Update")
          }
        },
        dismissButton = {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("update_dialog_later_button"),
          ) {
            Text("Later")
          }
        },
        modifier = Modifier.testTag("update_available_dialog"),
      )
    }

    ManualUpdateDialogState.UpToDate -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
          )
        },
        title = { Text("App is up to date") },
        text = {
          Text("You are running the latest version of GharBazar.")
        },
        confirmButton = {
          Button(
            onClick = onDismiss,
            modifier = Modifier.testTag("update_uptodate_ok_button"),
          ) {
            Text("OK")
          }
        },
        modifier = Modifier.testTag("update_uptodate_dialog"),
      )
    }

    is ManualUpdateDialogState.Failed -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
          Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        },
        title = { Text("Update check failed") },
        text = {
          Text(state.message)
        },
        confirmButton = {
          Button(onClick = onDismiss) {
            Text("OK")
          }
        },
      )
    }
  }
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
