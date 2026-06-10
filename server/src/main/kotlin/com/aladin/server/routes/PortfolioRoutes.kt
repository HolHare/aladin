package com.aladin.server.routes

import com.aladin.server.db.*
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
import kotlin.math.abs
import kotlin.math.sqrt

private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
private val tsf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

fun Route.portfolioRoutes() {
    route("/api") {
        get("/portfolios") {
            val portfolios = transaction { buildPortfolioList() }
            call.respond(portfolios)
        }

        get("/portfolios/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val portfolio = transaction {
                Portfolios.selectAll().where { Portfolios.id eq UUID.fromString(id) }
                    .firstOrNull()?.let { buildPortfolio(it) }
            }
            if (portfolio == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(portfolio)
        }

        get("/portfolios/{id}/holdings") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val holdings = transaction { buildHoldings(UUID.fromString(id)) }
            call.respond(holdings)
        }

        get("/portfolios/{id}/risk") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val risk = transaction { buildRiskMetrics(UUID.fromString(id)) }
            call.respond(risk)
        }

        get("/portfolios/{id}/performance") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val perf = transaction { buildPerformance(UUID.fromString(id)) }
            call.respond(perf)
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
                    )
                }
            }
            call.respond(assets)
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

private fun buildPortfolioList(): List<Portfolio> =
    Portfolios.selectAll().map { row ->
        val holdings = buildHoldings(row[Portfolios.id].value)
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
            unrealizedPnlPct = if (totalCost > 0) (pnl / totalCost) * 100 else 0.0,
            createdAt = tsf.format(row[Portfolios.createdAt]),
        )
    }

private fun buildPortfolio(row: ResultRow): Portfolio {
    val holdings = buildHoldings(row[Portfolios.id].value)
    val totalValue = holdings.sumOf { it.currentValue }
    val totalCost = holdings.sumOf { it.quantity * it.avgCost }
    val pnl = totalValue - totalCost
    return Portfolio(
        id = row[Portfolios.id].value.toString(),
        name = row[Portfolios.name],
        description = row[Portfolios.description],
        managerId = row[Portfolios.managerId],
        currency = row[Portfolios.currency],
        totalValue = totalValue,
        totalCost = totalCost,
        unrealizedPnl = pnl,
        unrealizedPnlPct = if (totalCost > 0) (pnl / totalCost) * 100 else 0.0,
        createdAt = tsf.format(row[Portfolios.createdAt]),
    )
}

