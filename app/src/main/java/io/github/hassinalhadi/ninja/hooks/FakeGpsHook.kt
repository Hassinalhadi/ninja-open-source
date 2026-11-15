package io.github.hassinalhadi.ninja.hooks

import android.app.Application
import android.location.Location
import android.os.Build
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

object FakeGpsHook {
    fun install(app: Application, classLoader: ClassLoader) {
        // 1. Bypass mock-location flags on android.location.Location
        HookUtils.hookSingle(Location::class.java, "isFromMockProvider", callback = object : MethodHook() {
            override fun afterCall(callFrame: Pine.CallFrame) {
                callFrame.result = false
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HookUtils.hookSingle(Location::class.java, "isMock", callback = object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    callFrame.result = false
                }
            })
        }

        // 2. Spoof coordinates when Fake GPS is enabled
        HookUtils.hookSingle(Location::class.java, "getLatitude", callback = object : MethodHook() {
            override fun afterCall(callFrame: Pine.CallFrame) {
                if (NinjaPrefs.isFakeGpsEnabled(app)) {
                    callFrame.result = NinjaPrefs.getFakeLat(app)
                }
            }
        })
        HookUtils.hookSingle(Location::class.java, "getLongitude", callback = object : MethodHook() {
            override fun afterCall(callFrame: Pine.CallFrame) {
                if (NinjaPrefs.isFakeGpsEnabled(app)) {
                    callFrame.result = NinjaPrefs.getFakeLng(app)
                }
            }
        })

        // 3. Bypass app-level Fake GPS enforcement & AllowMockProvider
        HookUtils.findClassOrNull("com.app.feature.location.api.AllowMockProvider", classLoader)?.let { cls ->
            cls.declaredMethods.filter { it.returnType == Boolean::class.javaPrimitiveType }.forEach { m ->
                runCatching {
                    m.isAccessible = true
                    Pine.hook(m, object : MethodHook() {
                        override fun afterCall(callFrame: Pine.CallFrame) {
                            callFrame.result = true
                        }
                    })
                }
            }
        }

        HookUtils.findClassOrNull("delivery.samurai.android.security.FakeGpsEnforcementCoordinator", classLoader)?.let { cls ->
            cls.declaredMethods.filter { it.returnType == Boolean::class.javaPrimitiveType }.forEach { m ->
                runCatching {
                    m.isAccessible = true
                    Pine.hook(m, object : MethodHook() {
                        override fun afterCall(callFrame: Pine.CallFrame) {
                            callFrame.result = false
                        }
                    })
                }
            }
        }
    }
}
