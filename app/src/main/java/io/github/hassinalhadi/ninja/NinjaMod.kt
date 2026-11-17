package io.github.hassinalhadi.ninja

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.github.hassinalhadi.ninja.hooks.AutoClickerHook
import io.github.hassinalhadi.ninja.hooks.AutoRefreshHook
import io.github.hassinalhadi.ninja.hooks.CustomerLocationHook
import io.github.hassinalhadi.ninja.hooks.DisplayOrderNumberHook
import io.github.hassinalhadi.ninja.hooks.FakeGpsHook
import io.github.hassinalhadi.ninja.hooks.HookUtils
import io.github.hassinalhadi.ninja.hooks.ShowSupportHook
import io.github.hassinalhadi.ninja.hooks.SignatureHook
import io.github.hassinalhadi.ninja.ui.ControlPanelDialog
import io.github.hassinalhadi.ninja.utils.DiagnosticLogger
import top.canyie.pine.Pine
import top.canyie.pine.PineConfig
import top.canyie.pine.callback.MethodHook
import java.util.concurrent.atomic.AtomicBoolean

object NinjaMod {
    private const val TAG = "NinjaMod"
    private const val FAB_TAG = "ninja_floating_panel_btn"
    private val initialized = AtomicBoolean(false)

    @JvmStatic
    fun init(app: Application) {
        if (!initialized.compareAndSet(false, true)) return

        // Initialize logger FIRST so any Pine or hook error is always captured
        try {
            DiagnosticLogger.init(app)
        } catch (t: Throwable) {
            Log.e(TAG, "DiagnosticLogger init failed: ${t.message}", t)
        }

        val pineOk = try {
            PineConfig.debug = false
            PineConfig.debuggable = false
            Pine.ensureInitialized()
            DiagnosticLogger.log("INIT", "Pine.ensureInitialized() OK")
            true
        } catch (t: Throwable) {
            DiagnosticLogger.log("INIT_ERR", "Pine.ensureInitialized() FAILED: ${t.stackTraceToString()}")
            Log.e(TAG, "Pine init failed: ${t.message}", t)
            false
        }

        if (pineOk) {
            try {
                DiagnosticLogger.installHooks(app)
            } catch (t: Throwable) {
                DiagnosticLogger.log("INIT_ERR", "DiagnosticLogger.installHooks failed: ${t.message}")
            }

            try {
                SignatureHook.install(app)
            } catch (t: Throwable) {
                DiagnosticLogger.log("INIT_ERR", "SignatureHook failed: ${t.message}")
            }

            val cl = app.classLoader
            try { CustomerLocationHook.install(app, cl) } catch (_: Throwable) {}
            try { ShowSupportHook.install(app, cl) } catch (_: Throwable) {}
            try { DisplayOrderNumberHook.install(app, cl) } catch (_: Throwable) {}
            try { FakeGpsHook.install(app, cl) } catch (_: Throwable) {}
            try { AutoRefreshHook.install(app, cl) } catch (_: Throwable) {}
            try { AutoClickerHook.install(app, cl) } catch (_: Throwable) {}

            try { injectFloatingPanelButton(cl) } catch (_: Throwable) {}
        }

        DiagnosticLogger.log("INIT", "NinjaMod.init finished (pineOk=$pineOk)")
        Log.i(TAG, "NinjaMod.init finished (pineOk=$pineOk)")
    }

    private fun injectFloatingPanelButton(classLoader: ClassLoader) {
        val homeCls = HookUtils.findClassOrNull(
            "delivery.samurai.android.ui.homev2.HomeActivityV2",
            classLoader
        ) ?: return

        HookUtils.hookAllByName(homeCls, "onResume", object : MethodHook() {
            override fun afterCall(callFrame: Pine.CallFrame) {
                val activity = callFrame.thisObject as? Activity ?: return
                activity.runOnUiThread { attachFab(activity) }
            }
        })
    }

    private fun attachFab(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<TextView>(FAB_TAG) != null) return

        val density = activity.resources.displayMetrics.density
        val size = (48 * density).toInt()

        val fab = TextView(activity).apply {
            tag = FAB_TAG
            text = "N"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE0F766E.toInt())
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            elevation = 12f
        }

        val lp = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = (16 * density).toInt()
        }

        var dX = 0f
        var dY = 0f
        var downRawX = 0f
        var downRawY = 0f

        fab.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.x = event.rawX + dX
                    v.y = event.rawY + dY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.hypot(
                        (event.rawX - downRawX).toDouble(),
                        (event.rawY - downRawY).toDouble()
                    )
                    if (moved < 12 * density) {
                        ControlPanelDialog.show(activity)
                    }
                    true
                }
                else -> false
            }
        }

        content.addView(fab, lp)
    }
}
