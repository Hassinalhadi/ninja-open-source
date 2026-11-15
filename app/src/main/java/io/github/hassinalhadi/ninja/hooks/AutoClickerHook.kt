package io.github.hassinalhadi.ninja.hooks

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.lang.ref.WeakReference

object AutoClickerHook {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivity: WeakReference<Activity>? = null
    private val targetKeywords = listOf("accept", "confirm", "قبول", "تأكيد", "موافق")

    private val clickRunnable = object : Runnable {
        override fun run() {
            val activity = currentActivity?.get()
            if (activity != null && !activity.isFinishing && NinjaPrefs.isAutoClickerEnabled(activity)) {
                runCatching {
                    activity.window?.decorView?.let { findAndClickTarget(it) }
                }
                mainHandler.postDelayed(this, NinjaPrefs.getClickSpeedMs(activity))
            } else {
                mainHandler.postDelayed(this, 800L)
            }
        }
    }

    fun install(app: Application, classLoader: ClassLoader) {
        listOf(
            "delivery.samurai.android.ui.homev2.HomeActivityV2",
            "delivery.samurai.android.ui.orders.v2.ProcessOrderActivityV2"
        ).forEach { actName ->
            val cls = HookUtils.findClassOrNull(actName, classLoader) ?: return@forEach
            HookUtils.hookAllByName(cls, "onResume", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val act = callFrame.thisObject as? Activity ?: return
                    currentActivity = WeakReference(act)
                    mainHandler.removeCallbacks(clickRunnable)
                    mainHandler.postDelayed(clickRunnable, 500L)
                }
            })
            HookUtils.hookAllByName(cls, "onPause", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    mainHandler.removeCallbacks(clickRunnable)
                }
            })
        }
    }

    private fun findAndClickTarget(view: View): Boolean {
        if (view.visibility == View.VISIBLE && view.isEnabled && view.isClickable) {
            val label = when (view) {
                is Button -> view.text?.toString().orEmpty()
                is TextView -> view.text?.toString().orEmpty()
                else -> view.contentDescription?.toString().orEmpty()
            }.lowercase()

            if (targetKeywords.any { label.contains(it) }) {
                view.performClick()
                return true
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (findAndClickTarget(view.getChildAt(i))) return true
            }
        }
        return false
    }
}
