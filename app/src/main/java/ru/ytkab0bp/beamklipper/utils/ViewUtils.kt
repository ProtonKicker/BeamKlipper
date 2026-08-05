package ru.ytkab0bp.beamklipper.utils

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import ru.ytkab0bp.beamklipper.KlipperApp

object ViewUtils {
    @JvmField
    val CUBIC_INTERPOLATOR: TimeInterpolator = PathInterpolator(0.25f, 0f, 0.25f, 1f)
    @JvmField
    val ROBOTO_MEDIUM = "Roboto-Medium"

    private val typefaceCache = HashMap<String, Typeface>()
    private val uiHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun removeCallbacks(r: Runnable) = uiHandler.removeCallbacks(r)

    @JvmStatic
    fun postOnMainThread(r: Runnable) = uiHandler.post(r)

    @JvmStatic
    fun postOnMainThread(r: Runnable, delay: Long) = uiHandler.postDelayed(r, delay)

    @JvmStatic
    fun getUiHandler(): Handler = uiHandler

    @JvmStatic
    fun getTypeface(key: String): Typeface {
        var tf = typefaceCache[key]
        if (tf == null) {
            tf = Typeface.createFromAsset(KlipperApp.INSTANCE.assets, "$key.ttf")
            typefaceCache[key] = tf
        }
        return tf!!
    }

    @JvmStatic
    fun lerp(from: Float, to: Float, `val`: Float): Float = from + (to - from) * `val`

    @JvmStatic
    fun dp(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, KlipperApp.INSTANCE.resources.displayMetrics).toInt()

    @JvmStatic
    fun dp(dp: Int): Int = dp(dp.toFloat())

    @JvmStatic
    fun resolveDrawable(ctx: Context, attr: Int): Drawable {
        val arr = ctx.obtainStyledAttributes(intArrayOf(attr))
        val d = arr.getDrawable(0) ?: throw RuntimeException("Failed to resolve drawable attr $attr")
        arr.recycle()
        return d
    }

    @JvmStatic
    fun resolveColor(ctx: Context, color: Int): Int {
        val arr = ctx.obtainStyledAttributes(intArrayOf(color))
        val i = arr.getColor(0, 0)
        arr.recycle()
        return i
    }

    @JvmStatic
    fun createRipple(color: Int, radiusDp: Float): RippleDrawable = createRipple(color, 0, radiusDp)

    @JvmStatic
    fun createRipple(color: Int, fillColor: Int, radiusDp: Float): RippleDrawable {
        if (radiusDp == -1f) {
            return RippleDrawable(ColorStateList.valueOf(color), null, null)
        }
        val mask = GradientDrawable().apply {
            setColor(Color.BLACK)
            setCornerRadius(dp(radiusDp).toFloat())
        }
        return RippleDrawable(
            ColorStateList.valueOf(color),
            if (fillColor != 0) {
                GradientDrawable().apply {
                    setColor(fillColor)
                    setCornerRadius(dp(radiusDp).toFloat())
                }
            } else null,
            mask
        )
    }

    @JvmStatic
    fun applyPressScale(view: View, pressedScale: Float = 0.985f) {
        view.scaleX = 1f
        view.scaleY = 1f

        val sx = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f)
                .setStiffness(900f)
                .setDampingRatio(0.85f)
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
        }
        val sy = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f)
                .setStiffness(900f)
                .setDampingRatio(0.85f)
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
        }

        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx.animateToFinalPosition(pressedScale)
                    sy.animateToFinalPosition(pressedScale)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sx.animateToFinalPosition(1f)
                    sy.animateToFinalPosition(1f)
                }
            }
            false
        }
    }
}
