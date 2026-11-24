package com.hebiace.campus.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebiace.campus.AppState
import com.hebiace.campus.CampusRepo
import com.hebiace.campus.data.jw.ZhengfangClient
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
        item { JwSection() }
        item { ChaoxingSection() }
        item { YuketangSection() }
        item { AboutSection() }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ---------- 教务（正方） ----------

@Composable
private fun JwSection() {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(AppState.jwHost()) }
    var account by remember { mutableStateOf(AppState.jwAccount().first) }
    var password by remember { mutableStateOf(AppState.jwAccount().second) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SectionCard("教务系统 · 正方") {
        OutlinedTextField(host, { host = it }, label = { Text("教务地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(account, { account = it }, label = { Text("学号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("教务密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                busy = true
                msg = null
                try {
                    val client = ZhengfangClient(host = host)
                    client.login(account, password)
                    AppState.saveJwHost(host)
                    AppState.saveJwAccount(account, password)
                    msg = "登录成功，可去「课程」页同步课表"
                } catch (e: Exception) {
                    msg = e.message ?: "登录失败"
                } finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "登录中…" else "登录并保存")
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
        Text("默认地址 jwx.hebiace.edu.cn（河北建筑工程学院教学管理信息服务平台）。密码仅加密保存在本机。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- 学习通 ----------

@Composable
private fun ChaoxingSection() {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(AppState.chaoxingCookie().isNotEmpty()) }
    var msg by remember { mutableStateOf<String?>(null) }

    SectionCard("学习通 · 作业") {
        Text(if (loggedIn) "已登录" else "未登录", color = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val ok = CampusRepo.loginChaoxing()
                    loggedIn = ok
                    msg = if (ok) "登录成功" else "已取消"
                }
            }) { Text("登录") }
            OutlinedButton(onClick = {
                AppState.saveChaoxingCookie("")
                loggedIn = false
            }) { Text("退出") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
        Text("登录后会话保存在本机，下次打开自动保持登录。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- 雨课堂 ----------

@Composable
private fun YuketangSection() {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(AppState.yktCookie().isNotEmpty()) }
    var msg by remember { mutableStateOf<String?>(null) }

    SectionCard("雨课堂 · 作业") {
        Text(if (loggedIn) "已登录" else "未登录", color = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val ok = CampusRepo.loginYuketang()
                    loggedIn = ok
                    msg = if (ok) "登录成功" else "已取消"
                }
            }) { Text("登录") }
            OutlinedButton(onClick = {
                AppState.saveYktCookie("")
                loggedIn = false
            }) { Text("退出") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
        Text("雨课堂默认微信扫码登录。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- 关于 / 隐私 ----------

@Composable
private fun AboutSection() {
    SectionCard("隐私与免责声明") {
        Text("建院助手 v1.0.0", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        val items = listOf(
            "登录凭证（教务账号密码、学习通/雨课堂 Cookie、趣智校园/U净 Token）仅加密保存在本机，不会上传到任何第三方服务器。",
            "课表、作业、洗浴、洗衣数据分别读取自正方教务、学习通、雨课堂、趣智校园、U净，仅用于个人查看与提醒。",
            "本应用为非官方个人项目，请遵守各平台服务条款，勿用于刷课、代做等违规用途；使用后果由使用者自行承担。",
        )
        items.forEach { t ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("· ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
