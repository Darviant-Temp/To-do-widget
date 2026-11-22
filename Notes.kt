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
import androidx.compose.ui.graphics.Brush
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

data class Note(val id: Long, val title: String, val text: String, val bg: Long, val fg: Long)

object NoteStore {
    private fun sp(c: Context) = c.getSharedPreferences("notes", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<Note> {
        val a = JSONArray(sp(c).getString("n", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Note(o.getLong("id"), o.optString("ti", ""), o.getString("t"), o.getLong("bg"), o.getLong("fg"))
        }
    }

    private fun save(c: Context, l: List<Note>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("ti", it.title).put("t", it.text).put("bg", it.bg).put("fg", it.fg))
        }
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

private val Cool1 = Color(0xFFF6F8FF)
private val Cool2 = Color(0xFFE4EAFF)
private val BGS = listOf(0xFFFFFFFF, 0xFFF1EEFF, 0xFFDCE4FF, 0xFFD6F3EA, 0xFFFFE9D6, 0xFFFFDDE5,
    0xFFFFF3C4, 0xFFE8EDF2, 0xFFCFE8FF, 0xFF20243A, 0xFF12141F, 0xFF4B3BFF)
private val FGS = listOf(0xFF151A3B, 0xFF4B3BFF, 0xFF2F6F5E, 0xFF8A3B5B, 0xFF5B6275, 0xFFFFFFFF, 0xFFE5484D, 0xFFB7791F)

@Composable
fun Root(tick: Int, start: Int) {
    var screen by remember { mutableIntStateOf(start) }
    var editorOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (screen == 0) App(tick) else NotesScreen(tick) { editorOpen = it }
        if (!(screen == 1 && editorOpen)) {
            Row(
                Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 16.dp, end = 18.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = .7f)).padding(3.dp)
            ) {
                listOf("Tasks", "Notes").forEachIndexed { i, label ->
                    val on = screen == i
                    Text(
                        label, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (on) Color.White else INK,
                        modifier = Modifier.clip(CircleShape).background(if (on) INDIGO else Color.Transparent)
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
    fun sync() { notes = NoteStore.all(ctx); refreshNotes(ctx) }
    val e = editing
    if (e != null) {
        Editor(e,
            onSave = { NoteStore.upsert(ctx, it); editing = null; sync() },
            onDelete = { NoteStore.delete(ctx, e.id); editing = null; sync() })
    } else {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Cool1, Cool2)))) {
            Column(Modifier.systemBarsPadding().padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(20.dp))
                Text("Notes", color = INK, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                Text("${notes.size} saved", color = INK.copy(alpha = .6f), fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                if (notes.isEmpty()) Text("Nothing here yet. Tap + to write your first note.",
                    color = INK.copy(alpha = .55f), fontSize = 16.sp)
                LazyVerticalStaggeredGrid(
                    StaggeredGridCells.Fixed(2), Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalItemSpacing = 12.dp, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { n -> NoteCard(n) { editing = n } }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).systemBarsPadding().padding(22.dp).size(60.dp).clip(CircleShape)
                    .background(INDIGO).clickable { editing = Note(System.currentTimeMillis(), "", "", 0xFFFFFFFF, 0xFF151A3B) },
                contentAlignment = Alignment.Center
            ) { Text("+", color = Color.White, fontSize = 30.sp) }
        }
    }
}

@Composable
private fun NoteCard(n: Note, open: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(n.bg)).border(1.dp, Color(0x1A151A3B), shape)
            .clickable { open() }.padding(16.dp)
    ) {
        if (n.title.isNotBlank()) Text(n.title, color = Color(n.fg), fontSize = 16.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (n.title.isNotBlank() && n.text.isNotBlank()) Spacer(Modifier.height(6.dp))
        if (n.text.isNotBlank()) Text(n.text, color = Color(n.fg).copy(alpha = .85f), fontSize = 14.sp,
            maxLines = 7, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun fieldColors(fg: Long) = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Color(fg)
)

@Composable
fun Editor(note: Note, onSave: (Note) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var text by remember { mutableStateOf(note.text) }
    var bg by remember { mutableLongStateOf(note.bg) }
    var fg by remember { mutableLongStateOf(note.fg) }
    fun finish() {
        if (title.isBlank() && text.isBlank()) onDelete()
        else onSave(note.copy(title = title.trim(), text = text, bg = bg, fg = fg))
    }
    BackHandler { finish() }
    Column(Modifier.fillMaxSize().background(Color(bg)).systemBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color(fg), fontSize = 26.sp, modifier = Modifier.clickable { finish() }.padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text("Delete", color = Color(fg).copy(alpha = .6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onDelete() }.padding(10.dp))
        }
        TextField(
            value = title, onValueChange = { title = it }, singleLine = true,
            placeholder = { Text("Title", color = Color(fg).copy(alpha = .4f), fontSize = 28.sp, fontWeight = FontWeight.Bold) },
            textStyle = TextStyle(color = Color(fg), fontSize = 28.sp, fontWeight = FontWeight.Bold),
            colors = fieldColors(fg), modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Start writing...", color = Color(fg).copy(alpha = .4f), fontSize = 18.sp) },
            textStyle = TextStyle(color = Color(fg), fontSize = 18.sp),
            colors = fieldColors(fg), modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Label("BACKGROUND", fg); Swatches(BGS, bg) { bg = it }
        Label("FONT COLOUR", fg); Swatches(FGS, fg) { fg = it }
    }
}

@Composable
private fun Label(t: String, fg: Long) =
    Text(t, color = Color(fg).copy(alpha = .6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)

@Composable
private fun Swatches(list: List<Long>, sel: Long, pick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        list.forEach { c ->
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                .border(if (c == sel) 3.dp else 1.dp, if (c == sel) INDIGO else Color(0x33808080), CircleShape)
                .clickable { pick(c) })
        }
    }
}
