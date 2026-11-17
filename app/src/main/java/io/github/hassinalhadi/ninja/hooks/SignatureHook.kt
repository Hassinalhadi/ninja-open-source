package io.github.hassinalhadi.ninja.hooks

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.util.Log
import io.github.hassinalhadi.ninja.utils.DiagnosticLogger
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.LinkedHashMap
import java.util.jar.JarEntry

object SignatureHook {
    private const val TAG = "NinjaMod[Sig]"
    private var origSignature: Signature? = null
    private var origCertificate: Certificate? = null
    private var origSha256: String = ""
    private var verName: String = "1.12.216"
    private var verCode: String = "655"

    @SuppressLint("PrivateApi")
    fun install(app: Application) {
        val certBytes = try {
            DiagnosticLogger.readAssetBytes(app, "ninja_orig_cert.der")
        } catch (t: Throwable) {
            DiagnosticLogger.log("SIG_ERR", "Cannot open ninja_orig_cert.der: ${t.message}")
            return
        }

        val sig = Signature(certBytes)
        origSignature = sig
        origSha256 = DiagnosticLogger.sha256Hex(certBytes)
        origCertificate = try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certBytes))
        } catch (_: Throwable) {
            null
        }
        val targetPkg = app.packageName
        DiagnosticLogger.log("SIG", "Loaded orig cert (${certBytes.size}B) sha256=$origSha256")

        try {
            val apmClass = Class.forName("android.app.ApplicationPackageManager")
            HookUtils.hookAllByName(apmClass, "getPackageInfo", object : MethodHook() {
                @Suppress("DEPRECATION")
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val info = callFrame.result as? PackageInfo ?: return
                    if (info.packageName != targetPkg) return
                    val hadSigs = info.signatures != null
                    if (info.signatures != null) {
                        info.signatures = arrayOf(sig)
                    }
                    DiagnosticLogger.log("SIG_CALL", "getPackageInfo($targetPkg) hadSigs=$hadSigs hasSigningInfo=${if (Build.VERSION.SDK_INT >= 28) info.signingInfo != null else false}")
                }
            })

            HookUtils.hookAllByName(apmClass, "hasSigningCertificate", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val args = callFrame.args
                    val pkgArg = (if (args != null && args.isNotEmpty()) args[0] else null) as? String ?: return
                    if (pkgArg == targetPkg) {
                        callFrame.result = true
                        DiagnosticLogger.log("SIG_CALL", "hasSigningCertificate($targetPkg) -> true")
                    }
                }
            })

            HookUtils.hookAllByName(apmClass, "getInstallerPackageName", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val args = callFrame.args
                    val pkgArg = (if (args != null && args.isNotEmpty()) args[0] else null) as? String ?: return
                    if (pkgArg == targetPkg) {
                        callFrame.result = "com.android.vending"
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "ApplicationPackageManager hook failed: ${t.message}")
            DiagnosticLogger.log("SIG_ERR", "APM hook failed: ${t.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val siClass = SigningInfo::class.java
                HookUtils.hookAllByName(siClass, "getApkContentsSigners", object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        origSignature?.let { callFrame.result = arrayOf(it) }
                        DiagnosticLogger.log("SIG_CALL", "SigningInfo.getApkContentsSigners() spoofed")
                    }
                })
                HookUtils.hookAllByName(siClass, "getSigningCertificateHistory", object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        origSignature?.let { callFrame.result = arrayOf(it) }
                        DiagnosticLogger.log("SIG_CALL", "SigningInfo.getSigningCertificateHistory() spoofed")
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "SigningInfo hook failed: ${t.message}")
                DiagnosticLogger.log("SIG_ERR", "SigningInfo hook failed: ${t.message}")
            }
        }

        try {
            HookUtils.hookAllByName(JarEntry::class.java, "getCertificates", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val entry = callFrame.thisObject as? JarEntry ?: return
                    val name = entry.name
                    if (name.endsWith(".dex") || name == "AndroidManifest.xml" || name == "resources.arsc") {
                        origCertificate?.let { callFrame.result = arrayOf(it) }
                        DiagnosticLogger.log("SIG_CALL", "JarEntry.getCertificates($name) spoofed")
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "JarEntry.getCertificates hook failed: ${t.message}")
            DiagnosticLogger.log("SIG_ERR", "JarEntry hook failed: ${t.message}")
        }

        // ua.d.juliet(ua.c) returns true when APK should be blocked; force false to allow launch
        try {
            val uaD = HookUtils.findClassOrNull("ua.d", app.classLoader)
            if (uaD != null) {
                HookUtils.hookAllByName(uaD, "juliet", object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        callFrame.result = false
                        DiagnosticLogger.log("SIG_CALL", "ua.d.juliet() -> false")
                    }
                })
            } else {
                DiagnosticLogger.log("SIG_WARN", "ua.d not found")
            }
        } catch (t: Throwable) {
            DiagnosticLogger.log("SIG_ERR", "ua.d.juliet hook failed: ${t.message}")
        }

        // ua.n.alpha(Context) builds X-Device-Integrity & X-App-* headers for API requests
        try {
            @Suppress("DEPRECATION")
            val pi = app.packageManager.getPackageInfo(targetPkg, 0)
            if (!pi.versionName.isNullOrEmpty()) verName = pi.versionName!!
            verCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toString() else @Suppress("DEPRECATION") pi.versionCode.toString()
        } catch (_: Throwable) {
        }

        try {
            val uaN = HookUtils.findClassOrNull("ua.n", app.classLoader)
            if (uaN != null) {
                HookUtils.hookAllByName(uaN, "alpha", object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        val clean = LinkedHashMap<String, String>()
                        clean["X-App-Installer"] = "com.android.vending"
                        clean["X-App-Signature"] = origSha256
                        clean["X-App-Version-Name"] = verName
                        clean["X-App-Version-Code"] = verCode
                        clean["X-Device-Integrity"] = "CLEAN"
                        clean["X-Integrity-Timestamp"] = System.currentTimeMillis().toString()
                        callFrame.result = clean
                        DiagnosticLogger.log("SIG_CALL", "ua.n.alpha() -> CLEAN")
                    }
                })
            } else {
                DiagnosticLogger.log("SIG_WARN", "ua.n not found")
            }
        } catch (t: Throwable) {
            DiagnosticLogger.log("SIG_ERR", "ua.n.alpha hook failed: ${t.message}")
        }
    }
}
