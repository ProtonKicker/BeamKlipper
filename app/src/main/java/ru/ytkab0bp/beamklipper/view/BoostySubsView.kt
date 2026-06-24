package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.view.View
import androidx.core.math.MathUtils
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class BoostySubsView(context: Context) : View(context) {
    private val paint = TextPaint()
    private val strings = mutableListOf<String>()
    private val ellipsizedStrings = SparseArray<CharSequence>()
    private var index = 0
    private var progress = 0f
    private var lastUpdated = 0L
    private var firstHeight = 0
    private val rect = Rect()

    init {
        paint.textSize = ViewUtils.dp(20).toFloat()
        paint.typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
        updateColors()
    }

    fun setStrings(strings: List<String>) {
        this.strings.clear()
        this.strings.addAll(strings)
        ellipsizedStrings.clear()
        index = 0
        progress = 0f
        if (strings.isNotEmpty()) {
            val str = strings[index]
            paint.getTextBounds(str, 0, str.length, rect)
            firstHeight = rect.height()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dt = minOf(16, System.currentTimeMillis() - lastUpdated)
        lastUpdated = System.currentTimeMillis()
        if (strings.isNotEmpty()) {
            val tY = (ViewUtils.dp(24) + firstHeight) * progress
            canvas.save()
            canvas.translate(0f, -tY)
            val halfHeight = height / 2f
            var y = 0

            var i = index
            while (y <= height + tY) {
                var j = i
                while (j < 0) j += strings.size
                while (j >= strings.size) j -= strings.size

                var str = ellipsizedStrings[j]
                if (str == null) {
                    str = TextUtils.ellipsize(strings[j], paint, width - paddingLeft - paddingRight.toFloat(), TextUtils.TruncateAt.END)
                    ellipsizedStrings[j] = str
                }

                paint.getTextBounds(str.toString(), 0, str.length, rect)
                var highlight = 1f - Math.abs((y - tY - firstHeight / 2f - halfHeight) / halfHeight)
                highlight = MathUtils.clamp(highlight, 0f, 1f)
                paint.alpha = (0xFF * highlight).toInt()

                val x = (width - rect.width()) / 2f
                canvas.drawText(str, 0, str.length, x, y.toFloat(), paint)

                y += rect.height() + ViewUtils.dp(24)
                i++
            }

            canvas.restore()

            progress += dt / 2000f
            if (progress > 1) {
                progress -= 1f
                index++
                index %= strings.size

                val str = strings[index]
                paint.getTextBounds(str, 0, str.length, rect)
                firstHeight = rect.height()
            }
            invalidate()
        }
    }

    fun updateColors() {
        paint.color = ViewUtils.resolveColor(context, R.attr.textColorOnAccent)
        invalidate()
    }
}
