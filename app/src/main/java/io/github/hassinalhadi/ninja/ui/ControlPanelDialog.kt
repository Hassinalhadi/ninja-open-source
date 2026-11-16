package io.github.hassinalhadi.ninja.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.hassinalhadi.ninja.prefs.NinjaPrefs
import io.github.hassinalhadi.ninja.utils.DiagnosticLogger

object ControlPanelDialog {
    fun show(activity: Activity) {
        val ctx = activity
        val pad = (16 * ctx.resources.displayMetrics.density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun addSwitch(label: String, checked: Boolean): Switch {
            return Switch(ctx).apply {
                text = label
                isChecked = checked
                setPadding(0, pad / 2, 0, pad / 2)
                root.addView(this)
            }
        }

        fun addInput(hintText: String, initial: String, numericDecimal: Boolean = false): EditText {
            val tv = TextView(ctx).apply { text = hintText }
            root.addView(tv)
            return EditText(ctx).apply {
                setText(initial)
                inputType = if (numericDecimal) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                } else {
                    InputType.TYPE_CLASS_NUMBER
                }
                root.addView(this)
            }
        }

        val swAutoClicker = addSwitch("Auto Clicker", NinjaPrefs.isAutoClickerEnabled(ctx))
        val etClickSpeed = addInput("Auto Clicker Speed (ms)", NinjaPrefs.getClickSpeedMs(ctx).toString())

        val swShowSupport = addSwitch("Show Support", NinjaPrefs.isShowSupportEnabled(ctx))
        val swCustomerLocation = addSwitch("Customer Location", NinjaPrefs.isCustomerLocationEnabled(ctx))
        val swDisplayOrderNum = addSwitch("Display Order Number", NinjaPrefs.isDisplayOrderNumberEnabled(ctx))

        val swAutoRefresh = addSwitch("Auto Refresh", NinjaPrefs.isAutoRefreshEnabled(ctx))
        val etRefreshSpeed = addInput("Auto Refresh Interval (ms)", NinjaPrefs.getRefreshSpeedMs(ctx).toString())

        val swFakeGps = addSwitch("Fake GPS", NinjaPrefs.isFakeGpsEnabled(ctx))
        val etLat = addInput("Fake Latitude", NinjaPrefs.getFakeLat(ctx).toString(), numericDecimal = true)
        val etLng = addInput("Fake Longitude", NinjaPrefs.getFakeLng(ctx).toString(), numericDecimal = true)

        val btnOpenSupport = Button(ctx).apply {
            text = "Open Support Screen"
            setOnClickListener {
                try {
                    val intent = Intent().setClassName(
                        ctx.packageName,
                        "delivery.samurai.android.ui.support.AddSupportTicketActivity"
                    )
                    ctx.startActivity(intent)
                } catch (t: Throwable) {
                    Toast.makeText(ctx, "Support screen unavailable: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(btnOpenSupport)

        val btnDiagLog = Button(ctx).apply {
            text = "View Diagnostic Log"
            setOnClickListener { DiagnosticLogger.showLogDialog(ctx) }
        }
        root.addView(btnDiagLog)

        val scroll = ScrollView(ctx).apply { addView(root) }

        AlertDialog.Builder(ctx)
            .setTitle("Ninja Open Source Panel")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                NinjaPrefs.saveAll(
                    context = ctx,
                    autoClicker = swAutoClicker.isChecked,
                    clickSpeedMs = etClickSpeed.text.toString().toLongOrNull() ?: 500L,
                    showSupport = swShowSupport.isChecked,
                    customerLocation = swCustomerLocation.isChecked,
                    fakeGps = swFakeGps.isChecked,
                    fakeLat = etLat.text.toString().trim(),
                    fakeLng = etLng.text.toString().trim(),
                    autoRefresh = swAutoRefresh.isChecked,
                    refreshSpeedMs = etRefreshSpeed.text.toString().toLongOrNull() ?: 2000L,
                    displayOrderNumber = swDisplayOrderNum.isChecked
                )
                Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
