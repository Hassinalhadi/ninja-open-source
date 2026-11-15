package io.github.hassinalhadi.ninja.hooks

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.util.Log
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

object SignatureHook {
    private const val TAG = "NinjaMod[Sig]"
    private var origSignature: Signature? = null

    @SuppressLint("PrivateApi")
    fun install(app: Application) {
        val certBytes = runCatching {
            app.assets.open("ninja_orig_cert.der").use { it.readBytes() }
        }.getOrNull() ?: return

        val sig = Signature(certBytes)
        origSignature = sig
        val targetPkg = app.packageName

        runCatching {
            val apmClass = Class.forName("android.app.ApplicationPackageManager")
            HookUtils.hookAllByName(apmClass, "getPackageInfo", object : MethodHook() {
                @Suppress("DEPRECATION")
                override fun afterCall(callFrame: Pine.CallFrame) {
                    val info = callFrame.result as? PackageInfo ?: return
                    if (info.packageName != targetPkg) return
                    if (info.signatures != null) {
                        info.signatures = arrayOf(sig)
                    }
                }
            })
        }.onFailure {
            Log.w(TAG, "ApplicationPackageManager hook failed: ${it.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val siClass = SigningInfo::class.java
                HookUtils.hookAllByName(siClass, "getApkContentsSigners", object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        origSignature?.let { callFrame.result = arrayOf(it) }
                    }
                })
                HookUtils.hookAllByName(siClass, "getSigningCertificateHistory", object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        origSignature?.let { callFrame.result = arrayOf(it) }
                    }
                })
            }.onFailure {
                Log.w(TAG, "SigningInfo hook failed: ${it.message}")
            }
        }
    }
}
