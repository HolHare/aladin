package com.aladin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladin.app.api.fetchAssetPrice
import com.aladin.app.api.fetchAssets
import com.aladin.app.api.fetchPortfolios
import com.aladin.app.api.fetchTrades
import com.aladin.app.api.submitTrade
import com.aladin.shared.model.*
import kotlinx.coroutines.launch

@Composable
fun TradesScreen(portfolioId: String?) {
    val scope = rememberCoroutineScope()
    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var assets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var trades by remember { mutableStateOf<List<Trade>>(emptyList()) }
    var selectedId by remember { mutableStateOf(portfolioId) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching {
                fetchPortfolios() to fetchAssets()
            }.onSuccess { (p, a) ->
                portfolios = p
                assets = a
                if (selectedId == null && p.isNotEmpty()) selectedId = p.first().id
            }.onFailure { error = it.message }
        }
    }

    LaunchedEffect(selectedId) {
        loading = true
        scope.launch {
            runCatching { fetchTrades(selectedId) }
                .onSuccess { trades = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (portfolios.isNotEmpty()) {
                Row(Modifier.weight(1f)) {
                    PortfolioSelector(portfolios, selectedId) { selectedId = it }
                }
            }
            Button(
                onClick = { showForm = !showForm },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (showForm) "Cancel" else "+ New Trade", fontSize = 13.sp)
            }
        }

        if (showForm && portfolios.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TradeForm(
                portfolios = portfolios,
                assets = assets,
                selectedPortfolioId = selectedId,
                onSubmit = { req ->
                    scope.launch {
                        runCatching { submitTrade(req) }
                            .onSuccess {
                                showForm = false
                                trades = fetchTrades(selectedId)
                            }
                            .onFailure { error = it.message }
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (error != null) { ErrorCard(error!!); return@Column }
        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }; return@Column }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            TableHeader("Asset", Modifier.weight(2f))
            TableHeader("Side", Modifier.weight(0.8f))
            TableHeader("Quantity", Modifier.weight(1f))
            TableHeader("Price", Modifier.weight(1f))
            TableHeader("Total", Modifier.weight(1.2f))
            TableHeader("Status", Modifier.weight(1f))
            TableHeader("Date", Modifier.weight(1.5f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Spacer(Modifier.height(4.dp))

        LazyColumn {
            items(trades) { trade ->
                TradeRow(trade)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            }
        }
    }
}

@Composable
private fun TradeRow(trade: Trade) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2f)) {
            Text(trade.asset.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary)
            Text(trade.asset.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        val sideColor = if (trade.side == TradeSide.BUY) Color(0xFF4CAF50) else Color(0xFFFF5252)
        Text(
            text = trade.side.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = sideColor,
            modifier = Modifier
                .weight(0.8f)
                .background(sideColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(trade.quantity.fmt(0), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("$${trade.price.fmt(2)}", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(formatMoney(trade.totalValue), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.2f))
        Text(trade.status.name, fontSize = 12.sp,
            color = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
        Text(trade.executedAt.take(10), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.weight(1.5f))
    }
}

@Composable
private fun TradeForm(
    portfolios: List<Portfolio>,
    assets: List<Asset>,
    selectedPortfolioId: String?,
    onSubmit: (TradeRequest) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var portfolioId by remember { mutableStateOf(selectedPortfolioId ?: portfolios.firstOrNull()?.id ?: "") }
    var assetId by remember { mutableStateOf(assets.firstOrNull()?.id ?: "") }
    var side by remember { mutableStateOf(TradeSide.BUY) }
    var quantity by remember { mutableStateOf("100") }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Fetch live price for the initially-selected asset
    LaunchedEffect(assetId) {
        val symbol = assets.find { it.id == assetId }?.symbol ?: return@LaunchedEffect
        runCatching { fetchAssetPrice(symbol) }.onSuccess { price = it.fmt(2) }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("New Trade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Portfolio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        portfolios.forEach { p ->
                            FilterChip(
                                selected = p.id == portfolioId,
                                onClick = { portfolioId = p.id },
                                label = { Text(p.name, fontSize = 11.sp) },
                            )
                        }
                    }
                }
                Column(Modifier.weight(0.5f)) {
                    Text("Side", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TradeSide.entries.forEach { s ->
                            FilterChip(
                                selected = s == side,
                                onClick = { side = s },
                                label = { Text(s.name, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(2f)) {
                    Text("Asset", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    val nonCashAssets = assets.filter { it.type.name != "CASH" }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        nonCashAssets.take(8).forEach { a ->
                            FilterChip(
                                selected = a.id == assetId,
                                onClick = {
                                    assetId = a.id
                                    scope.launch {
                                        runCatching { fetchAssetPrice(a.symbol) }
                                            .onSuccess { price = it.fmt(2) }
                                    }
                                },
                                label = { Text(a.symbol, fontSize = 11.sp) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price ($)") },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val qty = quantity.toDoubleOrNull() ?: return@Button
                    val prc = price.toDoubleOrNull() ?: return@Button
                    onSubmit(TradeRequest(portfolioId, assetId, side, qty, prc, notes))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (side == TradeSide.BUY) Color(0xFF2E7D32) else Color(0xFFC62828)
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Submit ${side.name} Order", fontSize = 13.sp)
            }
        }
    }
}

