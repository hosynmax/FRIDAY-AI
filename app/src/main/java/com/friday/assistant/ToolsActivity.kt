package com.friday.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val ToolBg = Color(0xFF071321)
private val ToolPanel = Color(0xFF10243A)
private val ToolAccent = Color(0xFF00D9FF)

class ToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ToolsScreen() }
    }
}

private data class FridayNote(val id: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("friday_settings", 0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme(primary = ToolAccent, background = ToolBg, surface = ToolPanel)) {
        Scaffold(
            containerColor = ToolBg,
            topBar = {
                TopAppBar(
                    title = { Text("ابزارهای فرایدی", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ToolPanel)
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("ماشین‌حساب", "یادداشت‌ها").forEachIndexed { index, title ->
                        FilterChip(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(title) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (selectedTab == 0) CalculatorPanel()
                else NotesPanel(prefs)
            }
        }
    }
}

@Composable
private fun CalculatorPanel() {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("۰") }
    var error by remember { mutableStateOf("") }
    val keys = listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ".", "(", ")", "C", "%", "⌫", "+")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = ToolPanel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.End) {
                Text(expression.ifBlank { "عبارت ریاضی" }, color = Color.LightGray, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text(result, color = ToolAccent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        OutlinedButton(
                            onClick = {
                                error = ""
                                when (key) {
                                    "C" -> { expression = ""; result = "۰" }
                                    "⌫" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
                                    else -> expression += key
                                }
                            },
                            modifier = Modifier.weight(1f).height(54.dp),
                            contentPadding = PaddingValues(2.dp)
                        ) { Text(key, fontSize = 18.sp, color = if (key in listOf("÷","×","−","+","=")) ToolAccent else Color.White) }
                    }
                }
            }
        }
        Button(
            onClick = {
                try {
                    result = ExpressionCalculator.format(ExpressionCalculator.evaluate(expression))
                    error = ""
                } catch (e: Exception) {
                    error = e.message ?: "عبارت نامعتبر است."
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("محاسبه کن", fontSize = 17.sp) }
        Text("محاسبه به‌صورت محلی و بدون اینترنت انجام می‌شود.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun NotesPanel(prefs: android.content.SharedPreferences) {
    fun loadNotes(): List<FridayNote> = runCatching {
        val array = JSONArray(prefs.getString("friday_notes", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            FridayNote(item.getString("id"), item.getString("text"))
        }
    }.getOrDefault(emptyList())

    var notes by remember { mutableStateOf(loadNotes()) }
    var draft by remember { mutableStateOf("") }

    fun persist(next: List<FridayNote>) {
        notes = next
        val array = JSONArray()
        next.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text)) }
        prefs.edit().putString("friday_notes", array.toString()).apply()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("یادداشت تازه") },
            minLines = 3,
            maxLines = 5
        )
        Button(
            onClick = {
                if (draft.isNotBlank()) {
                    persist(listOf(FridayNote(UUID.randomUUID().toString(), draft.trim())) + notes)
                    draft = ""
                }
            },
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ذخیره یادداشت") }
        Text("یادداشت‌ها فقط روی همین دستگاه ذخیره می‌شوند.", color = Color.Gray, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes, key = { it.id }) { note ->
                Surface(color = ToolPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(note.text, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { persist(notes.filterNot { it.id == note.id }) }) { Text("حذف", color = Color(0xFFFF8A80)) }
                    }
                }
            }
        }
        if (notes.isEmpty()) Text("هنوز یادداشتی ثبت نشده است.", color = Color.Gray)
    }
}
