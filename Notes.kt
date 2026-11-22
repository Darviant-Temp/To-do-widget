package com.darshan.todo

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class Note(val id: Long, val text: String, val bg: Long, val fg: Long)

object NoteStore {
    private fun sp(c: Context) = c.getSharedPreferences("notes", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<Note> {
        val a = JSONArray(sp(c).getString("n", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Note(o.getLong("id"), o.getString("t"), o.getLong("bg"), o.getLong("fg"))
        }
    }

    private fun save(c: Context, l: List<Note>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("bg", it.bg).put("fg", it.fg)) }
        sp(c).edit().putString("n", a.toString()).commit()
    }

    @Synchronized
    fun upsert(c: Context, n: Note) {
        val l = all(c).toMutableList()
        val i = l.indexOfFirst { it.id == n.id }
        if (i >= 0) l[i] = n else l.add(0, n)
        save(c, l)
    }

    @Synchronized
    fun delete(c: Context, id: Long) = save(c, all(c).filter { it.id != id })
}

@OptIn(DelicateCoroutinesApi::class)
fun refreshNotes(context: Context) {
    val app = context.applicationContext
    GlobalScope.launch(Dispatchers.Default) {
        GlanceAppWidgetManager(app).getGlanceIds(NotesWidget::class.java).forEach { id ->
            updateAppWidgetState(app, id) { it[REFRESH] = System.nanoTime() }
            NotesWidget().update(app, id)
        }
    }
}

private val NBG = Color(0xFF0B0B12)
private val LIME = Color(0xFFC6FF00)
private val PINK = Color(0xFFFF2E93)
private val BGS = listOf(0xFFFFF176, 0xFFFF9EC7, 0xFF9AF0FF, 0xFFB8FF5C, 0xFFFFB05C, 0xFFC4A1FF,
    0xFFFF6B6B, 0xFF5CFFB0, 0xFFFFFFFF, 0xFF1A1A24, 0xFF000000, 0xFF2B2BFF)
private val FGS = listOf(0xFF111111, 0xFFFFFFFF, 0xFF2B2BFF, 0xFFD6006E, 0xFF0B7A3B, 0xFFFFE600, 0xFFFF5A00, 0xFF6A00FF)

@Composable
fun Root(tick: Int, start: Int) {
    var screen by remember { mutableIntStateOf(start) }
    var editorOpen by remember { mutableStateOf(false) }
    val loud = screen == 1
    Box(Modifier.fillMaxSize()) {
        if (screen == 0) App(tick) else NotesScreen(tick) { editorOpen = it }
        if (!(loud && editorOpen)) {
            Row(
                Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 16.dp, end = 18.dp)
                    .clip(CircleShape).background(if (loud) Color(0x33FFFFFF) else Color.White.copy(alpha = .7f)).padding(3.dp)
            ) {
                listOf("Tasks", "Notes").forEachIndexed { i, label ->
                    val on = screen == i
                    Text(
                        label, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (on) (if (loud) Color.Black else Color.White) else (if (loud) Color.White else INK),
                        modifier = Modifier.clip(CircleShape)
                            .background(if (on) (if (loud) LIME else INDIGO) else Color.Transparent)
                            .clickable { screen = i }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotesScreen(tick: Int, onEditing: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var notes by remember { mutableStateOf(NoteStore.all(ctx)) }
    LaunchedEffect(tick) { notes = NoteStore.all(ctx) }
    var editing by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(editing) { onEditing(editing != null) }
    BackHandler(enabled = editing != null) { editing = null }
    fun sync() { notes = NoteStore.all(ctx); refreshNotes(ctx) }
    val e = editing
    if (e != null) {
        Editor(e,
            onSave = { NoteStore.upsert(ctx, it); editing = null; sync() },
            onDelete = { NoteStore.delete(ctx, e.id); editing = null; sync() },
            onBack = { editing = null })
    } else {
        Box(Modifier.fillMaxSize().background(NBG)) {
            Column(Modifier.systemBarsPadding().padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                Box {
                    Text("NOTES", color = PINK, fontSize = 52.sp, fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp, modifier = Modifier.offset(4.dp, 4.dp))
                    Text("NOTES", color = LIME, fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                }
                Text("${notes.size} stickies", color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                if (notes.isEmpty()) Text("Nothing stuck yet. Hit the big + and make some noise.",
                    color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
                LazyVerticalStaggeredGrid(
                    StaggeredGridCells.Fixed(2), Modifier.weight(1f),
                    verticalItemSpacing = 16.dp, horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(notes, key = { it.id }) { n ->
                        Box(
                            Modifier.rotate(if (n.id % 2L == 0L) -1.6f else 1.6f).clip(RoundedCornerShape(6.dp))
                                .background(Color(n.bg)).clickable { editing = n }.padding(14.dp)
                        ) {
                            Text(n.text, color = Color(n.fg), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                maxLines = 9, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).systemBarsPadding().padding(22.dp).size(64.dp).clip(CircleShape)
                    .background(LIME).clickable { editing = Note(System.currentTimeMillis(), "", 0xFFFFF176, 0xFF111111) },
                contentAlignment = Alignment.Center
            ) { Text("+", color = Color.Black, fontSize = 34.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun Editor(note: Note, onSave: (Note) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf(note.text) }
    var bg by remember { mutableLongStateOf(note.bg) }
    var fg by remember { mutableLongStateOf(note.fg) }
    Column(Modifier.fillMaxSize().background(Color(bg)).systemBarsPadding().imePadding().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color(fg), fontSize = 28.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text("DELETE", color = Color(fg), fontSize = 13.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { onDelete() }.padding(10.dp))
            Spacer(Modifier.width(8.dp))
            Text("SAVE", color = Color(bg), fontSize = 14.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(CircleShape).background(Color(fg))
                    .clickable { if (text.isBlank()) onDelete() else onSave(note.copy(text = text, bg = bg, fg = fg)) }
                    .padding(horizontal = 20.dp, vertical = 9.dp))
        }
        TextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Write something loud...", color = Color(fg).copy(alpha = .5f), fontSize = 24.sp) },
            textStyle = TextStyle(color = Color(fg), fontSize = 24.sp, fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(fg)
            ),
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Label("BACKGROUND", fg); Swatches(BGS, bg) { bg = it }
        Label("FONT COLOUR", fg); Swatches(FGS, fg) { fg = it }
    }
}

@Composable
private fun Label(t: String, fg: Long) =
    Text(t, color = Color(fg).copy(alpha = .7f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)

@Composable
private fun Swatches(list: List<Long>, sel: Long, pick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        list.forEach { c ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                .border(if (c == sel) 5.dp else 2.dp, if (c == sel) PINK else Color(0x66808080), CircleShape)
                .clickable { pick(c) })
        }
    }
}
