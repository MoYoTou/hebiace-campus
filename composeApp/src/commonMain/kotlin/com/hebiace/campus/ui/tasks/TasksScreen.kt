package com.hebiace.campus.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebiace.campus.CampusRepo
import com.hebiace.campus.model.Provider
import com.hebiace.campus.model.Task
import com.hebiace.campus.model.TaskStatus
import com.hebiace.campus.model.TaskType
import com.hebiace.campus.util.TimeFmt
import kotlinx.coroutines.launch

private val STATUS_TABS = listOf(
    "未完成" to TaskStatus.TODO,
    "待批阅" to TaskStatus.SUBMITTED,
    "已完成" to TaskStatus.GRADED,
    "已过期" to TaskStatus.EXPIRED,
)

@Composable
fun TasksScreen() {
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(TaskStatus.TODO) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                tasks = CampusRepo.fetchAllTasks()
                if (tasks.isEmpty()) error = "请先在「我的」里登录学习通 / 雨课堂"
            } catch (e: Exception) {
                error = e.message ?: "抓取失败"
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("作业", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(
                    "学习通 + 雨课堂 · ${tasks.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { refresh() }, enabled = !loading) {
                Text(if (loading) "抓取中…" else "刷新")
            }
        }

        // 状态筛选
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            STATUS_TABS.forEach { (label, status) ->
                val selected = status == filter
                val count = tasks.count { it.status == status }
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { filter = status },
                ) {
                    Text(
                        "$label $count",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        val filtered = tasks.filter { it.status == filter }
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty() && !loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(filtered) { t -> TaskCard(t) }
        }
    }
}

@Composable
private fun TaskCard(t: Task) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.courseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                ProviderBadge(t.provider)
                if (t.type == TaskType.EXAM) {
                    Text(
                        "考试",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.padding(top = 4.dp))
            Text(t.title, fontWeight = FontWeight.Medium)
            if (t.grade.isNotEmpty()) {
                Text("得分 ${t.grade}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val remain = t.endTime - kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                Text(
                    if (t.status == TaskStatus.TODO) "剩余 ${TimeFmt.remainText(t.endTime)}"
                    else t.statusText.ifEmpty { statusLabel(t.status) },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.status == TaskStatus.TODO && remain in 1L..(24L * 60L * 60L * 1000L)) {
                        MaterialTheme.colorScheme.error
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderBadge(p: Provider) {
    val label = when (p) {
        Provider.CHAOXING -> "学习通"
        Provider.YUKETANG -> "雨课堂"
        Provider.MANUAL -> "手动"
    }
    val color = when (p) {
        Provider.CHAOXING -> MaterialTheme.colorScheme.secondary
        Provider.YUKETANG -> MaterialTheme.colorScheme.primary
        Provider.MANUAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun statusLabel(s: TaskStatus): String = when (s) {
    TaskStatus.TODO -> "未完成"
    TaskStatus.EXPIRED -> "已过期"
    TaskStatus.SUBMITTED -> "待批阅"
    TaskStatus.GRADED -> "已完成"
}
