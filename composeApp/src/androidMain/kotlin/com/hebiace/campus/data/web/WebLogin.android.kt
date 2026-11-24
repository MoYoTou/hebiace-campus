package com.hebiace.campus.data.web

import android.content.Intent
import com.hebiace.campus.store.AppContextHolder
import com.hebiace.campus.ui.LoginWebActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private object Holder {
    var continuation: kotlin.coroutines.Continuation<String>? = null
}

actual suspend fun openWebLogin(spec: WebLoginSpec): String = suspendCancellableCoroutine { cont ->
    Holder.continuation = cont
    val ctx = AppContextHolder.context
    val intent = Intent(ctx, LoginWebActivity::class.java).apply {
        putExtra("url", spec.url)
        putExtra("successHosts", spec.successHosts)
        putExtra("successCookieName", spec.successCookieName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}

fun finishWebLogin(cookie: String) {
    Holder.continuation?.let { cont ->
        Holder.continuation = null
        cont.resume(cookie)
    }
}
