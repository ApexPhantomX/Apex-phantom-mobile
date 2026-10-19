package com.apexphantom.mobile.shield

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.monitor.Thermal
import com.apexphantom.mobile.monitor.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Oyun sırasında sol kenarda duran küçük tutamak ve dokununca açılan bilgi paneli.
 * "Diğer uygulamaların üzerinde göster" izni gerekir.
 */
class OverlayController(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var handle: View? = null
    private var panel: LinearLayout? = null

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun showHandle() = main.post {
        if (handle != null || !canShow()) return@post
        val v = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E5243B"))
                cornerRadii = floatArrayOf(0f, 0f, dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), 0f, 0f)
            }
            alpha = 0.85f
            contentDescription = "Apex Phantom panelini aç"
            setOnClickListener { togglePanel() }
        }
        val lp = WindowManager.LayoutParams(
            dp(14), dp(72),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        try {
            wm.addView(v, lp)
            handle = v
        } catch (_: Exception) {
        }
    }

    fun hideAll() = main.post {
        removePanel()
        handle?.let { runCatching { wm.removeView(it) } }
        handle = null
    }

    private fun removePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun togglePanel() {
        if (panel != null) removePanel() else openPanel()
    }

    private fun text(s: String, size: Float, color: String, bold: Boolean = false) = TextView(context).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(Color.parseColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun openPanel() {
        val app = context.applicationContext as ApexApp
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2111114"))
                setStroke(dp(1), Color.parseColor("#5A1420"))
                cornerRadius = dp(16).toFloat()
            }
        }
        root.addView(text("APEX PHANTOM", 12f, "#FF6B7C", bold = true))
        val game = ShieldStatusHolder.status.value.activeGame
        root.addView(text(if (game != null) "Kalkan etkin: ${Games.packages[game]}" else "Kalkan bekliyor", 15f, "#F2F2F3", bold = true))
        root.addView(text("Cihaz ısısı: ${Thermal.status(context)}", 13f, "#C9C9CF"))
        val listTitle = text("En çok yük yapanlar yükleniyor…", 13f, "#9A9AA3").apply { setPadding(0, dp(10), 0, dp(4)) }
        root.addView(listTitle)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        val close = Button(context).apply {
            text = "Kapat"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#26262B")); cornerRadius = dp(10).toFloat() }
            setOnClickListener { removePanel() }
        }
        root.addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })

        val lp = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = dp(20)
        }
        try {
            wm.addView(root, lp)
            panel = root
        } catch (_: Exception) {
            return
        }

        scope.launch {
            val usage = withContext(Dispatchers.IO) { app.monitor.sample(app.settings.settings.value.exceptions) }
            val top = usage.filter { it.verdict == Verdict.KESIN_KAPAT || it.verdict == Verdict.ONERILIR }
                .sortedByDescending { it.cpuPercent }
                .take(3)
            listTitle.text = if (top.isEmpty()) "Arka planda yük yapan uygulama yok" else "En çok yük yapanlar"
            top.forEach { u ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                val label = text("${u.label}  %${"%.0f".format(u.cpuPercent)}", 13f, "#F2F2F3")
                row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val stop = Button(context).apply {
                    text = "Durdur"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply { setColor(Color.parseColor("#E5243B")); cornerRadius = dp(8).toFloat() }
                    setOnClickListener {
                        scope.launch {
                            withContext(Dispatchers.IO) { app.monitor.stop(u.packageName) }
                            label.text = "${u.label}  durduruldu"
                            isEnabled = false
                        }
                    }
                }
                row.addView(stop, LinearLayout.LayoutParams(dp(84), dp(40)))
                list.addView(row)
            }
        }
    }
}
