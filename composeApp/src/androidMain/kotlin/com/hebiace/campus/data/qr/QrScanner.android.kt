package com.hebiace.campus.data.qr

import android.content.Intent
import com.hebiace.campus.store.AppContextHolder
import com.hebiace.campus.ui.QrScanActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private object QrHolder {
    var continuation: kotlin.coroutines.Continuation<String?>? = null
}

actual suspend fun scanQrCode(prompt: String): String? = suspendCancellableCoroutine { cont ->
    QrHolder.continuation = cont
    val ctx = AppContextHolder.context
    val intent = Intent(ctx, QrScanActivity::class.java).apply {
        putExtra("prompt", prompt)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}

fun finishQrScan(text: String?) {
    QrHolder.continuation?.let { cont ->
        QrHolder.continuation = null
        cont.resume(text)
    }
}
