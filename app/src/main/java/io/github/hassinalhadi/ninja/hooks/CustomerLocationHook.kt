package io.github.hassinalhadi.ninja.hooks

import android.app.Application
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

object CustomerLocationHook {
    fun install(app: Application, classLoader: ClassLoader) {
        val orderTaskClass = HookUtils.findClassOrNull(
            "com.app.network.network.models.OrderTask",
            classLoader
        ) ?: return

        val methods = listOf(
            "shouldShowLocationIcon",
            "shouldShowLocationIcon\$default",
            "shouldShowAddressImageIcon"
        )

        for (methodName in methods) {
            HookUtils.hookAllByName(orderTaskClass, methodName, object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    if (NinjaPrefs.isCustomerLocationEnabled(app)) {
                        callFrame.result = true
                    }
                }
            })
        }
    }
}
