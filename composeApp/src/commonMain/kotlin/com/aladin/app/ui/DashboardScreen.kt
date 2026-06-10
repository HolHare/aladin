package com.aladin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.aladin.app.api.fetchPortfolios
import com.aladin.shared.model.Portfolio
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(onPortfolioSelected: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { fetchPortfolios() }
                .onSuccess { portfolios = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (error != null) {
        ErrorCard(error!!)
        return
    }

    val totalAum = portfolios.sumOf { it.totalValue }
    val totalPnl = portfolios.sumOf { it.unrealizedPnl }

    Column(Modifier.fillMaxSize()) {
        // Summary row
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard("Total AUM", formatMoney(totalAum), Modifier.weight(1f))
            SummaryCard(
                "Total P&L",
                "${if (totalPnl >= 0) "+" else ""}${formatMoney(totalPnl)}",
                Modifier.weight(1f),
                valueColor = pnlColor(totalPnl),
            )
            SummaryCard("Portfolios", portfolios.size.toString(), Modifier.weight(1f))
            SummaryCard(
                "Avg Return",
                "${formatPct(portfolios.map { it.unrealizedPnlPct }.average())}",
                Modifier.weight(1f),
                valueColor = pnlColor(portfolios.map { it.unrealizedPnlPct }.average()),
            )
        }

        Text(
            text = "Portfolios",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(portfolios) { portfolio ->
                PortfolioCard(portfolio, onClick = { onPortfolioSelected(portfolio.id) })
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun PortfolioCard(portfolio: Portfolio, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(portfolio.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(portfolio.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp))
                Text("Manager: ${portfolio.managerId}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(portfolio.totalValue), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "${if (portfolio.unrealizedPnl >= 0) "+" else ""}${formatMoney(portfolio.unrealizedPnl)} " +
                            "(${formatPct(portfolio.unrealizedPnlPct)})",
                    fontSize = 13.sp,
                    color = pnlColor(portfolio.unrealizedPnl),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1A1A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Connection Error", fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Make sure the server is running: ./gradlew :server:run",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

fun formatMoney(value: Double): String {
    val absVal = kotlin.math.abs(value)
    val prefix = if (value < 0) "-$" else "$"
    return when {
        absVal >= 1_000_000_000 -> "${prefix}${String.format("%.2f", absVal / 1_000_000_000)}B"
        absVal >= 1_000_000 -> "${prefix}${String.format("%.2f", absVal / 1_000_000)}M"
        absVal >= 1_000 -> "${prefix}${String.format("%.1f", absVal / 1_000)}K"
        else -> "${prefix}${String.format("%.2f", absVal)}"
    }
}

fun formatPct(value: Double): String =
    "${if (value >= 0) "+" else ""}${String.format("%.2f", value)}%"

@Composable
fun pnlColor(value: Double): Color =
    if (value >= 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
