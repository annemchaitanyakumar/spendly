package com.spendly.app.core.database

import com.spendly.app.core.database.entity.AppSourceEntity
import com.spendly.app.core.database.entity.CategoryEntity

/**
 * Seed data for the Spendly database.
 * These are built-in categories and known payment app sources.
 */
object DatabaseSeedData {

    val defaultCategories = listOf(
        CategoryEntity(id = 1, name = "Food & Dining",       icon = "restaurant",          colorHex = "#FF7043", isDefault = true,  sortOrder = 1,  isSystem = true),
        CategoryEntity(id = 2, name = "Groceries",           icon = "shopping_cart",       colorHex = "#66BB6A", isDefault = false, sortOrder = 2,  isSystem = true),
        CategoryEntity(id = 3, name = "Transport",           icon = "directions_car",      colorHex = "#42A5F5", isDefault = false, sortOrder = 3,  isSystem = true),
        CategoryEntity(id = 4, name = "Shopping",            icon = "shopping_bag",        colorHex = "#AB47BC", isDefault = false, sortOrder = 4,  isSystem = true),
        CategoryEntity(id = 5, name = "Bills & Utilities",   icon = "receipt_long",         colorHex = "#FFA726", isDefault = false, sortOrder = 5,  isSystem = true),
        CategoryEntity(id = 6, name = "Entertainment",      icon = "movie",                colorHex = "#EC407A", isDefault = false, sortOrder = 6,  isSystem = true),
        CategoryEntity(id = 7, name = "Health & Medical",    icon = "local_hospital",       colorHex = "#26C6DA", isDefault = false, sortOrder = 7,  isSystem = true),
        CategoryEntity(id = 8, name = "Education",           icon = "school",               colorHex = "#5C6BC0", isDefault = false, sortOrder = 8,  isSystem = true),
        CategoryEntity(id = 9, name = "Travel & Trips",      icon = "flight",               colorHex = "#26A69A", isDefault = false, sortOrder = 9,  isSystem = true),
        CategoryEntity(id = 10, name = "Fuel & Vehicle",     icon = "local_gas_station",    colorHex = "#78909C", isDefault = false, sortOrder = 10, isSystem = true),
        CategoryEntity(id = 11, name = "Rent & Housing",     icon = "home",                 colorHex = "#8D6E63", isDefault = false, sortOrder = 11, isSystem = true),
        CategoryEntity(id = 12, name = "Subscriptions",      icon = "subscriptions",        colorHex = "#7E57C2", isDefault = false, sortOrder = 12, isSystem = true),
        CategoryEntity(id = 13, name = "Transfers & UPI",    icon = "swap_horiz",           colorHex = "#37474F", isDefault = false, sortOrder = 13, isSystem = true),
        CategoryEntity(id = 14, name = "Investments",        icon = "trending_up",          colorHex = "#00BFA5", isDefault = false, sortOrder = 14, isSystem = true),
        CategoryEntity(id = 15, name = "Personal Care",      icon = "face",                 colorHex = "#F06292", isDefault = false, sortOrder = 15, isSystem = true),
        CategoryEntity(id = 16, name = "Gifts & Donations",  icon = "card_giftcard",        colorHex = "#FF4081", isDefault = false, sortOrder = 16, isSystem = true),
        CategoryEntity(id = 17, name = "Family & Kids",      icon = "family_restroom",      colorHex = "#FFB74D", isDefault = false, sortOrder = 17, isSystem = true),
        CategoryEntity(id = 18, name = "Pet Care",           icon = "pets",                 colorHex = "#4DB6AC", isDefault = false, sortOrder = 18, isSystem = true),
        CategoryEntity(id = 19, name = "Home Services",      icon = "build",                colorHex = "#A1887F", isDefault = false, sortOrder = 19, isSystem = true),
        CategoryEntity(id = 20, name = "Other",              icon = "category",             colorHex = "#9E9E9E", isDefault = false, sortOrder = 20, isSystem = true),
    )

