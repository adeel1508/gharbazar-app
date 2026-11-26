package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CustomerOrdersLoadState
import com.example.model.formatOrderTimeForDisplay
import com.example.model.formatPkr
import com.example.viewmodel.StoreUiState

/**
 * Customer account screen: optional email login/sign-up and the customer's own
 * order history ("My Orders").
 *
 * Login is NEVER required here: guest checkout keeps working without an
 * account. Logging in only links orders to the account, so the record survives
 * an app reinstall and shows on the website too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAccountScreen(
  uiState: StoreUiState,
  onSignUp: (name: String, email: String, password: String) -> Unit,
  onSignIn: (email: String, password: String) -> Unit,
  onSignOut: () -> Unit,
  onMyOrdersOpened: () -> Unit,
  onRefreshMyOrders: () -> Unit,
  onClearAuthError: () -> Unit,
  onBack: () -> Unit,
  onBrowseProducts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val account = uiState.customerAccount

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("account_screen"),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.account_title)) },
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
          .padding(horizontal = 16.dp, vertical = 12.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (account == null) {
          CustomerAuthForm(
            isBusy = uiState.isCustomerAuthBusy,
            errorMessage = uiState.customerAuthError,
            onSignUp = onSignUp,
            onSignIn = onSignIn,
            onClearAuthError = onClearAuthError,
            onBrowseProducts = onBrowseProducts,
          )
        } else {
          CustomerAccountHome(
            uiState = uiState,
            onMyOrdersOpened = onMyOrdersOpened,
            onRefreshMyOrders = onRefreshMyOrders,
            onSignOut = onSignOut,
          )
        }
      }
    }
  }
}

@Composable
private fun CustomerAuthForm(
  isBusy: Boolean,
  errorMessage: String?,
  onSignUp: (name: String, email: String, password: String) -> Unit,
  onSignIn: (email: String, password: String) -> Unit,
  onClearAuthError: () -> Unit,
  onBrowseProducts: () -> Unit,
) {
  var isSignUpMode by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }

  val shownError = localError ?: errorMessage

  Card(
    modifier = Modifier.fillMaxWidth().testTag("account_auth_card"),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Login,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
        Text(
          text =
            if (isSignUpMode) {
              stringResource(R.string.account_signup_title)
            } else {
              stringResource(R.string.account_login_title)
            },
          style = MaterialTheme.typography.headlineSmall,
        )
      }

      Text(
        text = stringResource(R.string.account_login_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (isSignUpMode) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            localError = null
            onClearAuthError()
          },
          label = { Text(stringResource(R.string.field_full_name)) },
          leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.Words,
              imeAction = ImeAction.Next,
            ),
          modifier = Modifier.fillMaxWidth().testTag("account_name_input"),
          shape = MaterialTheme.shapes.small,
        )
      }

      OutlinedTextField(
        value = email,
        onValueChange = {
          email = it
          localError = null
          onClearAuthError()
        },
        label = { Text(stringResource(R.string.field_email)) },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
        singleLine = true,
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
          ),
        modifier = Modifier.fillMaxWidth().testTag("account_email_input"),
        shape = MaterialTheme.shapes.small,
      )

      OutlinedTextField(
        value = password,
        onValueChange = {
          password = it
          localError = null
          onClearAuthError()
        },
        label = { Text(stringResource(R.string.field_password)) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        singleLine = true,
        visualTransformation =
          if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
          ),
        modifier = Modifier.fillMaxWidth().testTag("account_password_input"),
        shape = MaterialTheme.shapes.small,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = {
            showPassword = !showPassword
            localError = null
          },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(if (showPassword) "Hide password" else "Show password")
        }
      }

      if (shownError != null) {
        Text(
          text = shownError,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("account_auth_error"),
        )
      }

      Button(
        onClick = {
          val cleanEmail = email.trim()
          val cleanName = name.trim()
          val validationError =
            when {
              isSignUpMode && cleanName.isEmpty() -> "Please enter your full name."
              cleanEmail.isEmpty() || !cleanEmail.contains("@") ->
                "Please enter a valid email address."
              password.length < 6 -> "Password must be at least 6 characters."
              else -> null
            }
          if (validationError != null) {
            localError = validationError
            return@Button
          }
          localError = null
          if (isSignUpMode) {
            onSignUp(cleanName, cleanEmail, password)
          } else {
            onSignIn(cleanEmail, password)
          }
        },
        enabled = !isBusy,
        modifier =
          Modifier.fillMaxWidth()
            .height(52.dp)
            .testTag(
              if (isSignUpMode) "account_signup_button" else "account_login_button"
            ),
        shape = MaterialTheme.shapes.medium,
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
          text =
            if (isSignUpMode) {
              stringResource(R.string.account_signup_title)
            } else {
              stringResource(R.string.account_login_title)
            },
          style = MaterialTheme.typography.labelLarge,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (isSignUpMode) {
              "Already have an account?"
            } else {
              "New to GharBazar?"
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = {
            isSignUpMode = !isSignUpMode
            localError = null
            onClearAuthError()
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("account_mode_toggle"),
        ) {
          Text(
            if (isSignUpMode) {
              stringResource(R.string.account_login_title)
            } else {
              stringResource(R.string.account_signup_title)
            }
          )
        }
      }
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
    shape = MaterialTheme.shapes.medium,
  ) {
    Text(
      text = stringResource(R.string.account_optional_note),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(16.dp),
    )
  }

  OutlinedButton(
    onClick = onBrowseProducts,
    modifier = Modifier.fillMaxWidth().height(52.dp),
    shape = MaterialTheme.shapes.medium,
  ) {
    Icon(
      imageVector = Icons.Filled.ShoppingBag,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = stringResource(R.string.browse_products))
  }
}

@Composable
private fun CustomerAccountHome(
  uiState: StoreUiState,
  onMyOrdersOpened: () -> Unit,
  onRefreshMyOrders: () -> Unit,
  onSignOut: () -> Unit,
) {
  val account = uiState.customerAccount ?: return

  // Load the customer's own orders when this screen opens (explicit user
  // action only — never on app startup or auth-state change).
  LaunchedEffect(account.uid) { onMyOrdersOpened() }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp),
        )
        Text(
          text = "Signed in",
          style = MaterialTheme.typography.titleLarge,
        )
      }
      Text(
        text = account.displayName?.takeIf { it.isNotBlank() } ?: account.email,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = account.email,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = stringResource(R.string.account_signed_in_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.my_orders_title),
      style = MaterialTheme.typography.titleLarge,
    )
    OutlinedButton(
      onClick = onRefreshMyOrders,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("my_orders_refresh_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(text = stringResource(R.string.refresh))
    }
  }

  when (val ordersState = uiState.myOrdersState) {
    is CustomerOrdersLoadState.Idle,
    is CustomerOrdersLoadState.Loading -> {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }
    is CustomerOrdersLoadState.Error -> {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = ordersState.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onRefreshMyOrders,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("my_orders_retry_button"),
          ) {
            Text(text = stringResource(R.string.refresh))
          }
        }
      }
    }
    is CustomerOrdersLoadState.Loaded -> {
      if (ordersState.orders.isEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
          shape = MaterialTheme.shapes.medium,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.ShoppingBag,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(40.dp),
            )
            Text(
              text = stringResource(R.string.account_no_orders_title),
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
            )
            Text(
              text = stringResource(R.string.account_no_orders_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      } else {
        Column(
          modifier = Modifier.testTag("my_orders_list"),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ordersState.orders.forEach { order ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              shape = MaterialTheme.shapes.medium,
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = order.orderNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = order.status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Text(
                  text =
                    formatOrderTimeForDisplay(order.timestampIso, order.timestampMillis),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                  text =
                    "${order.items.sumOf { it.quantity }} item(s) • ${formatPkr(order.totalPkr)}",
                  style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                  text =
                    order.items.joinToString(", ") { it.product.name },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }

  OutlinedButton(
    onClick = onSignOut,
    modifier =
      Modifier.fillMaxWidth().height(52.dp).testTag("account_signout_button"),
    shape = MaterialTheme.shapes.medium,
  ) {
    Text(text = stringResource(R.string.sign_out))
  }

  Spacer(modifier = Modifier.height(8.dp))
}
