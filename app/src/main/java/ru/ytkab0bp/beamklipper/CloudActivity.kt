package ru.ytkab0bp.beamklipper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.ytkab0bp.beamklipper.cloud.CloudAPI
import ru.ytkab0bp.beamklipper.cloud.CloudController
import ru.ytkab0bp.beamklipper.events.CloudLoginStateUpdatedEvent
import ru.ytkab0bp.beamklipper.events.CloudNeedQREvent
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.beamklipper.view.*
import ru.ytkab0bp.beamklipper.view.recycler.CloudPreferenceItem
import ru.ytkab0bp.eventbus.EventHandler

class CloudActivity : AppCompatActivity() {
    private lateinit var buttonView: FrameLayout
    private lateinit var buttonText: TextView
    private lateinit var buttonProgress: ProgressBar
    private lateinit var recyclerView: FadeRecyclerView
    private lateinit var adapter: SimpleRecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ViewUtils.dp(42), 0, 0)
        }

        val title = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setText(R.string.SettingsCloudManageTitle)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            gravity = Gravity.CENTER
            setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0)
        }
        ll.addView(title)

        val subtitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            setText(R.string.SettingsCloudManageDescription)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            gravity = Gravity.CENTER
            setPadding(ViewUtils.dp(12), ViewUtils.dp(3), ViewUtils.dp(12), ViewUtils.dp(6))
        }
        ll.addView(subtitle)

        val fl = FrameLayout(this)
        recyclerView = FadeRecyclerView(this).apply {
            setBitmapMode()
            adapter = SimpleRecyclerAdapter().also { this@CloudActivity.adapter = it }
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        fl.addView(recyclerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        bindFeatures()
        ll.addView(fl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val tosButton = TextView(this).apply {
            val sb = SpannableStringBuilder.valueOf(getString(R.string.SettingsCloudManageTermsOfService)).append(" ")
            val dr: Drawable = ContextCompat.getDrawable(this@CloudActivity, R.drawable.ic_external_link_outline_24)!!
            val size = ViewUtils.dp(16)
            dr.setBounds(0, 0, size, size)
            sb.append("d", TextColorImageSpan(dr, ViewUtils.dp(2f).toFloat()), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = sb
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setTextColor(Color.WHITE)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            gravity = Gravity.CENTER
            setPadding(ViewUtils.dp(12), ViewUtils.dp(8), ViewUtils.dp(12), ViewUtils.dp(8))
            background = ViewUtils.createRipple(ViewUtils.resolveColor(this@CloudActivity, android.R.attr.colorControlHighlight), 16f)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://beam3d.ru/slicebeam_cloud_tos.html")))
            }
        }
        ll.addView(tosButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)).apply {
            leftMargin = ViewUtils.dp(16)
            rightMargin = ViewUtils.dp(16)
            bottomMargin = ViewUtils.dp(8)
        })

        buttonView = FrameLayout(this).apply {
            background = ViewUtils.createRipple(
                ViewUtils.resolveColor(this@CloudActivity, android.R.attr.colorControlHighlight),
                ViewUtils.resolveColor(this@CloudActivity, android.R.attr.colorAccent), 16f)
        }

        buttonText = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        }
        buttonView.addView(buttonText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        buttonProgress = ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        }
        buttonView.addView(buttonProgress, FrameLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28), Gravity.CENTER))

        bindLoginButton(false)
        ll.addView(buttonView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)).apply {
            leftMargin = ViewUtils.dp(16)
            rightMargin = ViewUtils.dp(16)
            bottomMargin = ViewUtils.dp(16)
        })

        ll.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val frame = FrameLayout(this).apply {
            setOnApplyWindowInsetsListener { v, insets ->
                ll.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
            addView(GLNoiseView(this@CloudActivity))
            addView(ll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(frame)

        KlipperApp.EVENT_BUS.registerListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        KlipperApp.EVENT_BUS.unregisterListener(this)
    }

    @EventHandler(runOnMainThread = true)
    fun onCloudAuthStateUpdated(e: CloudLoginStateUpdatedEvent) {
        bindLoginButton(true)
        bindFeatures()
    }

    @EventHandler(runOnMainThread = true)
    fun onNeedQR(e: CloudNeedQREvent) {
        QRCodeAlertDialog(this, e.link).show()
    }

    private fun bindFeatures() {
        val items = mutableListOf<SimpleRecyclerItem<*>>()
        CloudController.getUserFeatures()?.let { features ->
            for (lvl in features.levels) {
                items.add(CloudSubscriptionLevel(lvl))
            }
        }
        adapter.items = items
    }

    private fun bindLoginButton(animate: Boolean) {
        val loggedIn = Prefs.cloudApiToken != null
        val loading = !loggedIn && CloudController.isLoggingIn()
        val wasLoading = buttonProgress.tag != null

        if (animate) {
            if (wasLoading != loading) {
                buttonProgress.tag = if (loading) 1 else null

                buttonProgress.animate().cancel()
                buttonProgress.animate()
                    .scaleX(if (loading) 1f else 0.4f)
                    .scaleY(if (loading) 1f else 0.4f)
                    .alpha(if (loading) 1f else 0f)
                    .setDuration(150)
                    .setInterpolator(ViewUtils.CUBIC_INTERPOLATOR)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            if (loading) {
                                buttonProgress.visibility = View.VISIBLE
                                buttonProgress.alpha = 0f
                                buttonProgress.scaleX = 0.4f
                                buttonProgress.scaleY = 0.4f
                            }
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (!loading) {
                                buttonProgress.visibility = View.GONE
                            }
                        }
                    }).start()

                buttonText.animate().cancel()
                buttonText.animate()
                    .scaleX(if (!loading) 1f else 0.4f)
                    .scaleY(if (!loading) 1f else 0.4f)
                    .alpha(if (!loading) 1f else 0f)
                    .setDuration(150)
                    .setInterpolator(ViewUtils.CUBIC_INTERPOLATOR)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            if (!loading) {
                                buttonText.visibility = View.VISIBLE
                                buttonText.alpha = 0f
                                buttonText.scaleX = 0.4f
                                buttonText.scaleY = 0.4f
                            }
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (loading) {
                                buttonText.visibility = View.GONE
                            }
                        }
                    }).start()
            }
        } else {
            buttonProgress.tag = if (loading) 1 else null
            buttonProgress.visibility = if (loading) View.VISIBLE else View.GONE
            buttonText.visibility = if (loading) View.GONE else View.VISIBLE
        }

        buttonText.setText(if (loggedIn) R.string.SettingsCloudManageButtonLogOut else R.string.SettingsCloudManageButtonLogIn)
        buttonView.setOnClickListener {
            if (loading) {
                MaterialAlertDialogBuilder(it.context)
                    .setTitle(R.string.SettingsCloudManageButtonLogInCancelTitle)
                    .setMessage(R.string.SettingsCloudManageButtonLogInCancel)
                    .setNegativeButton(R.string.No, null)
                    .setPositiveButton(R.string.Yes) { _, _ -> CloudController.cancelLogin() }
                    .show()
            } else if (Prefs.cloudApiToken != null) {
                CloudController.logout()
            } else {
                CloudController.beginLogin()
            }
        }
    }

    companion object {
        private const val TAG = "CloudActivity"
    }
}

