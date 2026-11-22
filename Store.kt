package com.darshan.todo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class Task(val id: Long, val title: String, val done: Boolean, val doneAt: Long = 0, val due: Long = 0)

fun dateLabel(day: Long): String {
    val t = LocalDate.now().toEpochDay()
    return when (day) {
        t -> "Today"
        t + 1 -> "Tomorrow"
        t - 1 -> "Yesterday"
        else -> LocalDate.ofEpochDay(day).let {
            "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
        }
    }
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("todo", Context.MODE_PRIVATE)
    fun today(): Long = LocalDate.now().toEpochDay()

    @Synchronized
    fun all(c: Context): List<Task> {
        val a = JSONArray(sp(c).getString("tasks", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getString("title"), o.getBoolean("done"), o.optLong("at"), o.optLong("due", today()))
        }
    }

    private fun save(c: Context, list: List<Task>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done)
                .put("at", it.doneAt).put("due", it.due))
        }
        sp(c).edit().putString("tasks", a.toString()).commit()
    }

    @Synchronized
    fun add(c: Context, title: String, due: Long) = save(c, all(c) + Task(System.currentTimeMillis(), title, false, 0, due))

    @Synchronized
    fun toggle(c: Context, id: Long) = save(c, all(c).map {
        if (it.id == id) it.copy(done = !it.done, doneAt = if (!it.done) System.currentTimeMillis() else 0) else it
    })

    @Synchronized
    fun delete(c: Context, id: Long) = save(c, all(c).filter { it.id != id })

    @Synchronized
    fun setDue(c: Context, id: Long, due: Long) = save(c, all(c).map { if (it.id == id) it.copy(due = due) else it })

    @Synchronized
    fun swap(c: Context, a: Long, b: Long) {
        val l = all(c).toMutableList()
        val i = l.indexOfFirst { it.id == a }
        val j = l.indexOfFirst { it.id == b }
        if (i >= 0 && j >= 0) { val t = l[i]; l[i] = l[j]; l[j] = t; save(c, l) }
    }

    @Synchronized
    fun reorder(c: Context, ids: List<Long>) {
        val l = all(c).toMutableList()
        val slots = l.indices.filter { l[it].id in ids }
        val byId = l.associateBy { it.id }
        ids.forEachIndexed { k, id -> if (k < slots.size) l[slots[k]] = byId.getValue(id) }
        save(c, l)
    }
}
