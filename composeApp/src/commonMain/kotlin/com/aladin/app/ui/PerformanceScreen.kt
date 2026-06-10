package com.aladin.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladin.app.api.fetchPerformance
import com.aladin.app.api.fetchPortfolios
import com.aladin.shared.model.PerformanceSummary
import com.aladin.shared.model.Portfolio
import kotlinx.coroutines.launch

@Composable
fun PerformanceScreen(portfolioId: String?) {
    val scope = rememberCoroutineScope()
    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var perf by remember { mutableStateOf<PerformanceSummary?>(null) }
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
            runCatching { fetchPerformance(id) }
                .onSuccess { perf = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (portfolios.isNotEmpty()) {
            PortfolioSelector(portfolios, selectedId) { selectedId = it }
            Spacer(Modifier.height(16.dp))
        }

        if (error != null) { ErrorCard(error!!); return@Column }
        if (loading || perf == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        val p = perf!!

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PerfMetricCard("YTD Return", formatPct(p.ytdReturn), pnlValue = p.ytdReturn, modifier = Modifier.weight(1f))
                    PerfMetricCard("1Y Return", formatPct(p.oneYearReturn), pnlValue = p.oneYearReturn, modifier = Modifier.weight(1f))
                    PerfMetricCard("Alpha", formatPct(p.alpha), pnlValue = p.alpha, modifier = Modifier.weight(1f))
                    PerfMetricCard("Info Ratio", String.format("%.2f", p.informationRatio),
                        pnlValue = p.informationRatio, modifier = Modifier.weight(1f))
                    PerfMetricCard("Tracking Error", "${String.format("%.2f", p.trackingError)}%",
                        pnlValue = 1.0, modifier = Modifier.weight(1f))
                }
            }
            item {
                ReturnChart(p)
            }
            item {
                PerformanceTable(p)
            }
        }
    }
}

@Composable
private fun PerfMetricCard(label: String, value: String, pnlValue: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = pnlColor(pnlValue))
        }
    }
}

@Composable
private fun ReturnChart(perf: PerformanceSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.padding(bottom = 16.dp)) {
                Text("30-Day Cumulative Returns", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(12.dp).background(Color(0xFF00BCD4), RoundedCornerShape(2.dp)))
                Text(" Portfolio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(12.dp).background(Color(0xFF9E9E9E), RoundedCornerShape(2.dp)))
                Text(" Benchmark", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            val portfolioColor = Color(0xFF00BCD4)
            val benchmarkColor = Color(0xFF9E9E9E)
            val entries = perf.entries

            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                if (entries.isEmpty()) return@Canvas

                val returns = entries.map { it.cumulativeReturn }
                val bmReturns = entries.map { it.cumulativeBenchmark }
                val allValues = returns + bmReturns
                val minVal = allValues.minOrNull() ?: 0.0
                val maxVal = allValues.maxOrNull() ?: 1.0
                val range = (maxVal - minVal).let { if (it < 0.001) 1.0 else it }

                drawGrid(size.width, size.height, minVal, maxVal, range)

                drawLine(entries.map { it.cumulativeReturn }, portfolioColor, size, minVal, range)
                drawLine(entries.map { it.cumulativeBenchmark }, benchmarkColor, size, minVal, range)
            }
        }
    }
}

private fun DrawScope.drawGrid(width: Float, height: Float, minVal: Double, maxVal: Double, range: Double) {
    val gridColor = Color(0xFF303030)
    repeat(5) { i ->
        val y = height * i / 4
        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawLine(
    values: List<Double>,
    color: Color,
    size: androidx.compose.ui.geometry.Size,
    minVal: Double,
    range: Double,
) {
    if (values.size < 2) return
    val path = Path()
    values.forEachIndexed { i, v ->
        val x = (i.toFloat() / (values.size - 1)) * size.width
        val y = size.height - ((v - minVal) / range * size.height).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 2.5f))

    val lastX = size.width
    val lastY = size.height - ((values.last() - minVal) / range * size.height).toFloat()
    drawCircle(color, radius = 5f, center = Offset(lastX, lastY))
}

@Composable
private fun PerformanceTable(perf: PerformanceSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Daily Returns", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                TableHeader("Date", Modifier.weight(1f))
                TableHeader("Portfolio", Modifier.weight(1f))
                TableHeader("Benchmark", Modifier.weight(1f))
                TableHeader("Alpha", Modifier.weight(1f))
                TableHeader("Portfolio Value", Modifier.weight(1.5f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            perf.entries.takeLast(10).reversed().forEach { entry ->
                val alpha = entry.portfolioReturn - entry.benchmarkReturn
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(entry.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f))
                    Text(formatPct(entry.portfolioReturn), fontSize = 12.sp, color = pnlColor(entry.portfolioReturn),
                        modifier = Modifier.weight(1f))
                    Text(formatPct(entry.benchmarkReturn), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    Text(formatPct(alpha), fontSize = 12.sp, color = pnlColor(alpha), modifier = Modifier.weight(1f))
                    Text(formatMoney(entry.totalValue), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.5f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            }
        }
    }
}
