package com.aladin.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladin.app.ui.*

enum class Screen(val label: String) {
    Dashboard("Dashboard"),
    Holdings("Holdings"),
    Risk("Risk"),
    Trades("Trades"),
    Performance("Performance"),
}

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00BCD4),
            secondary = Color(0xFF4CAF50),
            background = Color(0xFF0D1117),
            surface = Color(0xFF161B22),
            onBackground = Color(0xFFE6EDF3),
            onSurface = Color(0xFFE6EDF3),
        )
    ) {
        AladinApp()
    }
}

@Composable
private fun AladinApp() {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var selectedPortfolioId by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationSidebar(
            currentScreen = currentScreen,
            onScreenSelected = { currentScreen = it },
        )

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ALADIN",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Asset & Liability Investment Network",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = currentScreen.label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    onPortfolioSelected = { id ->
                        selectedPortfolioId = id
                        currentScreen = Screen.Holdings
                    }
                )
                Screen.Holdings -> HoldingsScreen(portfolioId = selectedPortfolioId)
                Screen.Risk -> RiskScreen(portfolioId = selectedPortfolioId)
                Screen.Trades -> TradesScreen(portfolioId = selectedPortfolioId)
                Screen.Performance -> PerformanceScreen(portfolioId = selectedPortfolioId)
            }
        }
    }
}

@Composable
private fun NavigationSidebar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
) {
    Column(
        Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = "⬡",
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
        )

        Screen.entries.forEach { screen ->
            val isSelected = screen == currentScreen
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onScreenSelected(screen) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = screen.icon,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = screen.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "v1.0.0",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

private val Screen.icon get() = when (this) {
    Screen.Dashboard -> "▦"
    Screen.Holdings -> "◫"
    Screen.Risk -> "◈"
    Screen.Trades -> "⇄"
    Screen.Performance -> "↗"
}
