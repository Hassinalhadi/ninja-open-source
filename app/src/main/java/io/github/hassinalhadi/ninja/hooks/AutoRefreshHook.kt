package io.github.hassinalhadi.ninja.hooks

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.lang.ref.WeakReference

object AutoRefreshHook {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appRef: WeakReference<Application>? = null
    private var listenerRef: WeakReference<Any>? = null
    private var isOrdersScreenActive = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val app = appRef?.get()
            if (app != null && isOrdersScreenActive && NinjaPrefs.isAutoRefreshEnabled(app)) {
                runCatching {
                    val listener = listenerRef?.get()
                    if (listener != null) {
                        val onRefresh = listener.javaClass.getMethod("onRefresh")
                        onRefresh.invoke(listener)
                    }
                }
                mainHandler.postDelayed(this, NinjaPrefs.getRefreshSpeedMs(app))
            } else {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    fun install(app: Application, classLoader: ClassLoader) {
        appRef = WeakReference(app)

        HookUtils.findClassOrNull("androidx.swiperefreshlayout.widget.SwipeRefreshLayout", classLoader)?.let { swipeCls ->
            HookUtils.hookAllByName(swipeCls, "setOnRefreshListener", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    callFrame.args.firstOrNull()?.let { listenerRef = WeakReference(it) }
                }
            })
        }

        HookUtils.findClassOrNull("delivery.samurai.android.ui.homev2.OrdersFragment", classLoader)?.let { fragCls ->
            HookUtils.hookAllByName(fragCls, "onResume", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    isOrdersScreenActive = true
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.postDelayed(refreshRunnable, 800L)
                }
            })
            HookUtils.hookAllByName(fragCls, "onPause", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    isOrdersScreenActive = false
                    mainHandler.removeCallbacks(refreshRunnable)
                }
            })
        }
    }
}
