package ru.ytkab0bp.beamklipper.view.recycler

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.beamklipper.view.SimpleRecyclerItem

class CloudPreferenceItem : SimpleRecyclerItem<CloudPreferenceItem.PreferenceHolderView>() {
    private var mIcon: Drawable? = null
    private var mTitle: CharSequence? = null
    private var mSubtitle: ValueProvider? = null
    private var onClickListener: View.OnClickListener? = null
    private var onLongClickListener: View.OnLongClickListener? = null
    private var textColorRes = 0
    private var noTint = false
    private var valueProvider: ValueProvider? = null
    private var roundRadius = 0f
    private var mPaddings = ViewUtils.dp(12)
    private var mForceDark = false

    fun setTitle(title: CharSequence): CloudPreferenceItem {
        mTitle = title
        return this
    }

    fun setSubtitle(subtitle: CharSequence): CloudPreferenceItem {
        mSubtitle = object : ValueProvider { override fun provide() = subtitle }
        return this
    }

    fun setPaddings(paddings: Int): CloudPreferenceItem {
        mPaddings = paddings
        return this
    }

    fun setForceDark(forceDark: Boolean): CloudPreferenceItem {
        mForceDark = forceDark
        return this
    }

    fun setSubtitleProvider(subtitle: ValueProvider): CloudPreferenceItem {
        mSubtitle = subtitle
        return this
    }

    fun setValueProvider(valueProvider: ValueProvider): CloudPreferenceItem {
        this.valueProvider = valueProvider
        return this
    }

    fun setValue(text: String): CloudPreferenceItem {
        valueProvider = object : ValueProvider { override fun provide() = text }
        return this
    }

    fun setIcon(iconRes: Int): CloudPreferenceItem {
        mIcon = ContextCompat.getDrawable(KlipperApp.INSTANCE, iconRes)
        return this
    }

    fun setIcon(drawable: Drawable): CloudPreferenceItem {
        mIcon = drawable
        return this
    }

    fun setNoTint(noTint: Boolean): CloudPreferenceItem {
        this.noTint = noTint
        return this
    }

    fun setRoundRadius(roundRadius: Float): CloudPreferenceItem {
        this.roundRadius = roundRadius
        return this
    }

    fun setTextColorRes(textColorRes: Int): CloudPreferenceItem {
        this.textColorRes = textColorRes
        return this
    }

    fun setOnClickListener(onClickListener: View.OnClickListener?): CloudPreferenceItem {
        this.onClickListener = onClickListener
        return this
    }

    fun setOnLongClickListener(onLongClickListener: View.OnLongClickListener?): CloudPreferenceItem {
        this.onLongClickListener = onLongClickListener
        return this
    }

    override fun onCreateView(ctx: Context): PreferenceHolderView = PreferenceHolderView(ctx)

    override fun onBindView(view: PreferenceHolderView) {
        view.bind(this)
    }

    class PreferenceHolderView(context: Context) : LinearLayout(context) {
        private val title: TextView
        private val subtitle: TextView
        private val icon: ImageView
        private val value: TextView
        private var radius = 0f
        private var item: CloudPreferenceItem? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            icon = object : AppCompatImageView(context) {
                private val path = Path()

                override fun draw(canvas: Canvas) {
                    if (radius != 0f) {
                        canvas.save()
                        path.rewind()
                        path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
                        canvas.clipPath(path)
                    }
                    super.draw(canvas)
                    if (radius != 0f) {
                        canvas.restore()
                    }
                }
            }.apply {
                layoutParams = LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)).apply {
                    marginStart = ViewUtils.dp(4)
                    marginEnd = ViewUtils.dp(8)
                }
            }
            addView(icon)

            val innerLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }

            title = TextView(context).apply {
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            }
            innerLayout.addView(title)

            subtitle = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            }
            innerLayout.addView(subtitle)

            addView(innerLayout, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ViewUtils.dp(8)
                marginEnd = ViewUtils.dp(8)
            })

            value = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
                setPadding(ViewUtils.dp(8), ViewUtils.dp(6), ViewUtils.dp(8), ViewUtils.dp(6))
                visibility = GONE
            }
            addView(value, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            minimumHeight = ViewUtils.dp(56)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            onApplyTheme()
        }

        fun bind(item: CloudPreferenceItem) {
            this.item = item
            setPadding(item.mPaddings, item.mPaddings, item.mPaddings, item.mPaddings)
            title.text = item.mTitle
            title.visibility = if (TextUtils.isEmpty(item.mTitle)) GONE else VISIBLE

            val sub = item.mSubtitle?.provide()
            subtitle.text = sub
            subtitle.visibility = if (TextUtils.isEmpty(sub)) GONE else VISIBLE

            val v = item.valueProvider?.provide()
            value.text = v
            value.visibility = if (TextUtils.isEmpty(v)) GONE else VISIBLE

            if (item.mIcon != null) {
                icon.visibility = VISIBLE
                icon.setImageDrawable(item.mIcon)
            } else {
                icon.visibility = GONE
            }
            if (item.onClickListener != null) {
                setOnClickListener(item.onClickListener)
            } else {
                isClickable = false
            }
            setOnLongClickListener(item.onLongClickListener)

            if (item.textColorRes != 0) {
                title.setTextColor(ViewUtils.resolveColor(KlipperApp.INSTANCE, item.textColorRes))
            }

            title.typeface = if (item.textColorRes != 0 || item.mIcon != null) {
                ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            } else {
                Typeface.DEFAULT
            }

            icon.imageTintList = if (item.noTint) {
                null
            } else {
                ColorStateList.valueOf(ViewUtils.resolveColor(context,
                    if (item.textColorRes != 0) item.textColorRes else android.R.attr.textColorSecondary))
            }
            radius = item.roundRadius
            icon.invalidate()

            val params = icon.layoutParams
            params.width = if (radius != 0f) ViewUtils.dp(42) else ViewUtils.dp(28)
            params.height = params.width
            if (item.mForceDark) {
                onApplyTheme()
            }
        }

        fun onApplyTheme() {
            val dark = item?.mForceDark == true
            title.setTextColor(if (dark) Color.WHITE else ViewUtils.resolveColor(context, android.R.attr.textColorPrimary))
            subtitle.setTextColor(if (dark) 0x99FFFFFF.toInt() else ViewUtils.resolveColor(context, android.R.attr.textColorSecondary))
            value.setTextColor(if (dark) 0x99FFFFFF.toInt() else ViewUtils.resolveColor(context, android.R.attr.textColorSecondary))
            icon.imageTintList = ColorStateList.valueOf(
                if (dark) 0x99FFFFFF.toInt() else ViewUtils.resolveColor(context, android.R.attr.textColorSecondary)
            )
            background = ViewUtils.createRipple(
                if (dark) 0x21FFFFFF.toInt() else ViewUtils.resolveColor(context, android.R.attr.colorControlHighlight), 16f
            )
        }
    }

    interface ValueProvider {
        fun provide(): CharSequence
    }
}
