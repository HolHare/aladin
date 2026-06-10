package com.aladin.app.api

import com.aladin.shared.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

const val BASE_URL = "http://localhost:8080"

val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

suspend fun fetchPortfolios(): List<Portfolio> =
    httpClient.get("$BASE_URL/api/portfolios").body()

suspend fun fetchPortfolio(id: String): Portfolio =
    httpClient.get("$BASE_URL/api/portfolios/$id").body()

suspend fun fetchHoldings(portfolioId: String): List<Holding> =
    httpClient.get("$BASE_URL/api/portfolios/$portfolioId/holdings").body()

suspend fun fetchRisk(portfolioId: String): RiskMetrics =
    httpClient.get("$BASE_URL/api/portfolios/$portfolioId/risk").body()

suspend fun fetchPerformance(portfolioId: String): PerformanceSummary =
    httpClient.get("$BASE_URL/api/portfolios/$portfolioId/performance").body()

suspend fun fetchTrades(portfolioId: String? = null): List<Trade> =
    if (portfolioId != null)
        httpClient.get("$BASE_URL/api/portfolios/$portfolioId/trades").body()
    else
        httpClient.get("$BASE_URL/api/trades").body()

suspend fun fetchAssets(): List<Asset> =
    httpClient.get("$BASE_URL/api/assets").body()

suspend fun submitTrade(req: TradeRequest): Trade =
    httpClient.post("$BASE_URL/api/trades") {
        header("Content-Type", "application/json")
        setBody(req)
    }.body()

suspend fun fetchAssetPrice(symbol: String): Double =
    httpClient.get("$BASE_URL/api/assets/$symbol/price").body()
