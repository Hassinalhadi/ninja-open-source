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
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Arrays
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
        try {
            val dir = app.getExternalFilesDir(null) ?: app.filesDir
            val f = File(dir, "ninja_diag.log")
            f.writeText("=== NinjaMod Diagnostic Log ===\n")
            logFile = f
        } catch (_: Throwable) {
        }
        log("INIT", "SDK=${Build.VERSION.SDK_INT} ABI=${Arrays.toString(Build.SUPPORTED_ABIS)} PKG=${app.packageName}")
        log("INIT", "sourceDir=${app.applicationInfo.sourceDir}")
        log("INIT", "logFile=${logFile?.absolutePath ?: "none"}")

        registerActivityLogButton(app)
    }

    fun installHooks(app: Application) {
        installDialogAndIntegrityObservers(app)
    }

    fun log(category: String, message: String) {
        val ts = timeFmt.format(Date())
        val entry = "[$ts][$category] $message"
        Log.i(TAG, entry)
        lines.add(entry)
        while (lines.size > MAX_LINES) {
            try {
                lines.removeAt(0)
            } catch (_: Throwable) {
                break
            }
        }
        try {
            logFile?.appendText(entry + "\n")
        } catch (_: Throwable) {
        }
    }

    fun getFullLog(): String {
        val sb = StringBuilder()
        for (i in lines.indices) {
            if (i > 0) sb.append('\n')
            sb.append(lines[i])
        }
        return sb.toString()
    }

    fun clear() {
        lines.clear()
        try {
            logFile?.writeText("=== Cleared ===\n")
        } catch (_: Throwable) {
        }
    }

    private fun registerActivityLogButton(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                log("ACT", "onCreate: ${activity.javaClass.name}")
                activity.window?.decorView?.post { attachLogButton(activity) }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                log("ACT", "onResume: ${activity.javaClass.name}")
                activity.window?.decorView?.post { attachLogButton(activity) }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun attachLogButton(activity: Activity) {
        try {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            if (decor.findViewWithTag<TextView>(LOG_BTN_TAG) != null) return

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
                elevation = 50f
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

            decor.addView(btn, lp)
        } catch (t: Throwable) {
            Log.w(TAG, "attachLogButton failed: ${t.message}")
        }
    }

    fun showLogDialog(context: Context) {
        try {
            val full = getFullLog()
            val tv = TextView(context).apply {
                text = if (full.isEmpty()) "(empty)" else full
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
        } catch (_: Throwable) {
        }
    }

    private fun installDialogAndIntegrityObservers(app: Application) {
        try {
            val showMethod = Dialog::class.java.getDeclaredMethod("show")
            Pine.hook(showMethod, object : MethodHook() {
                override fun beforeCall(callFrame: Pine.CallFrame) {
                    val dlg = callFrame.thisObject as? Dialog ?: return
                    val frames = Throwable().stackTrace
                    val sb = StringBuilder()
                    var count = 0
                    for (i in 2 until frames.size) {
                        if (count >= 12) break
                        if (count > 0) sb.append(" <- ")
                        val f = frames[i]
                        sb.append(f.className).append('.').append(f.methodName).append(':').append(f.lineNumber)
                        count++
                    }
                    log("DIALOG", "show() ${dlg.javaClass.name} | trace: $sb")
                }
            })
            log("HOOK", "Dialog.show hooked OK")
        } catch (t: Throwable) {
            log("HOOK_ERR", "Dialog.show hook failed: ${t.message}")
        }

        try {
            val assetBytes = readAssetBytes(app, "ninja_orig_cert.der")
            log("CERT", "assets/ninja_orig_cert.der sha256=${sha256Hex(assetBytes)}")
        } catch (t: Throwable) {
            log("CERT", "assets/ninja_orig_cert.der MISSING(${t.message})")
        }
    }

    fun readAssetBytes(context: Context, name: String): ByteArray {
        val stream = context.assets.open(name)
        return try {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            try {
                stream.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val hex = Integer.toHexString(b.toInt() and 0xFF)
            if (hex.length == 1) sb.append('0')
            sb.append(hex)
        }
        return sb.toString()
    }
}
