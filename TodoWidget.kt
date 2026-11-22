package com.darshan.todo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val INK = Color(0xFF151A3B)
val INK_DIM = Color(0x99151A3B)
val INDIGO = Color(0xFF4B3BFF)
val CORAL = Color(0xFFE5484D)
val TASK_ID = ActionParameters.Key<Long>("id")
val REFRESH = longPreferencesKey("refresh")

suspend fun refreshWidgetNow(context: Context) {
    val mgr = GlanceAppWidgetManager(context)
    for (w in listOf<GlanceAppWidget>(TodoWidget(), PendingWidget())) {
        mgr.getGlanceIds(w.javaClass).forEach { id ->
            updateAppWidgetState(context, id) { it[REFRESH] = System.nanoTime() }
            w.update(context, id)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun refreshWidget(context: Context) {
    val app = context.applicationContext
    GlobalScope.launch(Dispatchers.Default) { refreshWidgetNow(app) }
}

abstract class ListWidget(private val pending: Boolean) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        provideContent {
            val prefs = currentState<Preferences>()
            @Suppress("UNUSED_VARIABLE") val stamp = prefs[REFRESH]
            val today = Store.today()
            val tasks = Store.all(context).filter { !it.done && (if (pending) it.due < today else it.due == today) }
            val accent = if (pending) CORAL else INDIGO
            val rows = ((LocalSize.current.height.value - 100f) / 42f).toInt().coerceIn(1, 12)
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(18.dp)
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pending) "Pending" else "Today", GlanceModifier.defaultWeight().clickable(openApp),
                        style = TextStyle(color = ColorProvider(INK), fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Text("+", GlanceModifier.clickable(openApp).padding(horizontal = 8.dp),
                        style = TextStyle(color = ColorProvider(accent), fontSize = 30.sp, fontWeight = FontWeight.Medium))
                }
                Text("${tasks.size} left",
                    style = TextStyle(color = ColorProvider(if (pending) CORAL else INK_DIM), fontSize = 13.sp))
                Spacer(GlanceModifier.height(8.dp))
                if (tasks.isEmpty()) {
                    Text(if (pending) "Nothing overdue." else "All clear for now.",
                        style = TextStyle(color = ColorProvider(INK_DIM), fontSize = 15.sp))
                }
                tasks.take(rows).forEach { t ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 8.dp)
                            .clickable(actionRunCallback<ToggleAction>(actionParametersOf(TASK_ID to t.id))),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(if (pending) R.drawable.ic_ring_red else R.drawable.ic_ring),
                            contentDescription = null,
                            modifier = GlanceModifier.size(26.dp)
                        )
                        Spacer(GlanceModifier.width(12.dp))
                        Text(t.title, GlanceModifier.defaultWeight(), maxLines = 1,
                            style = TextStyle(color = ColorProvider(INK), fontSize = 16.sp))
                        if (pending) Text(dateLabel(t.due),
                            style = TextStyle(color = ColorProvider(INK_DIM), fontSize = 12.sp))
                    }
                }
                if (tasks.size > rows) {
                    Text("+${tasks.size - rows} more", GlanceModifier.clickable(openApp).padding(top = 4.dp),
                        style = TextStyle(color = ColorProvider(accent), fontSize = 14.sp))
                }
            }
        }
    }
}

class TodoWidget : ListWidget(false)
class PendingWidget : ListWidget(true)

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[TASK_ID]?.let { Store.toggle(context, it) }
        refreshWidgetNow(context)
    }
}

class TodoReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

class PendingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PendingWidget()
}
