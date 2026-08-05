package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class SectionHeaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    val title: TextView
    val count: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(ViewUtils.dp(24), ViewUtils.dp(28), ViewUtils.dp(24), ViewUtils.dp(10))
        layoutParams = lp

        title = TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            setTextColor(0xFF000000.toInt())
            isAllCaps = true
            letterSpacing = 0.1f
        }
        addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        count = TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        addView(count, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setCount(n: Int) {
        count.text = n.toString()
    }
}