    val knownPaymentApps = listOf(
        AppSourceEntity("com.phonepe.app",                         "PhonePe",       isEnabled = true),
        AppSourceEntity("com.google.android.apps.nbu.paisa.user", "Google Pay",    isEnabled = true),
        AppSourceEntity("net.one97.paytm",                         "Paytm",         isEnabled = true),
        AppSourceEntity("in.org.npci.upiapp",                      "BHIM",          isEnabled = true),
        AppSourceEntity("com.dreamplug.androidapp",                "CRED",          isEnabled = true),
        AppSourceEntity("com.amazon.mShop.android.shopping",       "Amazon Pay",    isEnabled = true),
        AppSourceEntity("com.whatsapp",                            "WhatsApp Pay",  isEnabled = false),
        AppSourceEntity("com.mobikwik_new",                        "MobiKwik",      isEnabled = true),
        // Banks
        AppSourceEntity("com.snapwork.hdfc",                       "HDFC Bank",     isEnabled = true),
        AppSourceEntity("com.csam.icici.bank.imobile",            "ICICI Bank",    isEnabled = true),
        AppSourceEntity("com.sbi.SBIFreedomPlus",                 "SBI",           isEnabled = true),
        AppSourceEntity("com.axis.mobile",                         "Axis Bank",     isEnabled = true),
        AppSourceEntity("com.msf.kbank.mobile",                    "Kotak Bank",    isEnabled = true),
        AppSourceEntity("com.yesbankltd.nobile",                  "Yes Bank",      isEnabled = true),
        AppSourceEntity("com.rblbank.mobank",                     "RBL Bank",      isEnabled = true),
        AppSourceEntity("com.indusind.indusmobile",               "IndusInd Bank", isEnabled = true),
        AppSourceEntity("com.idfcfirstbank.optimus",              "IDFC FIRST",    isEnabled = true),
    )

