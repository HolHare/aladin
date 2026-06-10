package com.aladin.server.db

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

fun seedData() {
    transaction {
        if (Assets.selectAll().count() > 0) return@transaction

        val assets = listOf(
            Triple("AAPL", "Apple Inc.", "EQUITY") to Triple("NASDAQ", "USD", "Technology"),
            Triple("MSFT", "Microsoft Corp.", "EQUITY") to Triple("NASDAQ", "USD", "Technology"),
            Triple("GOOGL", "Alphabet Inc.", "EQUITY") to Triple("NASDAQ", "USD", "Technology"),
            Triple("AMZN", "Amazon.com Inc.", "EQUITY") to Triple("NASDAQ", "USD", "Consumer Discretionary"),
            Triple("NVDA", "NVIDIA Corp.", "EQUITY") to Triple("NASDAQ", "USD", "Technology"),
            Triple("META", "Meta Platforms Inc.", "EQUITY") to Triple("NASDAQ", "USD", "Technology"),
            Triple("JPM", "JPMorgan Chase & Co.", "EQUITY") to Triple("NYSE", "USD", "Financials"),
            Triple("GS", "Goldman Sachs Group Inc.", "EQUITY") to Triple("NYSE", "USD", "Financials"),
            Triple("BRK.B", "Berkshire Hathaway Inc.", "EQUITY") to Triple("NYSE", "USD", "Financials"),
            Triple("TSLA", "Tesla Inc.", "EQUITY") to Triple("NASDAQ", "USD", "Consumer Discretionary"),
            Triple("TLT", "iShares 20+ Year Treasury Bond ETF", "ETF") to Triple("NASDAQ", "USD", "Fixed Income"),
            Triple("AGG", "iShares Core U.S. Aggregate Bond ETF", "ETF") to Triple("NYSE", "USD", "Fixed Income"),
            Triple("GLD", "SPDR Gold Shares", "ETF") to Triple("NYSE", "USD", "Commodities"),
            Triple("CASH", "US Dollar Cash", "CASH") to Triple("N/A", "USD", "Cash"),
        )

        val assetIds = mutableMapOf<String, UUID>()
        assets.forEach { (info, meta) ->
            val id = UUID.randomUUID()
            assetIds[info.first] = id
            Assets.insert {
                it[Assets.id] = org.jetbrains.exposed.dao.id.EntityID(id, Assets)
                it[symbol] = info.first
                it[name] = info.second
                it[type] = info.third
                it[exchange] = meta.first
                it[currency] = meta.second
                it[sector] = meta.third
            }
        }

        val prices = mapOf(
            "AAPL" to 195.50, "MSFT" to 415.20, "GOOGL" to 175.30,
            "AMZN" to 195.80, "NVDA" to 875.40, "META" to 520.10,
            "JPM" to 218.60, "GS" to 510.30, "BRK.B" to 412.50,
            "TSLA" to 172.90, "TLT" to 92.40, "AGG" to 96.80,
            "GLD" to 231.50, "CASH" to 1.00,
        )

        val portfolio1Id = UUID.randomUUID()
        val portfolio2Id = UUID.randomUUID()
        val portfolio3Id = UUID.randomUUID()

        Portfolios.insert {
            it[id] = org.jetbrains.exposed.dao.id.EntityID(portfolio1Id, Portfolios)
            it[name] = "Global Growth Fund"
            it[description] = "High-growth equity portfolio focused on technology and consumer sectors"
            it[managerId] = "manager-001"
            it[currency] = "USD"
            it[createdAt] = Instant.parse("2022-01-10T09:00:00Z")
        }
        Portfolios.insert {
            it[id] = org.jetbrains.exposed.dao.id.EntityID(portfolio2Id, Portfolios)
            it[name] = "Fixed Income Core"
            it[description] = "Conservative bond portfolio targeting stable income with low volatility"
            it[managerId] = "manager-002"
            it[currency] = "USD"
            it[createdAt] = Instant.parse("2021-06-15T09:00:00Z")
        }
        Portfolios.insert {
            it[id] = org.jetbrains.exposed.dao.id.EntityID(portfolio3Id, Portfolios)
            it[name] = "Balanced Diversified"
            it[description] = "60/40 equity/bond portfolio with commodity hedge overlay"
            it[managerId] = "manager-001"
            it[currency] = "USD"
            it[createdAt] = Instant.parse("2023-03-01T09:00:00Z")
        }

        fun addHolding(portfolioId: UUID, symbol: String, qty: Double, cost: Double) {
            val assetId = assetIds[symbol]!!
            Holdings.insert {
                it[id] = org.jetbrains.exposed.dao.id.EntityID(UUID.randomUUID(), Holdings)
                it[Holdings.portfolioId] = org.jetbrains.exposed.dao.id.EntityID(portfolioId, Portfolios)
                it[Holdings.assetId] = org.jetbrains.exposed.dao.id.EntityID(assetId, Assets)
                it[quantity] = qty
                it[avgCost] = cost
                it[currentPrice] = prices[symbol]!!
            }
        }

        // Global Growth Fund
        addHolding(portfolio1Id, "AAPL", 1500.0, 148.20)
        addHolding(portfolio1Id, "MSFT", 800.0, 310.50)
        addHolding(portfolio1Id, "NVDA", 600.0, 580.30)
        addHolding(portfolio1Id, "META", 500.0, 390.80)
        addHolding(portfolio1Id, "AMZN", 700.0, 165.40)
        addHolding(portfolio1Id, "GOOGL", 900.0, 140.20)
        addHolding(portfolio1Id, "TSLA", 400.0, 210.50)
        addHolding(portfolio1Id, "CASH", 250000.0, 1.0)

        // Fixed Income Core
        addHolding(portfolio2Id, "TLT", 5000.0, 98.60)
        addHolding(portfolio2Id, "AGG", 8000.0, 100.20)
        addHolding(portfolio2Id, "GLD", 1200.0, 185.30)
        addHolding(portfolio2Id, "CASH", 500000.0, 1.0)

        // Balanced Diversified
        addHolding(portfolio3Id, "AAPL", 400.0, 170.80)
        addHolding(portfolio3Id, "MSFT", 300.0, 380.20)
        addHolding(portfolio3Id, "JPM", 600.0, 195.40)
        addHolding(portfolio3Id, "GS", 200.0, 460.30)
        addHolding(portfolio3Id, "BRK.B", 500.0, 370.50)
        addHolding(portfolio3Id, "TLT", 2000.0, 95.80)
        addHolding(portfolio3Id, "AGG", 3000.0, 99.40)
        addHolding(portfolio3Id, "GLD", 500.0, 190.20)
        addHolding(portfolio3Id, "CASH", 150000.0, 1.0)

        fun addTrade(portfolioId: UUID, symbol: String, side: String, qty: Double, price: Double, daysAgo: Long) {
            Trades.insert {
                it[id] = org.jetbrains.exposed.dao.id.EntityID(UUID.randomUUID(), Trades)
                it[Trades.portfolioId] = org.jetbrains.exposed.dao.id.EntityID(portfolioId, Portfolios)
                it[assetId] = org.jetbrains.exposed.dao.id.EntityID(assetIds[symbol]!!, Assets)
                it[Trades.side] = side
                it[quantity] = qty
                it[Trades.price] = price
                it[status] = "EXECUTED"
                it[executedAt] = Instant.now().minusSeconds(daysAgo * 86400)
                it[notes] = ""
            }
        }

        addTrade(portfolio1Id, "NVDA", "BUY", 600.0, 580.30, 45)
        addTrade(portfolio1Id, "META", "BUY", 500.0, 390.80, 60)
        addTrade(portfolio1Id, "TSLA", "SELL", 200.0, 245.60, 30)
        addTrade(portfolio1Id, "AAPL", "BUY", 300.0, 185.40, 15)
        addTrade(portfolio2Id, "TLT", "BUY", 1000.0, 92.40, 7)
        addTrade(portfolio2Id, "AGG", "BUY", 2000.0, 96.80, 20)
        addTrade(portfolio3Id, "JPM", "BUY", 200.0, 210.50, 10)
        addTrade(portfolio3Id, "GLD", "BUY", 500.0, 190.20, 35)
    }
}
