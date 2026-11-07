package com.spotlight.app.trigger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.spotlight.app.R
import com.spotlight.app.ui.SpotlightActivity

/**
 * 可选的悬浮把手：在屏幕顶部显示一个 iPhone 灵动岛式的小胶囊，
 * 点按（或向下轻扫）即呼出 Spotlight。没有 LSPosed 时的主要兜底方案。
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var handleView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "缺少悬浮窗权限，无法显示把手", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        showHandle()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        handleView = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            SpotlightActivity.intent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.floating_handle))
            .setContentText("点按顶部把手呼出 Spotlight")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "悬浮把手",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "保持 Spotlight 顶部把手可见"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun showHandle() {
        if (handleView != null) return

        val density = resources.displayMetrics.density
        val width = (52 * density).toInt()
        val height = (10 * density).toInt()

        val view = View(this).apply {
            isClickable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(0x4DFFFFFF)
                setStroke((0.8f * density).toInt(), 0x66FFFFFF)
            }
            setOnTouchListener(HandleTouchListener { openSpotlight() })
            contentDescription = "Spotlight"
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (6 * density).toInt()
        }

        runCatching {
            windowManager.addView(view, params)
            handleView = view
        }.onFailure {
            Toast.makeText(this, "无法显示悬浮把手: ${it.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun openSpotlight() {
        runCatching {
            startActivity(SpotlightActivity.intent(this))
        }.onFailure {
            Toast.makeText(this, "无法打开 Spotlight: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private class HandleTouchListener(private val onOpen: () -> Unit) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var consumed = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    consumed = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    if (!consumed && dy > 10 * view.resources.displayMetrics.density && dy > kotlin.math.abs(dx)) {
                        consumed = true
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpen()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!consumed) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpen()
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val CHANNEL_ID = "spotlight_floating"
        private const val NOTIFICATION_ID = 42
    }
}
