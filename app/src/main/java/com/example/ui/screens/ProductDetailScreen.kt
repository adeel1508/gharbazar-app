package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Product
import com.example.model.formatPkr
import com.example.ui.components.CachedProductImage
import com.example.ui.components.CodTrustBadge
import com.example.ui.components.QuantityStepper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
  product: Product?,
  cartItemCount: Int,
  onBack: () -> Unit,
  onOpenCart: () -> Unit,
  onAddToCart: (Product, Int) -> Unit,
  onBuyNowCod: (Product, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedQuantity by remember(product?.id) { mutableIntStateOf(1) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.product_details_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back),
            )
          }
        },
        actions = {
          IconButton(
            onClick = onOpenCart,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("detail_cart_button"),
          ) {
            BadgedBox(
              badge = {
                if (cartItemCount > 0) {
                  Badge { Text(text = cartItemCount.toString()) }
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = stringResource(R.string.nav_cart),
              )
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
    bottomBar = {
      if (product != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLowest,
          tonalElevation = 4.dp,
          shadowElevation = 8.dp,
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              QuantityStepper(
                quantity = selectedQuantity,
                onIncrease = { selectedQuantity += 1 },
                onDecrease = { if (selectedQuantity > 1) selectedQuantity -= 1 },
                decreaseTestTag = "detail_decrease_qty",
                increaseTestTag = "detail_increase_qty",
                quantityTestTag = "detail_qty_text",
              )
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Total (${selectedQuantity}x)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = formatPkr(product.pricePkr * selectedQuantity),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              FilledTonalButton(
                onClick = { onBuyNowCod(product, selectedQuantity) },
                enabled = product.isAvailable,
                modifier =
                  Modifier.weight(1f)
                    .height(50.dp)
                    .testTag("buy_now_cod_button"),
                shape = MaterialTheme.shapes.medium,
              ) {
                Icon(
                  imageVector = Icons.Filled.Bolt,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (product.isAvailable) "Buy Now COD" else "Out of Stock",
                  style = MaterialTheme.typography.labelLarge,
                )
              }
              Button(
                onClick = { onAddToCart(product, selectedQuantity) },
                enabled = product.isAvailable,
                modifier =
                  Modifier.weight(1.25f)
                    .height(50.dp)
                    .testTag("add_to_cart_button"),
                shape = MaterialTheme.shapes.medium,
              ) {
                Icon(
                  imageVector = Icons.Filled.AddShoppingCart,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text =
                    if (product.isAvailable) stringResource(R.string.add_to_cart)
                    else "Out of Stock",
                  style = MaterialTheme.typography.labelLarge,
                )
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    if (product == null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "Product not found.",
          style = MaterialTheme.typography.titleMedium,
        )
      }
      return@Scaffold
    }

    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      val isWide = maxWidth >= 700.dp
      if (isWide) {
        Row(
          modifier =
            Modifier.widthIn(max = 1040.dp)
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(24.dp),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
          ProductSwipeableGallery(
            product = product,
            modifier = Modifier.weight(1f),
          )
          ProductDetailInfoColumn(
            product = product,
            modifier = Modifier.weight(1.1f),
          )
        }
      } else {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          ProductSwipeableGallery(product = product, modifier = Modifier.fillMaxWidth())
          ProductDetailInfoColumn(product = product, modifier = Modifier.fillMaxWidth())
          Spacer(modifier = Modifier.height(12.dp))
        }
      }
    }
  }
}

@Composable
private fun ProductSwipeableGallery(
  product: Product,
  modifier: Modifier = Modifier,
) {
  val remoteUrls = remember(product.id, product.imageUris, product.imageUri) {
    product.galleryImageUrls
  }
  val pageModels: List<String?> = remember(remoteUrls) {
    if (remoteUrls.isNotEmpty()) remoteUrls else listOf(null)
  }
  val pagerState = rememberPagerState(pageCount = { pageModels.size })
  val scope = rememberCoroutineScope()

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large),
      shape = MaterialTheme.shapes.large,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      shadowElevation = 2.dp,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
          state = pagerState,
          modifier = Modifier.fillMaxSize().testTag("detail_photo_pager"),
        ) { page ->
          val url = pageModels[page]
          CachedProductImage(
            imageRes = product.imageRes,
            imageUri = url,
            contentDescription = "${product.name} photo ${page + 1}",
            cacheKey = "${product.id}_$page",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        }

        if (pageModels.size > 1) {
          Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = CircleShape,
          ) {
            Text(
              text = "${pagerState.currentPage + 1} / ${pageModels.size}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }
    }

    if (pageModels.size > 1) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        pageModels.forEachIndexed { index, _ ->
          val selected = pagerState.currentPage == index
          Box(
            modifier =
              Modifier.padding(horizontal = 4.dp)
                .size(if (selected) 10.dp else 7.dp)
                .clip(CircleShape)
                .background(
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant
                )
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pageModels.forEachIndexed { index, url ->
          val selected = pagerState.currentPage == index
          Box(
            modifier =
              Modifier.size(64.dp)
                .clip(MaterialTheme.shapes.small)
                .border(
                  width = if (selected) 2.5.dp else 1.dp,
                  color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                  shape = MaterialTheme.shapes.small,
                )
                .clickable {
                  scope.launch { pagerState.animateScrollToPage(index) }
                }
                .testTag("detail_thumbnail_$index"),
          ) {
            CachedProductImage(
              imageRes = product.imageRes,
              imageUri = url,
              contentDescription = "Thumbnail ${index + 1}",
              cacheKey = "${product.id}_thumb_$index",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProductDetailInfoColumn(product: Product, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
      ) {
        Text(
          text = product.category,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
      CodTrustBadge()
    }

    Text(
      text = product.name,
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = formatPkr(product.pricePkr),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("detail_price_text"),
      )
      Surface(
        color =
          if (product.isAvailable) MaterialTheme.colorScheme.surfaceContainerLow
          else MaterialTheme.colorScheme.errorContainer,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Text(
          text =
            if (product.isAvailable) "In Stock (${product.stockQuantity})"
            else "Out of Stock",
          style = MaterialTheme.typography.labelSmall,
          color =
            if (product.isAvailable) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onErrorContainer,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      product.specs.forEach { spec ->
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLowest,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = MaterialTheme.shapes.small,
        ) {
          Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
              text = spec.label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = spec.value,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
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
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = stringResource(R.string.specifications_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        product.specs.forEachIndexed { index, spec ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = spec.label,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = spec.value,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          if (index < product.specs.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        ),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
      shape = MaterialTheme.shapes.medium,
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.LocalShipping,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Cash on Delivery Across Pakistan",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Bold,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Pay cash to rider on delivery • Zero card or advance payment",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
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
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.description_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = product.description,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
