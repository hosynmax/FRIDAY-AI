package com.friday.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Navy = Color(0xFF07111F)
private val Panel = Color(0xFF101F32)
private val Cyan = Color(0xFF00D9FF)
private val Mint = Color(0xFF4EF0C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER }
        enterImmersiveMode()
        setContent { FridayApp() }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }
    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

data class ChatMessage(val text: String, val fromUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridayApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("friday_settings", 0) }
    var input by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(prefs.getBoolean("speak_replies", true)) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            val saved = prefs.getString("chat_history", null)
            if (saved != null) runCatching {
                val array = JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(ChatMessage(item.getString("text"), item.getBoolean("user")))
                }
            }
            if (isEmpty()) add(ChatMessage("سلام! من فرایدی هستم. برای شروع پیام بده یا از دکمه میکروفون استفاده کن.", false))
        }
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("fa", "IR")
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }

    fun persist() {
        val array = JSONArray()
        messages.takeLast(100).forEach { array.put(JSONObject().put("text", it.text).put("user", it.fromUser)) }
        prefs.edit().putString("chat_history", array.toString()).apply()
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) input = spoken
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "با فرایدی صحبت کن")
            }
            runCatching { speechLauncher.launch(intent) }
        }
    }

    suspend fun sendMessage() {
        val question = input.trim()
        if (question.isEmpty() || busy) return
        messages.add(ChatMessage(question, true))
        input = ""
        persist()
        busy = true
        val answer = withContext(Dispatchers.IO) {
            requestFriday(serverUrl, messages.dropLast(1).takeLast(20), question)
        }
        messages.add(ChatMessage(answer, false))
        persist()
        busy = false
        if (speakReplies) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "friday_reply")
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Mint, background = Navy, surface = Panel)) {
        Scaffold(
            modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFF030812), Color(0xFF091D32), Color(0xFF160F32)))),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
                    title = { Column { Text("FRIDAY AI", fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, fontSize = 21.sp); Text("YOUR INTELLIGENT COMPANION", fontSize = 9.sp, color = Mint, letterSpacing = 1.5.sp) } },
                    actions = { IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, "تنظیمات", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC0B192B), titleContentColor = Color.White)
                )
            },
            bottomBar = {
                Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xEE10243A), Color(0xEE17132F)))).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "با فرایدی صحبت کن")
                            }
                            runCatching { speechLauncher.launch(intent) }
                        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Icon(Icons.Default.Mic, "گفتار به متن", tint = Mint) }
                    OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("پیامت را بنویس...") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(enabled = !busy && input.isNotBlank(), onClick = { scope.launch { sendMessage() } }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = Navy)) { Icon(Icons.Default.Send, "ارسال") }
                }
            }
        ) { padding ->
            if (showSettings) {
                SettingsPanel(Modifier.padding(padding), serverUrl, { serverUrl = it }, speakReplies, { speakReplies = it; prefs.edit().putBoolean("speak_replies", it).apply() }) {
                    prefs.edit().putString("server_url", serverUrl.trim().trimEnd('/')).apply()
                    serverUrl = serverUrl.trim().trimEnd('/')
                    showSettings = false
                }
            } else {
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071321), Color(0xFF0B1730), Color(0xFF120F28)))).padding(padding)) {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(142.dp).background(Brush.radialGradient(listOf(Color(0x884B55FF), Color(0x5530DFFF), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Color(0x5532DFFF), tonalElevation = 12.dp, shadowElevation = 18.dp) {
                                Box(Modifier.size(88.dp).background(Brush.linearGradient(listOf(Color(0xFF173E5B), Color(0xFF241B4A))), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("F", color = Cyan, fontSize = 52.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    Text(if (busy) "در حال فکر کردن..." else "در خدمتم.", Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), color = if (busy) Mint else Color.White, textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
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
}

private fun requestFriday(baseUrl: String, history: List<ChatMessage>, question: String): String {
    if (baseUrl.isBlank()) return "برای فعال شدن پاسخ هوشمند، نشانی سرور را در تنظیمات وارد کن. کلید API نباید داخل برنامه قرار بگیرد."
    return try {
        val connection = (URL(baseUrl.trimEnd('/') + "/v1/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val all = JSONArray()
        history.forEach { all.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
        all.put(JSONObject().put("role", "user").put("content", question))
        connection.outputStream.use { it.write(JSONObject().put("messages", all).toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) "خطای سرور ($code): ${response.take(250)}"
        else JSONObject(response).optString("reply").ifBlank { "سرور پاسخ خالی برگرداند." }
    } catch (e: Exception) {
        "اتصال به سرور برقرار نشد: ${e.localizedMessage ?: "خطای نامشخص"}. نشانی سرور و اینترنت را بررسی کن."
    }
}

@Composable
private fun SettingsPanel(modifier: Modifier = Modifier, serverUrl: String, onServerUrlChange: (String) -> Unit, speakReplies: Boolean, onSpeakChange: (Boolean) -> Unit, onSave: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("تنظیمات فرایدی", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("اتصال به سرور هوش مصنوعی", color = Mint)
        OutlinedTextField(value = serverUrl, onValueChange = onServerUrlChange, modifier = Modifier.fillMaxWidth(), label = { Text("نشانی سرور HTTPS") }, placeholder = { Text("https://...") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("خواندن پاسخ‌ها با صدا", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = speakReplies, onCheckedChange = onSpeakChange)
            Icon(Icons.Default.VolumeUp, null, tint = Mint)
        }
        Text("کلید API فقط روی سرور نگهداری می‌شود و داخل APK ذخیره نمی‌شود.", color = Color.Gray, fontSize = 13.sp)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("ذخیره تنظیمات") }
    }
}
