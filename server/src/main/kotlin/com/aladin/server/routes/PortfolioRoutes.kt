package com.aladin.server.routes

import com.aladin.server.db.*
import com.aladin.server.market.MarketDataService
import com.aladin.shared.model.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.sqrt

private val tsf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

// Raw DB row without live prices
private data class RawHolding(
    val holdingId: String,
    val portfolioId: String,
    val assetId: String,
    val symbol: String,
    val name: String,
    val assetType: String,
    val exchange: String,
    val currency: String,
    val sector: String,
    val quantity: Double,
    val avgCost: Double,
    val lastKnownPrice: Double,
)

private fun queryRawHoldings(portfolioId: UUID): List<RawHolding> =
    (Holdings innerJoin Assets)
        .selectAll()
        .where { Holdings.portfolioId eq portfolioId }
        .map { row ->
            RawHolding(
                holdingId = row[Holdings.id].value.toString(),
                portfolioId = portfolioId.toString(),
                assetId = row[Assets.id].value.toString(),
                symbol = row[Assets.symbol],
                name = row[Assets.name],
                assetType = row[Assets.type],
                exchange = row[Assets.exchange],
                currency = row[Assets.currency],
                sector = row[Assets.sector],
                quantity = row[Holdings.quantity],
                avgCost = row[Holdings.avgCost],
                lastKnownPrice = row[Holdings.currentPrice],
            )
        }

private fun buildHoldings(raw: List<RawHolding>, livePrices: Map<String, Double>): List<Holding> {
    val totalValue = raw.sumOf { it.quantity * (livePrices[it.symbol] ?: it.lastKnownPrice) }
    return raw.map { r ->
        val price = livePrices[r.symbol] ?: r.lastKnownPrice
        val value = r.quantity * price
        val cost = r.quantity * r.avgCost
        val pnl = value - cost
        Holding(
            id = r.holdingId,
            portfolioId = r.portfolioId,
            asset = Asset(
                id = r.assetId,
                symbol = r.symbol,
                name = r.name,
                type = AssetType.valueOf(r.assetType),
                exchange = r.exchange,
                currency = r.currency,
                sector = r.sector,
            ),
            quantity = r.quantity,
            avgCost = r.avgCost,
            currentPrice = price,
            currentValue = value,
            unrealizedPnl = pnl,
            unrealizedPnlPct = if (cost > 0) pnl / cost * 100 else 0.0,
            weight = if (totalValue > 0) value / totalValue * 100 else 0.0,
        )
    }
}

private fun fetchLivePrices(raw: List<RawHolding>): Map<String, Double> =
    raw.map { it.symbol }.distinct()
        .associateWith { sym -> MarketDataService.currentPrice(sym) ?: raw.first { it.symbol == sym }.lastKnownPrice }

