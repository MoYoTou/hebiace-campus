package com.hebiace.campus.data.jw

import com.hebiace.campus.data.createHttpClient
import com.hebiace.campus.model.Course
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 新正方教务系统（jwglxt）客户端：登录 + 拉课表。
 * 河北建筑工程学院：教学管理信息服务平台 http://jwx.hebiace.edu.cn（新正方，路径 /jwglxt）。
 * 参考：开源项目 f3ngka0/youwu（schedule_bridge.py）与通用新正方抓取脚本。
 *
 * 登录：csrftoken + RSA/AES 加密密码；课表：POST /kbcx/xskbcx_cxXsgrkb.html（返回 JSON kbList）。
 */
class ZhengfangClient(
    private val host: String = "jwx.hebiace.edu.cn",
    private val basePath: String = "/jwglxt",
    private val client: HttpClient = createHttpClient(),
) {

    companion object {
        const val DEFAULT_HOST = "jwx.hebiace.edu.cn"
        const val DEFAULT_BASE_PATH = "/jwglxt"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://$host$basePath"

    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun getHtml(path: String): String =
        client.get("$base$path") {
            header("User-Agent", BROWSER_UA)
        }.bodyAsText()

    // ---------- 登录 ----------

    /**
     * 登录，成功后会话存于 client 的 Cookie 罐。
     * @throws ZhengfangException 登录失败
     */
    suspend fun login(account: String, password: String) {
        val loginHtml = getHtml("/xtgl/login_slogin.html")
        val csrf = Regex("""id="csrftoken"\s+name="csrftoken"\s+value="([^"]*)"""")
            .find(loginHtml)?.groupValues?.get(1)
            ?: Regex("""csrftoken\s*=\s*["']([^"']+)["']""").find(loginHtml)?.groupValues?.get(1)
            ?: throw ZhengfangException("未找到 csrftoken，登录页结构可能已变更")

        val keyHtml = getHtml("/xtgl/login_getPublicKey.html")
        val keyObj = runCatching { json.parseToJsonElement(keyHtml).jsonObject }.getOrNull()
            ?: throw ZhengfangException("未获取到加密公钥")
        val modulus = keyObj["modulus"]?.jsonPrimitive?.contentOrNull
            ?: throw ZhengfangException("公钥缺少 modulus")
        val exponent = keyObj["exponent"]?.jsonPrimitive?.contentOrNull
            ?: throw ZhengfangException("公钥缺少 exponent")

        val mm = jwEncryptPassword(password, modulus, exponent)
        val resp = client.submitForm(
            url = "$base/xtgl/login_slogin.html",
            formParameters = parameters {
                append("csrftoken", csrf)
                append("language", "zh_CN")
                append("yhm", account)
                append("mm", mm)
            },
        ) {
            header("User-Agent", BROWSER_UA)
        }.bodyAsText()

        // 登录成功通常返回 index_initMenu 内容或 JSON status=1；失败返回登录页或错误提示
        if (resp.contains("用户名或密码不正确") || resp.contains("密码错误") ||
            resp.contains("用户名不存在") || resp.contains("验证码")
        ) {
            throw ZhengfangException("登录失败：用户名或密码错误")
        }
        val menu = getHtml("/xtgl/index_initMenu.html")
        if (menu.isEmpty() || menu.contains("login_slogin")) {
            throw ZhengfangException("登录校验未通过")
        }
    }

    // ---------- 课表 ----------

    /**
     * 拉取学期课表。
     * @param year 学年起始年，如 2026（表示 2026-2027 学年）
     * @param term 1=第一学期 2=第二学期
     */
    suspend fun fetchSchedule(year: Int, term: Int): List<Course> {
        val xnm = year.toString()
        val xqm = when (term) { 1 -> "3"; 2 -> "12"; else -> "3" }
        val resp = client.post("$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151") {
            header("User-Agent", BROWSER_UA)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "xnm=$xnm&xqm=$xqm&kzlx=ck&xsdm=&xqh_id=&format=json"
            )
        }.bodyAsText()

        val obj = runCatching { json.parseToJsonElement(resp).jsonObject }.getOrNull()
            ?: throw ZhengfangException("课表返回格式无法识别")

        val kbList = obj["kbList"]?.jsonArray
            ?: throw ZhengfangException("课表数据为空（kbList 缺失）")

        val courses = ArrayList<Course>()
        for (el in kbList) {
            val kb = el.jsonObject
            val name = kb["kcmc"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isEmpty()) continue
            val day = kb["xqj"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            if (day !in 1..7) continue
            val sections = kb["jcs"]?.jsonPrimitive?.contentOrNull.orEmpty()   // 如 "1-2"
            val (start, end) = parseSections(sections)
            val weeks = parseWeeks(kb["zcd"]?.jsonPrimitive?.contentOrNull.orEmpty())
            courses.add(
                Course(
                    id = "${name}_${day}_${sections}",
                    name = name,
                    teacher = kb["xm"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    location = kb["cdmc"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    dayOfWeek = day,
                    startSection = start,
                    endSection = end,
                    weeks = weeks,
                    colorIndex = (courses.size % 12),
                )
            )
        }
        return courses
    }

    private fun parseSections(jcs: String): Pair<Int, Int> {
        val m = Regex("""(\d+)\s*-\s*(\d+)""").find(jcs)
        return if (m != null) {
            m.groupValues[1].toInt() to m.groupValues[2].toInt()
        } else {
            val n = jcs.toIntOrNull() ?: 1
            n to n
        }
    }

    /** 解析周次，如 "1-16周(单)" "1-8周,10-16周" "1-16周" */
    private fun parseWeeks(zcd: String): List<Int> {
        val weeks = ArrayList<Int>()
        for (range in Regex("""(\d+)\s*-\s*(\d+)""").findAll(zcd)) {
            val a = range.groupValues[1].toInt()
            val b = range.groupValues[2].toInt()
            for (w in a..b) weeks.add(w)
        }
        if (weeks.isEmpty()) {
            zcd.toIntOrNull()?.let { weeks.add(it) }
        }
        // 单双周处理：默认保留全部，含(单)/(双)时过滤
        val odd = zcd.contains("单")
        val even = zcd.contains("双")
        return when {
            odd -> weeks.filter { it % 2 == 1 }
            even -> weeks.filter { it % 2 == 0 }
            else -> weeks
        }
    }
}

class ZhengfangException(message: String) : Exception(message)
