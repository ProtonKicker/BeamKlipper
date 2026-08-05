package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.events.InstanceStateChangedEvent
import ru.ytkab0bp.beamklipper.events.WebStateChangedEvent
import ru.ytkab0bp.beamklipper.service.WebService
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.eventbus.EventHandler

class KlipperInstanceView(context: Context) : MaterialCardView(context) {
    private class DotView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var currentColor: Int = 0xFF000000.toInt()
        override fun onDraw(canvas: Canvas) {
            paint.color = currentColor
            val r = (measuredWidth / 2f).coerceAtMost(measuredHeight / 2f)
            canvas.drawRect(measuredWidth / 2f - r, measuredHeight / 2f - r,
                measuredWidth / 2f + r, measuredHeight / 2f + r, paint)
        }
        fun setDotColor(c: Int) {
            if (currentColor != c) {
                currentColor = c
                invalidate()
            }
        }
    }

    private var id: String? = null
    private val iconCard: MaterialCardView
    private val icon: ImageView
    private val title: TextView
    private val statusDot: DotView
    private val statusLabel: TextView
    private val autoLabel: TextView
    private val actionBtn: MaterialCardView
    private val actionBtnIcon: ImageView

    private val idleDotColor = 0xFFAAAAAA.toInt()
    private val runningDotColor = 0xFF000000.toInt()
    private val startingDotColor = 0xFF000000.toInt()
    private val stoppingDotColor = 0xFF000000.toInt()

    init {
        radius = ViewUtils.dp(18).toFloat()
        cardElevation = 0f
        strokeColor = 0x1A000000
        strokeWidth = ViewUtils.dp(1)
        setCardBackgroundColor(0xFFFFFFFF.toInt())
        isClickable = true
        isFocusable = true
        rippleColor = ColorStateList.valueOf(ViewUtils.resolveColor(context, android.R.attr.colorControlHighlight))

        val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(ViewUtils.dp(24), ViewUtils.dp(8), ViewUtils.dp(24), ViewUtils.dp(8))
        layoutParams = lp

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(14), ViewUtils.dp(16))
        }

        val iconSize = ViewUtils.dp(56)
        iconCard = MaterialCardView(context).apply {
            cardElevation = 0f
            radius = ViewUtils.dp(14).toFloat()
            strokeWidth = ViewUtils.dp(1)
            strokeColor = 0x1A000000
            layoutParams = LayoutParams(iconSize, iconSize)
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            val wrap = FrameLayout(context).apply {
                icon = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28), Gravity.CENTER)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setColorFilter(0xFF000000.toInt())
                }
                addView(icon)
            }
            addView(wrap)
        }
        outer.addView(iconCard)

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ViewUtils.dp(16)
                marginEnd = ViewUtils.dp(12)
            }
        }
        title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.create(ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR), android.graphics.Typeface.NORMAL)
            setTextColor(0xFF000000.toInt())
            includeFontPadding = false
        }
        titleCol.addView(title)

        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ViewUtils.dp(8)
            }
        }
        statusDot = DotView(context).apply { setDotColor(idleDotColor) }
        val dotSize = ViewUtils.dp(6)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dotSize, dotSize).apply {
            marginEnd = ViewUtils.dp(8)
        })
        statusLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.create(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM), android.graphics.Typeface.NORMAL)
            setTextColor(0xFF333333.toInt())
            includeFontPadding = false
            isAllCaps = true
            letterSpacing = 0.08f
            setPadding(ViewUtils.dp(10), ViewUtils.dp(4), ViewUtils.dp(10), ViewUtils.dp(4))
            background = ViewUtils.makeRoundRectDrawable(0xFFEEEEEE.toInt(), ViewUtils.dp(6))
        }
        statusRow.addView(statusLabel)
        autoLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.create(ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR), 0)
            setTextColor(0xFF666666.toInt())
            includeFontPadding = false
            visibility = GONE
            isAllCaps = true
            letterSpacing = 0.08f
            text = "auto"
        }
        val autoLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ViewUtils.dp(10)
        }
        statusRow.addView(autoLabel, autoLp)
        titleCol.addView(statusRow)
        outer.addView(titleCol)

        val btnSize = ViewUtils.dp(48)
        actionBtn = MaterialCardView(context).apply {
            radius = (btnSize / 2).toFloat()
            cardElevation = 0f
            strokeWidth = ViewUtils.dp(1)
            isClickable = true
            isFocusable = true
            strokeColor = 0x1A000000
            setCardBackgroundColor(0xFF000000.toInt())
            layoutParams = LayoutParams(btnSize, btnSize)
            val wrap = FrameLayout(context).apply {
                actionBtnIcon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_star_fill_24)
                    imageTintList = ColorStateList.valueOf(-0x1)
                    layoutParams = FrameLayout.LayoutParams(ViewUtils.dp(22), ViewUtils.dp(22), Gravity.CENTER)
                }
                addView(actionBtnIcon)
            }
            addView(wrap)
        }
        outer.addView(actionBtn)
        addView(outer)

        ViewUtils.applyPressFeel(this, 0.99f, 2f)
        ViewUtils.applyPressFeel(actionBtn, 0.92f, 6f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        KlipperApp.EVENT_BUS.registerListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        KlipperApp.EVENT_BUS.unregisterListener(this)
    }

    private fun applyIconStyle() {
        iconCard.setCardBackgroundColor(0xFFFFFFFF.toInt())
        icon.setColorFilter(0xFF000000.toInt())
    }

    private fun applyActionStyle(state: KlipperInstance.State) {
        val stopBg = 0xFF000000.toInt()
        val stopFg = -0x1
        val disabledBg = 0xFFE5E5E5.toInt()
        val disabledFg = 0xFFAAAAAA.toInt()

        when (state) {
            KlipperInstance.State.IDLE -> {
                actionBtn.setCardBackgroundColor(0xFF000000.toInt())
                actionBtn.strokeColor = 0x1A000000
                actionBtnIcon.setImageResource(R.drawable.ic_star_fill_24)
                actionBtnIcon.imageTintList = ColorStateList.valueOf(-0x1)
                actionBtn.isEnabled = true
                actionBtn.alpha = 1f
            }
            KlipperInstance.State.RUNNING -> {
                actionBtn.setCardBackgroundColor(stopBg)
                actionBtn.strokeColor = 0x1A000000
                actionBtnIcon.setImageResource(R.drawable.ic_stop_24)
                actionBtnIcon.imageTintList = ColorStateList.valueOf(stopFg)
                actionBtn.isEnabled = true
                actionBtn.alpha = 1f
            }
            KlipperInstance.State.STARTING, KlipperInstance.State.STOPPING -> {
                actionBtn.setCardBackgroundColor(disabledBg)
                actionBtn.strokeColor = 0x00000000
                actionBtnIcon.setImageResource(if (state == KlipperInstance.State.STARTING) R.drawable.ic_play_28 else R.drawable.ic_stop_24)
                actionBtnIcon.imageTintList = ColorStateList.valueOf(disabledFg)
                actionBtn.isEnabled = false
                actionBtn.alpha = 0.6f
            }
        }
    }

    private fun applyStatus(state: KlipperInstance.State, autostart: Boolean) {
        val statusPair: Pair<Int, String> = when (state) {
            KlipperInstance.State.RUNNING -> runningDotColor to context.getString(R.string.state_running)
            KlipperInstance.State.STARTING -> startingDotColor to context.getString(R.string.state_starting)
            KlipperInstance.State.STOPPING -> stoppingDotColor to context.getString(R.string.state_stopping)
            else -> idleDotColor to context.getString(R.string.state_idle)
        }
        statusDot.setDotColor(statusPair.first)
        statusLabel.text = statusPair.second.uppercase(java.util.Locale.ROOT)
        val pillBg = when (state) {
            KlipperInstance.State.RUNNING -> 0xFF000000.toInt()
            else -> 0xFFEEEEEE.toInt()
        }
        statusLabel.background = ViewUtils.makeRoundRectDrawable(pillBg, ViewUtils.dp(6))
        statusLabel.setTextColor(when (state) {
            KlipperInstance.State.RUNNING -> 0xFFFFFFFF.toInt()
            else -> 0xFF333333.toInt()
        })
        autoLabel.visibility = if (autostart) View.VISIBLE else View.GONE
    }

    fun bindWeb() {
        id = null
        when (Prefs.webFrontend) {
            Prefs.FRONTEND_FLUIDD -> {
                icon.setImageResource(R.drawable.ic_square_stack_up_outline_28)
            }
            else -> {
                icon.setImageResource(R.drawable.ic_sailing_24)
            }
        }
        title.setText(R.string.Fluidd)
        applyIconStyle()

        statusDot.visibility = GONE
        autoLabel.visibility = GONE
        val visible = KlipperInstance.isWebServerRunning()
        if (visible) {
            statusLabel.text = context.getString(R.string.web_status_running)
            statusLabel.setTextColor(0xFFFFFFFF.toInt())
            statusLabel.background = ViewUtils.makeRoundRectDrawable(0xFF000000.toInt(), ViewUtils.dp(6))
            bindWebSubtitle()
        } else {
            statusLabel.text = context.getString(R.string.web_status_offline)
            statusLabel.setTextColor(0xFF333333.toInt())
            statusLabel.background = ViewUtils.makeRoundRectDrawable(0xFFEEEEEE.toInt(), ViewUtils.dp(6))
        }
        actionBtn.setCardBackgroundColor(0xFF000000.toInt())
        actionBtn.strokeColor = 0x1A000000
        actionBtnIcon.setImageResource(R.drawable.ic_external_link_outline_24)
        actionBtnIcon.imageTintList = ColorStateList.valueOf(-0x1)
        actionBtn.isEnabled = visible
        actionBtn.alpha = if (visible) 1f else 0.4f
        isClickable = visible
        setOnClickListener { openUi() }
        actionBtn.setOnClickListener { openUi() }
    }

    private fun openUi() {
        val wm = KlipperApp.INSTANCE.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val i = wm.connectionInfo.ipAddress
        val ip = if (i == 0 || !KlipperInstance.isWebServerRunning()) "127.0.0.1" else Formatter.formatIpAddress(i)
        val t = System.currentTimeMillis()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip:${WebService.PORT}/?t=$t"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    private fun bindWebSubtitle() {
        val wm = KlipperApp.INSTANCE.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = KlipperApp.INSTANCE.getString(R.string.IPInfo, Formatter.formatIpAddress(wm.connectionInfo.ipAddress), WebService.PORT)
        title.text = info
    }

    fun bind(instance: KlipperInstance) {
        id = instance.id
        statusDot.visibility = VISIBLE
        icon.setImageResource(instance.icon.drawable)
        title.text = instance.name
        applyIconStyle()
        val state = instance.getState()
        applyStatus(state, instance.autostart)
        applyActionStyle(state)
        setOnClickListener {
            val inst = KlipperInstance.getInstance(id ?: return@setOnClickListener) ?: return@setOnClickListener
            if (inst.getState() == KlipperInstance.State.STARTING || inst.getState() == KlipperInstance.State.STOPPING) return@setOnClickListener
            toggleStartStop(inst)
        }
        actionBtn.setOnClickListener {
            val inst = KlipperInstance.getInstance(id ?: return@setOnClickListener) ?: return@setOnClickListener
            if (inst.getState() == KlipperInstance.State.STARTING || inst.getState() == KlipperInstance.State.STOPPING) return@setOnClickListener
            toggleStartStop(inst)
        }
        invalidate()
    }

    private fun toggleStartStop(inst: KlipperInstance) {
        if (inst.getState() == KlipperInstance.State.IDLE) {
            if (!KlipperInstance.hasFreeSlots()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.NoFreeSlots)
                    .setMessage(context.getString(R.string.NoFreeSlotsDescription, KlipperInstance.SLOTS_COUNT))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            inst.start()
        } else {
            inst.stop()
            if (inst.autostart) {
                inst.autostart = false
                KlipperApp.DATABASE.update(inst)
            }
        }
    }

    @EventHandler(runOnMainThread = true)
    fun onWebStateChanged(e: WebStateChangedEvent) {
        if (id == null) {
            if (e.state == KlipperInstance.State.RUNNING) {
                bindWebSubtitle()
            }
            val visible = e.state == KlipperInstance.State.RUNNING
            isClickable = visible
            actionBtn.isEnabled = visible
            actionBtn.alpha = if (visible) 1f else 0.4f
            statusDot.visibility = GONE
            if (visible) {
                statusLabel.text = context.getString(R.string.web_status_running)
                statusLabel.setTextColor(0xFFFFFFFF.toInt())
                statusLabel.background = ViewUtils.makeRoundRectDrawable(0xFF000000.toInt(), ViewUtils.dp(6))
            } else {
                statusLabel.text = context.getString(R.string.web_status_offline)
                statusLabel.setTextColor(0xFF333333.toInt())
                statusLabel.background = ViewUtils.makeRoundRectDrawable(0xFFEEEEEE.toInt(), ViewUtils.dp(6))
            }
        }
    }

    @EventHandler(runOnMainThread = true)
    fun onStateChanged(e: InstanceStateChangedEvent) {
        if (id == e.id) {
            val inst = KlipperInstance.getInstance(id!!) ?: return
            applyStatus(e.state, inst.autostart)
            applyActionStyle(e.state)
        }
    }
}