fun Route.portfolioRoutes() {
    route("/api") {
        get("/portfolios") {
            val rows = transaction {
                Portfolios.selectAll().map { row ->
                    row to queryRawHoldings(row[Portfolios.id].value)
                }
            }
            val allRaw = rows.flatMap { it.second }
            val livePrices = fetchLivePrices(allRaw)
            val portfolios = rows.map { (row, raw) ->
                val holdings = buildHoldings(raw, livePrices)
                val totalValue = holdings.sumOf { it.currentValue }
                val totalCost = holdings.sumOf { it.quantity * it.avgCost }
                val pnl = totalValue - totalCost
                Portfolio(
                    id = row[Portfolios.id].value.toString(),
                    name = row[Portfolios.name],
                    description = row[Portfolios.description],
                    managerId = row[Portfolios.managerId],
                    currency = row[Portfolios.currency],
                    totalValue = totalValue,
                    totalCost = totalCost,
                    unrealizedPnl = pnl,
                    unrealizedPnlPct = if (totalCost > 0) pnl / totalCost * 100 else 0.0,
                    createdAt = tsf.format(row[Portfolios.createdAt]),
                )
            }
            call.respond(portfolios)
        }

        get("/portfolios/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val uuid = UUID.fromString(id)
            val (row, raw) = transaction {
                Portfolios.selectAll().where { Portfolios.id eq uuid }.firstOrNull()
                    ?.let { it to queryRawHoldings(uuid) }
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            val livePrices = fetchLivePrices(raw)
            val holdings = buildHoldings(raw, livePrices)
            val totalValue = holdings.sumOf { it.currentValue }
            val totalCost = holdings.sumOf { it.quantity * it.avgCost }
            val pnl = totalValue - totalCost
            call.respond(
                Portfolio(
                    id = row[Portfolios.id].value.toString(),
                    name = row[Portfolios.name],
                    description = row[Portfolios.description],
                    managerId = row[Portfolios.managerId],
                    currency = row[Portfolios.currency],
                    totalValue = totalValue,
                    totalCost = totalCost,
                    unrealizedPnl = pnl,
                    unrealizedPnlPct = if (totalCost > 0) pnl / totalCost * 100 else 0.0,
                    createdAt = tsf.format(row[Portfolios.createdAt]),
                )
            )
        }

        get("/portfolios/{id}/holdings") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val uuid = UUID.fromString(id)
            val raw = transaction { queryRawHoldings(uuid) }
            val livePrices = fetchLivePrices(raw)
            call.respond(buildHoldings(raw, livePrices))
        }

        get("/portfolios/{id}/risk") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val uuid = UUID.fromString(id)
            val raw = transaction { queryRawHoldings(uuid) }
            val livePrices = fetchLivePrices(raw)
            val holdings = buildHoldings(raw, livePrices)
            call.respond(buildRiskMetrics(holdings, id))
        }

        get("/portfolios/{id}/performance") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val uuid = UUID.fromString(id)
            val raw = transaction { queryRawHoldings(uuid) }
            val livePrices = fetchLivePrices(raw)
            val holdings = buildHoldings(raw, livePrices)
            call.respond(buildPerformance(holdings, id))
        }

        get("/portfolios/{id}/trades") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val trades = transaction { buildTrades(UUID.fromString(id)) }
            call.respond(trades)
        }

        get("/assets") {
            val assets = transaction {
                Assets.selectAll().map { row ->
                    Asset(
                        id = row[Assets.id].value.toString(),
                        symbol = row[Assets.symbol],
                        name = row[Assets.name],
                        type = AssetType.valueOf(row[Assets.type]),
                        exchange = row[Assets.exchange],
                        currency = row[Assets.currency],
                        sector = row[Assets.sector],
                    )
                }
            }
            call.respond(assets)
        }

        get("/assets/{symbol}/price") {
            val symbol = call.parameters["symbol"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val price = MarketDataService.currentPrice(symbol.uppercase())
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, "Price unavailable")
            call.respond(price)
        }

        get("/trades") {
            val trades = transaction { buildTrades(null) }
            call.respond(trades)
        }

        post("/trades") {
            val req = call.receive<TradeRequest>()
            val trade = transaction {
                val tradeId = UUID.randomUUID()
                Trades.insert {
                    it[id] = org.jetbrains.exposed.dao.id.EntityID(tradeId, Trades)
                    it[portfolioId] = org.jetbrains.exposed.dao.id.EntityID(UUID.fromString(req.portfolioId), Portfolios)
                    it[assetId] = org.jetbrains.exposed.dao.id.EntityID(UUID.fromString(req.assetId), Assets)
                    it[side] = req.side.name
                    it[quantity] = req.quantity
                    it[price] = req.price
                    it[status] = "EXECUTED"
                    it[executedAt] = Instant.now()
                    it[notes] = req.notes
                }
                buildTrades(null).first { it.id == tradeId.toString() }
            }
            call.respond(HttpStatusCode.Created, trade)
        }
    }
}

