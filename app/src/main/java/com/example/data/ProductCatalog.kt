package com.example.data

import androidx.annotation.DrawableRes
import com.example.R
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductPhotoOption
import com.example.model.ProductSpec

object ProductCatalog {

  val photoOptions: List<ProductPhotoOption> =
    listOf(
      ProductPhotoOption(
        key = "blanket_printed_black",
        label = "Black Printed Fleece Blanket",
        imageRes = R.drawable.img_blanket_printed_black_1795368929423,
      ),
      ProductPhotoOption(
        key = "blanket_navy_white",
        label = "Navy White Printed Blanket",
        imageRes = R.drawable.img_blanket_navy_white_1795368929031,
      ),
      ProductPhotoOption(
        key = "blanket_plain_blue",
        label = "Plain Blue Fleece Blanket",
        imageRes = R.drawable.img_blanket_plain_blue_1795368928571,
      ),
      ProductPhotoOption(
        key = "blanket_embossed_red",
        label = "Red 3D Embossed Blanket",
        imageRes = R.drawable.img_blanket_embossed_red_1795368929457,
      ),
      ProductPhotoOption(
        key = "suit_boski",
        label = "Boski Plain Suit",
        imageRes = R.drawable.img_suit_boski_1795368943150,
      ),
      ProductPhotoOption(
        key = "suit_wool_brown",
        label = "Brown Wool Suit",
        imageRes = R.drawable.img_suit_wool_brown_1795368944258,
      ),
      ProductPhotoOption(
        key = "suit_washwear_blue",
        label = "Blue Wash & Wear Suit",
        imageRes = R.drawable.img_suit_washwear_blue_1795368945567,
      ),
      ProductPhotoOption(
        key = "suit_cotton",
        label = "Ivory Cotton Suit",
        imageRes = R.drawable.img_suit_cotton_1795368945913,
      ),
    )

  @DrawableRes
  fun resolveDrawableForKey(
    key: String,
    fallbackCategoryLabel: String = ProductCategory.BLANKETS.label,
  ): Int {
    return photoOptions.find { it.key == key }?.imageRes
      ?: if (ProductCategory.isSuitCategory(fallbackCategoryLabel)) {
        R.drawable.img_suit_boski_1795368943150
      } else {
        R.drawable.img_blanket_plain_blue_1795368928571
      }
  }

