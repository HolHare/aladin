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
import com.aladin.app.api.fetchHoldings
import com.aladin.app.api.fetchPortfolios
import com.aladin.shared.model.Holding
import com.aladin.shared.model.Portfolio
import kotlinx.coroutines.launch

@Composable
fun HoldingsScreen(portfolioId: String?) {
    val scope = rememberCoroutineScope()
    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var holdings by remember { mutableStateOf<List<Holding>>(emptyList()) }
    var selectedId by remember { mutableStateOf(portfolioId) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { fetchPortfolios() }
                .onSuccess {
                    portfolios = it
                    if (selectedId == null && it.isNotEmpty()) selectedId = it.first().id
                }
                .onFailure { error = it.message }
        }
    }

    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        loading = true
        scope.launch {
            runCatching { fetchHoldings(id) }
                .onSuccess { holdings = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (portfolios.isNotEmpty()) {
            PortfolioSelector(portfolios, selectedId) { selectedId = it }
            Spacer(Modifier.height(16.dp))
        }

        if (error != null) { ErrorCard(error!!); return@Column }
        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }; return@Column }

        val totalValue = holdings.sumOf { it.currentValue }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            TableHeader("Asset", Modifier.weight(2f))
            TableHeader("Type", Modifier.weight(1f))
            TableHeader("Quantity", Modifier.weight(1f))
            TableHeader("Avg Cost", Modifier.weight(1f))
            TableHeader("Price", Modifier.weight(1f))
            TableHeader("Value", Modifier.weight(1.5f))
            TableHeader("P&L", Modifier.weight(1.5f))
            TableHeader("Weight", Modifier.weight(0.8f))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Spacer(Modifier.height(4.dp))

        LazyColumn {
            items(holdings.sortedByDescending { it.currentValue }) { h ->
                HoldingRow(h)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            }

            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("Total", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(2f))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    Text(formatMoney(totalValue), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.5f))
                    Text(
                        text = formatMoney(holdings.sumOf { it.unrealizedPnl }),
                        fontWeight = FontWeight.Bold,
                        color = pnlColor(holdings.sumOf { it.unrealizedPnl }),
                        modifier = Modifier.weight(1.5f),
                    )
                    Spacer(Modifier.weight(0.8f))
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(h: Holding) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2f)) {
            Text(h.asset.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary)
            Text(h.asset.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        AssetTypeBadge(h.asset.type.name, Modifier.weight(1f))
        Text(h.quantity.fmt(0), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Text("$${h.avgCost.fmt(2)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Text("$${h.currentPrice.fmt(2)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Text(formatMoney(h.currentValue), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.5f))
        Column(Modifier.weight(1.5f)) {
            Text(
                text = "${if (h.unrealizedPnl >= 0) "+" else ""}${formatMoney(h.unrealizedPnl)}",
                fontSize = 13.sp,
                color = pnlColor(h.unrealizedPnl),
            )
            Text(formatPct(h.unrealizedPnlPct), fontSize = 11.sp, color = pnlColor(h.unrealizedPnl))
        }
        Text("${h.weight.fmt(1)}%", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(0.8f))
    }
}

@Composable
fun AssetTypeBadge(type: String, modifier: Modifier = Modifier) {
    val color = when (type) {
        "EQUITY" -> Color(0xFF1565C0)
        "ETF" -> Color(0xFF2E7D32)
        "BOND" -> Color(0xFF6A1B9A)
        "CASH" -> Color(0xFF4E342E)
        "COMMODITY" -> Color(0xFFE65100)
        else -> Color(0xFF37474F)
    }
    Box(modifier) {
        Text(
            text = type,
            fontSize = 10.sp,
            color = color.copy(red = color.red + 0.3f, green = color.green + 0.3f, blue = color.blue + 0.3f),
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = modifier,
    )
}

@Composable
fun PortfolioSelector(
    portfolios: List<Portfolio>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        portfolios.forEach { p ->
            val selected = p.id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(p.id) },
                label = { Text(p.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
