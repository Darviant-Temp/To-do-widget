package com.darshan.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root(tick, if (intent.action == "com.darshan.todo.NOTES") 1 else 0) }
    }
    override fun onResume() { super.onResume(); tick++ }
    override fun onStop() { super.onStop(); refreshWidget(this) }
}

private val Mist = Color(0xFFEEF0FF)
private val Lilac = Color(0xFFD9DCFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(tick: Int) {
    val ctx = LocalContext.current
    var tasks by remember { mutableStateOf(Store.all(ctx)) }
    LaunchedEffect(tick) { tasks = Store.all(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var newDay by remember { mutableLongStateOf(Store.today()) }
    var picking by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    fun sync() { tasks = Store.all(ctx); refreshWidget(ctx) }
    fun add() { if (text.isNotBlank()) { Store.add(ctx, text.trim(), newDay); text = ""; sync() } }

    val today = Store.today()
    val todo = tasks.filter { !it.done && it.due >= today }
    val pend = tasks.filter { !it.done && it.due < today }
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt }
    val shown = when (tab) { 0 -> todo; 1 -> pend; else -> done }
    val doneCount = done.size
    val progress by animateFloatAsState(if (tasks.isEmpty()) 0f else doneCount / tasks.size.toFloat(), label = "p")

    MaterialTheme(colorScheme = lightColorScheme(primary = INDIGO, onPrimary = Color.White)) {
        if (picking) {
            val start = editId?.let { id -> tasks.firstOrNull { it.id == id }?.due } ?: newDay
            val st = rememberDatePickerState(initialSelectedDateMillis = start * 86400000L)
            DatePickerDialog(
                onDismissRequest = { picking = false; editId = null },
                confirmButton = {
                    TextButton(onClick = {
                        st.selectedDateMillis?.let { ms ->
                            val d = Math.floorDiv(ms, 86400000L)
                            val e = editId
                            if (e != null) { Store.setDue(ctx, e, d); sync() } else newDay = d
                        }
                        picking = false; editId = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = false; editId = null }) { Text("Cancel") } }
            ) { DatePicker(state = st) }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Mist, Lilac)))) {
            Column(Modifier.systemBarsPadding().imePadding().padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(20.dp))
                Text("Tasks", color = INK, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .7f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(INDIGO))
                }
                Spacer(Modifier.height(6.dp))
                Text("$doneCount of ${tasks.size} done", color = INK.copy(alpha = .6f), fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .6f)).padding(4.dp)) {
                    listOf("To do", if (pend.isEmpty()) "Pending" else "Pending ${pend.size}", "Done").forEachIndexed { i, label ->
                        Box(
                            Modifier.clip(CircleShape).background(if (tab == i) INDIGO else Color.Transparent)
                                .clickable { tab = i }.padding(horizontal = 16.dp, vertical = 9.dp)
                        ) { Text(label, color = if (tab == i) Color.White else INK, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (shown.isEmpty()) item {
                        Text(when (tab) { 0 -> "Nothing planned. Add a task below."; 1 -> "Nothing overdue."; else -> "Ticked tasks land here." },
                            color = INK.copy(alpha = .55f), modifier = Modifier.padding(top = 24.dp))
                    }
                    items(shown, key = { it.id }) { t ->
                        val i = shown.indexOf(t)
                        Row(
                            Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(22.dp)).background(Color.White)
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (t.done) INDIGO else Color.Transparent)
                                    .border(2.dp, if (!t.done && t.due < today) CORAL else INDIGO, CircleShape)
                                    .clickable { Store.toggle(ctx, t.id); sync() },
                                contentAlignment = Alignment.Center
                            ) { if (t.done) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title, fontSize = 17.sp,
                                    color = if (t.done) INK.copy(alpha = .4f) else INK,
                                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                                )
                                Text(
                                    dateLabel(t.due), fontSize = 12.sp,
                                    color = if (!t.done && t.due < today) CORAL else INK.copy(alpha = .5f),
                                    modifier = Modifier.clickable { editId = t.id; picking = true }.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                            if (tab != 2) {
                                Text("▲", color = INK.copy(alpha = .55f), fontSize = 15.sp,
                                    modifier = Modifier.clickable { if (i > 0) { Store.swap(ctx, t.id, shown[i - 1].id); sync() } }.padding(9.dp))
                                Text("▼", color = INK.copy(alpha = .55f), fontSize = 15.sp,
                                    modifier = Modifier.clickable { if (i < shown.size - 1) { Store.swap(ctx, t.id, shown[i + 1].id); sync() } }.padding(9.dp))
                            }
                            Text("✕", color = INK.copy(alpha = .4f), fontSize = 15.sp,
                                modifier = Modifier.clickable { Store.delete(ctx, t.id); sync() }.padding(9.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = .75f))
                        .clickable { editId = null; picking = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Due ${dateLabel(newDay)}", color = INK, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                TextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("What needs doing?") },
                    shape = RoundedCornerShape(26.dp),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = {
                        Box(Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape).background(INDIGO).clickable { add() },
                            contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 24.sp) }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = INDIGO, focusedTextColor = INK, unfocusedTextColor = INK
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)
                )
            }
        }
    }
}
