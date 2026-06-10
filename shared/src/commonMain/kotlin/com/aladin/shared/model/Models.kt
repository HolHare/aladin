package com.aladin.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssetType { EQUITY, BOND, COMMODITY, CASH, ETF, DERIVATIVE }

@Serializable
enum class TradeSide { BUY, SELL }

@Serializable
enum class TradeStatus { PENDING, EXECUTED, CANCELLED }

@Serializable
data class Asset(
    val id: String,
    val symbol: String,
    val name: String,
    val type: AssetType,
    val exchange: String,
    val currency: String,
    val sector: String = "Unknown",
)

@Serializable
data class Portfolio(
    val id: String,
    val name: String,
    val description: String,
    val managerId: String,
    val currency: String,
    val totalValue: Double,
    val totalCost: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPct: Double,
    val createdAt: String,
)

@Serializable
data class Holding(
    val id: String,
    val portfolioId: String,
    val asset: Asset,
    val quantity: Double,
    val avgCost: Double,
    val currentPrice: Double,
    val currentValue: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPct: Double,
    val weight: Double,
)

@Serializable
data class Trade(
    val id: String,
    val portfolioId: String,
    val asset: Asset,
    val side: TradeSide,
    val quantity: Double,
    val price: Double,
    val totalValue: Double,
    val status: TradeStatus,
    val executedAt: String,
    val notes: String = "",
)

@Serializable
data class TradeRequest(
    val portfolioId: String,
    val assetId: String,
    val side: TradeSide,
    val quantity: Double,
    val price: Double,
    val notes: String = "",
)

@Serializable
data class SectorExposure(
    val sector: String,
    val weight: Double,
    val value: Double,
)

@Serializable
data class RiskMetrics(
    val portfolioId: String,
    val var95: Double,
    val var99: Double,
    val maxDrawdown: Double,
    val sharpeRatio: Double,
    val beta: Double,
    val volatility: Double,
    val sectorExposures: List<SectorExposure>,
)

@Serializable
data class PerformanceEntry(
    val date: String,
    val portfolioReturn: Double,
    val benchmarkReturn: Double,
    val cumulativeReturn: Double,
    val cumulativeBenchmark: Double,
    val totalValue: Double,
)

@Serializable
data class PerformanceSummary(
    val portfolioId: String,
    val ytdReturn: Double,
    val oneYearReturn: Double,
    val alpha: Double,
    val trackingError: Double,
    val informationRatio: Double,
    val entries: List<PerformanceEntry>,
)
