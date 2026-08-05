package ru.ytkab0bp.beamklipper.view.preferences

import android.content.Context
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class PreferenceHeaderView(context: Context) : AppCompatTextView(context) {
    init {
        setPadding(ViewUtils.dp(24), ViewUtils.dp(24), ViewUtils.dp(24), ViewUtils.dp(10))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
        letterSpacing = 0.12f
        isAllCaps = true
        setTextColor(0xFF000000.toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
