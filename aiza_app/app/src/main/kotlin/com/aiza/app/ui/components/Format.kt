package com.aiza.app.ui.components

import java.text.DateFormat
import java.util.Date

fun relativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val s = (nowMs - epochMs) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        s < 7 * 86_400 -> "${s / 86_400} d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
    }
}

fun formatDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