private class CloudSubscriptionLevel(val level: CloudAPI.SubscriptionLevel) : SimpleRecyclerItem<LevelHolderView>() {
    override fun onCreateView(ctx: Context) = LevelHolderView(ctx)

    override fun onBindView(view: LevelHolderView) {
        view.bind(this)
    }
}

private class LevelHolderView(context: Context) : LinearLayout(context) {
    private val icon: ImageView
    private val title: TextView
    private val price: TextView
    private val featuresLayout: RecyclerView
    private val featuresAdapter: SimpleRecyclerAdapter

    init {
        orientation = VERTICAL
        setPadding(0, ViewUtils.dp(16), 0, ViewUtils.dp(8))

        val inner = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ViewUtils.dp(28), 0, ViewUtils.dp(28), 0)
        }
        addView(inner, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = ViewUtils.dp(8)
        })

        icon = ImageView(context)
        inner.addView(icon, LayoutParams(ViewUtils.dp(26), ViewUtils.dp(26)))

        title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
        }
        inner.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = ViewUtils.dp(12)
        })

        price = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
        }
        inner.addView(price)

        featuresLayout = object : RecyclerView(context) {
            override fun dispatchTouchEvent(ev: MotionEvent) = false
            override fun dispatchHoverEvent(event: MotionEvent) = false
        }.apply {
            layoutManager = LinearLayoutManager(context)
            featuresAdapter = SimpleRecyclerAdapter().also { adapter = it }
        }
        addView(featuresLayout, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ViewUtils.dp(3)
            leftMargin = ViewUtils.dp(16)
            rightMargin = ViewUtils.dp(16)
            bottomMargin = ViewUtils.dp(8)
        })

        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ViewUtils.dp(12)
            rightMargin = ViewUtils.dp(12)
            topMargin = ViewUtils.dp(12)
        }
        onApplyTheme()
    }

    fun bind(item: CloudSubscriptionLevel) {
        val lvl = item.level
        title.text = lvl.title
        price.text = lvl.price

        if (lvl.level <= 0) {
            icon.setImageResource(R.drawable.ic_zero_ruble_outline_28)
            price.setText(R.string.SettingsCloudManageFree)
        } else if (lvl.level == 1) {
            icon.setImageResource(R.drawable.ic_stars_outline_28)
        } else {
            icon.setImageResource(R.drawable.ic_cloud_plus_outline_28)
        }

        val features = CloudController.getUserFeatures() ?: return
        val info = CloudController.getUserInfo()
        val ctx = context
        val items = mutableListOf<SimpleRecyclerItem<*>>()

        if (!BuildConfig.IS_GOOGLE_PLAY && features.earlyAccessLevel != -1 && lvl.level >= features.earlyAccessLevel) {
            items.add(CloudPreferenceItem()
                .setForceDark(true)
                .setPaddings(ViewUtils.dp(8))
                .setIcon(R.drawable.ic_clock_circle_dashed_outline_24)
                .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureEarlyAccess))
                .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureEarlyAccessDescription)))
        }
        if (features.remoteAccessLevel != -1 && lvl.level >= features.remoteAccessLevel) {
            items.add(CloudPreferenceItem()
                .setForceDark(true)
                .setPaddings(ViewUtils.dp(8))
                .setIcon(R.drawable.ic_globe_outline_28)
                .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureRemoteAccess))
                .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureRemoteAccessDescription, features.remoteAccessPrintersLimit)))
        }
        if (features.syncRequiredLevel != -1 && lvl.level >= features.syncRequiredLevel) {
            items.add(CloudPreferenceItem()
                .setForceDark(true)
                .setPaddings(ViewUtils.dp(8))
                .setIcon(R.drawable.ic_sync_outline_28)
                .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureCloudSync))
                .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureCloudSyncDescription)))
        }
        if (features.aiGeneratorRequiredLevel != -1 && lvl.level >= features.aiGeneratorRequiredLevel) {
            items.add(CloudPreferenceItem()
                .setForceDark(true)
                .setPaddings(ViewUtils.dp(8))
                .setIcon(R.drawable.ic_brain_outline_28)
                .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureAIGenerator))
                .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureAIGeneratorDescription, features.aiGeneratorModelsPerMonth)))
        }
        if (lvl.level > 0) {
            items.add(CloudPreferenceItem()
                .setForceDark(true)
                .setPaddings(ViewUtils.dp(8))
                .setIcon(R.drawable.ic_box_heart_outline_28)
                .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureFreeForAll))
                .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureFreeForAllDescription)))
        }

        featuresAdapter.items = items
        featuresLayout.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        val subscribed = lvl.level > 0 && info != null && lvl.level == info.currentLevel
        val allowSubscribe = lvl.level > 0 && (info == null || lvl.level > info.currentLevel)

        if (subscribed) {
            price.setText(R.string.SettingsCloudManageSubscribed)
        }
        price.visibility = if (allowSubscribe || subscribed) View.VISIBLE else View.GONE

        setOnClickListener {
            if (subscribed) {
                it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lvl.manageUrl)))
            } else {
                MaterialAlertDialogBuilder(context)
                    .setTitle(lvl.title)
                    .setMessage(R.string.SettingsCloudManageLevelRedirectMessage)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lvl.subscribeOrUpgradeUrl)))
                    }
                    .setNegativeButton(R.string.SettingsCloudManageLevelRedirectAlreadySubscribed) { _, _ ->
                        it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(features.alreadySubscribedInfoUrl)))
                    }
                    .show()
            }
        }
        isClickable = allowSubscribe || subscribed
        onApplyTheme()
    }

    private fun onApplyTheme() {
        var accent = ViewUtils.resolveColor(context, android.R.attr.colorAccent)
        if (ColorUtils.calculateLuminance(accent) >= 0.6f) {
            accent = ColorUtils.blendARGB(accent, Color.BLACK, 0.075f)
        }
        val tooLight = ColorUtils.calculateLuminance(accent) >= 0.6f
        title.setTextColor(0xffffffff.toInt())
        price.setTextColor(0xffffffff.toInt())
        icon.imageTintList = ColorStateList.valueOf(0xffffffff.toInt())
        featuresLayout.background = ViewUtils.createRipple(0, if (tooLight) 0x33ffffff.toInt() else 0x21ffffff.toInt(), 24f)
        background = ViewUtils.createRipple(
            0x21000000.toInt(),
            ColorUtils.blendARGB(0xffffffff.toInt(), accent, if (tooLight) 0.9f else 0.75f), 32f)
    }
}
