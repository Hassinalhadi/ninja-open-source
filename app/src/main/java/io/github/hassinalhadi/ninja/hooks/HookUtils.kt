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

    private fun formatParamTypes(types: Array<Class<*>>): String {
        val sb = StringBuilder()
        for (i in types.indices) {
            if (i > 0) sb.append(',')
            sb.append(types[i].simpleName)
        }
        return sb.toString()
    }

    fun hookAllByName(
        clazz: Class<*>,
        methodName: String,
        callback: MethodHook
    ) {
        val declared = clazz.declaredMethods
        var found = false
        for (i in declared.indices) {
            val method = declared[i]
            if (method.name != methodName) continue
            found = true
            try {
                method.isAccessible = true
                Pine.hook(method, callback)
                DiagnosticLogger.log("HOOK", "OK ${clazz.simpleName}.$methodName(${formatParamTypes(method.parameterTypes)})")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook ${clazz.name}.$methodName: ${t.message}")
                DiagnosticLogger.log("HOOK_ERR", "FAIL ${clazz.simpleName}.$methodName: ${t.message}")
            }
        }
        if (!found) {
            DiagnosticLogger.log("HOOK_WARN", "No method ${clazz.name}.$methodName")
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
