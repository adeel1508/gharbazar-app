package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CartItem
import com.example.model.Product
import com.example.model.formatPkr
import com.example.ui.components.CachedProductImage
import com.example.ui.components.OrderSummaryCard
import com.example.ui.components.QuantityStepper
import com.example.viewmodel.StoreUiState

@Composable
fun CartScreen(
  uiState: StoreUiState,
  onIncreaseQuantity: (String) -> Unit,
  onDecreaseQuantity: (String) -> Unit,
  onRemoveItem: (String) -> Unit,
  onProductClick: (Product) -> Unit,
  onProceedToCheckout: () -> Unit,
  onBrowseProducts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (uiState.cartItems.isEmpty()) {
      EmptyCartState(
        onBrowseProducts = onBrowseProducts,
        modifier = Modifier.widthIn(max = 520.dp).align(Alignment.Center).padding(24.dp),
      )
      return@Box
    }

    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "cart_header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.cart_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Free Cash on Delivery unlocked for ${uiState.cartItemCount} ${if (uiState.cartItemCount == 1) "item" else "items"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }

      items(
        items = uiState.cartItems,
        key = { it.product.id },
        contentType = { "cart_item" },
      ) { item ->
        CartItemCard(
          item = item,
          onOpenProduct = { onProductClick(item.product) },
          onIncrease = { onIncreaseQuantity(item.product.id) },
          onDecrease = { onDecreaseQuantity(item.product.id) },
          onRemove = { onRemoveItem(item.product.id) },
        )
      }

      item(key = "cart_summary") {
        Spacer(modifier = Modifier.height(4.dp))
        OrderSummaryCard(
          itemCount = uiState.cartItemCount,
          totalPkr = uiState.cartTotalPkr,
          totalTestTag = "cart_order_total",
        )
      }

      item(key = "cart_checkout_cta") {
        Button(
          onClick = onProceedToCheckout,
          modifier =
            Modifier.fillMaxWidth()
              .height(54.dp)
              .testTag("proceed_to_checkout_button"),
          shape = MaterialTheme.shapes.medium,
        ) {
          Text(
            text = "${stringResource(R.string.proceed_to_checkout)} • ${formatPkr(uiState.cartTotalPkr)}",
            style = MaterialTheme.typography.labelLarge,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun CartItemCard(
  item: CartItem,
  onOpenProduct: () -> Unit,
  onIncrease: () -> Unit,
  onDecrease: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("cart_item_${item.product.id}"),
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
      verticalAlignment = Alignment.Top,
    ) {
      CachedProductImage(
        imageRes = item.product.imageRes,
        imageUri = item.product.coverImageUrl,
        contentDescription = item.product.name,
        cacheKey = item.product.id,
        modifier =
          Modifier.size(96.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpenProduct),
        contentScale = ContentScale.Crop,
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            text = item.product.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onOpenProduct),
          )
          IconButton(
            onClick = onRemove,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("remove_item_${item.product.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "${stringResource(R.string.remove_item)} ${item.product.name}",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }

        Text(
          text = "${formatPkr(item.product.pricePkr)} each • ${item.product.category}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          QuantityStepper(
            quantity = item.quantity,
            onIncrease = onIncrease,
            onDecrease = onDecrease,
            decreaseTestTag = "decrease_qty_${item.product.id}",
            increaseTestTag = "increase_qty_${item.product.id}",
            quantityTestTag = "qty_text_${item.product.id}",
          )
          Text(
            text = formatPkr(item.lineTotalPkr),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyCartState(
  onBrowseProducts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
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
        modifier = Modifier.size(56.dp),
      )
      Text(
        text = stringResource(R.string.empty_cart_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.empty_cart_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Button(
        onClick = onBrowseProducts,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("browse_products_button"),
      ) {
        Text(text = stringResource(R.string.browse_products))
      }
    }
  }
}
