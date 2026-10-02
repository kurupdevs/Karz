package com.kurupdevs.karz.ui.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money formatting for screens. All money is minor units (paise/pence) as Long.
 * The NumberFormat is remembered per currency (never built in composition).
 */
@Composable
fun rememberMinorFormatter(currencyCode: String): (Long) -> String {
    val format = remember(currencyCode) {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            currency = Currency.getInstance(currencyCode)
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }
    return remember(format) { { minor: Long -> format.format(minor / 100.0) } }
}

/** "50,00,000" style major-unit input string -> minor units, or null if invalid. */
fun parseMajorToMinor(input: String): Long? {
    val clean = input.trim().replace(",", "").replace("₹", "").replace("£", "")
    if (clean.isBlank()) return null
    val major = clean.toDoubleOrNull() ?: return null
    if (major <= 0) return null
    val minor = (major * 100).toLong()
    return minor.takeIf { it <= Int.MAX_VALUE.toLong() * 100 }
}

fun currencySymbol(code: String): String = when (code.uppercase()) {
    "INR" -> "₹"
    "GBP" -> "£"
    else -> try {
        Currency.getInstance(code).symbol
    } catch (_: Exception) {
        code
    }
}
