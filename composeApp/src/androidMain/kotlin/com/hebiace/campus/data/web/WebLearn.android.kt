package com.hebiace.campus.data.web

import android.content.Intent
import com.hebiace.campus.store.AppContextHolder
import com.hebiace.campus.ui.LearnWebActivity

actual fun openWebLearn(url: String, cookie: String, title: String, cookieHosts: String) {
    val ctx = AppContextHolder.context
    val intent = Intent(ctx, LearnWebActivity::class.java).apply {
        putExtra("url", url)
        putExtra("cookie", cookie)
        putExtra("title", title)
        putExtra("cookieHosts", cookieHosts)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}
