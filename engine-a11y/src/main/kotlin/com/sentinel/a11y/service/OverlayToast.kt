package com.sentinel.a11y.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.sentinel.a11y.di.A11yRuntime

/** 撤销提示与激励询问小窗，使用 TYPE_ACCESSIBILITY_OVERLAY（无需悬浮窗权限）。主线程使用。 */
class OverlayToast(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val wm get() = service.getSystemService(WindowManager::class.java)
    private var current: View? = null
    @Volatile private var closed = false

    private fun params(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.BOTTOM
        y = 120
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        current?.let { runCatching { wm.removeView(it) } }
        current = null
    }

    /** 服务退出时永久关闭，排队和后续展示均失效。 */
    fun close() {
        closed = true
        dismiss()
    }

    private fun postShow(block: () -> Unit) {
        if (closed) return
        handler.post {
            if (closed) return@post
            try { block() }
            catch (e: Exception) {
                dismiss()
                Log.w(A11yRuntime.TAG, "悬浮提示展示失败", e)
            }
        }
    }

    private fun show(view: View, autoDismissMs: Long?) {
        dismiss()
        current = view
        wm.addView(view, params(true))
        if (autoDismissMs != null) handler.postDelayed({ dismiss() }, autoDismissMs)
    }

    private fun row(): LinearLayout = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(32, 24, 32, 24)
        setBackgroundColor(0xE6222222.toInt())
    }

    /** 「已拦截 A → B 的跳转 [撤销]」，显示 [durationMs] 毫秒。 */
    fun showUndo(text: String, durationMs: Long, onUndo: () -> Unit) {
        postShow {
            val layout = row()
            layout.addView(TextView(service).apply { this.text = text; setTextColor(0xFFFFFFFF.toInt()) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            layout.addView(Button(service).apply {
                this.text = "撤销"
                setOnClickListener { dismiss(); onUndo() }
            })
            show(layout, durationMs)
        }
    }

    /** 激励视频询问：静默播完 / 正常观看 / 记住。 */
    fun showRewardedAsk(onChoice: (silent: Boolean, remember: Boolean) -> Unit) {
        postShow {
            val layout = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                setBackgroundColor(0xE6222222.toInt())
            }
            layout.addView(TextView(service).apply {
                text = "检测到激励视频，怎么处理？"; setTextColor(0xFFFFFFFF.toInt())
            })
            val remember = CheckBox(service).apply { text = "记住"; setTextColor(0xFFFFFFFF.toInt()) }
            layout.addView(remember)
            val buttons = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(service).apply {
                text = "静默播完"
                setOnClickListener { dismiss(); onChoice(true, remember.isChecked) }
            })
            buttons.addView(Button(service).apply {
                text = "正常观看"
                setOnClickListener { dismiss(); onChoice(false, remember.isChecked) }
            })
            layout.addView(buttons)
            show(layout, null)
        }
    }
}
