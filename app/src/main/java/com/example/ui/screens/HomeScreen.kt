package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.SortOption
import com.example.model.formatPkr
import com.example.ui.components.CachedProductImage
import com.example.ui.components.StoreTrustStrip
import com.example.viewmodel.StoreUiState

@Composable
fun HomeScreen(
  uiState: StoreUiState,
  onSearchQueryChange: (String) -> Unit,
  onSelectCategory: (ProductCategory) -> Unit,
  onSelectSortOption: (SortOption) -> Unit,
  onResetFilters: () -> Unit,
  onProductClick: (Product) -> Unit,
  onQuickAddToCart: (Product) -> Unit,
  onOpenCart: () -> Unit,
  onOpenSellerAdmin: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val cartQuantities =
    remember(uiState.cartItems) { uiState.cartQuantityByProductId }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "top_bar") {
      MarketplaceTopHeader(
        cartCount = uiState.cartItemCount,
        onOpenCart = onOpenCart,
        onOpenSellerAdmin = onOpenSellerAdmin,
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "search_bar") {
      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().testTag("search_input"),
        placeholder = {
          Text(
            text = stringResource(R.string.search_placeholder),
            style = MaterialTheme.typography.bodyLarge,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        trailingIcon = {
          AnimatedVisibility(
            visible = uiState.searchQuery.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
          ) {
            IconButton(
              onClick = { onSearchQueryChange("") },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_search_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.clear_search),
              )
            }
          }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          ),
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "promo_banner") {
      StorePromoBanner(
        selectedCategory = uiState.selectedCategory,
        onSelectCategory = onSelectCategory,
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "trust_strip") {
      StoreTrustStrip()
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "category_row") {
      CategoryFilterRow(
        selectedCategory = uiState.selectedCategory,
        totalCount = uiState.allProducts.size,
        blanketsCount =
          uiState.allProducts.count { it.category == ProductCategory.BLANKETS },
        suitsCount =
          uiState.allProducts.count { it.category == ProductCategory.MENS_SUITS },
        onSelectCategory = onSelectCategory,
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "results_sort_row") {
      ResultsAndSortRow(
        count = uiState.filteredProducts.size,
        sortOption = uiState.sortOption,
        onSelectSortOption = onSelectSortOption,
      )
    }

    if (uiState.filteredProducts.isEmpty()) {
      item(span = { GridItemSpan(maxLineSpan) }, key = "empty_search") {
        EmptySearchState(onResetFilters = onResetFilters)
      }
    } else {
      items(
        items = uiState.filteredProducts,
        key = { it.id },
        contentType = { "product_card" },
      ) { product ->
        ProductGridCard(
          product = product,
          inCartQuantity = cartQuantities[product.id] ?: 0,
          onClick = { onProductClick(product) },
          onQuickAdd = { onQuickAddToCart(product) },
        )
      }
    }
  }
}

@Composable
private fun MarketplaceTopHeader(
  cartCount: Int,
  onOpenCart: () -> Unit,
  onOpenSellerAdmin: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Storefront,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.tagline_pakistan_cod),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        onClick = onOpenCart,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("home_cart_button"),
      ) {
        Box(
          modifier = Modifier.size(44.dp),
          contentAlignment = Alignment.Center,
        ) {
          BadgedBox(
            badge = {
              if (cartCount > 0) {
                Badge { Text(text = cartCount.toString()) }
              }
            }
          ) {
            Icon(
              imageVector = Icons.Filled.ShoppingCart,
              contentDescription = stringResource(R.string.nav_cart),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }

      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("home_menu_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Store options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text("Seller / Admin") },
            onClick = {
              menuExpanded = false
              onOpenSellerAdmin()
            },
            modifier = Modifier.testTag("seller_admin_menu_item"),
          )
        }
      }
    }
  }
}

@Composable
private fun StorePromoBanner(
  selectedCategory: ProductCategory,
  onSelectCategory: (ProductCategory) -> Unit,
) {
  val bannerGradient =
    Brush.linearGradient(
      colors =
        listOf(
          MaterialTheme.colorScheme.primary,
          MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
          MaterialTheme.colorScheme.secondary,
        )
    )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    shadowElevation = 2.dp,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().background(bannerGradient).padding(18.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
          color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
          shape = CircleShape,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.LocalShipping,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "PAKISTAN WIDE CASH ON DELIVERY",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
        Text(
          text = "Cozy Blankets & Men's Unstitched Suits",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
          text = "Direct doorstep delivery across Pakistan. Pay cash to the rider on arrival.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.90f),
        )
        Row(
          modifier = Modifier.padding(top = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          BannerCategoryShortcut(
            label = "Shop Blankets",
            active = selectedCategory == ProductCategory.BLANKETS,
            onClick = { onSelectCategory(ProductCategory.BLANKETS) },
          )
          BannerCategoryShortcut(
            label = "Shop Men's Suits",
            active = selectedCategory == ProductCategory.MENS_SUITS,
            onClick = { onSelectCategory(ProductCategory.MENS_SUITS) },
          )
        }
      }
    }
  }
}

