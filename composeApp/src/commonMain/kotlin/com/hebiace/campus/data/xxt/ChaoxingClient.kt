package com.hebiace.campus.data.xxt

import com.hebiace.campus.data.createHttpClient
import com.hebiace.campus.model.Provider
import com.hebiace.campus.model.Task
import com.hebiace.campus.model.TaskStatus
import com.hebiace.campus.model.TaskType
import com.hebiace.campus.util.TimeFmt
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 学习通（超星）作业/考试抓取。
 * 逆向成果源自开源项目 siye4366/xxt-Tool（XtApi.kt / HtmlParsers.kt）。
 *
 * 登录走官方 WebView 后取 Cookie（本类只消费 cookie）。
 * 课程列表 backclazzdata 是 JSON；作业/考试列表是 H5 页面，这里用正则解析，替代 Jsoup（跨平台）。
 * status: 1未交 2已过期 3待批阅 4已完成
 */
class ChaoxingClient(
    private val cookie: String,
    private val client: HttpClient = createHttpClient(),
) {

    companion object {
        const val MOOC = "https://mooc1-api.chaoxing.com"
        const val LOGIN_URL =
            "https://passport2.chaoxing.com/login?fid=&newversion=true&refer=https%3A%2F%2Fi.chaoxing.com"
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Course(val courseId: String, val classId: String, val name: String)

    private suspend fun get(url: String, referer: String = "$MOOC/"): String =
        client.get(url) {
            header("User-Agent", BROWSER_UA)
            header("Accept", "*/*")
            header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (cookie.isNotBlank()) header("Cookie", cookie)
            header("Referer", referer)
        }.bodyAsText()

    /** 课程列表 */
    suspend fun fetchCourses(): List<Course> {
        val text = runCatching {
            get("$MOOC/mycourse/backclazzdata?view=json&getTchClazzType=1&mcode=", "https://i.chaoxing.com")
        }.getOrElse { return emptyList() }

        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        if (obj["result"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != 1) return emptyList()
        val channels = obj["channelList"]?.jsonArray ?: return emptyList()

        val out = ArrayList<Course>()
        for (ch in channels) {
            val channel = ch.jsonObject
            var content = channel["content"]
            if (content != null && content is kotlinx.serialization.json.JsonPrimitive) {
                content = runCatching { json.parseToJsonElement(content.content).jsonObject }.getOrNull()
            }
            val clazz = content?.jsonObject ?: continue
            val classId = clazz["id"]?.jsonPrimitive?.contentOrNull
                ?: channel["key"]?.jsonPrimitive?.contentOrNull ?: ""
            if (classId.isEmpty()) continue

            val courseData = clazz["course"]?.jsonObject?.get("data")?.jsonArray
            if (courseData != null && courseData.isNotEmpty()) {
                for (c in courseData) {
                    val co = c.jsonObject
                    val cid = co["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (cid.isEmpty()) continue
                    out.add(Course(cid, classId, co["name"]?.jsonPrimitive?.contentOrNull ?: ""))
                }
            } else {
                val cid = clazz["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val clazzArr = clazz["clazz"]?.jsonArray ?: continue
                if (cid.isEmpty()) continue
                for (c in clazzArr) {
                    val co = c.jsonObject
                    out.add(
                        Course(
                            cid,
                            co["id"]?.jsonPrimitive?.contentOrNull ?: "",
                            clazz["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        )
                    )
                }
            }
        }
        return out.distinctBy { it.courseId + "_" + it.classId }
    }

    suspend fun fetchWorkList(courseId: String, classId: String): List<Task> =
        ChaoxingParser.parseWorkList(get("$MOOC/phone/moocAnalysis/workList?courseId=$courseId&classId=$classId"), courseId, classId)

    suspend fun fetchExamList(courseId: String, classId: String): List<Task> =
        ChaoxingParser.parseExamList(get("$MOOC/phone/moocAnalysis/examList?courseId=$courseId&classId=$classId"), courseId, classId)

    /** 抓全部课程的全部作业+考试 */
    suspend fun fetchAll(): List<Task> {
        val courses = fetchCourses()
        val all = ArrayList<Task>()
        val nameById = courses.associate { it.courseId to it.name }
        for (c in courses) {
            runCatching { all.addAll(fetchWorkList(c.courseId, c.classId)) }
            runCatching { all.addAll(fetchExamList(c.courseId, c.classId)) }
        }
        return all.map { t ->
            if (t.courseName.isEmpty()) t.copy(courseName = nameById[t.courseId] ?: "") else t
        }
    }

    /** 测试 Cookie 是否有效 */
    suspend fun testCookie(): Boolean {
        val text = runCatching {
            get("$MOOC/mycourse/backclazzdata?view=json&getTchClazzType=1&mcode=", "https://i.chaoxing.com")
        }.getOrNull() ?: return false
        return text.contains("\"result\":1")
    }
}

/** 学习通 H5 列表页正则解析（替代 Jsoup，跨平台可用） */
object ChaoxingParser {

    fun statusOf(code: Int): TaskStatus = when (code) {
        1 -> TaskStatus.TODO
        2 -> TaskStatus.EXPIRED
        3 -> TaskStatus.SUBMITTED
        4 -> TaskStatus.GRADED
        else -> TaskStatus.TODO
    }

    private val DT = Regex("""<dt\b[^>]*\bdata="([^"]*)"[^>]*>([\s\S]*?)</dt>""")
    private val H3 = Regex("""<h3[^>]*>([\s\S]*?)</h3>""")
    private val P = Regex("""<p[^>]*>([\s\S]*?)</p>""")
    private val B = Regex("""<b[^>]*>([\s\S]*?)</b>""")
    private val STATUS_ATTR = Regex("""\bstatus="?(\d+)""")

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()

    fun parseWorkList(html: String, courseId: String, classId: String): List<Task> {
        val out = ArrayList<Task>()
        for (m in DT.findAll(html)) {
            val tag = m.groupValues[1]
            val inner = m.groupValues[2]
            val data = tag.substringBefore(" ").trim()
            if (data.isEmpty()) continue
            val title = stripTags(H3.find(inner)?.groupValues?.get(1).orEmpty())
            if (title.isEmpty()) continue
            val code = STATUS_ATTR.find(tag)?.groupValues?.get(1)?.toIntOrNull()
            val publish = stripTags(P.find(inner)?.groupValues?.get(1).orEmpty())
            val action = stripTags(B.find(inner)?.groupValues?.get(1).orEmpty())
            out.add(
                Task(
                    key = keyOf(TaskType.WORK, courseId, title),
                    title = title,
                    courseId = courseId,
                    classId = classId,
                    type = TaskType.WORK,
                    status = code?.let { statusOf(it) } ?: inferStatus(action),
                    publishTime = TimeFmt.parseFlexible(publish),
                    url = normalizeUrl(data),
                    statusText = action,
                    provider = Provider.CHAOXING,
                )
            )
        }
        return out
    }

    private val TR = Regex("""<tr\b[^>]*\bdata="([^"]*)"[^>]*>([\s\S]*?)</tr>""")
    private val TD = Regex("""<td[^>]*>([\s\S]*?)</td>""")

    fun parseExamList(html: String, courseId: String, classId: String): List<Task> {
        val out = ArrayList<Task>()
        for (m in TR.findAll(html)) {
            val tag = m.groupValues[1]
            val inner = m.groupValues[2]
            val data = tag.substringBefore(" ").trim()
            if (data.isEmpty()) continue
            val titleAttr = Regex("""title="([^"]*)"""").find(tag)?.groupValues?.get(1).orEmpty()
            val cells = TD.findAll(inner).map { stripTags(it.groupValues[1]) }.toList()
            val title = titleAttr.ifEmpty { cells.firstOrNull().orEmpty() }
            if (title.isEmpty()) continue
            val code = STATUS_ATTR.find(tag)?.groupValues?.get(1)?.toIntOrNull()
            val statusText = cells.getOrElse(2) { "" }
            val score = cells.getOrNull(1)?.takeIf { it != "—" && it != "-" }.orEmpty()
            out.add(
                Task(
                    key = keyOf(TaskType.EXAM, courseId, title),
                    title = title,
                    courseId = courseId,
                    classId = classId,
                    type = TaskType.EXAM,
                    status = code?.let { statusOf(it) } ?: inferStatus(statusText),
                    grade = if (code == 4) score else "",
                    url = normalizeUrl(data),
                    statusText = statusText,
                    provider = Provider.CHAOXING,
                )
            )
        }
        return out
    }

    fun inferStatus(text: String): TaskStatus = when {
        text.contains("已完成") || text.contains("完成") || text.contains("已批阅") -> TaskStatus.GRADED
        text.contains("待批阅") || text.contains("等待教师批阅") -> TaskStatus.SUBMITTED
        text.contains("过期") -> TaskStatus.EXPIRED
        else -> TaskStatus.TODO
    }

    fun keyOf(type: TaskType, courseId: String, title: String): String =
        "${type.name}|$courseId|${title.trim()}"

    fun normalizeUrl(path: String): String {
        val p = path.trim()
        return when {
            p.startsWith("http://") || p.startsWith("https://") -> p
            p.startsWith("//") -> "https:$p"
            p.startsWith("/") -> "https://mooc1-api.chaoxing.com$p"
            else -> "https://mooc1-api.chaoxing.com/$p"
        }
    }
}