private fun buildHoldings(portfolioId: UUID): List<Holding> {
    val rows = (Holdings innerJoin Assets)
        .selectAll()
        .where { Holdings.portfolioId eq portfolioId }
        .toList()

    val totalValue = rows.sumOf { it[Holdings.quantity] * it[Holdings.currentPrice] }

    return rows.map { row ->
        val qty = row[Holdings.quantity]
        val avgCost = row[Holdings.avgCost]
        val price = row[Holdings.currentPrice]
        val value = qty * price
        val cost = qty * avgCost
        val pnl = value - cost
        Holding(
            id = row[Holdings.id].value.toString(),
            portfolioId = portfolioId.toString(),
            asset = Asset(
                id = row[Assets.id].value.toString(),
                symbol = row[Assets.symbol],
                name = row[Assets.name],
                type = AssetType.valueOf(row[Assets.type]),
                exchange = row[Assets.exchange],
                currency = row[Assets.currency],
            ),
            quantity = qty,
            avgCost = avgCost,
            currentPrice = price,
            currentValue = value,
            unrealizedPnl = pnl,
            unrealizedPnlPct = if (cost > 0) (pnl / cost) * 100 else 0.0,
            weight = if (totalValue > 0) (value / totalValue) * 100 else 0.0,
        )
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

private fun buildRiskMetrics(portfolioId: UUID): RiskMetrics {
    val holdings = buildHoldings(portfolioId)
    val totalValue = holdings.sumOf { it.currentValue }

    val equityVolatilities = mapOf(
        "AAPL" to 0.26, "MSFT" to 0.24, "GOOGL" to 0.28,
        "AMZN" to 0.30, "NVDA" to 0.55, "META" to 0.38,
        "JPM" to 0.22, "GS" to 0.25, "BRK.B" to 0.18,
        "TSLA" to 0.65, "TLT" to 0.14, "AGG" to 0.06,
        "GLD" to 0.16, "CASH" to 0.001,
    )
    val betas = mapOf(
        "AAPL" to 1.2, "MSFT" to 1.1, "GOOGL" to 1.15,
        "AMZN" to 1.25, "NVDA" to 1.8, "META" to 1.35,
        "JPM" to 1.1, "GS" to 1.2, "BRK.B" to 0.85,
        "TSLA" to 2.0, "TLT" to -0.2, "AGG" to -0.05,
        "GLD" to 0.05, "CASH" to 0.0,
    )

    val portfolioVol = if (totalValue > 0)
        holdings.sumOf { h -> (h.weight / 100.0) * (equityVolatilities[h.asset.symbol] ?: 0.25) }
    else 0.20

    val portfolioBeta = if (totalValue > 0)
        holdings.sumOf { h -> (h.weight / 100.0) * (betas[h.asset.symbol] ?: 1.0) }
    else 1.0

    val z95 = 1.645
    val z99 = 2.326
    val var95 = totalValue * portfolioVol * z95 / sqrt(252.0)
    val var99 = totalValue * portfolioVol * z99 / sqrt(252.0)

    val sectorRows = (Holdings innerJoin Assets)
        .select(Assets.sector, Holdings.quantity, Holdings.currentPrice)
        .where { Holdings.portfolioId eq portfolioId }
        .groupBy { it[Assets.sector] }
        .map { (sector, rows) ->
            val sectorValue = rows.sumOf { it[Holdings.quantity] * it[Holdings.currentPrice] }
            SectorExposure(
                sector = sector,
                weight = if (totalValue > 0) (sectorValue / totalValue) * 100 else 0.0,
                value = sectorValue,
            )
        }
        .sortedByDescending { it.weight }

    return RiskMetrics(
        portfolioId = portfolioId.toString(),
        var95 = var95,
        var99 = var99,
        maxDrawdown = portfolioVol * 2.5 * 100,
        sharpeRatio = (0.08 - 0.05) / portfolioVol,
        beta = portfolioBeta,
        volatility = portfolioVol * 100,
        sectorExposures = sectorRows,
    )
}

private fun buildPerformance(portfolioId: UUID): PerformanceSummary {
    val holdings = buildHoldings(portfolioId)
    val currentValue = holdings.sumOf { it.currentValue }

    val entries = (0..29).map { daysAgo ->
        val factor = 1.0 + (daysAgo * 0.0008) + Math.sin(daysAgo * 0.3) * 0.005
        val bmFactor = 1.0 + (daysAgo * 0.0006) + Math.sin(daysAgo * 0.25) * 0.003
        val date = java.time.LocalDate.now().minusDays(daysAgo.toLong())
        PerformanceEntry(
            date = date.toString(),
            portfolioReturn = (1.0 / factor - 1.0) * 100,
            benchmarkReturn = (1.0 / bmFactor - 1.0) * 100,
            cumulativeReturn = (1.0 / factor - 1.0) * 100,
            cumulativeBenchmark = (1.0 / bmFactor - 1.0) * 100,
            totalValue = currentValue * factor,
        )
    }.reversed()

    val ytdReturn = entries.last().cumulativeReturn
    val oneYearReturn = ytdReturn * 12 / 6
    val alpha = ytdReturn - entries.last().cumulativeBenchmark

    return PerformanceSummary(
        portfolioId = portfolioId.toString(),
        ytdReturn = ytdReturn,
        oneYearReturn = oneYearReturn,
        alpha = alpha,
        trackingError = 2.8,
        informationRatio = if (2.8 > 0) alpha / 2.8 else 0.0,
        entries = entries,
    )
}
