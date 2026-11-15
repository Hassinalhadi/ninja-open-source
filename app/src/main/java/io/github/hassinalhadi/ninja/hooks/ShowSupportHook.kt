package io.github.hassinalhadi.ninja.hooks

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

object ShowSupportHook {
    fun install(app: Application, classLoader: ClassLoader) {
        listOf(
            "com.app.network.network.models.Order",
            "com.app.network.network.models.OrderTask",
            "com.app.network.network.models.Support"
        ).forEach { className ->
            val cls = HookUtils.findClassOrNull(className, classLoader) ?: return@forEach
            cls.declaredMethods
                .filter {
                    it.returnType == Boolean::class.javaPrimitiveType &&
                        it.name.contains("support", ignoreCase = true)
                }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        Pine.hook(method, object : MethodHook() {
                            override fun afterCall(callFrame: Pine.CallFrame) {
                                if (NinjaPrefs.isShowSupportEnabled(app)) {
                                    callFrame.result = true
                                }
                            }
                        })
                    }
                }
        }

        listOf(
            "delivery.samurai.android.ui.orders.v2.ProcessOrderActivityV2",
            "delivery.samurai.android.ui.homev2.HomeActivityV2"
        ).forEach { actName ->
            val actCls = HookUtils.findClassOrNull(actName, classLoader) ?: return@forEach
            HookUtils.hookAllByName(actCls, "onResume", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val activity = callFrame.thisObject as? Activity ?: return
                    if (!NinjaPrefs.isShowSupportEnabled(activity)) return
                    val root = activity.window?.decorView ?: return
                    root.post { revealSupportViews(root) }
                }
            })
        }
    }

    private fun revealSupportViews(view: View) {
        runCatching {
            val idName = if (view.id != View.NO_ID) {
                runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull().orEmpty()
            } else ""
            if (idName.contains("support", ignoreCase = true) && view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    revealSupportViews(view.getChildAt(i))
                }
            }
        }
    }
}
