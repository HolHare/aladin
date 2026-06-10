package com.aladin.server.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Assets : UUIDTable("assets") {
    val symbol = varchar("symbol", 20).uniqueIndex()
    val name = varchar("name", 200)
    val type = varchar("type", 20)
    val exchange = varchar("exchange", 50)
    val currency = varchar("currency", 10)
    val sector = varchar("sector", 100).default("Unknown")
}

object Portfolios : UUIDTable("portfolios") {
    val name = varchar("name", 200)
    val description = text("description").default("")
    val managerId = varchar("manager_id", 100)
    val currency = varchar("currency", 10).default("USD")
    val createdAt = timestamp("created_at")
}

object Holdings : UUIDTable("holdings") {
    val portfolioId = reference("portfolio_id", Portfolios)
    val assetId = reference("asset_id", Assets)
    val quantity = double("quantity")
    val avgCost = double("avg_cost")
    val currentPrice = double("current_price")
}

object Trades : UUIDTable("trades") {
    val portfolioId = reference("portfolio_id", Portfolios)
    val assetId = reference("asset_id", Assets)
    val side = varchar("side", 10)
    val quantity = double("quantity")
    val price = double("price")
    val status = varchar("status", 20)
    val executedAt = timestamp("executed_at")
    val notes = text("notes").default("")
}
