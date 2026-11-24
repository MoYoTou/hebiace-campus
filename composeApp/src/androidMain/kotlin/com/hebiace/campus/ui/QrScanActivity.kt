package com.hebiace.campus.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.hebiace.campus.data.qr.finishQrScan
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** 相机扫码页：启动后弹出 zxing 扫码界面，扫到或取消即回传结果并结束 */
class QrScanActivity : ComponentActivity() {

    private var launched = false

    private val launcher = registerForActivityResult(ScanContract()) { result ->
        finishQrScan(result?.contents)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 延迟到 onStart 再启动，避免结果回调早于生命周期就绪
    }

    override fun onStart() {
        super.onStart()
        if (!launched) {
            launched = true
            val prompt = intent.getStringExtra("prompt") ?: "对准二维码"
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(prompt)
                setBeepEnabled(false)
                setOrientationLocked(false)
                setCameraId(0)
            }
            launcher.launch(options)
        }
    }

    override fun onBackPressed() {
        finishQrScan(null)
        finish()
    }
}
