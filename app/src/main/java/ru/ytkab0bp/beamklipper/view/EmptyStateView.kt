package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class EmptyStateView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {

    private val badge: BadgeCanvasView

    class BadgeCanvasView @JvmOverloads constructor(
        ctx: Context, ats: AttributeSet? = null
    ) : FrameLayout(ctx, ats) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ViewUtils.dp(3).toFloat() }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setWillNotDraw(false)
            val lavender = ViewUtils.resolveColor(ctx, com.google.android.material.R.attr.colorPrimary)
            bgPaint.color = ColorUtils.blendARGB(lavender, Color.WHITE, 0.5f)
            ringPaint.color = lavender
            glowPaint.color = ColorUtils.setAlphaComponent(lavender, 48)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = (Math.min(width, height) / 2f) - ViewUtils.dp(2).toFloat()
            canvas.drawCircle(cx, cy, r + ViewUtils.dp(14).toFloat(), glowPaint)
            canvas.drawCircle(cx, cy, r, bgPaint)
            canvas.drawCircle(cx, cy, r - ViewUtils.dp(6).toFloat(), ringPaint)
        }
    }

    init {
        cardElevation = 0f
        radius = ViewUtils.dp(32).toFloat()
        strokeWidth = 0
        setCardBackgroundColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow))
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(ViewUtils.dp(16), ViewUtils.dp(8), ViewUtils.dp(16), ViewUtils.dp(8))
        layoutParams = lp

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ViewUtils.dp(24), ViewUtils.dp(36), ViewUtils.dp(24), ViewUtils.dp(32))
        }

        val size = ViewUtils.dp(96)
        badge = BadgeCanvasView(context).apply {
            layoutParams = LayoutParams(size, size)
        }
        root.addView(badge)

        val title = TextView(context).apply {
            setText(R.string.EmptyTitle)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            setTextColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorOnSurface))
        }
        val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = ViewUtils.dp(22)
        root.addView(title, tlp)

        val sub = TextView(context).apply {
            setText(R.string.EmptySubtitle)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR)
            setTextColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.topMargin = ViewUtils.dp(10)
        slp.marginStart = ViewUtils.dp(8)
        slp.marginEnd = ViewUtils.dp(8)
        root.addView(sub, slp)

        addView(root)
    }
}
