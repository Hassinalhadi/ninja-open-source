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
import java.util.jar.JarEntry

object SignatureHook {
    private const val TAG = "NinjaMod[Sig]"
    private var origSignature: Signature? = null
    private var origCertificate: Certificate? = null

    @SuppressLint("PrivateApi")
    fun install(app: Application) {
        val certBytes = runCatching {
            app.assets.open("ninja_orig_cert.der").use { it.readBytes() }
        }.getOrElse {
            DiagnosticLogger.log("SIG_ERR", "Cannot open ninja_orig_cert.der: ${it.message}")
            return
        }

        val sig = Signature(certBytes)
        origSignature = sig
        origCertificate = runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certBytes))
        }.getOrNull()
        val targetPkg = app.packageName
        DiagnosticLogger.log("SIG", "Loaded orig cert (${certBytes.size}B) sha256=${DiagnosticLogger.sha256Hex(certBytes)}")

        runCatching {
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
                    val pkgArg = callFrame.args.firstOrNull() as? String ?: return
                    if (pkgArg == targetPkg) {
                        callFrame.result = true
                        DiagnosticLogger.log("SIG_CALL", "hasSigningCertificate($targetPkg) -> true")
                    }
                }
            })

            HookUtils.hookAllByName(apmClass, "getInstallerPackageName", object : MethodHook() {
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val pkgArg = callFrame.args.firstOrNull() as? String ?: return
                    if (pkgArg == targetPkg) {
                        callFrame.result = "com.android.vending"
                    }
                }
            })
        }.onFailure {
            Log.w(TAG, "ApplicationPackageManager hook failed: ${it.message}")
            DiagnosticLogger.log("SIG_ERR", "APM hook failed: ${it.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
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
            }.onFailure {
                Log.w(TAG, "SigningInfo hook failed: ${it.message}")
                DiagnosticLogger.log("SIG_ERR", "SigningInfo hook failed: ${it.message}")
            }
        }

        runCatching {
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
        }.onFailure {
            Log.w(TAG, "JarEntry.getCertificates hook failed: ${it.message}")
            DiagnosticLogger.log("SIG_ERR", "JarEntry hook failed: ${it.message}")
        }
    }
}
