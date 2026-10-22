package com.spendly.app.core.database

import com.spendly.app.core.database.entity.AppSourceEntity
import com.spendly.app.core.database.entity.CategoryEntity

/**
 * Seed data for the Spendly database.
 * These are built-in categories and known payment app sources.
 */
object DatabaseSeedData {

    val defaultCategories = listOf(
        CategoryEntity(id = 1, name = "Food & Dining",    icon = "restaurant",   colorHex = "#FF7043", isDefault = true, sortOrder = 1, isSystem = true),
        CategoryEntity(id = 2, name = "Groceries",        icon = "shopping_cart", colorHex = "#66BB6A", isDefault = false, sortOrder = 2, isSystem = true),
        CategoryEntity(id = 3, name = "Transport",        icon = "directions_car",colorHex = "#42A5F5", isDefault = false, sortOrder = 3, isSystem = true),
        CategoryEntity(id = 4, name = "Shopping",         icon = "shopping_bag", colorHex = "#AB47BC", isDefault = false, sortOrder = 4, isSystem = true),
        CategoryEntity(id = 5, name = "Bills & Utilities",icon = "receipt_long",  colorHex = "#FFA726", isDefault = false, sortOrder = 5, isSystem = true),
        CategoryEntity(id = 6, name = "Entertainment",   icon = "movie",         colorHex = "#EC407A", isDefault = false, sortOrder = 6, isSystem = true),
        CategoryEntity(id = 7, name = "Health",           icon = "local_hospital",colorHex = "#26C6DA", isDefault = false, sortOrder = 7, isSystem = true),
        CategoryEntity(id = 8, name = "Education",        icon = "school",        colorHex = "#5C6BC0", isDefault = false, sortOrder = 8, isSystem = true),
        CategoryEntity(id = 9, name = "Travel",           icon = "flight",        colorHex = "#26A69A", isDefault = false, sortOrder = 9, isSystem = true),
        CategoryEntity(id = 10, name = "Fuel",            icon = "local_gas_station", colorHex = "#78909C", isDefault = false, sortOrder = 10, isSystem = true),
        CategoryEntity(id = 11, name = "Rent & Housing",  icon = "home",          colorHex = "#8D6E63", isDefault = false, sortOrder = 11, isSystem = true),
        CategoryEntity(id = 12, name = "Subscriptions",   icon = "subscriptions", colorHex = "#7E57C2", isDefault = false, sortOrder = 12, isSystem = true),
        CategoryEntity(id = 13, name = "Transfers",       icon = "swap_horiz",    colorHex = "#455A64", isDefault = false, sortOrder = 13, isSystem = true),
        CategoryEntity(id = 14, name = "Other",           icon = "category",      colorHex = "#9E9E9E", isDefault = false, sortOrder = 14, isSystem = true),
    )

    val knownPaymentApps = listOf(
        AppSourceEntity("com.phonepe.app",                 "PhonePe",       isEnabled = true),
        AppSourceEntity("com.google.android.apps.nbu.paisa.user", "Google Pay", isEnabled = true),
        AppSourceEntity("net.one97.paytm",                 "Paytm",         isEnabled = true),
        AppSourceEntity("in.org.npci.upiapp",              "BHIM",          isEnabled = true),
        AppSourceEntity("com.dreamplug.androidapp",        "CRED",          isEnabled = true),
        AppSourceEntity("com.amazon.mShop.android.shopping","Amazon Pay",   isEnabled = true),
        AppSourceEntity("com.whatsapp",                    "WhatsApp Pay",  isEnabled = false),
        AppSourceEntity("com.mobikwik_new",                "MobiKwik",      isEnabled = true),
        // HDFC Bank
        AppSourceEntity("com.snapwork.hdfc",               "HDFC Bank",     isEnabled = true),
        // ICICI Bank
        AppSourceEntity("com.csam.icici.bank.imobile",    "ICICI Bank",    isEnabled = true),
        // SBI
        AppSourceEntity("com.sbi.SBIFreedomPlus",         "SBI",           isEnabled = true),
        // Axis Bank
        AppSourceEntity("com.axis.mobile",                 "Axis Bank",     isEnabled = true),
        // Kotak
        AppSourceEntity("com.msf.kbank.mobile",            "Kotak Bank",    isEnabled = true),
        // Yes Bank
        AppSourceEntity("com.yesbankltd.nobile",          "Yes Bank",      isEnabled = true),
    )

    /** Category keyword map for suggested auto-classification */
    val merchantCategoryHints = mapOf(
        "swiggy" to 1L,  "zomato" to 1L, "mcdonalds" to 1L, "kfc" to 1L,
        "dominos" to 1L, "pizza" to 1L, "cafe" to 1L, "restaurant" to 1L,
        "bigbasket" to 2L, "grofer" to 2L, "blinkit" to 2L, "zepto" to 2L,
        "dmart" to 2L, "reliance fresh" to 2L, "more" to 2L,
        "uber" to 3L, "ola" to 3L, "rapido" to 3L, "metro" to 3L,
        "irctc" to 3L, "makemytrip" to 9L, "goibibo" to 9L,
        "myntra" to 4L, "amazon" to 4L, "flipkart" to 4L, "nykaa" to 4L,
        "ajio" to 4L, "meesho" to 4L,
        "electricity" to 5L, "water" to 5L, "jio" to 5L, "airtel" to 5L,
        "vodafone" to 5L, "bsnl" to 5L, "tatapower" to 5L, "gas" to 5L,
        "netflix" to 6L, "hotstar" to 6L, "spotify" to 6L, "amazon prime" to 6L,
        "pvr" to 6L, "inox" to 6L, "bookmyshow" to 6L,
        "pharmacy" to 7L, "hospital" to 7L, "clinic" to 7L, "medplus" to 7L,
        "apollo" to 7L, "netmeds" to 7L, "tata 1mg" to 7L,
        "byju" to 8L, "unacademy" to 8L, "coursera" to 8L, "udemy" to 8L,
        "petrol" to 10L, "diesel" to 10L, "fuel" to 10L, "hpcl" to 10L,
        "bpcl" to 10L, "iocl" to 10L,
    )
}
