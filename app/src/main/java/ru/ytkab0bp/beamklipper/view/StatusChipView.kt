package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class StatusChipView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {

    enum class Kind { IDLE, RUNNING, STARTING, STOPPING, ERROR }

    private val text: TextView
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dotColor: Int = Color.GRAY
    private var containerColor: Int = Color.GRAY
    private var kind: Kind = Kind.IDLE

    init {
        cardElevation = 0f
        radius = ViewUtils.dp(999).toFloat()
        strokeWidth = 0
        setCardBackgroundColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh))

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = ViewUtils.dp(12)
            val padV = ViewUtils.dp(6)
            setPadding(padH + ViewUtils.dp(14), padV, padH, padV)
        }

        text = TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            setTextColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        inner.addView(text)
        addView(inner)
        setKind(Kind.IDLE)
    }

    fun setKind(kind: Kind) {
        this.kind = kind
        val (dot, container, textId) = when (kind) {
            Kind.RUNNING -> Triple(
                Color.parseColor("#34C759"),
                ColorUtils.setAlphaComponent(Color.parseColor("#34C759"), 22),
                R.string.InstanceRunning
            )
            Kind.STARTING -> Triple(
                Color.parseColor("#FF9F0A"),
                ColorUtils.setAlphaComponent(Color.parseColor("#FF9F0A"), 22),
                R.string.InstanceStarting
            )
            Kind.STOPPING -> Triple(
                Color.parseColor("#FF9F0A"),
                ColorUtils.setAlphaComponent(Color.parseColor("#FF9F0A"), 22),
                R.string.InstanceStopping
            )
            Kind.ERROR -> Triple(
                Color.parseColor("#FF3B30"),
                ColorUtils.setAlphaComponent(Color.parseColor("#FF3B30"), 22),
                R.string.InstanceStarting
            )
            Kind.IDLE -> Triple(
                Color.parseColor("#8E8E93"),
                ColorUtils.setAlphaComponent(Color.parseColor("#8E8E93"), 20),
                R.string.InstanceIdle
            )
        }
        this.dotColor = dot
        this.containerColor = container
        setCardBackgroundColor(containerColor)
        text.setText(textId)
        text.setTextColor(ColorUtils.blendARGB(
            dotColor,
            ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorOnSurface),
            0.55f
        ))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = ViewUtils.dp(4).toFloat()
        val cx = ViewUtils.dp(10).toFloat()
        val cy = height / 2f
        dotPaint.color = dotColor
        canvas.drawCircle(cx, cy, r, dotPaint)
    }
}
