package com.friday.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF07111F)
private val Panel = Color(0xFF101F32)
private val Cyan = Color(0xFF00D9FF)
private val Mint = Color(0xFF4EF0C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FridayApp() }
    }
}

data class ChatMessage(val text: String, val fromUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridayApp() {
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf(ChatMessage("سلام! من فرایدی هستم. چطور می‌توانم کمکت کنم؟", false)) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Mint, background = Navy, surface = Panel)) {
        Scaffold(containerColor = Navy, topBar = {
            TopAppBar(
                title = { Column { Text("FRIDAY AI", fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("دستیار هوشمند شخصی", fontSize = 12.sp, color = Mint) } },
                actions = { IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, "تنظیمات", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White)
            )
        }, bottomBar = {
            Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("پیامت را بنویس...") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { if (input.isNotBlank()) { messages.add(ChatMessage(input.trim(), true)); messages.add(ChatMessage("پیامت دریافت شد. برای پاسخ هوشمند، اتصال امن به سرویس هوش مصنوعی باید در مرحله بعد پیکربندی شود.", false)); input = "" } }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = Navy)) { Icon(Icons.Default.Send, "ارسال") }
            }
        }) { padding ->
            if (showSettings) SettingsPanel(Modifier.padding(padding)) else Column(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(138.dp).background(Brush.radialGradient(listOf(Cyan.copy(alpha = .24f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(96.dp).background(Brush.linearGradient(listOf(Color(0xFF123A55), Color(0xFF0B1A2A))), CircleShape), contentAlignment = Alignment.Center) {
                            Text("F", color = Cyan, fontSize = 48.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                Text("در خدمتم.", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), color = Color.White, textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.Start else Arrangement.End) {
                            Surface(color = if (msg.fromUser) Color(0xFF17334A) else Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                                Text(msg.text, Modifier.padding(14.dp), color = Color.White, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("تنظیمات فرایدی", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("زبان رابط: فارسی", color = Color.LightGray)
        Text("نسخه برنامه: 1.0.0 (پایه اولیه)", color = Color.LightGray)
        Text("اتصال هوش مصنوعی: هنوز پیکربندی نشده", color = Mint)
        Text("کلیدهای API باید فقط در سرور امن نگهداری شوند، نه داخل APK.", color = Color.Gray, fontSize = 13.sp)
    }
}
