package com.example.clawpaw.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.example.clawpaw.R
import com.example.clawpaw.util.Logger
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * 仅相机预览 + 二维码解码，无条形码样式、无取景框。
 */
class QrScanActivity : LocaleAwareActivity() {

    private lateinit var previewView: PreviewView

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (isDestroyed) return@registerForActivityResult
        if (!granted) {
            Toast.makeText(this@QrScanActivity, getString(R.string.main_link_qr_camera_permission_denied), Toast.LENGTH_SHORT).show()
            finish()
        }
        // 授权时不在回调里 startCamera()：此时可能还在 onPause，bindToLifecycle 会失败。等对话框关闭后 onResume 会再次执行并启动相机。
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(previewView)
        val hint = TextView(this).apply {
            setText(R.string.main_link_scan_prompt)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 32, 32, 64)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }
        root.addView(hint)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        // 等 PreviewView 完成布局后再 bind，避免 surfaceProvider 未就绪导致 bindToLifecycle 失败
        previewView.post { startCamera() }
    }

    private fun startCamera() {
        if (isDestroyed) return
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                if (isDestroyed) return@addListener
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED).not()) return@addListener
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val executor = Executors.newSingleThreadExecutor()
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, QrAnalyzer { result -> onQrDecoded(result) }) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                } catch (e: Throwable) {
                    Logger.e("QrScanActivity", "bindToLifecycle failed", e)
                    if (!isDestroyed) {
                        Toast.makeText(this, getString(R.string.main_link_qr_camera_failed), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Throwable) {
            Logger.e("QrScanActivity", "ProcessCameraProvider.getInstance or addListener failed", e)
            Toast.makeText(this, getString(R.string.main_link_qr_camera_failed), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun onQrDecoded(content: String) {
        if (isDestroyed) return
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            val intent = Intent().putExtra(EXTRA_QR_CONTENT, content)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    companion object {
        const val EXTRA_QR_CONTENT = "qr_content"
    }
}

private class QrAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE))

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val image = imageProxy.image ?: return imageProxy.close()
            if (image.planes.isEmpty()) return imageProxy.close()
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return imageProxy.close()
            val plane = image.planes[0]
            val yBuffer = plane.buffer
            val ySize = yBuffer.remaining()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            imageProxy.close()

            val luminance = if (rowStride == width && pixelStride == 1) {
                ByteArrayLuminanceSource(yArray, width, height)
            } else {
                val needSize = (height - 1) * rowStride + width * pixelStride
                if (needSize > yArray.size) return
                val matrix = ByteArray(width * height)
                var offset = 0
                for (y in 0 until height) {
                    val rowStart = y * rowStride
                    for (x in 0 until width) {
                        matrix[offset++] = yArray[rowStart + x * pixelStride]
                    }
                }
                ByteArrayLuminanceSource(matrix, width, height)
            }
            val bitmap = BinaryBitmap(HybridBinarizer(luminance))
            val result: Result? = try { reader.decode(bitmap, hints) } catch (_: Exception) { null }
            result?.text?.let { onResult(it) }
        } catch (_: Throwable) {
            try { imageProxy.close() } catch (_: Throwable) { }
        }
    }
}

private class ByteArrayLuminanceSource(
    private val data: ByteArray,
    width: Int,
    height: Int
) : LuminanceSource(width, height) {
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val result = row ?: ByteArray(width)
        System.arraycopy(data, y * width, result, 0, width)
        return result
    }
    override fun getMatrix(): ByteArray = data
}