private fun buildTrades(portfolioId: UUID?): List<Trade> {
    val query = (Trades innerJoin Assets).selectAll()
    if (portfolioId != null) query.where { Trades.portfolioId eq portfolioId }
    return query.orderBy(Trades.executedAt, SortOrder.DESC).map { row ->
        val qty = row[Trades.quantity]
        val price = row[Trades.price]
        Trade(
            id = row[Trades.id].value.toString(),
            portfolioId = row[Trades.portfolioId].value.toString(),
            asset = Asset(
                id = row[Assets.id].value.toString(),
                symbol = row[Assets.symbol],
                name = row[Assets.name],
                type = AssetType.valueOf(row[Assets.type]),
                exchange = row[Assets.exchange],
                currency = row[Assets.currency],
                sector = row[Assets.sector],
            ),
            side = TradeSide.valueOf(row[Trades.side]),
            quantity = qty,
            price = price,
            totalValue = qty * price,
            status = TradeStatus.valueOf(row[Trades.status]),
            executedAt = tsf.format(row[Trades.executedAt]),
            notes = row[Trades.notes],
        )
    }
}

// Calculates real risk metrics from 30-day historical prices + SPY benchmark
private fun buildRiskMetrics(holdings: List<Holding>, portfolioId: String): RiskMetrics {
    val totalValue = holdings.sumOf { it.currentValue }
    val symbols = holdings.map { it.asset.symbol }.distinct()

    val histData = symbols.associateWith { MarketDataService.historicalPrices(it, 32) }
    val spyHistory = MarketDataService.historicalPrices("SPY", 32)

    // Portfolio NAV per trading day using SPY calendar
    val calendar = spyHistory.map { it.first }
    val navSeries = calendar.map { date ->
        holdings.sumOf { h ->
            val price = histData[h.asset.symbol]?.findLast { it.first <= date }?.second ?: h.currentPrice
            h.quantity * price
        }
    }

    val portReturns = navSeries.zipWithNext { a, b -> if (a > 0) (b - a) / a else 0.0 }
    val spyReturns = spyHistory.map { it.second }.zipWithNext { a, b -> if (a > 0) (b - a) / a else 0.0 }

    // Annualized volatility (σ_daily × √252)
    val portVol = if (portReturns.size > 1) {
        val mean = portReturns.average()
        sqrt(portReturns.map { (it - mean) * (it - mean) }.average()) * sqrt(252.0)
    } else 0.20

    // Beta via OLS: Cov(port, spy) / Var(spy)
    val n = minOf(portReturns.size, spyReturns.size)
    val portBeta = if (n > 1) {
        val pr = portReturns.takeLast(n)
        val sr = spyReturns.takeLast(n)
        val spyMean = sr.average()
        val spyVar = sr.map { (it - spyMean) * (it - spyMean) }.average()
        if (spyVar > 1e-10) {
            val portMean = pr.average()
            pr.zip(sr).map { (p, s) -> (p - portMean) * (s - spyMean) }.average() / spyVar
        } else 1.0
    } else 1.0

    // Max drawdown from NAV peak-to-trough
    val maxDrawdown = if (navSeries.size >= 2) {
        var peak = navSeries.first()
        var maxDD = 0.0
        navSeries.forEach { v ->
            if (v > peak) peak = v
            val dd = if (peak > 0) (peak - v) / peak else 0.0
            if (dd > maxDD) maxDD = dd
        }
        maxDD * 100
    } else portVol * 250

    // Annualized return for Sharpe (risk-free ≈ 5.25% Fed funds rate)
    val annualReturn = if (navSeries.size >= 2 && navSeries.first() > 0) {
        val totalRet = (navSeries.last() - navSeries.first()) / navSeries.first()
        Math.pow(1 + totalRet, 252.0 / portReturns.size.coerceAtLeast(1)) - 1
    } else 0.0
    val sharpe = if (portVol > 0) (annualReturn - 0.0525) / portVol else 0.0

    val sectorExposures = holdings
        .groupBy { it.asset.sector }
        .map { (sector, hs) ->
            val sv = hs.sumOf { it.currentValue }
            SectorExposure(sector = sector, weight = if (totalValue > 0) sv / totalValue * 100 else 0.0, value = sv)
        }
        .sortedByDescending { it.weight }

    return RiskMetrics(
        portfolioId = portfolioId,
        var95 = totalValue * portVol * 1.645 / sqrt(252.0),
        var99 = totalValue * portVol * 2.326 / sqrt(252.0),
        maxDrawdown = maxDrawdown,
        sharpeRatio = sharpe,
        beta = portBeta,
        volatility = portVol * 100,
        sectorExposures = sectorExposures,
    )
}

