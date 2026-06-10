package com.aladin.server.market

import kotlinx.serialization.json.*
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

object MarketDataService {
    private data class CacheEntry<T>(val value: T, val ts: Long)

    private val priceCache = ConcurrentHashMap<String, CacheEntry<Double>>()
    private val historyCache = ConcurrentHashMap<String, CacheEntry<List<Pair<String, Double>>>>()
    private const val CACHE_TTL_MS = 15 * 60 * 1000L

    private fun yahooSymbol(symbol: String) = symbol.replace(".", "-")

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection()
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AladinPMS/1.0)")
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        return conn.getInputStream().bufferedReader().readText()
    }

    fun currentPrice(symbol: String): Double? {
        if (symbol == "CASH") return 1.0
        val now = System.currentTimeMillis()
        priceCache[symbol]?.takeIf { now - it.ts < CACHE_TTL_MS }?.let { return it.value }
        return try {
            val sym = yahooSymbol(symbol)
            val text = fetch("https://query1.finance.yahoo.com/v8/finance/chart/$sym?interval=1d&range=1d")
            val price = Json.parseToJsonElement(text).jsonObject
                .get("chart")?.jsonObject
                ?.get("result")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("meta")?.jsonObject
                ?.get("regularMarketPrice")?.jsonPrimitive?.double
            if (price != null) priceCache[symbol] = CacheEntry(price, now)
            price
        } catch (e: Exception) {
            null
        }
    }

    // Returns (date, close) pairs sorted ascending for the last `days` trading days
    fun historicalPrices(symbol: String, days: Int = 32): List<Pair<String, Double>> {
        if (symbol == "CASH") {
            return (0 until days).map { i ->
                LocalDate.now().minusDays((days - 1 - i).toLong()).toString() to 1.0
            }
        }
        val now = System.currentTimeMillis()
        historyCache[symbol]?.takeIf { now - it.ts < CACHE_TTL_MS }?.let { entry ->
            if (entry.value.size >= days) return entry.value.takeLast(days)
        }
        return try {
            val sym = yahooSymbol(symbol)
            val text = fetch("https://query1.finance.yahoo.com/v8/finance/chart/$sym?interval=1d&range=3mo")
            val root = Json.parseToJsonElement(text).jsonObject
            val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            val timestamps = result?.get("timestamp")?.jsonArray?.map { it.jsonPrimitive.long } ?: emptyList()
            val closes = result?.get("indicators")?.jsonObject?.get("quote")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("close")?.jsonArray
                ?.map { el -> if (el is JsonNull) Double.NaN else el.jsonPrimitive.double }
                ?: emptyList()
            val data = timestamps.zip(closes)
                .filter { !it.second.isNaN() }
                .map { (ts, close) ->
                    Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDate().toString() to close
                }
                .sortedBy { it.first }
            historyCache[symbol] = CacheEntry(data, now)
            data.takeLast(days)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
