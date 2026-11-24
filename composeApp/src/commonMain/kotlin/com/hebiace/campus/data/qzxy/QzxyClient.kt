package com.hebiace.campus.data.qzxy

import com.hebiace.campus.data.createHttpClient
import com.hebiace.campus.model.QzxySession
import com.hebiace.campus.model.QzxyWallet
import com.hebiace.campus.model.ShowerDevice
import com.hebiace.campus.util.md5Hex
import com.hebiace.campus.util.md5Last10Upper
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 趣智校园（洗浴/热水）客户端。
 * 逆向成果源自开源项目 yehu-imei/linyu 的 API-qzxy.md，接口见其完整文档。
 *
 * Base: https://v3-api.china-qzxy.cn
 * 认证：loginCode + userId + accountId + projectId + telephone + telPhone（两个手机号字段都要传）
 * 密码：MD5 取后 10 位大写
 * 注意：业务失败也常返回 HTTP 200，一律以 success / errorCode 字段为准。
 */
class QzxyClient(private val client: HttpClient = createHttpClient()) {

    companion object {
        const val BASE = "https://v3-api.china-qzxy.cn"
        const val VERSION = "6.5.28"
        const val PHONE_SYSTEM = "android"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- 登录 ----------

    /** 密码登录：password 会做 MD5 后 10 位大写 */
    suspend fun loginByPassword(telephone: String, password: String): QzxySession {
        val resp = client.submitForm(
            url = "$BASE/user/login",
            formParameters = parameters {
                append("telephone", telephone)
                append("password", md5Last10Upper(password))
                append("phoneSystem", PHONE_SYSTEM)
                append("type", "0")
                append("version", VERSION)
            }
        ).bodyAsText()
        return parseSession(resp)
    }

    /**
     * 短信验证码登录（secret 由手机号本地推导，任何手机号可用）。
     * 没注册过的手机号会自动注册。
     */
    suspend fun sendSms(telephone: String) {
        val secret = smsSecret(telephone)
        client.get("$BASE/user/verification/code/get") {
            parameter("telephone", telephone)
            parameter("typeId", "3")
            parameter("platform", "1")
            parameter("secret", secret)
        }
    }

    suspend fun loginBySms(telephone: String, smsCode: String): QzxySession {
        val resp = client.submitForm(
            url = "$BASE/user/registerAndLogin",
            formParameters = parameters {
                append("telephone", telephone)
                append("smsCode", smsCode)
                append("type", "5")
                append("phoneSystem", PHONE_SYSTEM)
                append("version", VERSION)
            }
        ).bodyAsText()
        return parseSession(resp)
    }

    // ---------- 认证字段 ----------

    private fun authFields(s: QzxySession): List<Pair<String, String>> = listOf(
        "loginCode" to s.loginCode,
        "userId" to s.userId,
        "accountId" to s.accountId,
        "projectId" to s.projectId,
        "telephone" to s.telephone,
        "telPhone" to s.telephone,   // 两个都要，值相同
        "phoneSystem" to PHONE_SYSTEM,
        "version" to VERSION,
    )

    // ---------- 业务 ----------

    suspend fun wallet(s: QzxySession): QzxyWallet {
        val obj = getData("/account/wallet", s)
        return QzxyWallet(
            money = obj["money"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: obj["accountRealMoney"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            realMoney = obj["accountRealMoney"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            givenMoney = obj["accountGivenMoney"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }

    suspend fun deviceByMac(s: QzxySession, mac: String): ShowerDevice {
        val obj = getData("/device/info/mac", s, listOf("macAddress" to mac))
        return ShowerDevice(
            deviceId = str(obj, "deviceId"),
            deviceName = str(obj, "deviceName"),
            snCode = str(obj, "snCode"),
            macAddress = str(obj, "macAddress"),
            withholdMoney = obj["withholdMoney"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            online = (obj["onlineStatusId"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
        )
    }

    /** 开始洗澡：下发开阀指令 */
    suspend fun startShower(s: QzxySession, snCode: String) {
        postData("/order/tcpDevice/downRate/rateOrder", s, listOf("xfModel" to "0", "snCode" to snCode))
    }

    /** 开阀结果确认：返回 orderNo，null 表示还没开好（需重试） */
    suspend fun confirmStart(s: QzxySession, snCode: String): String? {
        val obj = postData("/order/tcpDevice/query/downRateResult", s, listOf("snCode" to snCode))
        return obj["orderNo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /** 停止洗澡 */
    suspend fun stopShower(s: QzxySession, snCode: String, orderNo: String) {
        postData("/order/tcpDevice/closeOrder", s, listOf("snCode" to snCode, "orderNo" to orderNo))
    }

    /**
     * 查询进行中订单。
     * @return using=true 表示设备有订单（还在用），orderNo 为当前订单号（可能是他人订单，
     *   停止前需确认 isOwner）；using=false 表示没有订单（确实停了）。请求失败返回 null。
     */
    suspend fun queryUsing(s: QzxySession, snCode: String): ShowerStatus? {
        val resp = runCatching {
            client.submitForm(
                url = "$BASE/order/tcpDevice/query/rateOrder/using",
                formParameters = parameters {
                    append("xfModel", "0")
                    append("snCode", snCode)
                    authFields(s).forEach { append(it.first, it.second) }
                }
            ).bodyAsText()
        }.getOrNull() ?: return null

        val obj = runCatching { json.parseToJsonElement(resp).jsonObject }.getOrNull()
            ?: return null
        val code = obj["errorCode"]?.jsonPrimitive?.intOrNull
        val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
        // 307 = 设备正在使用中（即使是他人订单，success 为 false）
        if (code == 307) return ShowerStatus(true, null)
        val data = obj["data"]?.jsonObject
        val orderNo = data?.get("orderNo")?.jsonPrimitive?.contentOrNull
        val isOwner = data?.get("isOwner")?.jsonPrimitive?.booleanOrNull
        return when {
            success && orderNo.isNullOrBlank() -> ShowerStatus(false, null)  // 确实没有订单
            !orderNo.isNullOrBlank() -> ShowerStatus(true, orderNo, isOwner == true)
            else -> null  // 请求失败 / 其他业务错误：不确定
        }
    }

    /** 消费结算结果：consumeMoney 单位是「厘」，返回元 */
    suspend fun consumeResult(s: QzxySession, snCode: String, orderNo: String): Double {
        val obj = postData("/order/consumeOrder/result/query", s, listOf("snCode" to snCode, "orderNo" to orderNo))
        val fen = obj["consumeMoney"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        return fen / 1000.0   // 厘 → 元
    }

    suspend fun accountInfo(s: QzxySession): Pair<String, String> {
        val obj = getData("/account/info", s)
        return str(obj, "name") to str(obj, "idCardNumber")
    }

    suspend fun projectName(s: QzxySession): String {
        val obj = getData("/project/info/triple", s)
        return str(obj, "projectName")
    }

    // ---------- 内部 ----------

    private suspend fun getData(
        path: String,
        s: QzxySession,
        extra: List<Pair<String, String>> = emptyList(),
    ): JsonObject {
        val resp = client.get("$BASE$path") {
            authFields(s).forEach { parameter(it.first, it.second) }
            extra.forEach { parameter(it.first, it.second) }
        }.bodyAsText()
        return parseData(resp)
    }

    private suspend fun postData(
        path: String,
        s: QzxySession,
        extra: List<Pair<String, String>> = emptyList(),
    ): JsonObject {
        val resp = client.submitForm(
            url = "$BASE$path",
            formParameters = parameters {
                extra.forEach { append(it.first, it.second) }
                authFields(s).forEach { append(it.first, it.second) }
            }
        ).bodyAsText()
        return parseData(resp)
    }

    private fun parseData(resp: String): JsonObject {
        val obj = runCatching { json.parseToJsonElement(resp).jsonObject }.getOrNull()
            ?: throw QzxyException(null, "响应不是合法 JSON")
        val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            val code = obj["errorCode"]?.jsonPrimitive?.intOrNull
            val msg = obj["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "请求失败"
            throw QzxyException(code, msg)
        }
        return obj["data"]?.jsonObject ?: JsonObject(emptyMap())
    }

    private fun parseSession(resp: String): QzxySession {
        val obj = json.parseToJsonElement(resp).jsonObject
        val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            val code = obj["errorCode"]?.jsonPrimitive?.intOrNull
            val msg = obj["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "登录失败"
            throw QzxyException(code, msg)
        }
        val d = obj["data"]?.jsonObject ?: throw QzxyException(null, "登录响应缺少 data")
        val account = d["userAccount"]?.jsonObject ?: JsonObject(emptyMap())
        return QzxySession(
            loginCode = str(d, "loginCode"),
            userId = str(d, "userId"),
            accountId = str(account, "accountId"),
            projectId = str(account, "projectId"),
            telephone = str(d, "telephone"),
            name = str(account, "name"),
        )
    }

    private fun str(obj: JsonObject, key: String): String =
        obj[key]?.jsonPrimitive?.contentOrNull ?: ""

    /** secret = MD5(手机号前3位 + 后4位 + "klcx") */
    private fun smsSecret(telephone: String): String =
        md5Hex(telephone.take(3) + telephone.takeLast(4) + "klcx")
}

/** 设备进行中订单状态 */
data class ShowerStatus(
    val using: Boolean,
    val orderNo: String?,
    val isOwner: Boolean = false,
)

class QzxyException(val code: Int?, message: String) : Exception(message)
