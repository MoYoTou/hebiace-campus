package com.hebiace.campus.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant

/** 宽松时间解析：把学习通/雨课堂/教务里各种日期字符串转成 epoch millis，失败返回 0 */
object TimeFmt {

    private val full = Regex(
        """(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})日?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val short = Regex(
        """(\d{1,2})[-/月](\d{1,2})日?\s*(\d{1,2}):(\d{2})"""
    )
    private val dateOnly = Regex(
        """(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})日?"""
    )

    fun parseFlexible(text: String): Long {
        full.find(text)?.let { m ->
            return runCatching {
                LocalDateTime(
                    m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(), m.groupValues[5].toInt(),
                    m.groupValues[6].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                ).toMillis()
            }.getOrDefault(0L)
        }
        short.find(text)?.let { m ->
            val year = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
            return runCatching {
                LocalDateTime(
                    year, m.groupValues[1].toInt(), m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(), m.groupValues[4].toInt(),
                ).toMillis()
            }.getOrDefault(0L)
        }
        dateOnly.find(text)?.let { m ->
            return runCatching {
                LocalDate(
                    m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                ).toMillis()
            }.getOrDefault(0L)
        }
        return 0L
    }

    /** 在正文里找「标签」后面的时间，如「截止时间：2026-11-24 23:59」 */
    fun grabTime(text: String, labels: List<String>): Long {
        for (label in labels) {
            val idx = text.indexOf(label)
            if (idx < 0) continue
            val window = text.substring(idx, minOf(idx + 40, text.length))
            val v = parseFlexible(window)
            if (v > 0) return v
        }
        return 0L
    }

    fun LocalDateTime.toMillis(): Long =
        toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    fun LocalDate.toMillis(): Long =
        LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    /** 截止剩余时间文案：如「3天2时」「5时30分」「已截止」 */
    fun remainText(endTime: Long, now: Long = Clock.System.now().toEpochMilliseconds()): String {
        if (endTime <= 0) return "时间未知"
        val diff = endTime - now
        if (diff < 0) return "已截止"
        val minutes = diff / 60_000
        if (minutes < 60) return "${minutes}分"
        val hours = minutes / 60
        if (hours < 24) return "${hours}时${minutes % 60}分"
        val days = hours / 24
        return "${days}天${hours % 24}时"
    }
}
