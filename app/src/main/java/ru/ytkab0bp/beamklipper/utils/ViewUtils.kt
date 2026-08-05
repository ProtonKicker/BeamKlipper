package ru.ytkab0bp.beamklipper.utils

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
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
    @JvmField
    val ROBOTO_REGULAR = "Roboto-Regular"

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
        val cached: Typeface? = typefaceCache[key]
        if (cached != null) return cached
        val created: Typeface = try {
            Typeface.createFromAsset(KlipperApp.INSTANCE.assets, "$key.ttf") ?: fallbackFor(key)
        } catch (_: Throwable) {
            fallbackFor(key)
        }
        typefaceCache[key] = created
        return created
    }

    private fun fallbackFor(key: String): Typeface = when (key) {
        ROBOTO_MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL) ?: Typeface.DEFAULT
        ROBOTO_REGULAR -> Typeface.create("sans-serif", Typeface.NORMAL) ?: Typeface.DEFAULT
        else -> Typeface.DEFAULT
    }!!

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
    fun makeRoundRectDrawable(color: Int, radiusPx: Int): Drawable {
        return GradientDrawable().apply {
            setColor(color)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
        }
    }

    @JvmStatic
    fun makeChevronDrawable(color: Int): Drawable {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
        }
        drawable.setColor(Color.TRANSPARENT)
        drawable.shape = GradientDrawable.OVAL
        return object : Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val w = bounds.width()
                val h = bounds.height()
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    this.color = color
                    strokeWidth = Math.max(2f, Math.min(w, h) * 0.11f)
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }
                val cx = w * 0.52f
                val cy = h / 2f
                val half = Math.min(w, h) * 0.24f
                val path = android.graphics.Path().apply {
                    moveTo(cx - half, cy - half)
                    lineTo(cx + half * 0.35f, cy)
                    lineTo(cx - half, cy + half)
                }
                canvas.drawPath(path, paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            override fun getIntrinsicWidth(): Int = 24
            override fun getIntrinsicHeight(): Int = 24
        }
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
    fun applyPressScale(view: View, pressedScale: Float = 0.982f) {
        applyPressFeel(view, pressedScale, 0f)
    }

    @JvmStatic
    fun applyLiftOnPress(view: View, liftDp: Float = 6f) {
        applyPressFeel(view, 0.982f, liftDp)
    }

    @JvmStatic
    fun applyPressFeel(
        view: View,
        pressedScale: Float = 0.982f,
        liftDp: Float = 5f
    ) {
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationZ = 0f
        view.alpha = 1f

        val sx = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f).setStiffness(1050f).setDampingRatio(0.93f)
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
        }
        val sy = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f).setStiffness(1050f).setDampingRatio(0.93f)
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
        }
        val tz = SpringAnimation(view, DynamicAnimation.TRANSLATION_Z, 0f).apply {
            spring = SpringForce(0f).setStiffness(900f).setDampingRatio(0.92f)
        }
        val alpha = SpringAnimation(view, DynamicAnimation.ALPHA, 1f).apply {
            spring = SpringForce(1f).setStiffness(1200f).setDampingRatio(1f)
        }
        val targetElev = dp(liftDp).toFloat()

        view.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx.animateToFinalPosition(pressedScale)
                    sy.animateToFinalPosition(pressedScale)
                    tz.animateToFinalPosition(targetElev)
                    alpha.animateToFinalPosition(0.985f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sx.animateToFinalPosition(1f)
                    sy.animateToFinalPosition(1f)
                    tz.animateToFinalPosition(0f)
                    alpha.animateToFinalPosition(1f)
                }
            }
            false
        }
    }

    @JvmStatic
    fun createRoundRectDrawable(color: Int, radiusPx: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        setCornerRadius(radiusPx)
    }

    @JvmStatic
    fun applyMaxWidth(
        view: android.view.View,
        maxWidthDp: Int,
        gravity: Int = android.view.Gravity.CENTER,
        sidePaddingWhenConstrainedDp: Int = 16
    ) {
        val ctx = view.context
        val cfg = ctx.resources.configuration
        val widthDp = cfg.screenWidthDp
        val maxW = dp(maxWidthDp)
        val sidePad = if (widthDp > maxWidthDp + 24) dp(sidePaddingWhenConstrainedDp) else 0
        val lp = view.layoutParams
        if (lp is android.widget.FrameLayout.LayoutParams) {
            val canCap = widthDp > maxWidthDp
            lp.width = if (canCap) maxW else android.view.ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = gravity
            val (l, t, r, b) = listOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin)
            if (canCap) {
                lp.leftMargin = sidePad.coerceAtLeast(l)
                lp.rightMargin = sidePad.coerceAtLeast(r)
            } else {
                lp.leftMargin = l
                lp.rightMargin = r
            }
            view.layoutParams = lp
        }
    }

    @JvmStatic
    fun screenWidthDp(ctx: Context): Int = ctx.resources.configuration.screenWidthDp

    @JvmStatic
    fun screenHeightDp(ctx: Context): Int = ctx.resources.configuration.screenHeightDp

    @JvmStatic
    fun isLandscape(ctx: Context): Boolean =
        ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    @JvmStatic
    fun isWideScreen(ctx: Context): Boolean = screenWidthDp(ctx) >= 600
}
