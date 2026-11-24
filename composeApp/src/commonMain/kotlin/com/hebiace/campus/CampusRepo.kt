package com.hebiace.campus

import com.hebiace.campus.data.jw.ZhengfangClient
import com.hebiace.campus.data.qzxy.QzxyClient
import com.hebiace.campus.data.ujing.UjingClient
import com.hebiace.campus.data.xxt.ChaoxingClient
import com.hebiace.campus.data.ykt.YuketangClient
import com.hebiace.campus.model.Course
import com.hebiace.campus.model.QzxySession
import com.hebiace.campus.model.Task

/**
 * 统一仓库：把 AppState 与各客户端串起来，给 UI 提供高层操作。
 */
object CampusRepo {

    /** 教务：用本地保存的账号登录并拉课表 */
    suspend fun syncSchedule(): List<Course> {
        val (account, password) = AppState.jwAccount()
        if (account.isEmpty() || password.isEmpty()) throw IllegalStateException("请先在「我的」里登录教务系统")
        val client = ZhengfangClient(host = AppState.jwHost())
        client.login(account, password)
        return client.fetchSchedule(AppState.semesterYear(), AppState.semesterTerm())
    }

    /** 学习通作业（需要已登录并保存 Cookie） */
    suspend fun fetchChaoxingTasks(): List<Task> {
        val cookie = AppState.chaoxingCookie()
        if (cookie.isEmpty()) return emptyList()
        return ChaoxingClient(cookie).fetchAll()
    }

    /** 雨课堂作业（需要已登录并保存 Cookie） */
    suspend fun fetchYuketangTasks(): List<Task> {
        val cookie = AppState.yktCookie()
        if (cookie.isEmpty()) return emptyList()
        return YuketangClient(cookie).fetchAll()
    }

    /** 合并学习通 + 雨课堂作业，按截止时间排序 */
    suspend fun fetchAllTasks(): List<Task> {
        val xxt = runCatching { fetchChaoxingTasks() }.getOrDefault(emptyList())
        val ykt = runCatching { fetchYuketangTasks() }.getOrDefault(emptyList())
        return (xxt + ykt).sortedBy { if (it.endTime <= 0) Long.MAX_VALUE else it.endTime }
    }

    fun qzxySession(): QzxySession? {
        val loginCode = AppState.qzxyLoginCode() ?: return null
        return QzxySession(
            loginCode = loginCode,
            userId = AppState.qzxyUserId(),
            accountId = AppState.qzxyAccountId(),
            projectId = AppState.qzxyProjectId(),
            telephone = AppState.qzxyPhone(),
            name = AppState.qzxyName(),
        )
    }

    fun saveQzxySession(s: QzxySession) {
        AppState.saveQzxyLoginCode(s.loginCode)
        AppState.saveQzxyUserId(s.userId)
        AppState.saveQzxyAccountId(s.accountId)
        AppState.saveQzxyProjectId(s.projectId)
        AppState.saveQzxyPhone(s.telephone)
        AppState.saveQzxyName(s.name)
    }

    fun newQzxyClient(): QzxyClient = QzxyClient()
    fun newUjingClient(): UjingClient = UjingClient().also {
        AppState.ujingToken()?.let { t -> it.setToken(t) }
    }
}
