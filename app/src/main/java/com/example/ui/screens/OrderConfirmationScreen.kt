package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConfirmedOrder
import com.example.model.formatPkr
import com.example.viewmodel.OrderSyncState

@Composable
fun OrderConfirmationScreen(
  order: ConfirmedOrder?,
  orderSyncState: OrderSyncState,
  onRetryOrderSync: () -> Unit,
  onContinueShopping: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onContinueShopping)

  var animateIn by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { animateIn = true }
  val badgeScale by
    animateFloatAsState(
      targetValue = if (animateIn) 1f else 0.6f,
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessLow,
        ),
      label = "confirm_scale",
    )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier =
          Modifier.size(88.dp)
            .scale(badgeScale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(54.dp),
        )
      }

      Text(
        text = stringResource(R.string.order_confirmed_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )

      Text(
        text = stringResource(R.string.order_confirmed_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      when (orderSyncState) {
        OrderSyncState.Idle -> Unit
        OrderSyncState.Saving -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Text(
                text = "Sending order to GharBazar…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        OrderSyncState.Saved -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Order synced to GharBazar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
        is OrderSyncState.Failed -> {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("order_sync_error_card"),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
              ),
            shape = MaterialTheme.shapes.medium,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.CloudOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Could not send order to server: ${orderSyncState.message}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
              }
              OutlinedButton(
                onClick = onRetryOrderSync,
                modifier = Modifier.testTag("retry_order_sync_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry Upload")
              }
            }
          }
        }
      }

      if (order != null) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          shape = MaterialTheme.shapes.large,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = stringResource(R.string.order_number_label),
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = order.orderNumber,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("confirmed_order_number"),
            )
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
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            StatusStep(
              icon = Icons.Filled.CheckCircle,
              label = "Placed",
              active = true,
            )
            StatusStep(
              icon = Icons.Filled.Inventory2,
              label = "Packing",
              active = false,
            )
            StatusStep(
              icon = Icons.Filled.LocalShipping,
              label = "COD Delivery",
              active = false,
            )
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
            ConfirmationRow(label = "Customer", value = order.customerName)
            ConfirmationRow(label = "Phone", value = order.phoneNumber)
            ConfirmationRow(label = "City", value = order.city)
            ConfirmationRow(label = "Address", value = order.deliveryAddress)
            ConfirmationRow(
              label = stringResource(R.string.payment_method_label),
              value = stringResource(R.string.payment_method_cod),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            order.items.forEach { item ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "${item.quantity}x ${item.product.name}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Text(
                  text = formatPkr(item.lineTotalPkr),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Payable on Delivery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = formatPkr(order.totalPkr),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Button(
        onClick = onContinueShopping,
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .testTag("continue_shopping_button"),
        shape = MaterialTheme.shapes.medium,
      ) {
        Icon(
          imageVector = Icons.Filled.Storefront,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
          text = stringResource(R.string.continue_shopping),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun StatusStep(
  icon: ImageVector,
  label: String,
  active: Boolean,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint =
        if (active) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
      color =
        if (active) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ConfirmationRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 12.dp),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f),
    )
  }
}
