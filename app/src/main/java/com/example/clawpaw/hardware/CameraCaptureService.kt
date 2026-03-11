package com.example.clawpaw.hardware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.clawpaw.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 后台拍照：根据 Intent 的 facing（0 后置，1 前置）拍一张并保存到应用外部存储。
 * 与无障碍分离，纯硬件拍照。
 */
class CameraCaptureService : Service() {

    companion object {
        private const val TAG = "CameraCaptureService"
        const val EXTRA_FACING = "facing" // 0 back, 1 front
        const val NOTIFICATION_CHANNEL_ID = "camera_capture"
        const val NOTIFICATION_ID = 9001
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val facing = intent?.getIntExtra(EXTRA_FACING, 0) ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        runCapture(facing)
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "拍照服务",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, com.example.clawpaw.R.mipmap.ic_launcher)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("正在拍照")
            .setSmallIcon(com.example.clawpaw.R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun runCapture(facing: Int) {
        val manager = getSystemService(CAMERA_SERVICE) as? CameraManager ?: run {
            finishWithError("无相机服务")
            return
        }
        val cameraId = findCameraIdByFacing(manager, facing) ?: run {
            finishWithError("未找到相机 facing=$facing")
            return
        }
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSessionAndCapture(camera)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Logger.e(TAG, "相机打开失败 code=$error", null)
                    camera.close()
                    cameraDevice = null
                    finishWithError("相机错误 $error")
                }
            }, mainHandler)
        } catch (e: SecurityException) {
            finishWithError("无相机权限")
        } catch (e: Exception) {
            Logger.e(TAG, "openCamera", e)
            finishWithError(e.message ?: "打开失败")
        }
    }

    private fun findCameraIdByFacing(manager: CameraManager, facing: Int): String? {
        val facingConstant = if (facing == 1) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == facingConstant) return id
        }
        return null
    }

    private fun createSessionAndCapture(camera: CameraDevice) {
        val chars = (getSystemService(CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(camera.id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run {
            finishWithError("无流配置")
            return
        }
        val size = map.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }
            ?: android.util.Size(1920, 1080)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener({ r ->
                var image: Image? = null
                try {
                    image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val file = saveImageToFile(image)
                    if (file != null) {
                        Logger.i(TAG, "拍照已保存: $file")
                        finishWithSuccess(file.absolutePath)
                    } else {
                        finishWithError("保存失败")
                    }
                } finally {
                    image?.close()
                }
            }, mainHandler)
        }
        imageReader = reader
        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            set(CaptureRequest.JPEG_ORIENTATION, 0)
        }
        camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    session.capture(requestBuilder.build(), null, mainHandler)
                } catch (e: Exception) {
                    Logger.e(TAG, "capture", e)
                    finishWithError(e.message ?: "拍照失败")
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                finishWithError("会话配置失败")
            }
        }, mainHandler)
    }

    private fun saveImageToFile(image: Image): File? {
        val dir = getExternalFilesDir(null) ?: filesDir
        val name = "capture_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val file = File(dir, name)
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            FileOutputStream(file).use { out: OutputStream ->
                out.write(bytes)
            }
            file
        } catch (e: Exception) {
            Logger.e(TAG, "saveImageToFile", e)
            null
        }
    }

    private fun finishWithSuccess(path: String) {
        try {
            imageReader?.close()
            imageReader = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) { }
        stopForeground(true)
        stopSelf()
    }

    private fun finishWithError(msg: String) {
        Logger.w(TAG, msg)
        try {
            imageReader?.close()
            cameraDevice?.close()
            imageReader = null
            cameraDevice = null
        } catch (_: Exception) { }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        try {
            imageReader?.close()
            cameraDevice?.close()
        } catch (_: Exception) { }
        super.onDestroy()
    }
}
