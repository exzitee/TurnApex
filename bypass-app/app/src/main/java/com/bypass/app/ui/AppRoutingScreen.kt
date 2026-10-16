package com.bypass.app.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermDim
import com.bypass.app.ui.theme.TermWhite

data class AppRouteEntry(
    val packageName: String,
    val appName: String,
    var isTurn: Boolean
)

@Composable
fun AppRoutingScreen(
    currentRoutes: Map<String, String>, // pkg -> "turn" | "direct"
    onSaveRoutes: (Map<String, String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val routesMap = remember { mutableStateMapOf<String, String>().apply { putAll(currentRoutes) } }

    // Query installed applications on the device
    val installedApps = remember {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val defaultDirect = setOf(
            "ru.sberbankmobile",
            "ru.tinkoff.activities",
            "ru.gosuslugi.portal",
            "ru.vtb24.mobilebanking",
            "ru.yandex.searchplugin"
        )
        val defaultTurn = setOf(
            "org.telegram.messenger",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.twitter.android",
            "com.android.chrome"
        )

        packages
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || defaultDirect.contains(it.packageName) || defaultTurn.contains(it.packageName) }
            .map { appInfo ->
                val name = pm.getApplicationLabel(appInfo).toString()
                val pkg = appInfo.packageName
                val isTurn = routesMap[pkg]?.lowercase() == "turn" || (routesMap[pkg] == null && !defaultDirect.contains(pkg))
                AppRouteEntry(pkg, name, isTurn)
            }
            .sortedBy { it.appName.lowercase() }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBlack)
            .padding(12.dp)
            .border(1.dp, TermBorder, RectangleShape)
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "╔══ APP ROUTING ═════════════════════",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Toggle route: [TURN] (tunnel) vs [DIRECT] (bypass)",
            color = TermDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Filter Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TermDim, RectangleShape)
                .background(TermBlack)
                .padding(6.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(TermWhite),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "/ search apps by name or package...",
                            color = TermDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "── INSTALLED APPLICATIONS (${filteredApps.size}) ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Applications List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(filteredApps) { entry ->
                val currentRoute = routesMap[entry.packageName] ?: if (entry.isTurn) "turn" else "direct"
                val isTurn = currentRoute == "turn"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newRoute = if (isTurn) "direct" else "turn"
                            routesMap[entry.packageName] = newRoute
                            entry.isTurn = !isTurn
                        }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.appName,
                            color = TermWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Text(
                            text = entry.packageName,
                            color = TermDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .border(1.dp, TermWhite, RectangleShape)
                            .background(if (isTurn) TermWhite else TermBlack)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isTurn) "[TURN]" else "[DIRECT]",
                            color = if (isTurn) TermBlack else TermWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TerminalButton("[S]AVE") {
                onSaveRoutes(routesMap.toMap())
                onBack()
            }
            TerminalButton("[R]ESET") {
                routesMap.clear()
            }
            TerminalButton("[B]ACK") {
                onBack()
            }
        }
    }
}
