package io.github.hassinalhadi.ninja.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(this).apply {
            textSize = 20f
            text = "Ninja Open Source (Pine In-App Mod)"
        })
        layout.addView(TextView(this).apply {
            setPadding(0, pad / 2, 0, 0)
            text = """
                Standalone In-Process Mod for delivery.samurai.android (No LSPosed / No Root required).

                Features:
                • Auto Clicker
                • Show Support
                • Customer Location
                • Fake GPS
                • Auto Refresh
                • Display Order Number

                Place any version of orgnail.apk in original/orgnail.apk and run the GitHub Actions workflow to build the patched APK.
            """.trimIndent()
        })
        setContentView(layout)
    }
}
