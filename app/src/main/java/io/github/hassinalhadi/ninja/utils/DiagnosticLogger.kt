package io.github.hassinalhadi.ninja.utils

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object DiagnosticLogger {
    private const val TAG = "NinjaDiag"
    private const val LOG_BTN_TAG = "ninja_diag_log_btn"
    private const val MAX_LINES = 600

    private val lines = CopyOnWriteArrayList<String>()
    private var logFile: File? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(app: Application) {
        runCatching {
            val dir = app.getExternalFilesDir(null) ?: app.filesDir
            logFile = File(dir, "ninja_diag.log").apply {
                writeText("=== NinjaMod Diagnostic Log ===\n")
            }
        }
        log("INIT", "SDK=${Build.VERSION.SDK_INT} ABI=${Build.SUPPORTED_ABIS.joinToString()} PKG=${app.packageName}")
        log("INIT", "sourceDir=${app.applicationInfo.sourceDir}")
        log("INIT", "logFile=${logFile?.absolutePath ?: "none"}")

        registerActivityLogButton(app)
        installDialogAndIntegrityObservers(app)
    }

    fun log(category: String, message: String) {
        val ts = timeFmt.format(Date())
        val entry = "[$ts][$category] $message"
        Log.i(TAG, entry)
        lines.add(entry)
        while (lines.size > MAX_LINES) {
            runCatching { lines.removeAt(0) }
        }
        runCatching {
            logFile?.appendText(entry + "\n")
        }
    }

    fun getFullLog(): String = lines.joinToString("\n")

    fun clear() {
        lines.clear()
        runCatching { logFile?.writeText("=== Cleared ===\n") }
    }

    private fun registerActivityLogButton(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                log("ACT", "onCreate: ${activity.javaClass.name}")
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                activity.runOnUiThread { attachLogButton(activity) }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun attachLogButton(activity: Activity) {
        runCatching {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            if (content.findViewWithTag<TextView>(LOG_BTN_TAG) != null) return

            val density = activity.resources.displayMetrics.density
            val btn = TextView(activity).apply {
                tag = LOG_BTN_TAG
                text = "LOG"
                textSize = 11f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 16 * density
                    setColor(0xCCB91C1C.toInt())
                    setStroke((1.5f * density).toInt(), Color.WHITE)
                }
                elevation = 20f
                setOnClickListener { showLogDialog(activity) }
            }

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = (44 * density).toInt()
                marginStart = (12 * density).toInt()
            }

            content.addView(btn, lp)
        }
    }

    fun showLogDialog(context: Context) {
        runCatching {
            val tv = TextView(context).apply {
                text = getFullLog().ifEmpty { "(empty)" }
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                val pad = (12 * context.resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            val hScroll = HorizontalScrollView(context).apply { addView(tv) }
            val vScroll = ScrollView(context).apply { addView(hScroll) }

            AlertDialog.Builder(context)
                .setTitle("NinjaMod Diagnostic Log")
                .setView(vScroll)
                .setPositiveButton("Copy") { _, _ ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("NinjaDiagLog", getFullLog()))
                    Toast.makeText(context, "Log copied (${lines.size} lines)", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Clear") { _, _ ->
                    clear()
                    Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun installDialogAndIntegrityObservers(app: Application) {
        // 1. Capture stack trace whenever any Dialog is shown
        runCatching {
            val showMethod = Dialog::class.java.getDeclaredMethod("show")
            Pine.hook(showMethod, object : MethodHook() {
                override fun beforeCall(callFrame: Pine.CallFrame) {
                    val dlg = callFrame.thisObject as? Dialog ?: return
                    val trace = Throwable().stackTrace
                        .drop(2)
                        .take(12)
                        .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                    log("DIALOG", "show() ${dlg.javaClass.name} | trace: $trace")
                }
            })
            log("HOOK", "Dialog.show hooked OK")
        }.onFailure {
            log("HOOK_ERR", "Dialog.show hook failed: ${it.message}")
        }

        // 2. Observe Samurai integrity checker class (ua.d) if present
        runCatching {
            val cl = app.classLoader
            val uaD = Class.forName("ua.d", false, cl)
            uaD.declaredMethods.forEach { m ->
                runCatching {
                    m.isAccessible = true
                    Pine.hook(m, object : MethodHook() {
                        override fun afterCall(callFrame: Pine.CallFrame) {
                            val resStr = callFrame.result?.toString() ?: "null"
                            val trimmed = if (resStr.length > 260) resStr.take(260) + "..." else resStr
                            log("UA_D", "${m.name}() -> $trimmed")
                        }
                    })
                }
            }
            log("HOOK", "ua.d observer hooked (${uaD.declaredMethods.size} methods)")
        }.onFailure {
            log("HOOK_ERR", "ua.d observer skipped: ${it.message}")
        }

        // 3. Log actual PackageInfo / Cert SHA-256 seen by app
        runCatching {
            val assetSha = runCatching {
                val bytes = app.assets.open("ninja_orig_cert.der").use { it.readBytes() }
                sha256Hex(bytes)
            }.getOrElse { "MISSING(${it.message})" }
            log("CERT", "assets/ninja_orig_cert.der sha256=$assetSha")
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
