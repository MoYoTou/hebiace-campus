package com.hebiace.campus.data.ykt

import com.hebiace.campus.data.createHttpClient
import com.hebiace.campus.model.Provider
import com.hebiace.campus.model.Task
import com.hebiace.campus.model.TaskStatus
import com.hebiace.campus.model.TaskType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 雨课堂（长江雨课堂）作业/考试抓取。
 * 逆向成果源自开源项目 siye4366/xxt-Tool（YtApi.kt）。
 *
 * 登录走 /web 微信扫码（WebView 取 Cookie），本类只消费 Cookie。
 * 请求头需 xtbz: ykt 与 X-CSRFToken（从 Cookie 的 csrftoken 取）。
 */
class YuketangClient(
    private val cookie: String,
    private val host: String = "www.yuketang.cn",
    private val client: HttpClient = createHttpClient(),
) {

    companion object {
        const val DEFAULT_HOST = "www.yuketang.cn"
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun loginUrl(h: String = DEFAULT_HOST): String = "https://$h/web"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun csrfToken(): String {
        for (part in cookie.split(';')) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            if (part.substring(0, idx).trim() == "csrftoken") return part.substring(idx + 1).trim()
        }
        return ""
    }

    private suspend fun get(path: String, classroomId: String? = null): String =
        client.get("https://$host$path") {
            header("User-Agent", BROWSER_UA)
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "zh-CN,zh;q=0.9")
            header("xtbz", "ykt")
            header("Referer", "https://$host/v2/web/index")
            if (!classroomId.isNullOrEmpty()) header("classroom-id", classroomId)
            if (cookie.isNotBlank()) header("Cookie", cookie)
            val csrf = csrfToken()
            if (csrf.isNotEmpty()) header("X-CSRFToken", csrf)
        }.bodyAsText()

    private fun toJson(text: String): kotlinx.serialization.json.JsonObject? {
        val s = text.trim()
        if (s.isEmpty()) return null
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(s.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    /** 校验登录态并返回 (uid, name) */
    suspend fun fetchUser(): Pair<String, String>? {
        val obj = runCatching { toJson(get("/api/v3/user/basic-info")) }.getOrNull() ?: return null
        val data = obj["data"]?.jsonObject ?: return null
        val uid = data["id"]?.jsonPrimitive?.contentOrNull
            ?: data["user_id"]?.jsonPrimitive?.contentOrNull ?: ""
        val name = data["name"]?.jsonPrimitive?.contentOrNull
            ?: data["nickname"]?.jsonPrimitive?.contentOrNull ?: ""
        if (uid.isEmpty()) return null
        return uid to name
    }

    suspend fun testLogin(): Boolean = fetchUser() != null

    private data class Course(val classroomId: String, val name: String)

    private suspend fun fetchClassEnd(classroomId: String): Long {
        val obj = runCatching { toJson(get("/v2/api/web/classrooms/$classroomId?role=5")) }.getOrNull()
            ?: return 0L
        return obj["data"]?.jsonObject?.get("class_end")?.jsonPrimitive?.longOrNull ?: 0L
    }

    private suspend fun fetchLeafAssessed(classroomId: String, leafId: String): Boolean {
        if (leafId.isEmpty()) return false
        val obj = runCatching {
            toJson(get("/mooc-api/v1/lms/learn/leaf_info/$classroomId/$leafId/", classroomId))
        }.getOrNull() ?: return false
        return obj["data"]?.jsonObject?.get("is_assessed")?.jsonPrimitive?.contentOrNull == "true"
    }

    private suspend fun fetchCourses(): List<Course> {
        val obj = runCatching { toJson(get("/v2/api/web/courses/list?identity=2")) }.getOrNull()
            ?: return emptyList()
        val list = obj["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()
        val out = ArrayList<Course>()
        for (el in list) {
            val o = el.jsonObject
            val classroomId = o["classroom_id"]?.jsonPrimitive?.contentOrNull ?: ""
            if (classroomId.isEmpty()) continue
            val name = o["course"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                ?: o["name"]?.jsonPrimitive?.contentOrNull ?: ""
            out.add(Course(classroomId, name))
        }
        return out
    }

    /** 抓全部课程的作业+习题 */
    suspend fun fetchAll(): List<Task> {
        val courses = fetchCourses()
        val raws = ArrayList<Task>()
        for (c in courses) {
            runCatching { raws.addAll(fetchActivities(c)) }
        }
        return raws.distinctBy { "${it.courseId}|${it.title}|${it.type}" }
    }

    private suspend fun fetchActivities(course: Course): List<Task> {
        val path = "/v2/api/web/logs/learn/${course.classroomId}?actype=-1&page=0&offset=100&sort=-1"
        val obj = runCatching { toJson(get(path)) }.getOrNull() ?: return emptyList()
        val arr = obj["data"]?.jsonObject?.get("activities")?.jsonArray ?: return emptyList()
        val classEnd = fetchClassEnd(course.classroomId)
        val out = ArrayList<Task>()
        for (el in arr) {
            val a = el.jsonObject
            val task = mapActivity(course, classEnd, a) ?: continue
            out.add(task)
        }
        return out
    }

    private suspend fun mapActivity(course: Course, classEnd: Long, a: kotlinx.serialization.json.JsonObject): Task? {
        val type = a["type"]?.jsonPrimitive?.intOrNull ?: 0
        val title = a["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = a["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val createTime = a["create_time"]?.jsonPrimitive?.longOrNull ?: 0L
        val url = "https://$host/v2/web/studentLog/${course.classroomId}"

        return when (type) {
            5 -> {
                val statusCode = a["status"]?.jsonPrimitive?.intOrNull ?: 0
                val deadline = a["deadline"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: classEnd
                val total = a["total_score"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val rawScore = a["score"]?.jsonPrimitive?.doubleOrNull
                val status = when {
                    statusCode == 3 -> TaskStatus.GRADED
                    statusCode == 2 -> TaskStatus.SUBMITTED
                    deadline > 0 && deadline < nowMillis() -> TaskStatus.EXPIRED
                    else -> TaskStatus.TODO
                }
                val isExam = title.contains("考试") || title.contains("测试")
                Task(
                    key = "YT|5|${course.classroomId}|$id",
                    title = title,
                    courseName = course.name,
                    courseId = course.classroomId,
                    classId = course.classroomId,
                    type = if (isExam) TaskType.EXAM else TaskType.WORK,
                    status = status,
                    endTime = deadline,
                    publishTime = createTime,
                    grade = if (status == TaskStatus.GRADED && rawScore != null && rawScore >= 0) fmtScore(rawScore) else "",
                    url = url,
                    statusText = when {
                        status == TaskStatus.GRADED -> "已完成"
                        status == TaskStatus.SUBMITTED -> "已提交，等待批阅"
                        statusCode == 0 -> "未作答"
                        else -> "进行中"
                    } + if (total > 0) " · 满分 ${fmtScore(total)}" else "",
                    provider = Provider.YUKETANG,
                )
            }
            19 -> {
                val content = a["content"]?.jsonObject
                val leafId = content?.get("leaf_id")?.jsonPrimitive?.contentOrNull.orEmpty()
                val deadline = content?.get("score_d")?.jsonPrimitive?.longOrNull ?: 0L
                val assessed = fetchLeafAssessed(course.classroomId, leafId)
                val status = when {
                    assessed -> TaskStatus.GRADED
                    deadline > 0 && deadline < nowMillis() -> TaskStatus.EXPIRED
                    else -> TaskStatus.TODO
                }
                Task(
                    key = "YT|19|${course.classroomId}|$id",
                    title = title,
                    courseName = course.name,
                    courseId = course.classroomId,
                    classId = course.classroomId,
                    type = TaskType.WORK,
                    status = status,
                    endTime = deadline,
                    publishTime = createTime,
                    url = url,
                    statusText = if (assessed) "已完成" else "作业",
                    provider = Provider.YUKETANG,
                )
            }
            else -> null
        }
    }

    private fun fmtScore(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else ((v * 10).toLong() / 10.0).toString()

    private fun nowMillis(): Long =
        kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
