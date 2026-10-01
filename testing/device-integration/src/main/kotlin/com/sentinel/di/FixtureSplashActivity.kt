package com.sentinel.di

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicInteger

/**
 * 模拟开屏广告页的夹具（DI-05）。
 *
 * 右上角显示一个文字为「跳过 3」的按钮；每次被点击时累加 [clickCount]。
 * 只用 android.widget 视图，不依赖 Compose / AppCompat。
 *
 * 读取点击次数的两种方式：
 * - 同进程：[clickCount] / [reset]。
 * - 跨进程（推荐）：本模块是 `com.android.test`，插桩代码运行在被测 App 进程
 *   （com.sentinel.adblock），而按组件名启动的本 Activity 运行在测试 APK 自己的进程
 *   （com.sentinel.di），静态计数对测试不可见。因此页面底部同时显示一个
 *   contentDescription 为 [COUNT_DESC] 的 TextView，文字为当前点击次数，
 *   测试用 UiAutomator `By.desc(COUNT_DESC)` 读取 `text`。每次 onCreate 都会清零。
 */
class FixtureSplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        val ad = TextView(this).apply {
            text = "开屏广告（测试夹具）"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        root.addView(
            ad,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        reset()
        val counter = TextView(this).apply {
            text = "0"
            contentDescription = COUNT_DESC
            textSize = 14f
        }
        root.addView(
            counter,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ),
        )

        val skip = Button(this).apply {
            text = SKIP_TEXT
            isAllCaps = false
            setOnClickListener { counter.text = clickCount.incrementAndGet().toString() }
        }
        root.addView(
            skip,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                val margin = (resources.displayMetrics.density * 24).toInt()
                setMargins(margin, margin * 2, margin, margin)
            },
        )

        setContentView(root)
    }

    companion object {
        /** 按钮文字，测试用 UiAutomator `By.text(SKIP_TEXT)` 查找。 */
        const val SKIP_TEXT: String = "跳过 3"

        /** 显示点击次数的 TextView 的 contentDescription，供跨进程读取。 */
        const val COUNT_DESC: String = "fixture_skip_click_count"

        /** 「跳过 3」被点击的累计次数（进程内静态；每次 onCreate 清零）。 */
        @JvmStatic
        val clickCount: AtomicInteger = AtomicInteger(0)

        /** 每个用例开始前清零。 */
        @JvmStatic
        fun reset() {
            clickCount.set(0)
        }
    }
}
