package com.hebiace.campus.ui.life

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebiace.campus.AppState
import com.hebiace.campus.CampusRepo
import com.hebiace.campus.data.qr.scanQrCode
import com.hebiace.campus.data.qzxy.scanShowerDevices
import com.hebiace.campus.model.ShowerDevice
import com.hebiace.campus.model.WasherModel
import com.hebiace.campus.model.WasherProgram
import kotlinx.coroutines.launch

@Composable
fun LifeScreen() {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("生活", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
        item { ShowerSection() }
        item { WasherSection() }
    }
}

// ============================================================
// 洗浴（趣智校园）
// ============================================================

/** 解析扫码文本：返回 (snCode?, mac?) */
private fun parseShowerScan(text: String): Pair<String?, String?> {
    val t = text.trim()
    val mac = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
        .find(t)?.value?.replace('-', ':')?.uppercase()
    var sn: String? = null
    for (k in listOf("snCode", "deviceSnCode", "sn", "code", "deviceNo")) {
        sn = Regex("(?:$k)[=:]([^&\\s\"',}]+)").find(t)?.groupValues?.get(1)
        if (sn != null) break
    }
    if (sn == null && mac == null && t.isNotBlank()) {
        sn = if (t.contains(':')) null else t
    }
    return sn to mac
}

