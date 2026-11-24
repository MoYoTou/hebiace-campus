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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebiace.campus.AppState
import com.hebiace.campus.CampusRepo
import com.hebiace.campus.data.web.openWebLearn
import com.hebiace.campus.model.CourseEntry
import com.hebiace.campus.model.Provider
import kotlinx.coroutines.launch

@Composable
fun TasksScreen() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("学习", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Text(
                "点击课程进入学习 · 学习通 / 雨课堂",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StudySection(
                    title = "学习通",
                    loggedInInitially = AppState.chaoxingCookie().isNotEmpty(),
                    onLogin = { CampusRepo.loginChaoxing() },
                    onLogout = { AppState.saveChaoxingCookie("") },
                    onFetch = { CampusRepo.fetchChaoxingCourses() },
                    cookieHosts = "mooc1.chaoxing.com,www.chaoxing.com,i.chaoxing.com,passport2.chaoxing.com",
                )
            }
            item {
                StudySection(
                    title = "雨课堂",
                    loggedInInitially = AppState.yktCookie().isNotEmpty(),
                    onLogin = { CampusRepo.loginYuketang() },
                    onLogout = { AppState.saveYktCookie("") },
                    onFetch = { CampusRepo.fetchYuketangCourses() },
                    cookieHosts = "www.yuketang.cn",
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun StudySection(
    title: String,
    loggedInInitially: Boolean,
    onLogin: suspend () -> Boolean,
    onLogout: () -> Unit,
    onFetch: suspend () -> List<CourseEntry>,
    cookieHosts: String,
) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(loggedInInitially) }
    var courses by remember { mutableStateOf<List<CourseEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            msg = null
            try {
                courses = onFetch()
                if (courses.isEmpty()) msg = "暂无课程，点击「刷新」同步"
            } catch (e: Exception) {
                msg = e.message ?: "加载失败"
            } finally {
                loading = false
            }
        }
    }

    // 已登录后自动加载课程（含首次进入与登录完成后）
    LaunchedEffect(loggedIn) {
        if (loggedIn && courses.isEmpty()) refresh()
    }

    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (loggedIn) "已登录" else "未登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            (if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                if (loggedIn) {
                    OutlinedButton(onClick = { refresh() }, enabled = !loading) {
                        Text(if (loading) "加载中…" else "刷新")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        onLogout()
                        loggedIn = false
                        courses = emptyList()
                        msg = null
                    }) { Text("退出") }
                } else {
                    Button(onClick = {
                        scope.launch {
                            loading = true
                            msg = null
                            val ok = onLogin()
                            loggedIn = ok
                            loading = false
                            if (!ok) msg = "登录未完成"
                        }
                    }, enabled = !loading) {
                        Text(if (loading) "登录中…" else "登录")
                    }
                }
            }

            msg?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (loggedIn) {
                Spacer(Modifier.height(12.dp))
                when {
                    loading && courses.isEmpty() -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                    courses.isEmpty() -> Text(
                        "暂无课程，点击「刷新」同步课程列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> courses.forEach { c -> CourseCard(c, cookieHosts) }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(c: CourseEntry, cookieHosts: String) {
    val cookie = when (c.provider) {
        Provider.CHAOXING -> AppState.chaoxingCookie()
        Provider.YUKETANG -> AppState.yktCookie()
        Provider.MANUAL -> ""
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable { openWebLearn(c.url, cookie, c.name, cookieHosts) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.Medium)
                Text(
                    "点击进入学习",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("进入 ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