// Calculates portfolio NAV history vs SPY benchmark using real closing prices
private fun buildPerformance(holdings: List<Holding>, portfolioId: String): PerformanceSummary {
    val symbols = holdings.map { it.asset.symbol }.distinct()
    val histData = symbols.associateWith { MarketDataService.historicalPrices(it, 32) }
    val spyHistory = MarketDataService.historicalPrices("SPY", 32)

    if (spyHistory.size < 2) return PerformanceSummary(
        portfolioId = portfolioId, ytdReturn = 0.0, oneYearReturn = 0.0,
        alpha = 0.0, trackingError = 0.0, informationRatio = 0.0, entries = emptyList()
    )

    // Last 31 trading days → 30 daily return points
    val calendar = spyHistory.takeLast(31)
    val navSeries = calendar.map { (date, _) ->
        val value = holdings.sumOf { h ->
            val price = histData[h.asset.symbol]?.findLast { it.first <= date }?.second ?: h.currentPrice
            h.quantity * price
        }
        date to value
    }

    val firstNav = navSeries.first().second.takeIf { it > 0 } ?: return PerformanceSummary(
        portfolioId = portfolioId, ytdReturn = 0.0, oneYearReturn = 0.0,
        alpha = 0.0, trackingError = 0.0, informationRatio = 0.0, entries = emptyList()
    )
    val firstSpy = calendar.first().second

    val entries = navSeries.mapIndexed { i, (date, value) ->
        val prevNav = if (i == 0) value else navSeries[i - 1].second
        val dailyRet = if (i > 0 && prevNav > 0) (value - prevNav) / prevNav * 100 else 0.0
        val cumRet = (value - firstNav) / firstNav * 100

        val spyNow = calendar[i].second
        val spyPrev = if (i == 0) spyNow else calendar[i - 1].second
        val bmDailyRet = if (i > 0 && spyPrev > 0) (spyNow - spyPrev) / spyPrev * 100 else 0.0
        val bmCumRet = if (firstSpy > 0) (spyNow - firstSpy) / firstSpy * 100 else 0.0

        PerformanceEntry(
            date = date,
            portfolioReturn = dailyRet,
            benchmarkReturn = bmDailyRet,
            cumulativeReturn = cumRet,
            cumulativeBenchmark = bmCumRet,
            totalValue = value,
        )
    }

    val ytdReturn = entries.last().cumulativeReturn
    val bmReturn = entries.last().cumulativeBenchmark
    val alpha = ytdReturn - bmReturn
    val activeReturns = entries.drop(1).map { it.portfolioReturn - it.benchmarkReturn }
    val activeMean = if (activeReturns.isEmpty()) 0.0 else activeReturns.average()
    val trackingError = if (activeReturns.size > 1)
        sqrt(activeReturns.map { (it - activeMean) * (it - activeMean) }.sum() / (activeReturns.size - 1)) * sqrt(252.0)
    else 0.0

    return PerformanceSummary(
        portfolioId = portfolioId,
        ytdReturn = ytdReturn,
        oneYearReturn = ytdReturn * 252.0 / entries.size.coerceAtLeast(1),
        alpha = alpha,
        trackingError = trackingError,
        informationRatio = if (trackingError > 0.001) alpha / trackingError else 0.0,
        entries = entries,
    )
}
