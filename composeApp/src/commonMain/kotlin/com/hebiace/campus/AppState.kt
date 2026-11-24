package com.hebiace.campus

import com.hebiace.campus.store.Settings
import com.hebiace.campus.store.SecureStore

/** 各模块登录态与配置的集中存取。 */
object AppState {

    // ---- 教务 ----
    fun jwAccount(): Pair<String, String> {
        val acc = SecureStore.getString("jw_account") ?: return "" to ""
        return acc to (SecureStore.getString("jw_password") ?: "")
    }
    fun saveJwAccount(account: String, password: String) {
        SecureStore.putString("jw_account", account)
        SecureStore.putString("jw_password", password)
    }
    fun clearJw() {
        SecureStore.remove("jw_account")
        SecureStore.remove("jw_password")
    }

    fun jwHost(): String = Settings.getString("jw_host", "jwx.hebiace.edu.cn")
    fun saveJwHost(h: String) = Settings.putString("jw_host", h)

    // ---- 学习通 ----
    fun chaoxingCookie(): String = Settings.getString("xxt_cookie")
    fun saveChaoxingCookie(c: String) = Settings.putString("xxt_cookie", c)

    // ---- 雨课堂 ----
    fun yktCookie(): String = Settings.getString("ykt_cookie")
    fun saveYktCookie(c: String) = Settings.putString("ykt_cookie", c)

    // ---- 趣智校园（洗浴） ----
    fun qzxyPhone(): String = Settings.getString("qzxy_phone")
    fun saveQzxyPhone(p: String) = Settings.putString("qzxy_phone", p)
    fun qzxyLoginCode(): String? = SecureStore.getString("qzxy_login_code")
    fun saveQzxyLoginCode(c: String) = SecureStore.putString("qzxy_login_code", c)
    fun qzxyUserId(): String = Settings.getString("qzxy_user_id")
    fun saveQzxyUserId(u: String) = Settings.putString("qzxy_user_id", u)
    fun qzxyAccountId(): String = Settings.getString("qzxy_account_id")
    fun saveQzxyAccountId(a: String) = Settings.putString("qzxy_account_id", a)
    fun qzxyProjectId(): String = Settings.getString("qzxy_project_id")
    fun saveQzxyProjectId(p: String) = Settings.putString("qzxy_project_id", p)
    fun qzxyName(): String = Settings.getString("qzxy_name")
    fun saveQzxyName(n: String) = Settings.putString("qzxy_name", n)

    // ---- U净（洗衣） ----
    fun ujingToken(): String? = SecureStore.getString("ujing_token")
    fun saveUjingToken(t: String) = SecureStore.putString("ujing_token", t)
    fun ujingMobile(): String = Settings.getString("ujing_mobile")
    fun saveUjingMobile(m: String) = Settings.putString("ujing_mobile", m)

    // ---- 学期 ----
    fun semesterYear(): Int = Settings.getLong("sem_year", 2026L).toInt()
    fun saveSemesterYear(y: Int) = Settings.putLong("sem_year", y.toLong())
    fun semesterTerm(): Int = Settings.getLong("sem_term", 1L).toInt()
    fun saveSemesterTerm(t: Int) = Settings.putLong("sem_term", t.toLong())
}
