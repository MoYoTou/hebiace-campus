package com.hebiace.campus.data.ujing

import com.hebiace.campus.data.createHttpClient
import com.hebiace.campus.model.WasherModel
import com.hebiace.campus.model.WasherOrder
import com.hebiace.campus.model.WasherProgram
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * U净（海尔洗衣/饮水）客户端。
 * 逆向成果源自开源项目 amamiyakazuki/FlandreSY（io_ujing_transport / ujing_http_adapter）。
 *
 * Base: https://phoenix.ujing.online/api/v1/
 * 认证：POST /login 手机号+验证码 → Bearer token
 * 洗衣：appCode=BA，weex-version=1.1.68；饮水：appCode=CA
 * 响应：{code, message, data}，code==0 成功
 */
class UjingClient(private val client: HttpClient = createHttpClient()) {

    companion object {
        const val BASE = "https://phoenix.ujing.online/api/v1/"
        private const val APP_VERSION = "2.4.14"
        private const val MODEL = "HBN-AL00"
        private const val BRAND = "HUAWEI"
        private const val USER_AGENT = "okhttp/4.3.1"
        private const val APP_CODE_ACCOUNT = "ZA"
        private const val APP_CODE_LAUNDRY = "BA"
        private const val WEEX_LAUNDRY = "1.1.68"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var token: String? = null

    fun hasToken(): Boolean = !token.isNullOrEmpty()
    fun currentToken(): String? = token
    fun setToken(t: String?) { token = t }
    fun invalidateAuth() { token = null }

    private fun requireToken(): String =
        token?.takeIf { it.isNotBlank() } ?: throw UjingException(null, "请先登录 U净账号")

    // ---------- 账号 ----------

    /** 发送验证码 */
    suspend fun requestCaptcha(mobile: String) {
        send(
            method = "GET",
            path = "captcha",
            appCode = APP_CODE_ACCOUNT,
            query = mapOf(
                "mobile" to mobile,
                "type" to "1",
                "sessionId" to "AFS_SWITCH_OFF",
                "token" to "AFS_SWITCH_OFF",
                "sig" to "AFS_SWITCH_OFF",
            ),
        )
    }

    /** 验证码登录，成功后内部持有 token */
    suspend fun login(mobile: String, captcha: String): UjingAccount {
        val data = send(
            method = "POST",
            path = "login",
            appCode = APP_CODE_ACCOUNT,
            body = buildJsonObject {
                put("mobile", mobile)
                put("captcha", captcha)
            },
        )
        val t = str(data, "token")
        if (t.isBlank()) throw UjingException(null, "登录响应缺少有效 token")
        token = t
        return UjingAccount(
            mobile = str(data, "mobile", mobile),
            userId = str(data, "userId"),
            serviceSubjectId = str(data, "serviceSubjectId"),
        )
    }

    // ---------- 洗衣 ----------

    /** 扫码识别洗衣机，返回套餐列表（不可下单时 models 为空） */
    suspend fun scanWasher(qrCode: String): WasherProgram {
        val t = requireToken()
        val scan = send(
            method = "POST",
            path = "devices/scanWasherCode",
            appCode = APP_CODE_LAUNDRY,
            weex = WEEX_LAUNDRY,
            token = t,
            body = buildJsonObject { put("qrCode", qrCode.trim()) },
        )
        val result = scan["result"]?.jsonObject ?: JsonObject(emptyMap())
        val deviceId = str(result, "deviceId")
        if (deviceId.isEmpty()) throw UjingException(null, "扫码响应缺少设备信息")

        val enabled = bool(result, "createOrderEnabled")
        val reason = str(result, "reason")
        if (!enabled) {
            return WasherProgram(
                deviceId = deviceId,
                createOrderEnabled = false,
                reason = reason,
            )
        }

        val info = send(
            method = "GET",
            path = "app/washer/devices/program/info",
            appCode = APP_CODE_LAUNDRY,
            weex = WEEX_LAUNDRY,
            token = t,
            query = mapOf("deviceId" to deviceId),
        )
        val models = parseModels(info)
        var defaultId = 0
        if (models.any { it.id == 1 }) defaultId = 1 else if (models.isNotEmpty()) defaultId = models.first().id

        // 下单需要的 storeId/deviceTypeId 缓存在程序对象里
        programCache = ScannedProgram(
            deviceId = deviceId,
            deviceTypeId = int(result, "deviceTypeId"),
            storeId = str(info, "storeId"),
        )
        return WasherProgram(
            deviceId = deviceId,
            deviceNo = str(info, "deviceNo"),
            deviceTypeName = str(info, "deviceTypeName"),
            storeName = str(info, "storeName"),
            createOrderEnabled = true,
            reason = reason,
            defaultModelId = defaultId,
            models = models,
        )
    }

    /** 创建洗衣订单 */
    suspend fun createWasherOrder(
        program: WasherProgram,
        washModelId: Int,
        temperatureId: Int = 1,
    ): String {
        val t = requireToken()
        val cached = programCache ?: throw UjingException(null, "请先扫描洗衣机并获取套餐")
        val body = buildJsonObject {
            put("type", 1)
            put("deviceTypeId", cached.deviceTypeId)
            put("deviceId", cached.deviceId)
            put("deviceWashModelId", washModelId)
            put("storeId", cached.storeId)
            put("washTemperatureId", temperatureId)
        }
        val created = send(
            method = "POST",
            path = "orders/create",
            appCode = APP_CODE_LAUNDRY,
            weex = WEEX_LAUNDRY,
            token = t,
            body = body,
        )
        val orderId = str(created, "orderId")
        if (orderId.isEmpty() || orderId == "0") throw UjingException(null, "创建订单缺少有效 orderId")
        return orderId
    }

    /** 拉取支付宝支付参数（orderInfo），返回给上层跳转支付宝 SDK */
    suspend fun paymentOrderInfo(orderId: String): String {
        val t = requireToken()
        val args = send(
            method = "GET",
            path = "payment/arguments",
            appCode = APP_CODE_LAUNDRY,
            weex = WEEX_LAUNDRY,
            token = t,
            query = mapOf(
                "channel" to "alipay",
                "orderId" to orderId,
                "couponId" to "",
                "isUseRedPacket" to "false",
                "redPacketId" to "0",
                "payVersion" to "1",
            ),
        )
        val payInfo = args["payInfo"]?.jsonObject ?: JsonObject(emptyMap())
        val orderInfo = payInfo["orderInfo"]?.jsonPrimitive?.contentOrNull
        if (orderInfo.isNullOrBlank()) {
            throw UjingException(null, "未获取到支付宝支付参数，暂时只支持支付宝")
        }
        return orderInfo
    }

    suspend fun startWasher(orderId: String): WasherOrder {
        val t = requireToken()
        send("GET", "orders/$orderId/control/start", APP_CODE_LAUNDRY, WEEX_LAUNDRY, t)
        return washerDetail(orderId, t)
    }

    suspend fun stopWasher(orderId: String): WasherOrder {
        val t = requireToken()
        send("GET", "orders/$orderId/control/stop", APP_CODE_LAUNDRY, WEEX_LAUNDRY, t)
        return washerDetail(orderId, t)
    }

    suspend fun cancelWasher(orderId: String) {
        val t = requireToken()
        send(
            method = "POST",
            path = "orders/$orderId/cancel",
            appCode = APP_CODE_LAUNDRY,
            weex = WEEX_LAUNDRY,
            token = t,
            body = buildJsonObject { put("orderId", orderId) },
        )
    }

    suspend fun washerDetail(orderId: String): WasherOrder = washerDetail(orderId, requireToken())

    private suspend fun washerDetail(orderId: String, t: String): WasherOrder {
        val d = send("GET", "orders/$orderId/detail", APP_CODE_LAUNDRY, WEEX_LAUNDRY, t)
        return WasherOrder(
            orderId = orderId,
            deviceNo = str(d, "deviceNo"),
            status = str(d, "status"),
            statusText = str(d, "statusRemark", str(d, "status")),
            payPrice = formatPrice(d["payPrice"]),
            remainSeconds = int(d, "remainTime"),
            countDownSeconds = int(d, "countDown"),
        )
    }

    // ---------- 内部 ----------

    private var programCache: ScannedProgram? = null

    private data class ScannedProgram(
        val deviceId: String,
        val deviceTypeId: Int,
        val storeId: String,
    )

    private suspend fun send(
        method: String,
        path: String,
        appCode: String,
        weex: String? = null,
        token: String? = null,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
    ): JsonObject {
        val url = BASE + path
        val resp = if (method == "GET") {
            client.get(url) {
                applyHeaders(appCode, weex, token)
                query.forEach { parameter(it.key, it.value) }
            }
        } else {
            client.post(url) {
                applyHeaders(appCode, weex, token)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
        }
        return parseData(resp.bodyAsText())
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(
        appCode: String, weex: String?, token: String?,
    ) {
        header("x-mobile-brand", BRAND)
        header("x-mobile-model", MODEL)
        header("x-app-version", APP_VERSION)
        header("x-mobile-id", "")
        header("accept-encoding", "identity")
        header("user-agent", USER_AGENT)
        header("x-app-code", appCode)
        if (!weex.isNullOrEmpty()) header("weex-version", weex)
        if (!token.isNullOrEmpty()) header("authorization", "Bearer $token")
    }

    private fun parseData(resp: String): JsonObject {
        val obj = runCatching { json.parseToJsonElement(resp).jsonObject }.getOrNull()
            ?: throw UjingException(null, "响应不是合法 JSON")
        val code = obj["code"]?.jsonPrimitive?.intOrNull
        if (code == null || code != 0) {
            val msg = obj["message"]?.jsonPrimitive?.contentOrNull ?: "接口返回错误"
            throw UjingException(code, msg)
        }
        return obj["data"]?.jsonObject ?: JsonObject(emptyMap())
    }

    private fun parseModels(info: JsonObject): List<WasherModel> {
        val raw = info["deviceWashModel"] ?: return emptyList()
        val arr = raw as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val m = el.jsonObject
            WasherModel(
                id = int(m, "workModelId"),
                name = str(m, "workModelName"),
                priceFen = int(m, "basePrice"),
                timeMinutes = int(m, "time"),
            )
        }
    }

    private fun str(obj: JsonObject, key: String, fallback: String = ""): String =
        obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallback

    private fun int(obj: JsonObject, key: String): Int =
        obj[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun bool(obj: JsonObject, key: String): Boolean {
        val v = obj[key] ?: return false
        return v.jsonPrimitive.let { p ->
            p.booleanOrNull ?: (p.contentOrNull?.let { it == "true" || it == "1" } ?: false)
        }
    }

    private fun formatPrice(v: Any?): String = when (v) {
        null -> ""
        is Number -> "¥" + twoDecimals(v.toDouble())
        else -> {
            val n = v.toString().toDoubleOrNull()
            if (n != null) "¥" + twoDecimals(n) else v.toString()
        }
    }

    private fun twoDecimals(d: Double): String {
        val cents = kotlin.math.round(d * 100).toLong()
        val yuan = cents / 100
        val rem = if (cents < 0) -cents else cents
        return "$yuan.${(rem % 100).toString().padStart(2, '0')}"
    }
}

data class UjingAccount(
    val mobile: String,
    val userId: String,
    val serviceSubjectId: String,
)

class UjingException(val code: Int?, message: String) : Exception(message)
