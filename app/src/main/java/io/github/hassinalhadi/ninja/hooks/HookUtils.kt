package io.github.hassinalhadi.ninja.hooks

import android.util.Log
import io.github.hassinalhadi.ninja.utils.DiagnosticLogger
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
        val matches = clazz.declaredMethods.filter { it.name == methodName }
        if (matches.isEmpty()) {
            DiagnosticLogger.log("HOOK_WARN", "No method ${clazz.name}.$methodName")
        }
        matches.forEach { method ->
            runCatching {
                method.isAccessible = true
                Pine.hook(method, callback)
                DiagnosticLogger.log("HOOK", "OK ${clazz.simpleName}.$methodName(${method.parameterTypes.joinToString(",") { it.simpleName }})")
            }.onFailure {
                Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${it.message}")
                DiagnosticLogger.log("HOOK_ERR", "FAIL ${clazz.simpleName}.$methodName: ${it.message}")
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
            DiagnosticLogger.log("HOOK", "OK ${clazz.simpleName}.$methodName")
        }.onFailure {
            Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${it.message}")
            DiagnosticLogger.log("HOOK_ERR", "FAIL ${clazz.simpleName}.$methodName: ${it.message}")
        }
    }
}
