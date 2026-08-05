package ru.ytkab0bp.beamklipper.view.preferences

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.jvm.JvmName
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class PreferenceSwitchView(context: Context) : MaterialCardView(context) {
    private val row: LinearLayout
    private val iconBox: ImageView
    private val textColumn: LinearLayout
    private val title: TextView
    private val subtitle: TextView
    private val mSwitch: MaterialSwitch

    companion object {
        private const val ICON_TINT = 0xFF000000.toInt()
        private const val ICON_TILE_BG = 0xFFFFFFFF.toInt()
    }

    init {
        cardElevation = 0f
        radius = ViewUtils.dp(16f).toFloat()
        strokeWidth = ViewUtils.dp(1)
        strokeColor = 0x1A000000
        isClickable = true
        isFocusable = true
        setCardBackgroundColor(0xFFFFFFFF.toInt())

        row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ViewUtils.dp(18), ViewUtils.dp(16), ViewUtils.dp(18), ViewUtils.dp(16))
            minimumHeight = ViewUtils.dp(72)
        }

        iconBox = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(ViewUtils.dp(10), ViewUtils.dp(10), ViewUtils.dp(10), ViewUtils.dp(10))
            background = ViewUtils.makeRoundRectDrawable(
                ICON_TILE_BG,
                ViewUtils.dp(14)
            )
        }
        row.addView(iconBox, LinearLayout.LayoutParams(ViewUtils.dp(48), ViewUtils.dp(48)).apply {
            marginEnd = ViewUtils.dp(16)
        })

        textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR)
            setTextColor(0xFF000000.toInt())
        }
        textColumn.addView(title)

        subtitle = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF666666.toInt())
        }
        textColumn.addView(subtitle)

        mSwitch = object : MaterialSwitch(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
        }
        row.addView(mSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ViewUtils.dp(10)
        })

        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ViewUtils.dp(24)
            rightMargin = ViewUtils.dp(24)
            topMargin = ViewUtils.dp(6)
            bottomMargin = ViewUtils.dp(6)
        }

        ViewUtils.applyPressFeel(this, 0.984f, 4f)
        foreground = ViewUtils.resolveDrawable(context, androidx.appcompat.R.attr.selectableItemBackground)
    }

    @get:JvmName("getSwitchCheckedValue")
    @set:JvmName("setSwitchCheckedValue")
    var checked: Boolean
        get() = mSwitch.isChecked
        set(value) { mSwitch.isChecked = value }

    fun bind(
        title: String,
        subtitle: String?,
        checked: Boolean,
        icon: Int = 0,
        tintColor: Int? = null
    ) {
        this.title.text = title
        if (TextUtils.isEmpty(subtitle)) {
            this.subtitle.visibility = GONE
        } else {
            this.subtitle.text = subtitle
            this.subtitle.visibility = VISIBLE
        }
        mSwitch.isChecked = checked
        if (icon == 0) {
            iconBox.visibility = GONE
        } else {
            iconBox.visibility = VISIBLE
            iconBox.setImageResource(icon)
            iconBox.setColorFilter(ICON_TINT)
        }
    }
}
