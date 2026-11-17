package io.github.hassinalhadi.ninja.hooks

import android.util.Log
import io.github.hassinalhadi.ninja.utils.DiagnosticLogger
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

object HookUtils {
    private const val TAG = "NinjaMod"

    fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? =
        try {
            Class.forName(name, false, classLoader)
        } catch (_: Throwable) {
            null
        }

    fun hookAllByName(
        clazz: Class<*>,
        methodName: String,
        callback: MethodHook
    ) {
        val matches = clazz.declaredMethods.filter { it.name == methodName }
        if (matches.isEmpty()) {
            DiagnosticLogger.log("HOOK_WARN", "No method ${clazz.name}.$methodName")
        }
        for (method in matches) {
            try {
                method.isAccessible = true
                Pine.hook(method, callback)
                DiagnosticLogger.log("HOOK", "OK ${clazz.simpleName}.$methodName(${method.parameterTypes.joinToString(",") { it.simpleName }})")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${t.message}")
                DiagnosticLogger.log("HOOK_ERR", "FAIL ${clazz.simpleName}.$methodName: ${t.message}")
            }
        }
    }

    fun hookSingle(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>,
        callback: MethodHook
    ) {
        try {
            val method = clazz.getDeclaredMethod(methodName, *paramTypes)
            method.isAccessible = true
            Pine.hook(method, callback)
            DiagnosticLogger.log("HOOK", "OK ${clazz.simpleName}.$methodName")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${t.message}")
            DiagnosticLogger.log("HOOK_ERR", "FAIL ${clazz.simpleName}.$methodName: ${t.message}")
        }
    }
}