    /** Category keyword map for suggested auto-classification */
    val merchantCategoryHints = mapOf(
        // Food & Dining (1)
        "swiggy" to 1L, "zomato" to 1L, "mcdonalds" to 1L, "kfc" to 1L,
        "dominos" to 1L, "pizza" to 1L, "cafe" to 1L, "restaurant" to 1L,
        "starbucks" to 1L, "burger" to 1L, "subway" to 1L, "eatfit" to 1L,
        "chai" to 1L, "chaayos" to 1L, "haldiram" to 1L, "biryani" to 1L,
        "bakery" to 1L, "rebel" to 1L, "behrouz" to 1L, "barbeque" to 1L,

        // Groceries (2)
        "bigbasket" to 2L, "grofer" to 2L, "blinkit" to 2L, "zepto" to 2L,
        "dmart" to 2L, "reliance fresh" to 2L, "smart bazaar" to 2L, "more" to 2L,
        "instamart" to 2L, "bbdaily" to 2L, "nature's basket" to 2L, "spencer" to 2L,
        "dairy" to 2L, "milk" to 2L, "country delight" to 2L, "jiomart" to 2L,

        // Transport (3)
        "uber" to 3L, "ola" to 3L, "rapido" to 3L, "metro" to 3L,
        "irctc" to 3L, "redbus" to 3L, "abhibus" to 3L, "fastag" to 3L,
        "toll" to 3L, "auto" to 3L, "cab" to 3L, "chalo" to 3L,

        // Shopping (4)
        "myntra" to 4L, "amazon" to 4L, "flipkart" to 4L, "nykaa" to 4L,
        "ajio" to 4L, "meesho" to 4L, "tata cliq" to 4L, "zara" to 4L,
        "h&m" to 4L, "uniqlo" to 4L, "decathlon" to 4L, "croma" to 4L,
        "reliance digital" to 4L, "vijay sales" to 4L, "apple store" to 4L,

        // Bills & Utilities (5)
        "electricity" to 5L, "water" to 5L, "jio" to 5L, "airtel" to 5L,
        "vodafone" to 5L, "vi" to 5L, "bsnl" to 5L, "tatapower" to 5L, "gas" to 5L,
        "bescom" to 5L, "msedcl" to 5L, "uppcl" to 5L, "tneb" to 5L,
        "adani" to 5L, "indane" to 5L, "hp gas" to 5L, "bharat gas" to 5L,
        "broadband" to 5L, "act corp" to 5L, "tata play" to 5L, "recharge" to 5L,

        // Entertainment (6)
        "netflix" to 6L, "hotstar" to 6L, "spotify" to 6L, "amazon prime" to 6L,
        "pvr" to 6L, "inox" to 6L, "bookmyshow" to 6L, "cinepolis" to 6L,
        "youtube" to 6L, "sony liv" to 6L, "zee5" to 6L, "playstation" to 6L,
        "steam" to 6L, "apple music" to 6L,

        // Health & Medical (7)
        "pharmacy" to 7L, "hospital" to 7L, "clinic" to 7L, "medplus" to 7L,
        "apollo" to 7L, "netmeds" to 7L, "tata 1mg" to 7L, "pharmeasy" to 7L,
        "practo" to 7L, "max healthcare" to 7L, "fortis" to 7L, "manipal" to 7L,
        "diagnostic" to 7L, "pathlab" to 7L, "thyrocare" to 7L,

        // Education (8)
        "byju" to 8L, "unacademy" to 8L, "coursera" to 8L, "udemy" to 8L,
        "physicswallah" to 8L, "pw" to 8L, "allen" to 8L, "aakash" to 8L,
        "simplilearn" to 8L, "college" to 8L, "school" to 8L, "exam fee" to 8L,

        // Travel (9)
        "makemytrip" to 9L, "goibibo" to 9L, "cleartrip" to 9L, "yatra" to 9L,
        "easemytrip" to 9L, "agoda" to 9L, "booking.com" to 9L, "airbnb" to 9L,
        "indigo" to 9L, "air india" to 9L, "spicejet" to 9L, "akasa" to 9L,
        "vistara" to 9L, "oyo" to 9L, "hotel" to 9L, "flight" to 9L,

        // Fuel (10)
        "petrol" to 10L, "diesel" to 10L, "fuel" to 10L, "hpcl" to 10L,
        "bpcl" to 10L, "iocl" to 10L, "indian oil" to 10L, "shell" to 10L,
        "nayara" to 10L, "cng" to 10L,

        // Rent & Housing (11)
        "rent" to 11L, "society maintenance" to 11L, "nobroker" to 11L,
        "housing" to 11L, "landlord" to 11L,

        // Subscriptions (12)
        "icloud" to 12L, "google one" to 12L, "chatgpt" to 12L, "openai" to 12L,
        "claude" to 12L, "notion" to 12L, "github" to 12L, "canva" to 12L,
        "microsoft 365" to 12L, "subscription" to 12L,

        // Transfers (13)
        "transfer" to 13L, "sent to" to 13L, "upi transfer" to 13L, "imps" to 13L,
        "neft" to 13L, "self transfer" to 13L,

        // Investments (14)
        "zerodha" to 14L, "groww" to 14L, "angel" to 14L, "upstox" to 14L,
        "coin" to 14L, "kuvera" to 14L, "indmoney" to 14L, "smallcase" to 14L,
        "mutual fund" to 14L, "sip" to 14L, "wint wealth" to 14L, "kite" to 14L,

        // Personal Care (15)
        "salon" to 15L, "spa" to 15L, "barber" to 15L, "urban company" to 15L,
        "urbanclap" to 15L, "enrich" to 15L, "toni&guy" to 15L, "looks" to 15L,
        "kaya" to 15L,

        // Gifts & Donations (16)
        "gift" to 16L, "donation" to 16L, "temple" to 16L, "charity" to 16L,
        "ngo" to 16L, "pm cares" to 16L, "ketto" to 16L, "milaap" to 16L,

        // Family & Kids (17)
        "firstcry" to 17L, "toys" to 17L, "daycare" to 17L, "hamleys" to 17L,

        // Pet Care (18)
        "vet" to 18L, "pets" to 18L, "heads up for tails" to 18L, "dog" to 18L, "cat" to 18L,

        // Home Services (19)
        "plumber" to 19L, "electrician" to 19L, "carpenter" to 19L, "maid" to 19L,
        "pest control" to 19L, "hardware" to 19L
    )
}
