package ru.ytkab0bp.beamklipper.view

import android.content.Context
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
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils

class MainHeaderView(context: Context) : LinearLayout(context) {
    private val searchInput: EditText
    private val settingsBtn: ImageView
    private val logoTile: ImageView
    private val titleText: TextView
    private val statusRow: LinearLayout
    private val webCard: MaterialCardView
    private val webStatusDot: View
    private val webTitle: TextView
    private val webUrl: TextView
    private val webPill: MaterialCardView
    private val webPillLabel: TextView

    private var onQueryChanged: ((String) -> Unit)? = null
    private var onOpenWeb: (() -> Unit)? = null
    private var onAddInstance: (() -> Unit)? = null
    private var onOpenSettings: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(ViewUtils.dp(24), ViewUtils.dp(18), ViewUtils.dp(24), ViewUtils.dp(10))

        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        logoTile = ImageView(context).apply {
            setImageResource(R.drawable.ic_logo_tile_28)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        headerRow.addView(logoTile, LayoutParams(ViewUtils.dp(48), ViewUtils.dp(48)).apply {})

        titleText = TextView(context).apply {
            setText(R.string.AppName)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = android.graphics.Typeface.create(ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR), android.graphics.Typeface.NORMAL)
            setTextColor(0xFF000000.toInt())
            includeFontPadding = false
            isAllCaps = false
            letterSpacing = -0.01f
        }
        headerRow.addView(titleText, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ViewUtils.dp(14)
        })

        settingsBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_services_outline_28)
            setColorFilter(0xFF000000.toInt())
            setPadding(ViewUtils.dp(10), ViewUtils.dp(10), ViewUtils.dp(10), ViewUtils.dp(10))
            background = ViewUtils.resolveDrawable(context, androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpenSettings?.invoke() }
        }
        headerRow.addView(settingsBtn, LayoutParams(ViewUtils.dp(48), ViewUtils.dp(48)))
        addView(headerRow)

        statusRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ViewUtils.dp(18)
            }
        }

        val textPrimary = 0xFF000000.toInt()

        fun createStatusPill(label: String): Pair<MaterialCardView, TextView> {
            val card = MaterialCardView(context).apply {
                radius = ViewUtils.dp(8).toFloat()
                cardElevation = 0f
                strokeWidth = ViewUtils.dp(1)
                strokeColor = 0x1A000000
                setCardBackgroundColor(0xFFFFFFFF.toInt())
            }
            val wrap = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ViewUtils.dp(14), 0, ViewUtils.dp(16), 0)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(34))
            }
            val dot = View(context).apply {
                background = ViewUtils.makeRoundRectDrawable(0xFF000000.toInt(), ViewUtils.dp(2))
            }
            wrap.addView(dot, LayoutParams(ViewUtils.dp(6), ViewUtils.dp(6)).apply {
                marginEnd = ViewUtils.dp(8)
            })
            val tv = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                setTextColor(textPrimary)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                isAllCaps = true
                letterSpacing = 0.06f
            }
            wrap.addView(tv)
            card.addView(wrap)
            return card to tv
        }

        val pLp1 = createPillLayoutParams(0)
        val pLp2 = createPillLayoutParams(ViewUtils.dp(10))
        statusRow.addView(createStatusPill("Klippy").first, pLp1)
        statusRow.addView(createStatusPill("Moonraker").first, pLp2)
        statusRow.addView(createStatusPill("Web").first, pLp2)
        addView(statusRow)

        val searchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ViewUtils.dp(18)
            }
        }
        val searchCard = MaterialCardView(context).apply {
            radius = ViewUtils.dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            strokeWidth = ViewUtils.dp(1)
            strokeColor = 0x1A000000
            layoutParams = LayoutParams(0, ViewUtils.dp(54), 1f)
            val inner = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ViewUtils.dp(18), 0, ViewUtils.dp(18), 0)
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            inner.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search_outline_28)
                setColorFilter(0xFF000000.toInt())
                imageAlpha = 220
            }, LayoutParams(ViewUtils.dp(20), ViewUtils.dp(20)).apply {
                marginEnd = ViewUtils.dp(12)
            })
            searchInput = EditText(context).apply {
                hint = context.getString(R.string.SearchHint)
                setBackgroundColor(0)
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(0xFF000000.toInt())
                setHintTextColor(0xFFAAAAAA.toInt())
                isSingleLine = true
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            inner.addView(searchInput)
            addView(inner)
        }
        searchRow.addView(searchCard)
        addView(searchRow)

        webCard = MaterialCardView(context).apply {
            radius = ViewUtils.dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            strokeWidth = ViewUtils.dp(1)
            strokeColor = 0x1A000000
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ViewUtils.dp(18)
            }
            val inner = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ViewUtils.dp(18), ViewUtils.dp(18), ViewUtils.dp(16), ViewUtils.dp(18))
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            webStatusDot = View(context).apply {
                background = ViewUtils.makeRoundRectDrawable(0xFFAAAAAA.toInt(), ViewUtils.dp(2))
            }
            inner.addView(webStatusDot, LayoutParams(ViewUtils.dp(8), ViewUtils.dp(8)).apply {
                marginEnd = ViewUtils.dp(14)
            })
            val leftCol = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.TOP
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            webTitle = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = android.graphics.Typeface.create(ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR), android.graphics.Typeface.NORMAL)
                setTextColor(0xFF000000.toInt())
                maxLines = 1
                includeFontPadding = false
            }
            leftCol.addView(webTitle)
            webUrl = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFF666666.toInt())
                maxLines = 1
                includeFontPadding = false
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = ViewUtils.dp(6)
                }
            }
            leftCol.addView(webUrl)
            inner.addView(leftCol)

            webPill = MaterialCardView(context).apply {
                radius = ViewUtils.dp(10).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(0xFFEEEEEE.toInt())
                strokeWidth = ViewUtils.dp(1)
                strokeColor = 0x1A000000
                isClickable = true
                isFocusable = true
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(44))
                val inner2 = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(ViewUtils.dp(18), 0, ViewUtils.dp(18), 0)
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(44))
                }
                webPillLabel = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                    setTextColor(0xFF000000.toInt())
                    isAllCaps = true
                    letterSpacing = 0.06f
                }
                inner2.addView(webPillLabel)
                addView(inner2)
                ViewUtils.applyPressScale(this, 0.96f)
            }
            inner.addView(webPill)
            addView(inner)
        }
        webPill.setOnClickListener { onOpenWeb?.invoke() }
        addView(webCard)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onQueryChanged?.invoke(s?.toString().orEmpty())
            }
        })
    }

    private fun createPillLayoutParams(marginStart: Int): LayoutParams =
        LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(34)).apply {
            if (marginStart > 0) this.marginStart = marginStart
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

    fun bind(
        webUrlString: String,
        isWebRunning: Boolean,
        activeCount: Int,
        instancesTotal: Int
    ) {
        val black = 0xFF000000.toInt()
        val muted = 0xFFAAAAAA.toInt()

        webTitle.text = when (Prefs.webFrontend) {
            Prefs.FRONTEND_FLUIDD -> context.getString(R.string.Fluidd)
            else -> context.getString(R.string.Mainsail)
        }

        if (isWebRunning) {
            webUrl.text = webUrlString
            (webStatusDot.background as? android.graphics.drawable.GradientDrawable)?.setColor(black)
            webPill.setCardBackgroundColor(black)
            webPill.strokeColor = 0x00000000
            webPillLabel.text = context.getString(R.string.OpenUI)
            webPillLabel.setTextColor(-0x1)
            webPill.isEnabled = true
        } else {
            webUrl.text = webUrlString
            (webStatusDot.background as? android.graphics.drawable.GradientDrawable)?.setColor(muted)
            webPill.setCardBackgroundColor(0xFFEEEEEE.toInt())
            webPill.strokeColor = 0x1A000000
            webPillLabel.text = context.getString(R.string.StartProfile)
            webPillLabel.setTextColor(black)
            webPill.isEnabled = true
        }
    }
}
