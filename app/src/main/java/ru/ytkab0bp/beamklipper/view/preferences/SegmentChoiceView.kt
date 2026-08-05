package ru.ytkab0bp.beamklipper.view.preferences

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class SegmentChoiceView(context: Context) : MaterialCardView(context) {
    private val row: LinearLayout
    private val textColumn: LinearLayout
    private val title: TextView
    private val subtitle: TextView
    private val segmentBar: LinearLayout

    private var options: List<String> = emptyList()
    private var value: String = ""
    private var onSelect: ((String, Int) -> Unit)? = null

    init {
        cardElevation = 0f
        radius = ViewUtils.dp(16f).toFloat()
        strokeWidth = ViewUtils.dp(1)
        strokeColor = 0x1A000000
        isClickable = false
        isFocusable = false
        setCardBackgroundColor(0xFFFFFFFF.toInt())

        row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ViewUtils.dp(18), ViewUtils.dp(16), ViewUtils.dp(18), ViewUtils.dp(16))
            minimumHeight = ViewUtils.dp(72)
        }

        textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = ViewUtils.dp(16)
        })

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

        segmentBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewUtils.makeRoundRectDrawable(
                0xFFEEEEEE.toInt(),
                ViewUtils.dp(12)
            )
            setPadding(ViewUtils.dp(4), ViewUtils.dp(4), ViewUtils.dp(4), ViewUtils.dp(4))
        }
        row.addView(segmentBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(44)))

        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ViewUtils.dp(24)
            rightMargin = ViewUtils.dp(24)
            topMargin = ViewUtils.dp(6)
            bottomMargin = ViewUtils.dp(6)
        }
    }

    fun bind(
        title: CharSequence,
        subtitle: CharSequence?,
        options: List<String>,
        value: String,
        icon: Int = 0,
        optionIcons: List<Int> = emptyList(),
        onSelect: (selected: String, index: Int) -> Unit
    ) {
        this.title.text = title
        if (subtitle == null) {
            this.subtitle.visibility = View.GONE
        } else {
            this.subtitle.text = subtitle
            this.subtitle.visibility = View.VISIBLE
        }
        this.options = options
        this.value = value
        this.onSelect = onSelect
        rebuildSegmentBar()
    }

    private fun rebuildSegmentBar() {
        segmentBar.removeAllViews()
        if (options.isEmpty()) return

        val black = 0xFF000000.toInt()
        val unselectedText = 0xFF333333.toInt()
        for (i in options.indices) {
            val isSelected = options[i] == this.value
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = ViewUtils.dp(36)
                setPadding(ViewUtils.dp(16), 0, ViewUtils.dp(16), 0)
                if (isSelected) {
                    background = ViewUtils.makeRoundRectDrawable(black, ViewUtils.dp(10))
                } else {
                    setBackgroundColor(Color.TRANSPARENT)
                }
                isClickable = true
                isFocusable = true
                foreground = ViewUtils.resolveDrawable(context, androidx.appcompat.R.attr.selectableItemBackground)
            }
            pill.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(36)).apply {
                if (i > 0) marginStart = ViewUtils.dp(2)
            }
            val tv = TextView(context).apply {
                text = options[i]
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                setTextColor(if (isSelected) -0x1 else unselectedText)
            }
            pill.addView(tv)
            pill.setOnClickListener {
                if (options[i] == this@SegmentChoiceView.value) return@setOnClickListener
                val before = this@SegmentChoiceView.value
                this@SegmentChoiceView.value = options[i]
                onSelect?.invoke(options[i], i)
                if (before != this@SegmentChoiceView.value) rebuildSegmentBar()
            }
            ViewUtils.applyPressFeel(pill, 0.98f, 2f)
            segmentBar.addView(pill)
        }
    }
}
