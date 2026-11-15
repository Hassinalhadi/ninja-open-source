package io.github.hassinalhadi.ninja.hooks

import android.util.Log
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

object HookUtils {
    private const val TAG = "NinjaMod"

    fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    fun hookAllByName(
        clazz: Class<*>,
        methodName: String,
        callback: MethodHook
    ) {
        clazz.declaredMethods
            .filter { it.name == methodName }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    Pine.hook(method, callback)
                }.onFailure {
                    Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${it.message}")
                }
            }
    }

    fun hookSingle(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>,
        callback: MethodHook
    ) {
        runCatching {
            val method = clazz.getDeclaredMethod(methodName, *paramTypes)
            method.isAccessible = true
            Pine.hook(method, callback)
        }.onFailure {
            Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${it.message}")
        }
    }
}
