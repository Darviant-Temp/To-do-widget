package com.darshan.todo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
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

val NOTE_IDX = intPreferencesKey("noteIdx")
val DIR = ActionParameters.Key<Int>("dir")

class NotesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openNotes = actionStartActivity(
            Intent(context, MainActivity::class.java).apply { action = "com.darshan.todo.NOTES" }
        )
        provideContent {
            val prefs = currentState<Preferences>()
            @Suppress("UNUSED_VARIABLE") val stamp = prefs[REFRESH]
            val notes = NoteStore.all(context)
            val idx = if (notes.isEmpty()) 0 else (prefs[NOTE_IDX] ?: 0).mod(notes.size)
            val n = notes.getOrNull(idx)
            val bg = Color(n?.bg ?: 0xFF1A1A24)
            val fg = Color(n?.fg ?: 0xFFFFFFFF)
            Column(GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(26.dp).padding(14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", GlanceModifier.clickable(actionRunCallback<StepAction>(actionParametersOf(DIR to -1)))
                        .padding(horizontal = 12.dp),
                        style = TextStyle(color = ColorProvider(fg), fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(if (notes.isEmpty()) "No notes" else "${idx + 1} / ${notes.size}",
                        style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("›", GlanceModifier.clickable(actionRunCallback<StepAction>(actionParametersOf(DIR to 1)))
                        .padding(horizontal = 12.dp),
                        style = TextStyle(color = ColorProvider(fg), fontSize = 26.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(n?.text ?: "Tap to add your first note", GlanceModifier.fillMaxWidth().clickable(openNotes),
                    maxLines = 14,
                    style = TextStyle(color = ColorProvider(fg), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

class StepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val d = parameters[DIR] ?: 0
        val count = NoteStore.all(context).size
        updateAppWidgetState(context, glanceId) {
            it[NOTE_IDX] = if (count == 0) 0 else ((it[NOTE_IDX] ?: 0) + d).mod(count)
        }
        NotesWidget().update(context, glanceId)
    }
}

class NotesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}