@Composable
private fun BannerCategoryShortcut(
  label: String,
  active: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color =
      if (active) MaterialTheme.colorScheme.onPrimary
      else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
    contentColor =
      if (active) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimary,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
    )
  }
}

@Composable
private fun CategoryFilterRow(
  selectedCategory: ProductCategory,
  totalCount: Int,
  blanketsCount: Int,
  suitsCount: Int,
  onSelectCategory: (ProductCategory) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ProductCategory.entries.forEach { category ->
      val count =
        when (category) {
          ProductCategory.ALL -> totalCount
          ProductCategory.BLANKETS -> blanketsCount
          ProductCategory.MENS_SUITS -> suitsCount
        }
      val tag =
        when (category) {
          ProductCategory.ALL -> "filter_all"
          ProductCategory.BLANKETS -> "filter_blankets"
          ProductCategory.MENS_SUITS -> "filter_mens_suits"
        }
      val isSelected = selectedCategory == category
      FilterChip(
        selected = isSelected,
        onClick = { onSelectCategory(category) },
        modifier = Modifier.minimumInteractiveComponentSize().testTag(tag),
        label = {
          Text(
            text = "${category.label} ($count)",
            style = MaterialTheme.typography.labelLarge,
          )
        },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            labelColor = MaterialTheme.colorScheme.onSurface,
          ),
        border =
          FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
          ),
      )
    }
  }
}

@Composable
private fun ResultsAndSortRow(
  count: Int,
  sortOption: SortOption,
  onSelectSortOption: (SortOption) -> Unit,
) {
  var sortExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Showing $count ${if (count == 1) "product" else "products"}",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Box {
      Surface(
        onClick = { sortExpanded = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("sort_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = sortOption.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      DropdownMenu(
        expanded = sortExpanded,
        onDismissRequest = { sortExpanded = false },
      ) {
        SortOption.entries.forEach { option ->
          DropdownMenuItem(
            text = { Text(option.label) },
            leadingIcon = {
              if (option == sortOption) {
                Icon(
                  imageVector = Icons.Filled.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
            onClick = {
              onSelectSortOption(option)
              sortExpanded = false
            },
          )
        }
      }
    }
  }
}

@Composable
private fun ProductGridCard(
  product: Product,
  inCartQuantity: Int,
  onClick: () -> Unit,
  onQuickAdd: () -> Unit,
) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("product_card_${product.id}"),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        CachedProductImage(
          imageRes = product.imageRes,
          imageUri = product.imageUri,
          contentDescription = product.name,
          cacheKey = product.id,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
          shape = CircleShape,
        ) {
          Text(
            text = product.category.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Surface(
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
          color =
            if (product.isAvailable) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
          shape = CircleShape,
        ) {
          Text(
            text = if (product.isAvailable) "COD" else "Out of Stock",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color =
              if (product.isAvailable) MaterialTheme.colorScheme.onTertiaryContainer
              else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
          )
        }
        if (inCartQuantity > 0 && product.isAvailable) {
          Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
          ) {
            Text(
              text = "In Cart ($inCartQuantity)",
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }

      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          text = product.name,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          minLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = product.shortSpec,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = formatPkr(product.pricePkr),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = if (product.isAvailable) "Free Delivery" else "Unavailable",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (product.isAvailable) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
            )
          }
          Surface(
            onClick = { if (product.isAvailable) onQuickAdd() },
            enabled = product.isAvailable,
            shape = CircleShape,
            color =
              if (product.isAvailable) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            contentColor =
              if (product.isAvailable) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("quick_add_${product.id}"),
          ) {
            Box(
              modifier = Modifier.size(40.dp),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.AddShoppingCart,
                contentDescription = "Add ${product.name} to cart",
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptySearchState(onResetFilters: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.SearchOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp),
      )
      Text(
        text = stringResource(R.string.empty_search_title),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.empty_search_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Button(
        onClick = onResetFilters,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("reset_filters_button"),
      ) {
        Text(text = stringResource(R.string.reset_filters))
      }
    }
  }
}
