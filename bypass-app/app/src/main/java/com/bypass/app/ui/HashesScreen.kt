package com.bypass.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import com.bypass.app.VkHashItem
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermDim
import com.bypass.app.ui.theme.TermWhite

@Composable
fun HashesScreen(
    currentHashMode: String, // "auto" or "manual"
    hashes: List<VkHashItem>,
    onModeChange: (String) -> Unit,
    onAddHashes: (List<String>) -> Unit,
    onOpenAutoWebView: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var inputQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBlack)
            .padding(12.dp)
            .border(1.dp, TermBorder, RectangleShape)
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // Title
        Text(
            text = "╔══ VK AUTHORIZATION ═══════════════",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Hash Mode Selection
        Text(
            text = "── HASH MODE ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onModeChange("auto") }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentHashMode.lowercase() == "auto") "● AUTO (JS WebView)" else "○ AUTO (JS WebView)",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (currentHashMode.lowercase() == "auto") FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onModeChange("manual") }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentHashMode.lowercase() == "manual") "● MANUAL INPUT" else "○ MANUAL INPUT",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (currentHashMode.lowercase() == "manual") FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hashes List
        Text(
            text = "── HASHES ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        val displayHashes = hashes.take(3).toMutableList()
        while (displayHashes.size < 3) {
            displayHashes.add(VkHashItem(raw = "", valid = false))
        }

        displayHashes.forEachIndexed { idx, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.raw.isNotEmpty()) "[#${idx + 1}] ${item.masked}" else "[#${idx + 1}] —",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = if (item.raw.isNotEmpty()) {
                        if (item.valid) "██ VALID ██" else "[EXPIRED]"
                    } else {
                        "EMPTY"
                    },
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalButton("[A]UTO-GENERATE") {
                onOpenAutoWebView()
            }
            TerminalButton("[P]ASTE LINK") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    inputQuery = text
                    statusMessage = "Pasted from clipboard"
                }
            }
            TerminalButton("[B]ACK") {
                onBack()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Input Field
        Text(
            text = "> INPUT VK CALL LINK OR HASH:",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TermWhite, RectangleShape)
                .background(TermBlack)
                .padding(8.dp)
        ) {
            BasicTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                textStyle = TextStyle(
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(TermWhite),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (inputQuery.isEmpty()) {
                        Text(
                            text = "https://vk.com/call/join/... or hash",
                            color = TermDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalButton("[+] APPLY HASH") {
                if (inputQuery.isNotBlank()) {
                    val tokens = inputQuery
                        .split(',', ';', '\n', ' ')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onAddHashes(tokens)
                    statusMessage = "Added ${tokens.size} hash(es)"
                    inputQuery = ""
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    color = TermDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "(supports raw hashes, call links, or comma-separated list)",
            color = TermDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}