  val products: List<Product> =
    listOf(
      Product(
        id = "blanket_cozy_fleece_black",
        name = "Cozy Fleece Printed Double Bed Blanket",
        pricePkr = 1949,
        category = ProductCategory.BLANKETS.label,
        fabric = "Fleece",
        size = "Double Bed, 89x90 inches",
        shortSpec = "Double Bed • 89x90 in • Fleece • Black",
        specs =
          listOf(
            ProductSpec("Bed Size", "Double Bed"),
            ProductSpec("Dimensions", "89x90 inches"),
            ProductSpec("Fabric", "Fleece"),
            ProductSpec("Pattern", "Printed"),
            ProductSpec("Color", "Black"),
          ),
        description =
          "Wrap yourself in plush warmth with this Cozy Fleece Printed Double Bed Blanket. Crafted from ultra-soft fleece in a striking black printed finish, its generous 89x90 inch dimensions comfortably cover a full double bed while staying lightweight and easy to care for throughout the winter season.",
        imageKey = "blanket_printed_black",
        imageRes = R.drawable.img_blanket_printed_black_1795368929423,
        inStock = true,
        stockQuantity = 25,
      ),
      Product(
        id = "blanket_fleece_navy_white",
        name = "Fleece Printed Double Bed Blanket Navy White",
        pricePkr = 1949,
        category = ProductCategory.BLANKETS.label,
        fabric = "Fleece",
        size = "Double Bed, 88x90 inches",
        shortSpec = "Double Bed • 88x90 in • Fleece • Blue",
        specs =
          listOf(
            ProductSpec("Bed Size", "Double Bed"),
            ProductSpec("Dimensions", "88x90 inches"),
            ProductSpec("Fabric", "Fleece"),
            ProductSpec("Pattern", "Printed"),
            ProductSpec("Color", "Blue"),
          ),
        description =
          "Designed for crisp comfort and timeless bedroom style, the Fleece Printed Double Bed Blanket in Navy White pairs a rich blue and crisp white print with soft insulating fleece. Sized at 88x90 inches for double beds, it delivers cozy warmth without heavy bulk.",
        imageKey = "blanket_navy_white",
        imageRes = R.drawable.img_blanket_navy_white_1795368929031,
        inStock = true,
        stockQuantity = 22,
      ),
      Product(
        id = "blanket_stylish_plain_blue",
        name = "Stylish Fleece Plain Double Bed Blanket",
        pricePkr = 2599,
        category = ProductCategory.BLANKETS.label,
        fabric = "Fleece",
        size = "Double Bed, 89x90 inches",
        shortSpec = "Double Bed • 89x90 in • Fleece • Plain Blue",
        specs =
          listOf(
            ProductSpec("Bed Size", "Double Bed"),
            ProductSpec("Dimensions", "89x90 inches"),
            ProductSpec("Fabric", "Fleece"),
            ProductSpec("Style", "Plain"),
            ProductSpec("Color", "Blue"),
          ),
        description =
          "Elevate your winter bedding with the Stylish Fleece Plain Double Bed Blanket. Tailored in a refined solid blue shade from dense velvety fleece (89x90 inches), it offers smooth drape, lasting softness, and dependable warmth for chilly nights across Pakistan.",
        imageKey = "blanket_plain_blue",
        imageRes = R.drawable.img_blanket_plain_blue_1795368928571,
        inStock = true,
        stockQuantity = 18,
      ),
      Product(
        id = "blanket_3d_embossed_red",
        name = "3D Panel Fleece Embossed Double Bed Blanket",
        pricePkr = 3599,
        category = ProductCategory.BLANKETS.label,
        fabric = "Fleece",
        size = "Double Bed, 90 inches",
        shortSpec = "Double Bed • 90 in • 3D Embossed • Red",
        specs =
          listOf(
            ProductSpec("Bed Size", "Double Bed"),
            ProductSpec("Size", "90 inches"),
            ProductSpec("Fabric", "Fleece"),
            ProductSpec("Finish", "Embossed (3D Panel)"),
            ProductSpec("Color", "Red"),
          ),
        description =
          "Make a bold, luxurious statement in your bedroom with our 3D Panel Fleece Embossed Double Bed Blanket. Featuring deeply sculpted embossed relief panels in rich red fleece at a generous 90-inch double bed size, it combines plush thermal comfort with premium heirloom texture.",
        imageKey = "blanket_embossed_red",
        imageRes = R.drawable.img_blanket_embossed_red_1795368929457,
        inStock = true,
        stockQuantity = 14,
      ),
      Product(
        id = "suit_boski_plain",
        name = "Men's Unstitched Boski Plain Suit",
        pricePkr = 1999,
        category = ProductCategory.MENS_SUITS.label,
        fabric = "Boski fabric",
        size = "Unstitched",
        shortSpec = "Unstitched • Boski Fabric • Plain",
        specs =
          listOf(
            ProductSpec("Type", "Unstitched"),
            ProductSpec("Fabric", "Boski fabric"),
            ProductSpec("Style", "Plain"),
          ),
        description =
          "Classic Pakistani elegance begins with our Men's Unstitched Boski Plain Suit. Woven with the signature smooth sheen and graceful fall of Boski fabric, this plain unstitched suit is ideal for formal gatherings, Eid wear, and effortless tailored shalwar kameez craftsmanship.",
        imageKey = "suit_boski",
        imageRes = R.drawable.img_suit_boski_1795368943150,
        inStock = true,
        stockQuantity = 30,
      ),
      Product(
        id = "suit_wool_plain_brown",
        name = "Men's Unstitched Wool Plain Suit",
        pricePkr = 2299,
        category = ProductCategory.MENS_SUITS.label,
        fabric = "Wool",
        size = "Unstitched, 4 meter cutting",
        shortSpec = "Unstitched • Wool • Brown • 4m Cutting",
        specs =
          listOf(
            ProductSpec("Type", "Unstitched"),
            ProductSpec("Fabric", "Wool"),
            ProductSpec("Style", "Plain"),
            ProductSpec("Color", "Brown"),
            ProductSpec("Cutting", "4 meter cutting"),
          ),
        description =
          "Stay warm and sharp through winter with the Men's Unstitched Wool Plain Suit in rich brown. Supplied in a full 4 meter cutting of soft, insulating plain wool suiting, it tailors cleanly into a structured, dignified winter shalwar kameez or waistcoat ensemble.",
        imageKey = "suit_wool_brown",
        imageRes = R.drawable.img_suit_wool_brown_1795368944258,
        inStock = true,
        stockQuantity = 20,
      ),
      Product(
        id = "suit_wash_and_wear_blue",
        name = "Men's Unstitched Wash And Wear Plain Suit",
        pricePkr = 2999,
        category = ProductCategory.MENS_SUITS.label,
        fabric = "Wash and Wear",
        size = "Unstitched, 4 meter cutting",
        shortSpec = "Unstitched • Wash and Wear • Blue • 4m",
        specs =
          listOf(
            ProductSpec("Type", "Unstitched"),
            ProductSpec("Fabric", "Wash and Wear"),
            ProductSpec("Style", "Plain"),
            ProductSpec("Color", "Blue"),
            ProductSpec("Cutting", "4 meter cutting"),
          ),
        description =
          "Engineered for everyday polish with minimal ironing, the Men's Unstitched Wash And Wear Plain Suit in deep blue offers crisp drape and crease resistance from morning to evening. Includes a generous 4 meter cutting ready for custom stitching.",
        imageKey = "suit_washwear_blue",
        imageRes = R.drawable.img_suit_washwear_blue_1795368945567,
        inStock = true,
        stockQuantity = 24,
      ),
      Product(
        id = "suit_cotton_plain",
        name = "Men's Unstitched Cotton Plain Suit",
        pricePkr = 3099,
        category = ProductCategory.MENS_SUITS.label,
        fabric = "Cotton",
        size = "Unstitched, 4.5 meter cutting",
        shortSpec = "Unstitched • Cotton • Plain • 4.5m Cutting",
        specs =
          listOf(
            ProductSpec("Type", "Unstitched"),
            ProductSpec("Fabric", "Cotton"),
            ProductSpec("Style", "Plain"),
            ProductSpec("Cutting", "4.5 meter cutting"),
          ),
        description =
          "Breathable, crisp, and tailored for all-day comfort, our Men's Unstitched Cotton Plain Suit features premium natural cotton fabric in an extra-generous 4.5 meter cutting. Ideal for bespoke shalwar kameez stitching with clean structure and soft skin feel.",
        imageKey = "suit_cotton",
        imageRes = R.drawable.img_suit_cotton_1795368945913,
        inStock = true,
        stockQuantity = 16,
      ),
    )

  val popularPakistaniCities: List<String> =
    listOf(
      "Lahore",
      "Karachi",
      "Islamabad",
      "Rawalpindi",
      "Faisalabad",
      "Multan",
      "Peshawar",
      "Sialkot",
    )
}
