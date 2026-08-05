package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class MainHeaderView(context: Context) : LinearLayout(context) {
    private val searchInput: EditText
    private val openWebTile: MaterialCardView
    private val openWebTitle: TextView
    private val openWebSubtitle: TextView
    private val addTile: MaterialCardView
    private val addTitle: TextView
    private val addSubtitle: TextView

    private var onQueryChanged: ((String) -> Unit)? = null
    private var onOpenWeb: (() -> Unit)? = null
    private var onAddInstance: (() -> Unit)? = null
    private var onOpenSettings: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(ViewUtils.dp(16), ViewUtils.dp(12), ViewUtils.dp(16), ViewUtils.dp(8))

        val searchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val searchCard = MaterialCardView(context).apply {
            radius = ViewUtils.dp(28).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            layoutParams = LayoutParams(0, ViewUtils.dp(56), 1f)

            val inner = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ViewUtils.dp(16), 0, ViewUtils.dp(16), 0)
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            inner.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search_outline_28)
                imageTintList = ColorStateList.valueOf(ViewUtils.resolveColor(context, android.R.attr.textColorSecondary))
            }, LayoutParams(ViewUtils.dp(22), ViewUtils.dp(22)).apply {
                marginEnd = ViewUtils.dp(12)
            })

            searchInput = EditText(context).apply {
                hint = context.getString(R.string.search)
                setBackgroundColor(0)
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorPrimary))
                setHintTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorSecondary))
                isSingleLine = true
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            inner.addView(searchInput)
            addView(inner)
        }
        searchRow.addView(searchCard)

        val settingsBtn = MaterialCardView(context).apply {
            radius = ViewUtils.dp(28).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            isClickable = true
            isFocusable = true
            rippleColor = ColorStateList.valueOf(ViewUtils.resolveColor(context, android.R.attr.colorControlHighlight))
            layoutParams = LayoutParams(ViewUtils.dp(56), ViewUtils.dp(56)).apply { marginStart = ViewUtils.dp(12) }

            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_services_outline_28)
                imageTintList = ColorStateList.valueOf(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorPrimary))
                layoutParams = FrameLayout.LayoutParams(ViewUtils.dp(24), ViewUtils.dp(24), Gravity.CENTER)
            }, FrameLayout.LayoutParams(ViewUtils.dp(56), ViewUtils.dp(56)))

            setOnClickListener { onOpenSettings?.invoke() }
            ViewUtils.applyPressScale(this, 0.98f)
        }
        searchRow.addView(settingsBtn)

        addView(searchRow)

        val tilesRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ViewUtils.dp(12)
            }
        }

        openWebTile = createActionTile().also { tile ->
            val content = createActionTileContent(
                iconRes = R.drawable.ic_globe_outline_28,
                titleText = "",
                subtitleText = ""
            )
            openWebTitle = content.first
            openWebSubtitle = content.second
            tile.addView(content.third)
            tile.setOnClickListener { onOpenWeb?.invoke() }
            tilesRow.addView(tile, LayoutParams(0, ViewUtils.dp(86), 1f).apply { marginEnd = ViewUtils.dp(12) })
        }

        addTile = createActionTile().also { tile ->
            val content = createActionTileContent(
                iconRes = R.drawable.ic_add_outline_28,
                titleText = context.getString(R.string.NewInstance),
                subtitleText = context.getString(R.string.Instances)
            )
            addTitle = content.first
            addSubtitle = content.second
            tile.addView(content.third)
            tile.setOnClickListener { onAddInstance?.invoke() }
            tilesRow.addView(tile, LayoutParams(0, ViewUtils.dp(86), 1f))
        }

        addView(tilesRow)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onQueryChanged?.invoke(s?.toString().orEmpty())
            }
        })
    }

    private fun createActionTile(): MaterialCardView {
        return MaterialCardView(context).apply {
            radius = ViewUtils.dp(28).toFloat()
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            rippleColor = ColorStateList.valueOf(ViewUtils.resolveColor(context, android.R.attr.colorControlHighlight))
            setCardBackgroundColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorPrimaryContainer))
            ViewUtils.applyPressScale(this, 0.98f)
        }
    }

    private data class ActionTileContent(
        val title: TextView,
        val subtitle: TextView,
        val root: View
    )

    private fun createActionTileContent(
        iconRes: Int,
        titleText: String,
        subtitleText: String
    ): Triple<TextView, TextView, View> {
        val root = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ViewUtils.dp(16), ViewUtils.dp(14), ViewUtils.dp(16), ViewUtils.dp(14))
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(MaterialCardView(context).apply {
            radius = ViewUtils.dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(ViewUtils.resolveColor(context, com.google.android.material.R.attr.colorPrimary))
                layoutParams = FrameLayout.LayoutParams(ViewUtils.dp(22), ViewUtils.dp(22), Gravity.CENTER)
            }, FrameLayout.LayoutParams(ViewUtils.dp(44), ViewUtils.dp(44), Gravity.CENTER))
        }, LayoutParams(ViewUtils.dp(44), ViewUtils.dp(44)).apply { marginEnd = ViewUtils.dp(12) })

        val title = TextView(context).apply {
            text = titleText
            setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
        }

        val subtitle = TextView(context).apply {
            text = subtitleText
            setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
        }

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(title)
            addView(subtitle)
        }
        root.addView(textColumn)
        return Triple(title, subtitle, root)
    }

    fun setCallbacks(
        onQueryChanged: (String) -> Unit,
        onOpenWeb: () -> Unit,
        onAddInstance: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        this.onQueryChanged = onQueryChanged
        this.onOpenWeb = onOpenWeb
        this.onAddInstance = onAddInstance
        this.onOpenSettings = onOpenSettings
    }

    fun bind(webTitle: String, webSubtitle: String, isWebEnabled: Boolean, instancesSubtitle: String) {
        openWebTitle.text = webTitle
        openWebSubtitle.text = webSubtitle
        openWebTile.isEnabled = isWebEnabled
        openWebTile.alpha = if (isWebEnabled) 1f else 0.6f
        addSubtitle.text = instancesSubtitle
    }
}
