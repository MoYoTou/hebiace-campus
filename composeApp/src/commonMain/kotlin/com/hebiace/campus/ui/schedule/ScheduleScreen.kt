package com.hebiace.campus.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebiace.campus.CampusRepo
import com.hebiace.campus.model.Course
import com.hebiace.campus.store.Settings
import com.hebiace.campus.ui.theme.CourseColors
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun todayDay(): Int = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.ordinal + 1

@Composable
fun ScheduleScreen() {
    var courses by remember { mutableStateOf(loadCachedCourses()) }
    var selectedDay by remember { mutableStateOf(todayDay()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { courses = loadCachedCourses() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("课程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(
                    "第 ${Settings.getLong("sem_year", 2026L)} 学年 · 第 ${Settings.getLong("sem_term", 1L)} 学期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            courses = CampusRepo.syncSchedule()
                            saveCourses(courses)
                        } catch (e: Exception) {
                            error = e.message ?: "同步失败"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
            ) {
                Text(if (loading) "同步中…" else "同步")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showAdd = true }) { Text("手动添加") }
        }

        // 星期选择
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DAY_NAMES.forEachIndexed { i, name ->
                val day = i + 1
                val selected = day == selectedDay
                val isToday = day == todayDay()
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).clickable { selectedDay = day },
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            name,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (isToday) "今" else "",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        val dayCourses = courses.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startSection }
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dayCourses.isEmpty() && !loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (courses.isEmpty()) "还没有课表，点右上角「同步」导入，或「手动添加」离线录入"
                            else "当天无课",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(dayCourses) { c -> CourseCard(c) }
        }
    }

    if (showAdd) {
        AddCourseDialog(
            courses = courses,
            onDismiss = { showAdd = false },
            onAdd = { c ->
                courses = courses + c
                saveCourses(courses)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddCourseDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onAdd: (Course) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(todayDay()) }
    var startSection by remember { mutableStateOf("1") }
    var endSection by remember { mutableStateOf("2") }
    var weeks by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加课程") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("教室/地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("星期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_NAMES.forEachIndexed { i, n ->
                        val d = i + 1
                        val sel = d == day
                        Surface(
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { day = d },
                        ) {
                            Text(
                                n,
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startSection, { startSection = it }, label = { Text("开始节") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(endSection, { endSection = it }, label = { Text("结束节") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(weeks, { weeks = it }, label = { Text("周次（如 1-16，留空=每周）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = startSection.toIntOrNull() ?: 1
                    val e = endSection.toIntOrNull() ?: s
                    onAdd(
                        Course(
                            id = "m_${Clock.System.now().toEpochMilliseconds()}",
                            name = name.trim(),
                            teacher = teacher.trim(),
                            location = location.trim(),
                            dayOfWeek = day,
                            startSection = minOf(s, e),
                            endSection = maxOf(s, e),
                            weeks = parseWeeks(weeks),
                            colorIndex = courses.size % CourseColors.size,
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 解析周次文本："1-16"、"1,3,5,7"、"1-8,10-16"，空/无法解析返回空列表（每周） */
private fun parseWeeks(text: String): List<Int> {
    val out = ArrayList<Int>()
    for (raw in text.split(',', '，', ' ', ';', '；')) {
        val p = raw.trim()
        if (p.isEmpty()) continue
        if (p.contains('-')) {
            val parts = p.split('-')
            val a = parts.firstOrNull()?.trim()?.toIntOrNull()
            val b = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (a != null && b != null && a <= b && a >= 1) for (i in a..b) out.add(i)
        } else {
            p.toIntOrNull()?.takeIf { it >= 1 }?.let { out.add(it) }
        }
    }
    return out.distinct().sorted()
}

@Composable
private fun CourseCard(c: Course) {
    val color = CourseColors[c.colorIndex % CourseColors.size]
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        Box(
            Modifier.width(5.dp).height(72.dp).background(color)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Medium)
                    Text(
                        "第 ${c.startSection}-${c.endSection} 节 · ${c.teacher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    c.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun saveCourses(c: List<Course>) =
    Settings.putString("courses", json.encodeToString(c))

private fun loadCachedCourses(): List<Course> {
    val raw = Settings.getString("courses")
    if (raw.isEmpty()) return emptyList()
    return runCatching { json.decodeFromString<List<Course>>(raw) }.getOrDefault(emptyList())
}
