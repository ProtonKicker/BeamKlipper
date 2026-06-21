package ru.ytkab0bp.beamklipper.view

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Scroller
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.BeamServerData
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.events.BeamServerDataUpdatedEvent
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.eventbus.EventHandler
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.Locale

class ChangeLogBottomSheet(context: Context) : BottomSheetDialog(context) {
    private var subsView: BoostySubsView? = null
    private val scrollView: ScrollView
    private val pager: ViewPager

    init {
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val gd = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    ViewUtils.dp(28), ViewUtils.dp(28),
                    ViewUtils.dp(28), ViewUtils.dp(28),
                    0f, 0f,
                    0f, 0f
                )
                setColor(ViewUtils.resolveColor(context, android.R.attr.windowBackground))
            }
            background = gd
            setPadding(0, ViewUtils.dp(12), 0, ViewUtils.dp(12))
        }

        val fl = FrameLayout(context)
        val titleA = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            setText(R.string.Changelog)
            setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorPrimary))
            gravity = Gravity.CENTER
        }
        fl.addView(titleA)

        val titleB = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            setText(R.string.ChangelogBoosty)
            setTextColor(ViewUtils.resolveColor(context, R.attr.textColorOnAccent))
            gravity = Gravity.CENTER
            alpha = 0f
        }
        fl.addView(titleB)

        ll.addView(fl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ViewUtils.dp(21)
            rightMargin = ViewUtils.dp(21)
        })

        scrollView = ScrollView(context)
        val text = TextView(context).apply {
            setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setPadding(ViewUtils.dp(16), ViewUtils.dp(12), ViewUtils.dp(16), ViewUtils.dp(12))
        }
        scrollView.addView(text)

        try {
            context.assets.open("update.json").use { inp ->
                ByteArrayOutputStream().use { bos ->
                    val buffer = ByteArray(10240)
                    var c: Int
                    while (inp.read(buffer).also { c = it } != -1) {
                        bos.write(buffer, 0, c)
                    }
                    val obj = JSONObject(bos.toString())
                    val code = Locale.getDefault().language
                    text.text = if (obj.has(code)) obj.getString(code) else obj.getString("en")
                }
            }
        } catch (e: Exception) {
            Log.e("Changelog", "Failed to open update file", e)
        }

        val dm: DisplayMetrics = context.resources.displayMetrics

        pager = object : ViewPager(context) {
            init {
                try {
                    val scrollerField = ViewPager::class.java.getDeclaredField("mScroller")
                    scrollerField.isAccessible = true
                    val mScroller = Scroller(context, ViewUtils.CUBIC_INTERPOLATOR::getInterpolation)
                    scrollerField.set(this, mScroller)
                } catch (_: Exception) {
                }
            }
        }
        pager.adapter = object : PagerAdapter() {
            override fun getCount(): Int = if (BeamServerData.isBoostyAvailable()) 2 else 1

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val v: View = if (position == 0) {
                    scrollView
                } else {
                    val innerLl = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL

                        addView(TextView(context).apply {
                            setTextColor(ViewUtils.resolveColor(context, R.attr.textColorOnAccent))
                            setText(R.string.ChangelogBoostyDescription)
                            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
                            gravity = Gravity.CENTER
                            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                            setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0)
                        })

                        subsView = BoostySubsView(context).apply {
                            if (BeamServerData.SERVER_DATA != null) {
                                val list = ArrayList(BeamServerData.SERVER_DATA!!.boostySubscribers)
                                Collections.shuffle(list)
                                setStrings(list)
                            }
                        }
                        addView(subsView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

                        addView(TextView(context).apply {
                            setText(R.string.ChangelogBoostySubscribe)
                            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                            setTextColor(ViewUtils.resolveColor(context, R.attr.boostyColorTop))
                            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                            gravity = Gravity.CENTER
                            setPadding(ViewUtils.dp(12), ViewUtils.dp(8), ViewUtils.dp(12), ViewUtils.dp(8))
                            setOnClickListener {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://boosty.to/ytkab0bp")))
                            }
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                    innerLl
                }

                container.addView(v)
                return v
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView(`object` as View)
            }

            override fun isViewFromObject(view: View, `object`: Any): Boolean =
                view === `object`
        }

        val btn = BeamButton(context)
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            private val colors = IntArray(2)

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                btn.text = getContext().getString(if (position == pager.adapter!!.count - 1) R.string.ChangelogOK else R.string.ChangelogNext)
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val pr = if (position == 0) positionOffset else 1f
                colors[0] = ColorUtils.blendARGB(
                    ViewUtils.resolveColor(context, R.attr.dialogBackground),
                    ViewUtils.resolveColor(context, R.attr.boostyColorTop), pr)
                colors[1] = ColorUtils.blendARGB(
                    ViewUtils.resolveColor(context, R.attr.dialogBackground),
                    ViewUtils.resolveColor(context, R.attr.boostyColorBottom), pr)
                (ll.background as GradientDrawable).setColors(colors)
                titleA.alpha = 1f - pr
                titleA.translationX = -titleA.width * 0.25f * pr
                titleB.alpha = pr
                titleB.translationX = titleB.width * 0.25f * (1f - pr)
                btn.setColor(ColorUtils.blendARGB(
                    ViewUtils.resolveColor(context, android.R.attr.colorAccent),
                    ViewUtils.resolveColor(context, R.attr.boostyColorTop), pr))
            }
        })
        ll.addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.45f).toInt()))

        btn.apply {
            setText(R.string.ChangelogNext)
            setOnClickListener {
                if (pager.currentItem != pager.adapter!!.count - 1) {
                    pager.currentItem = pager.currentItem + 1
                } else {
                    dismiss()
                }
            }
        }
        ll.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(48)).apply {
            leftMargin = ViewUtils.dp(12)
            topMargin = ViewUtils.dp(12)
            rightMargin = ViewUtils.dp(12)
            bottomMargin = ViewUtils.dp(12)
        })

        ll.fitsSystemWindows = true
        setContentView(ll)

        KlipperApp.EVENT_BUS.registerListener(this)
        setOnDismissListener { KlipperApp.EVENT_BUS.unregisterListener(this) }
    }

    @EventHandler(runOnMainThread = true)
    fun onDataUpdated(e: BeamServerDataUpdatedEvent) {
        if (BeamServerData.SERVER_DATA != null) {
            val list = ArrayList(BeamServerData.SERVER_DATA!!.boostySubscribers)
            Collections.shuffle(list)
            subsView?.setStrings(list)
        }
        pager.adapter?.notifyDataSetChanged()
    }

    override fun show() {
        super.show()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
