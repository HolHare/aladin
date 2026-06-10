package com.aladin.app.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

fun Double.fmt(decimals: Int): String {
    if (decimals == 0) return toLong().toString()
    val factor = 10.0.pow(decimals.toDouble())
    val rounded = floor(this * factor + 0.5)
    val whole = (rounded / factor).toLong()
    val frac = abs(rounded - whole * factor).toLong()
    return "$whole.${frac.toString().padStart(decimals, '0')}"
}
