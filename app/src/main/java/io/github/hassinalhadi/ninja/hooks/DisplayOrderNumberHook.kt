package io.github.hassinalhadi.ninja.hooks

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.lang.ref.WeakReference

object DisplayOrderNumberHook {
    private var lastOrderNumber: String? = null
    private var activeActivityRef: WeakReference<Activity>? = null
    private const val BADGE_TAG = "ninja_order_number_badge"

    fun install(app: Application, classLoader: ClassLoader) {
        listOf(
            "com.app.network.network.models.Order",
            "com.app.network.network.models.OrderTask"
        ).forEach { className ->
            val cls = HookUtils.findClassOrNull(className, classLoader) ?: return@forEach
            HookUtils.hookAllByName(cls, "getOrderNumber", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val num = callFrame.result?.toString()?.takeIf { it.isNotBlank() } ?: return
                    lastOrderNumber = num
                    val act = activeActivityRef?.get() ?: return
                    if (NinjaPrefs.isDisplayOrderNumberEnabled(app)) {
                        act.runOnUiThread { updateBadge(act, num) }
                    }
                }
            })
        }

        listOf(
            "delivery.samurai.android.ui.orders.v2.ProcessOrderActivityV2",
            "delivery.samurai.android.ui.orders.v2.showorder.ShowOrderActivity",
            "delivery.samurai.android.ui.homev2.HomeActivityV2"
        ).forEach { actName ->
            val actCls = HookUtils.findClassOrNull(actName, classLoader) ?: return@forEach
            HookUtils.hookAllByName(actCls, "onResume", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val activity = callFrame.thisObject as? Activity ?: return
                    activeActivityRef = WeakReference(activity)
                    if (NinjaPrefs.isDisplayOrderNumberEnabled(app)) {
                        lastOrderNumber?.let { updateBadge(activity, it) }
                    }
                }
            })
        }
    }

    private fun updateBadge(activity: Activity, orderNumber: String) {
        runCatching {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            var badge = content.findViewWithTag<TextView>(BADGE_TAG)
            if (badge == null) {
                badge = TextView(activity).apply {
                    tag = BADGE_TAG
                    setTextColor(Color.WHITE)
                    setBackgroundColor(0xCC111827.toInt())
                    textSize = 13f
                    val padH = (12 * resources.displayMetrics.density).toInt()
                    val padV = (6 * resources.displayMetrics.density).toInt()
                    setPadding(padH, padV, padH, padV)
                }
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = (36 * activity.resources.displayMetrics.density).toInt()
                }
                content.addView(badge, lp)
            }
            badge.text = "Order #$orderNumber"
        }
    }
}
