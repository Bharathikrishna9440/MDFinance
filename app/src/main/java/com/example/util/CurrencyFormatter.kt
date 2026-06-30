package com.example.util

import java.util.Locale

object CurrencyFormatter {
    fun format(value: Double, decimals: Int = 0): String {
        val isNegative = value < 0
        val absValue = Math.abs(value)
        
        // Format with English locale to ensure standard decimal point
        val formattedString = String.format(Locale.US, "%.${decimals}f", absValue)
        
        val parts = formattedString.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) "." + parts[1] else ""
        
        val len = intPart.length
        if (len <= 3) {
            return (if (isNegative) "-" else "") + intPart + decPart
        }
        
        val lastThree = intPart.substring(len - 3)
        val remaining = intPart.substring(0, len - 3)
        
        val sb = java.lang.StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            if (count > 0 && count % 2 == 0) {
                sb.append(",")
            }
            sb.append(remaining[i])
            count++
        }
        val formattedRemaining = sb.reverse().toString()
        var cleanRemaining = formattedRemaining.trim()
        while (cleanRemaining.startsWith(",")) {
            cleanRemaining = cleanRemaining.substring(1)
        }
        while (cleanRemaining.endsWith(",")) {
            cleanRemaining = cleanRemaining.substring(0, cleanRemaining.length - 1)
        }
        
        val result = if (cleanRemaining.isEmpty()) lastThree else "$cleanRemaining,$lastThree"
        
        return (if (isNegative) "-" else "") + result + decPart
    }

    fun format(value: Float, decimals: Int = 0): String {
        return format(value.toDouble(), decimals)
    }

    fun format(value: Long, decimals: Int = 0): String {
        return format(value.toDouble(), decimals)
    }

    fun format(value: Int, decimals: Int = 0): String {
        return format(value.toDouble(), decimals)
    }
}
