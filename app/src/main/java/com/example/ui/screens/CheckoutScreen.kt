package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ProductCatalog
import com.example.model.formatPkr
import com.example.ui.components.CachedProductImage
import com.example.ui.components.CodOnlyNoticeCard
import com.example.ui.components.OrderSummaryCard
import com.example.viewmodel.StoreUiState

@Composable
fun CheckoutScreen(
  uiState: StoreUiState,
  onNameChange: (String) -> Unit,
  onPhoneChange: (String) -> Unit,
  onAddressChange: (String) -> Unit,
  onCityChange: (String) -> Unit,
  onPlaceOrder: () -> Unit,
  onBrowseProducts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (uiState.cartItems.isEmpty()) {
      Card(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .align(Alignment.Center)
            .padding(24.dp),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.ShoppingBag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
          )
          Text(
            text = stringResource(R.string.cart_empty_checkout_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )
          Text(
            text = stringResource(R.string.cart_empty_checkout_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Button(
            onClick = onBrowseProducts,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("checkout_browse_button"),
          ) {
            Text(text = stringResource(R.string.browse_products))
          }
        }
      }
      return@Box
    }

    val form = uiState.checkoutForm

    Column(
      modifier =
        Modifier.widthIn(max = 720.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(R.string.checkout_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )

      CodOnlyNoticeCard()

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
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.delivery_details_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )

          OutlinedTextField(
            value = form.customerName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.field_customer_name)) },
            placeholder = { Text(stringResource(R.string.field_customer_name_hint)) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            isError = form.nameError != null,
            supportingText = form.nameError?.let { error -> { Text(error) } },
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
              ),
            modifier = Modifier.fillMaxWidth().testTag("checkout_name_input"),
            shape = MaterialTheme.shapes.small,
            colors = checkoutFieldColors(),
          )

          OutlinedTextField(
            value = form.phoneNumber,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.field_phone_number)) },
            placeholder = { Text(stringResource(R.string.field_phone_number_hint)) },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            isError = form.phoneError != null,
            supportingText = form.phoneError?.let { error -> { Text(error) } },
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
              ),
            modifier = Modifier.fillMaxWidth().testTag("checkout_phone_input"),
            shape = MaterialTheme.shapes.small,
            colors = checkoutFieldColors(),
          )

          OutlinedTextField(
            value = form.deliveryAddress,
            onValueChange = onAddressChange,
            label = { Text(stringResource(R.string.field_delivery_address)) },
            placeholder = { Text(stringResource(R.string.field_delivery_address_hint)) },
            leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
            isError = form.addressError != null,
            supportingText = form.addressError?.let { error -> { Text(error) } },
            minLines = 2,
            maxLines = 3,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
              ),
            modifier = Modifier.fillMaxWidth().testTag("checkout_address_input"),
            shape = MaterialTheme.shapes.small,
            colors = checkoutFieldColors(),
          )

          OutlinedTextField(
            value = form.city,
            onValueChange = onCityChange,
            label = { Text(stringResource(R.string.field_city)) },
            placeholder = { Text(stringResource(R.string.field_city_hint)) },
            leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
            isError = form.cityError != null,
            supportingText = form.cityError?.let { error -> { Text(error) } },
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
              ),
            modifier = Modifier.fillMaxWidth().testTag("checkout_city_input"),
            shape = MaterialTheme.shapes.small,
            colors = checkoutFieldColors(),
          )

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ProductCatalog.popularPakistaniCities.forEach { cityName ->
              AssistChip(
                onClick = { onCityChange(cityName) },
                label = { Text(text = cityName) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              )
            }
          }
        }
      }

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
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Items in Order (${uiState.cartItemCount})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          uiState.cartItems.forEachIndexed { index, item ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CachedProductImage(
                imageRes = item.product.imageRes,
                imageUri = item.product.imageUri,
                contentDescription = item.product.name,
                cacheKey = item.product.id,
                modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.extraSmall),
                contentScale = ContentScale.Crop,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.product.name,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Qty: ${item.quantity} × ${formatPkr(item.product.pricePkr)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = formatPkr(item.lineTotalPkr),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            if (index < uiState.cartItems.lastIndex) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
        }
      }

      OrderSummaryCard(
        itemCount = uiState.cartItemCount,
        totalPkr = uiState.cartTotalPkr,
        totalTestTag = "checkout_order_total",
      )

      Button(
        onClick = onPlaceOrder,
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .testTag("place_order_button"),
        shape = MaterialTheme.shapes.medium,
      ) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "${stringResource(R.string.place_order)} • ${formatPkr(uiState.cartTotalPkr)}",
          style = MaterialTheme.typography.labelLarge,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun checkoutFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
  )
