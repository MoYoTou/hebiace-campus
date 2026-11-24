package com.hebiace.campus.data.web

import android.content.Intent
import com.hebiace.campus.store.AppContextHolder
import com.hebiace.campus.ui.LoginWebActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private object Holder {
    var continuation: kotlin.coroutines.Continuation<String>? = null
}

actual suspend fun openWebLogin(url: String): String = suspendCancellableCoroutine { cont ->
    Holder.continuation = cont
    val ctx = AppContextHolder.context
    val intent = Intent(ctx, LoginWebActivity::class.java).apply {
        putExtra("url", url)
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
