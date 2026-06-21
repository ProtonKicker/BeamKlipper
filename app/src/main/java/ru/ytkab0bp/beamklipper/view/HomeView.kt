package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.content.res.Configuration
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.math.MathUtils
import androidx.core.util.Consumer
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class HomeView(context: Context) : FrameLayout(context) {
    companion object {
        private const val SETTINGS_ENABLED = true
    }

    private var progressListener: Consumer<Float>? = null
    private val gestureDetector: GestureDetector
    private var touchSlop: Int
    private var startOffset = 0f
    private var startProgress = 0f
    private var isTouchDisabled = false
    private var processingSwipe = false
    private var animation: SpringAnimation? = null
    private var progress = 0f
    private var scrollView: View? = null

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!processingSwipe && !isTouchDisabled) {
                    if (progress == 0f && scrollView != null && scrollView!!.canScrollVertically(if (e1!!.y - e2.y > 0) 1 else -1)) {
                        isTouchDisabled = true
                    } else if (animation == null && Math.abs(e2.y - e1!!.y) >= touchSlop && Math.abs(distanceY) >= Math.abs(distanceX) * 1.5f) {
                        startOffset = e2.y - e1.y
                        startProgress = progress
                        processingSwipe = true

                        val ev = MotionEvent.obtain(e2)
                        ev.action = MotionEvent.ACTION_CANCEL
                        for (i in 0 until childCount) {
                            getChildAt(i).dispatchTouchEvent(ev)
                        }
                        ev.recycle()
                    } else {
                        isTouchDisabled = true
                    }
                }
                if (processingSwipe) {
                    progress = MathUtils.clamp(
                        startProgress + (e2.y - e1!!.y - startOffset) / height,
                        if (SETTINGS_ENABLED) -1f else 0f, 1f)
                    invalidateProgress()
                }
                return processingSwipe
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (processingSwipe && Math.abs(velocityY) >= 3500) {
                    if (velocityY > 0) {
                        animateTo(if (progress >= 0) 1f else 0f)
                    } else {
                        animateTo(if (SETTINGS_ENABLED && progress > 0) 0f else if (SETTINGS_ENABLED) -1f else 0f)
                    }
                }
                return false
            }
        })
    }

    fun setScrollView(scrollView: View?) {
        this.scrollView = scrollView
    }

    fun animateTo(to: Float) = animateTo(to, null)

    fun animateTo(to: Float, callback: Runnable?) {
        if (progress == to) {
            callback?.run()
            return
        }
        animation = SpringAnimation(FloatValueHolder(progress))
            .setMinimumVisibleChange(1 / 256f)
            .setSpring(SpringForce(to)
                .setStiffness(800f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener { _, value, _ ->
                progress = value
                invalidateProgress()
            }
            .addEndListener { _, _, _, _ ->
                animation = null
                callback?.run()
            }
        animation?.start()
    }

    private fun invalidateProgress() {
        progressListener?.accept(progress)
    }

    fun getTargetProgress(): Float = animation?.spring?.finalPosition ?: progress

    fun getProgress(): Float = progress

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    fun setProgressListener(progressListener: Consumer<Float>?) {
        this.progressListener = progressListener
        progressListener?.accept(progress)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateProgress()
    }

    private fun clearFlags() {
        processingSwipe = false
        isTouchDisabled = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val det = gestureDetector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (processingSwipe) {
                if (animation == null && progress != 0f && progress != 1f && progress != -1f) {
                    if (progress > 0) {
                        if (progress > 0.5f) animateTo(1f) else animateTo(0f)
                    } else if (progress < 0) {
                        if (progress < -0.5f) animateTo(-1f) else animateTo(0f)
                    }
                }
            }
            clearFlags()
        }
        return det || super.dispatchTouchEvent(ev) || ev.actionMasked == MotionEvent.ACTION_DOWN
    }
}