@Composable
private fun ShowerSection() {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf(AppState.qzxyPhone()) }
    var smsCode by remember { mutableStateOf("") }
    var session by remember { mutableStateOf(CampusRepo.qzxySession()) }
    var wallet by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<ShowerDevice>>(emptyList()) }
    var activeKey by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var consume by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val client = remember { CampusRepo.newQzxyClient() }

    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("洗浴 · 趣智校园", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            if (session == null) {
                OutlinedTextField(phone, { phone = it }, label = { Text("手机号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(smsCode, { smsCode = it }, label = { Text("验证码") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            msg = try { client.sendSms(phone); "验证码已发送" } catch (e: Exception) { e.message }
                            busy = false
                        }
                    }) { Text("发送") }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val s = client.loginBySms(phone, smsCode)
                            CampusRepo.saveQzxySession(s)
                            AppState.saveQzxyPhone(phone)
                            session = s
                            msg = "登录成功"
                        } catch (e: Exception) { msg = e.message } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("登录") }
            } else {
                Text("余额：${wallet ?: "加载中…"}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            statusText = null
                            devices = scanShowerDevices().map { r ->
                                try { client.deviceByMac(session!!, r.mac) } catch (e: Exception) {
                                    ShowerDevice(deviceName = r.name, macAddress = r.mac)
                                }
                            }
                            wallet = try { "¥${client.wallet(session!!).money}" } catch (e: Exception) { wallet }
                            busy = false
                        }
                    }) { Text(if (busy) "扫描中…" else "蓝牙搜设备") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            statusText = null
                            try {
                                val text = scanQrCode("对准热水器二维码") ?: return@launch
                                val (sn, mac) = parseShowerScan(text)
                                when {
                                    mac != null -> {
                                        val dev = client.deviceByMac(session!!, mac)
                                        devices = (devices + dev).distinctBy { it.snCode.ifEmpty { it.macAddress } }
                                        activeKey = dev.snCode.ifEmpty { dev.macAddress }
                                    }
                                    sn != null -> {
                                        val dev = ShowerDevice(deviceName = "扫码设备", snCode = sn)
                                        devices = (devices + dev).distinctBy { it.snCode }
                                        activeKey = sn
                                    }
                                    else -> msg = "无法识别二维码内容"
                                }
                            } catch (e: Exception) { msg = e.message } finally { busy = false }
                        }
                    }) { Text("扫码") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            wallet = try { "¥" + client.wallet(session!!).money.toString() } catch (e: Exception) { msg = e.message; wallet }
                            busy = false
                        }
                    }) { Text("刷新余额") }
                }

                devices.forEach { d ->
                    val key = d.snCode.ifEmpty { d.macAddress }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.deviceName.ifEmpty { d.macAddress }, style = MaterialTheme.typography.bodySmall)
                            Text(d.snCode.ifEmpty { d.macAddress }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (activeKey == key) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        busy = true
                                        statusText = null
                                        try {
                                            val status = client.queryUsing(session!!, d.snCode)
                                            when {
                                                status == null -> msg = "查询设备状态失败"
                                                status.using && status.isOwner -> {
                                                    val orderNo = status.orderNo ?: ""
                                                    client.stopShower(session!!, d.snCode, orderNo)
                                                    msg = "已请求结束"
                                                    val cost = runCatching { client.consumeResult(session!!, d.snCode, orderNo) }.getOrNull()
                                                    if (cost != null && cost > 0) consume = "¥${cost}" else consume = "扣费结算中"
                                                }
                                                status.using -> msg = "设备正在被他人使用"
                                                else -> {
                                                    client.startShower(session!!, d.snCode)
                                                    val orderNo = client.confirmStart(session!!, d.snCode)
                                                    msg = if (orderNo != null) "已开启（订单 $orderNo）" else "开启确认中，请稍候"
                                                }
                                            }
                                        } catch (e: Exception) { msg = e.message } finally { busy = false }
                                    }
                                }) { Text("开始/结束") }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val st = client.queryUsing(session!!, d.snCode)
                                            statusText = when {
                                                st == null -> "查询失败"
                                                !st.using -> "当前空闲，未开阀"
                                                st.isOwner -> "使用中（本人订单 ${st.orderNo ?: ""}）"
                                                else -> "使用中（他人订单）"
                                            }
                                        } catch (e: Exception) { msg = e.message } finally { busy = false }
                                    }
                                }) { Text("查看状态") }
                            }
                        } else {
                            OutlinedButton(onClick = { activeKey = key }) { Text("选择") }
                        }
                    }
                }

                if (devices.isEmpty()) {
                    Text("点「蓝牙搜设备」发现附近热水器，或「扫码」直接绑定设备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                statusText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                consume?.let { Text("本次消费：$it", color = MaterialTheme.colorScheme.primary) }
            }

            msg?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ============================================================
// 洗衣（U净）
// ============================================================

@Composable
private fun WasherSection() {
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf(AppState.ujingMobile()) }
    var captcha by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(AppState.ujingToken() != null) }
    var qrCode by remember { mutableStateOf("") }
    var program by remember { mutableStateOf<WasherProgram?>(null) }
    var selectedModel by remember { mutableStateOf<WasherModel?>(null) }
    var orderId by remember { mutableStateOf<String?>(null) }
    var orderText by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val client = remember { CampusRepo.newUjingClient() }

    fun scanAndRecognize() {
        scope.launch {
            busy = true
            msg = null
            try {
                val text = scanQrCode("对准洗衣机二维码")
                if (!text.isNullOrBlank()) qrCode = text
                val code = text?.takeIf { it.isNotBlank() } ?: qrCode
                if (code.isBlank()) { msg = "未获取到二维码" ; return@launch }
                program = client.scanWasher(code)
                selectedModel = program?.models?.firstOrNull()
            } catch (e: Exception) { msg = e.message } finally { busy = false }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("洗衣 · U净", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            if (!loggedIn) {
                OutlinedTextField(mobile, { mobile = it }, label = { Text("手机号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(captcha, { captcha = it }, label = { Text("验证码") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            msg = try { client.requestCaptcha(mobile); "验证码已发送" } catch (e: Exception) { e.message }
                            busy = false
                        }
                    }) { Text("发送") }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            client.login(mobile, captcha)
                            client.currentToken()?.let { AppState.saveUjingToken(it) }
                            AppState.saveUjingMobile(mobile)
                            loggedIn = true
                            msg = "登录成功"
                        } catch (e: Exception) { msg = e.message } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("登录") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(qrCode, { qrCode = it }, label = { Text("洗衣机二维码") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedButton(onClick = { scanAndRecognize() }) { Text("扫码") }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        busy = true
                        msg = null
                        try {
                            program = client.scanWasher(qrCode)
                            selectedModel = program?.models?.firstOrNull()
                        } catch (e: Exception) { msg = e.message } finally { busy = false }
                    }
                }, enabled = !busy && qrCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "识别中…" else "识别设备")
                }

                program?.let { p ->
                    if (!p.createOrderEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("该设备暂不可用：${p.reason.ifEmpty { "未知原因" }}", color = MaterialTheme.colorScheme.error)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("${p.deviceTypeName} · ${p.storeName} · ${p.deviceNo}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        p.models.forEach { m ->
                            val selected = m.id == selectedModel?.id
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .clickable { selectedModel = m },
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("${m.name} · ${m.timeMinutes}分钟 · ¥${m.priceFen / 100.0}",
                                        Modifier.weight(1f),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    orderId = client.createWasherOrder(p, selectedModel?.id ?: p.defaultModelId)
                                    orderText = "订单已创建，请支付后启动"
                                    msg = null
                                } catch (e: Exception) { msg = e.message } finally { busy = false }
                            }
                        }, enabled = !busy && selectedModel != null, modifier = Modifier.fillMaxWidth()) { Text("下单并支付") }
                    }
                }

                orderId?.let { id ->
                    Spacer(Modifier.height(8.dp))
                    Text("订单：$id", style = MaterialTheme.typography.bodySmall)
                    orderText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val d = client.startWasher(id); orderText = d.statusText
                            }
                        }) { Text("启动") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val d = client.stopWasher(id); orderText = d.statusText
                            }
                        }) { Text("停止") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                try {
                                    val d = client.washerDetail(id)
                                    orderText = "状态：${d.statusText}" +
                                        (if (d.remainSeconds > 0) " · 剩余 ${d.remainSeconds / 60} 分" else "")
                                } catch (e: Exception) { msg = e.message }
                            }
                        }) { Text("查看状态") }
                    }
                }
            }

            msg?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
