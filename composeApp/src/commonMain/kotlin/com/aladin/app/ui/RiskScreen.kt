package com.aladin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.aladin.app.api.fetchRisk
import com.aladin.shared.model.Portfolio
import com.aladin.shared.model.RiskMetrics
import kotlinx.coroutines.launch

@Composable
fun RiskScreen(portfolioId: String?) {
    val scope = rememberCoroutineScope()
    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var risk by remember { mutableStateOf<RiskMetrics?>(null) }
    var selectedId by remember { mutableStateOf(portfolioId) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { fetchPortfolios() }
                .onSuccess {
                    portfolios = it
                    if (selectedId == null && it.isNotEmpty()) selectedId = it.first().id
                }.onFailure { error = it.message }
        }
    }

    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        loading = true
        scope.launch {
            runCatching { fetchRisk(id) }
                .onSuccess { risk = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (portfolios.isNotEmpty()) {
            PortfolioSelector(portfolios, selectedId) { selectedId = it }
            Spacer(Modifier.height(16.dp))
        }

        if (error != null) { ErrorCard(error!!); return@Column }
        if (loading || risk == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        val r = risk!!

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RiskMetricCard("VaR 95%", formatMoney(r.var95), "Daily 1-day VaR", Color(0xFFFF9800), Modifier.weight(1f))
                    RiskMetricCard("VaR 99%", formatMoney(r.var99), "Daily 1-day VaR", Color(0xFFFF5252), Modifier.weight(1f))
                    RiskMetricCard("Max Drawdown", "-${r.maxDrawdown.fmt(1)}%", "Historical max", Color(0xFFFF5252), Modifier.weight(1f))
                    RiskMetricCard("Volatility", "${r.volatility.fmt(1)}%", "Annualized", Color(0xFFFF9800), Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RiskMetricCard("Sharpe Ratio", r.sharpeRatio.fmt(2), "Risk-adjusted return",
                        if (r.sharpeRatio >= 1.0) Color(0xFF4CAF50) else Color(0xFFFF9800), Modifier.weight(1f))
                    RiskMetricCard("Beta", r.beta.fmt(2), "vs. S&P 500",
                        if (r.beta < 1.0) Color(0xFF4CAF50) else Color(0xFFFF9800), Modifier.weight(1f))
                    Spacer(Modifier.weight(2f))
                }
            }
            item {
                SectorExposureChart(r)
            }
        }
    }
}

@Composable
private fun RiskMetricCard(
    label: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SectorExposureChart(risk: RiskMetrics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Sector Exposure", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 16.dp))

            val sectorColors = listOf(
                Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFFFF9800),
                Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF795548),
            )

            risk.sectorExposures.forEachIndexed { i, sector ->
                val barColor = sectorColors[i % sectorColors.size]
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sector.sector,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(160.dp),
                    )
                    Box(Modifier.weight(1f).height(20.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (sector.weight / 100f).toFloat().coerceIn(0f, 1f))
                                .background(barColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                        )
                    }
                    Text(
                        text = "${sector.weight.fmt(1)}%",
                        fontSize = 12.sp,
                        color = barColor,
                        modifier = Modifier.width(52.dp).padding(start = 8.dp),
                    )
                    Text(
                        text = formatMoney(sector.value),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.width(80.dp),
                    )
                }
            }
        }
    }
}
