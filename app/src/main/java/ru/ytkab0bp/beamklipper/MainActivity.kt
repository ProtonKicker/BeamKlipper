package ru.ytkab0bp.beamklipper

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.TextUtils
import android.text.format.Formatter
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoho.android.usbserial.driver.UsbSerialProber
import ru.ytkab0bp.beamklipper.events.*
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.service.WebService
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.beamklipper.view.*
import ru.ytkab0bp.beamklipper.view.preferences.PreferenceSwitchView
import ru.ytkab0bp.eventbus.EventHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_INSTANCE = 1
        private val NOTIFY_LIVE = Any()
    }

    private lateinit var homeView: HomeView
    private lateinit var listCardView: MaterialCardView
    private lateinit var resizeFrame: SmoothResizeFrameLayout
    private lateinit var listView: RecyclerView
    private var instances = mutableListOf<KlipperInstance>()
    private var visibleInstances: List<KlipperInstance> = emptyList()
    private var searchQuery: String = ""
    private lateinit var instancesAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>

    private var newOrEditAnimation: SpringAnimation? = null
    private lateinit var newOrEditLayout: LinearLayout
    private lateinit var newOrEditTitle: TextView
    private var editInstance: KlipperInstance? = null
    private lateinit var nameRow: EditTextRowView
    private lateinit var configRow: EditTextRowView
    private lateinit var editOpenDirectoryRow: TextView
    private lateinit var autostartRow: PreferenceSwitchView
    private lateinit var newOrEditContinue: TextView

    private lateinit var preferencesView: PreferencesCardView

    private lateinit var noPermsLayout: MaterialCardView
    private lateinit var batteryRow: PermissionRowView
    private var notificationsRow: PermissionRowView? = null
    private var hideServicesChannelRow: PermissionRowView? = null
    private var brokenBySDCardRow: PermissionRowView? = null

    private lateinit var logoView: ImageView
    private lateinit var titleView: TextView
    private lateinit var badgesLayout: FrameLayout
    private var refBadges: Array<RefBadgeView?> = emptyArray()

    private var isTV = false
    private var isCurrentLauncher = false

    @SuppressLint("BatteryLife", "InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature("android.hardware.touchscreen") ||
            !packageManager.hasSystemFeature("android.hardware.telephony")
        ) {
            isTV = true
            PermissionsChecker.setIgnoreNotificationsChannel(true)
        }
        if (Build.MANUFACTURER.lowercase(Locale.ROOT).contains("meizu") ||
            Build.BRAND.lowercase(Locale.ROOT).contains("meizu")
        ) {
            PermissionsChecker.setIgnoreNotificationsChannel(true)
        }
        isCurrentLauncher = intent?.categories?.contains(Intent.CATEGORY_HOME) == true

        val fl = FrameLayout(this)

        homeView = HomeView(this)

        badgesLayout = object : FrameLayout(this) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                invalidateHomeProgress(homeView.progress)
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
        }

        fl.setOnApplyWindowInsetsListener { v, insets ->
            badgesLayout.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            preferencesView.setPadding(insets.systemWindowInsetLeft, 0, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            val params = listCardView.layoutParams as ViewGroup.MarginLayoutParams
            params.leftMargin = ViewUtils.dp(21) + insets.systemWindowInsetLeft
            params.topMargin = ViewUtils.dp(64) + insets.systemWindowInsetTop
            params.rightMargin = ViewUtils.dp(21) + insets.systemWindowInsetRight
            params.bottomMargin = ViewUtils.dp(72) + insets.systemWindowInsetBottom
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.bottomMargin -= insets.getInsets(WindowInsets.Type.ime()).bottom / 2
            } else if (insets.systemWindowInsetBottom >= ViewUtils.dp(20)) {
                params.bottomMargin -= insets.systemWindowInsetBottom / 2
            }
            listCardView.requestLayout()
            insets
        }

        logoView = ImageView(this).apply {
            setImageResource(R.drawable.icon_logo)
        }
        badgesLayout.addView(logoView, FrameLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)).apply {
            topMargin = ViewUtils.dp(6)
            leftMargin = ViewUtils.dp(9)
        })

        titleView = TextView(this).apply {
            setText(R.string.AppName)
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.colorAccent))
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        badgesLayout.addView(titleView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewUtils.dp(22 + 18)).apply {
            leftMargin = ViewUtils.dp(9 + 28 + 12)
            rightMargin = ViewUtils.dp(9)
        })
        buildBadges()

        listCardView = MaterialCardView(this).apply {
            setStrokeColor(0)
            setCardBackgroundColor(
                ViewUtils.resolveColor(
                    this@MainActivity,
                    com.google.android.material.R.attr.colorSurfaceContainerLow
                )
            )
            cardElevation = ViewUtils.dp(1).toFloat()
            radius = ViewUtils.dp(32).toFloat()
        }

        preferencesView = PreferencesCardView(this).apply {
            header.setOnClickListener { homeView.animateTo(-1f) }
        }
        homeView.setProgressListener { invalidateHomeProgress(it) }

        resizeFrame = SmoothResizeFrameLayout(this)

        listView = RecyclerView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = SmoothItemAnimator()
        }
        homeView.setScrollView(listView)
        instancesAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v: View = when (viewType) {
                    VIEW_TYPE_HEADER -> {
                        MainHeaderView(this@MainActivity).apply {
                            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        }
                    }
                    VIEW_TYPE_INSTANCE -> KlipperInstanceView(this@MainActivity)
                    else -> throw IllegalStateException("Unknown viewType: $viewType")
                }
                return object : RecyclerView.ViewHolder(v) {}
            }

            @Suppress("UNCHECKED_CAST")
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
                if (payloads.contains(NOTIFY_LIVE)) {
                    val view = holder.itemView as KlipperInstanceView
                    view.bind(visibleInstances[position - 1])
                    return
                }
                super.onBindViewHolder(holder, position, payloads)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> {
                        val header = holder.itemView as MainHeaderView
                        header.setCallbacks(
                            onQueryChanged = { q ->
                                searchQuery = q
                                applyFilter()
                            },
                            onOpenWeb = { openWebFrontend() },
                            onAddInstance = { openNewInstanceSheet() },
                            onOpenSettings = { homeView.animateTo(-1f) }
                        )
                        bindHeader(header)
                    }
                    VIEW_TYPE_INSTANCE -> {
                        val view = holder.itemView as KlipperInstanceView
                        view.bind(visibleInstances[position - 1])
                        view.setOnClickListener {
                            val inst = visibleInstances[position - 1]
                            newOrEditTitle.setText(R.string.EditInstance)
                            editInstance = inst
                            editOpenDirectoryRow.visibility = View.VISIBLE
                            autostartRow.bind(getString(R.string.Autostart), null, inst.autostart)
                            nameRow.bind(R.string.InstanceName, inst.name)
                            configRow.visibility = View.GONE
                            animateNewOrEditLayout(true)
                            newOrEditContinue.setText(R.string.InstanceOK)
                        }
                        view.setOnLongClickListener {
                            val inst = visibleInstances[position - 1]
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle(getString(R.string.InstanceDelete, inst.name))
                                .setMessage(R.string.InstanceDeleteConfirm)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    KlipperApp.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        KlipperApp.DATABASE.delete(inst)
                                    }
                                }
                                .show()
                            true
                        }
                    }
                }
            }

            override fun getItemViewType(position: Int): Int {
                return when (position) {
                    0 -> VIEW_TYPE_HEADER
                    else -> VIEW_TYPE_INSTANCE
                }
            }

            override fun getItemCount(): Int = visibleInstances.size + 1
        }
        listView.adapter = instancesAdapter
        resizeFrame.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ViewUtils.resolveColor(this@MainActivity, R.attr.dividerColor)
            style = Paint.Style.STROKE
            strokeWidth = ViewUtils.dp(1f).toFloat()
        }

        newOrEditLayout = object : LinearLayout(this@MainActivity) {
            init {
                setWillNotDraw(false)
            }

            override fun draw(canvas: Canvas) {
                super.draw(canvas)
                for (i in 0 until childCount - 1) {
                    val child = getChildAt(i)
                    if (child.visibility == View.VISIBLE) {
                        canvas.drawLine(
                            ViewUtils.dp(1.5f).toFloat(), child.y + child.height - ViewUtils.dp(1),
                            (child.width - ViewUtils.dp(1.5f)).toFloat(), child.y + child.height - ViewUtils.dp(1),
                            dividerPaint
                        )
                    }
                }
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        newOrEditTitle = TextView(this@MainActivity).apply {
            setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            gravity = Gravity.CENTER
            setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52))
            setOnClickListener { animateNewOrEditLayout(false) }
            isFocusable = false
        }
        newOrEditLayout.addView(newOrEditTitle)

        nameRow = EditTextRowView(this@MainActivity).apply {
            setOnClickListener {
                val frame = FrameLayout(it.context).apply {
                    setPadding(ViewUtils.dp(21), 0, ViewUtils.dp(21), 0)
                    val et = EditText(it.context).apply {
                        setText(this@apply.text)
                    }
                    addView(et)
                }
                MaterialAlertDialogBuilder(it.context)
                    .setTitle(R.string.InstanceName)
                    .setView(frame)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                        nameRow.bind(R.string.InstanceName, (frame.getChildAt(0) as EditText).text.toString())
                    }
                    .show()
            }
        }
        newOrEditLayout.addView(nameRow)

        configRow = EditTextRowView(this@MainActivity).apply {
            setOnClickListener {
                val config = File(KlipperApp.INSTANCE.filesDir, "klipper/config")
                val filesList = config.listFiles()?.map { it.name }?.sorted() ?: emptyList()
                MaterialAlertDialogBuilder(it.context)
                    .setTitle(R.string.InstanceConfig)
                    .setItems(filesList.toTypedArray()) { dialog, which -> configRow.bind(R.string.InstanceConfig, filesList[which]) }
                    .show()
            }
        }
        newOrEditLayout.addView(configRow)

        editOpenDirectoryRow = TextView(this@MainActivity).apply {
            setText(R.string.EditOpenDirectory)
            setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(ViewUtils.dp(21), 0, ViewUtils.dp(21), 0)
            background = ViewUtils.resolveDrawable(this@MainActivity, android.R.attr.selectableItemBackground)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52))
            setOnClickListener {
                val uri = DocumentsContract.buildRootUri("ru.ytkab0bp.beamklipper", editInstance!!.id)
                try {
                    try {
                        try {
                            startActivity(Intent("android.intent.action.VIEW").setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR))
                        } catch (_: ActivityNotFoundException) {
                            startActivity(Intent("android.provider.action.BROWSE").setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR))
                        }
                    } catch (_: ActivityNotFoundException) {
                        startActivity(Intent("android.provider.action.BROWSE_DOCUMENT_ROOT").setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR))
                    }
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        newOrEditLayout.addView(editOpenDirectoryRow)

        autostartRow = PreferenceSwitchView(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52))
            setOnClickListener { isChecked = !isChecked }
        }
        newOrEditLayout.addView(autostartRow)

        newOrEditContinue = TextView(this@MainActivity).apply {
            setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            gravity = Gravity.CENTER
            setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0)
            background = ViewUtils.resolveDrawable(this@MainActivity, android.R.attr.selectableItemBackground)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52))
            var saving = false
            setOnClickListener {
                if (saving) return@setOnClickListener
                if (TextUtils.isEmpty(nameRow.text)) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.Error)
                        .setMessage(R.string.ErrorNameEmpty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setOnClickListener
                }

                if (editInstance != null) {
                    val editing = editInstance!!
                    editing.name = nameRow.text.toString().trim()
                    editing.autostart = autostartRow.isChecked
                    saving = true
                    isEnabled = false
                    KlipperApp.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            KlipperApp.DATABASE.update(editing)
                        } finally {
                            runOnUiThread {
                                saving = false
                                isEnabled = true
                            }
                        }
                    }
                    editInstance = null
                    animateNewOrEditLayout(false)
                    return@setOnClickListener
                }

                if (TextUtils.isEmpty(configRow.text)) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.Error)
                        .setMessage(R.string.ErrorConfigEmpty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setOnClickListener
                }

                val inst = KlipperInstance().apply {
                    id = UUID.randomUUID().toString()
                    name = nameRow.text.toString().trim()
                    autostart = autostartRow.isChecked
                }
                val cfg = File(inst.publicDirectory, "config/printer.cfg")
                val cfgText = configRow.text.toString()
                saving = true
                isEnabled = false
                KlipperApp.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        cfg.parentFile?.mkdirs()
                        try {
                            FileInputStream(File(KlipperApp.INSTANCE.filesDir, "klipper/config/$cfgText")).use { fis ->
                                FileOutputStream(cfg).use { fos ->
                                    fis.copyTo(fos)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Failed to copy config file", e)
                        }
                        KlipperApp.DATABASE.insert(inst)
                    } finally {
                        runOnUiThread {
                            saving = false
                            isEnabled = true
                        }
                    }
                }
                animateNewOrEditLayout(false)
            }
        }
        newOrEditLayout.addView(newOrEditContinue)

        newOrEditLayout.visibility = View.GONE
        resizeFrame.addView(newOrEditLayout)

        listCardView.addView(resizeFrame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        homeView.addView(listCardView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = ViewUtils.dp(21)
            rightMargin = ViewUtils.dp(21)
            topMargin = ViewUtils.dp(64)
            bottomMargin = ViewUtils.dp(72)
        })
        homeView.addView(preferencesView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        homeView.addView(badgesLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = ViewUtils.dp(12)
            leftMargin = ViewUtils.dp(12)
            rightMargin = ViewUtils.dp(12)
        })

        fl.addView(homeView)

        noPermsLayout = MaterialCardView(this@MainActivity).apply {
            setCardBackgroundColor(ViewUtils.resolveColor(this@MainActivity, R.attr.cardOutlineColor))
            setStrokeColor(0)
            radius = ViewUtils.dp(32).toFloat()
        }
        val ll = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

        batteryRow = PermissionRowView(this@MainActivity).apply {
            bind(R.string.BatteryOptimizationExclusion, PermissionsChecker.hasBatteryPerm(), true)
            setPadding(paddingLeft, ViewUtils.dp(6), paddingRight, paddingBottom)
            setOnClickListener {
                val r = it as PermissionRowView
                if (!r.isChecked) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
            }
        }
        ll.addView(batteryRow)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsRow = PermissionRowView(this@MainActivity).apply {
                bind(R.string.Notifications, PermissionsChecker.hasNotificationPerm(), true)
                setOnClickListener {
                    val r = it as PermissionRowView
                    if (!r.isChecked) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                    }
                }
            }
            ll.addView(notificationsRow)
        }
        if (PermissionsChecker.ENABLE_NOTIFICATIONS_CHANNEL_CHECK &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !PermissionsChecker.ignoreNotificationsChannel()
        ) {
            hideServicesChannelRow = PermissionRowView(this@MainActivity).apply {
                bind(R.string.HideNotificationsChannel, PermissionsChecker.isNotificationsChannelHidden(), true)
                setOnClickListener {
                    val r = it as PermissionRowView
                    if (!r.isChecked) {
                        Toast.makeText(this@MainActivity, getString(R.string.HideNotificationsChannelInfo, getString(R.string.ServicesChannel)), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, KlipperApp.SERVICES_CHANNEL))
                    }
                }
            }
            ll.addView(hideServicesChannelRow)
        }
        if (!PermissionsChecker.isNotBrokenBySDCard()) {
            brokenBySDCardRow = PermissionRowView(this@MainActivity).apply {
                bind(R.string.NotOnSdcard, PermissionsChecker.isNotBrokenBySDCard(), true)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${KlipperApp.INSTANCE.packageName}")))
                    Toast.makeText(this@MainActivity, R.string.NotOnSdcardInfo, Toast.LENGTH_SHORT).show()
                }
            }
            ll.addView(brokenBySDCardRow)
        }

        PermissionRowView(this@MainActivity).apply {
            titleView.gravity = Gravity.CENTER
            titleView.typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            titleView.setText(R.string.Next)
            mSwitch.visibility = View.GONE
            setPadding(paddingLeft, ViewUtils.dp(14), paddingRight, ViewUtils.dp(14))
            setOnClickListener {
                if (PermissionsChecker.needBlockStart()) return@setOnClickListener
                animateHomeView()
            }
            ll.addView(this)
        }

        noPermsLayout.addView(ll)
        fl.addView(noPermsLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = ViewUtils.dp(21)
            topMargin = ViewUtils.dp(21)
            rightMargin = ViewUtils.dp(21)
            bottomMargin = ViewUtils.dp(21)
        })

        noPermsLayout.visibility = if (PermissionsChecker.needBlockStart()) View.VISIBLE else View.GONE
        homeView.visibility = if (PermissionsChecker.needBlockStart()) View.GONE else View.VISIBLE

        if (isTV) {
            preferencesView.isFocusable = false
            preferencesView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            badgesLayout.isFocusable = false
            badgesLayout.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }

        fl.setBackgroundColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.windowBackground))
        setContentView(fl)

        processIntent(intent)
        instances = ArrayList(KlipperInstance.getInstances())
        KlipperApp.EVENT_BUS.registerListener(this)

        if (Prefs.getLastCommit() != BuildConfig.COMMIT && KlipperApp.hasUpdateInfo) {
            Prefs.setLastCommit()
            ChangeLogBottomSheet(this@MainActivity).show()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (newOrEditLayout.visibility != View.GONE) {
                    animateNewOrEditLayout(false)
                    return
                }
                if (homeView.progress != 0f) {
                    homeView.animateTo(0f)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        KlipperApp.EVENT_BUS.unregisterListener(this)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (newOrEditLayout.findFocus() != null && keyCode != KeyEvent.KEYCODE_BACK) {
            return newOrEditLayout.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (newOrEditLayout.findFocus() != null && keyCode != KeyEvent.KEYCODE_BACK) {
            return newOrEditLayout.onKeyDown(keyCode, event)
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val focusInList = homeView.getTargetProgress() == 0f
            val focusInSettings = homeView.getTargetProgress() == -1f

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (focusInSettings) return super.onKeyDown(keyCode, event)

                val isLast = if (focusInList) {
                    val focus = listView.findFocus()
                    val adapterCount = listView.adapter?.itemCount ?: return false
                    focus != null && listView.getChildViewHolder(focus).adapterPosition == adapterCount - 1
                } else {
                    false
                }

                if (!isLast) return super.onKeyDown(keyCode, event)

                homeView.animateTo(-1f) {
                    preferencesView.listView.getChildAt(1).requestFocus()
                }
                return true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (focusInList) return super.onKeyDown(keyCode, event)

                val isFirst = if (!focusInList) {
                    val focus = preferencesView.listView.findFocus()
                    focus != null && preferencesView.listView.getChildViewHolder(focus).adapterPosition == 1
                } else {
                    false
                }

                if (!isFirst) return super.onKeyDown(keyCode, event)

                homeView.animateTo(0f) {
                    listView.getChildAt(1)?.requestFocus()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun buildBadges() {
        for (refBadge in refBadges) {
            if (refBadge != null) {
                badgesLayout.removeView(refBadge)
            }
        }
        refBadges = emptyArray()
    }

    fun isCurrentLauncher(): Boolean = isCurrentLauncher

    @EventHandler(runOnMainThread = true)
    fun onInstancesRefreshed(e: InstancesRefreshedEvent) {
        instances = ArrayList(KlipperInstance.getInstances())
        applyFilter()
    }

    @EventHandler(runOnMainThread = true)
    fun onFrontendChanged(e: WebFrontendChangedEvent) {
        listView.adapter?.notifyItemChanged(0)
    }

    @EventHandler(runOnMainThread = true)
    fun onWebStateChanged(e: WebStateChangedEvent) {
        listView.adapter?.notifyItemChanged(0)
    }

    @EventHandler(runOnMainThread = true)
    fun onInstanceStateChanged(e: InstanceStateChangedEvent) {
        val idx = visibleInstances.indexOfFirst { it.id == e.id }
        if (idx >= 0) {
            listView.adapter?.notifyItemChanged(idx + 1, NOTIFY_LIVE)
        }
    }

    private fun applyFilter() {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        visibleInstances = if (q.isEmpty()) instances else instances.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        listView.adapter?.notifyDataSetChanged()
    }

    private fun openNewInstanceSheet() {
        newOrEditTitle.setText(R.string.NewInstance)
        editInstance = null
        editOpenDirectoryRow.visibility = View.GONE
        autostartRow.bind(getString(R.string.Autostart), null, false)
        nameRow.bind(R.string.InstanceName, null)
        configRow.apply {
            bind(R.string.InstanceConfig, null)
            visibility = View.VISIBLE
        }
        newOrEditContinue.setText(R.string.InstanceCreate)
        animateNewOrEditLayout(true)
    }

    private fun openWebFrontend() {
        val wm = KlipperApp.INSTANCE.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val i = wm.connectionInfo.ipAddress
        val ip = if (i == 0 || !KlipperInstance.isWebServerRunning()) "127.0.0.1" else Formatter.formatIpAddress(i)
        val t = System.currentTimeMillis()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip:${WebService.PORT}/?t=$t"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun bindHeader(header: MainHeaderView) {
        val webTitle = when (Prefs.webFrontend) {
            Prefs.FRONTEND_FLUIDD -> getString(R.string.Fluidd)
            else -> getString(R.string.Mainsail)
        }
        val isWebRunning = KlipperInstance.isWebServerRunning()
        val webSubtitle = if (isWebRunning) {
            val wm = KlipperApp.INSTANCE.getSystemService(Context.WIFI_SERVICE) as WifiManager
            getString(R.string.IPInfo, Formatter.formatIpAddress(wm.connectionInfo.ipAddress), WebService.PORT)
        } else {
            getString(R.string.web_tile_offline)
        }
        header.bind(
            webTitle = webTitle,
            webSubtitle = webSubtitle,
            isWebEnabled = isWebRunning,
            instancesSubtitle = getString(R.string.Instances) + " • " + instances.size
        )
    }

    private fun animateNewOrEditLayout(visible: Boolean) {
        if (newOrEditAnimation != null) return

        if (visible) {
            resizeFrame.addForceNotMeasure(listView)
            newOrEditLayout.visibility = View.VISIBLE
            newOrEditLayout.alpha = 0f
        } else {
            resizeFrame.addForceNotMeasure(newOrEditLayout)
            listView.visibility = View.VISIBLE
            listView.alpha = 0f
        }

        newOrEditAnimation = SpringAnimation(FloatValueHolder(if (visible) 0f else 1f))
            .setMinimumVisibleChange(1 / 500f)
            .setSpring(SpringForce(if (visible) 1f else 0f)
                .setStiffness(850f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener { _, value, _ ->
                listView.alpha = 1f - value
                newOrEditLayout.alpha = value
            }
            .addEndListener { _, canceled, _, _ ->
                if (visible) {
                    listView.visibility = View.GONE
                    resizeFrame.removeForceNotMeasure(listView)
                    nameRow.requestFocus()
                } else {
                    newOrEditLayout.visibility = View.GONE
                    resizeFrame.removeForceNotMeasure(newOrEditLayout)
                    listView.getChildAt(1)?.requestFocus()
                    editInstance = null
                }
                newOrEditAnimation = null
            }
        newOrEditAnimation?.start()
    }

    private fun animateHomeView() {
        SpringAnimation(FloatValueHolder(0f))
            .setMinimumVisibleChange(1 / 256f)
            .setSpring(SpringForce(1f)
                .setStiffness(1000f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener { _, value, _ ->
                homeView.pivotX = homeView.width / 2f
                homeView.pivotY = homeView.height / 2f

                noPermsLayout.scaleX = ViewUtils.lerp(1f, 0.6f, value)
                noPermsLayout.scaleY = ViewUtils.lerp(1f, 0.6f, value)
                noPermsLayout.alpha = 1f - value

                homeView.scaleX = ViewUtils.lerp(0.6f, 1f, value)
                homeView.scaleY = ViewUtils.lerp(0.6f, 1f, value)
                homeView.alpha = value
            }
            .addEndListener { _, _, _, _ -> noPermsLayout.visibility = View.GONE }
            .also {
                homeView.visibility = View.VISIBLE
                it.start()
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            notificationsRow?.isChecked = true
        }
    }

    override fun onResume() {
        super.onResume()
        batteryRow.isChecked = PermissionsChecker.hasBatteryPerm()
        hideServicesChannelRow?.isChecked = PermissionsChecker.isNotificationsChannelHidden()
        brokenBySDCardRow?.isChecked = PermissionsChecker.isNotBrokenBySDCard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        if (intent != null && intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val prober = UsbSerialProber(KlipperProbeTable.getInstance())
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            for (drv in prober.findAllDrivers(manager)) {
                if (!manager.hasPermission(drv.device)) {
                    manager.requestPermission(drv.device,
                        PendingIntent.getBroadcast(this, 0,
                            Intent(UsbSerialManager.ACTION_ON_DEVICE_CONNECTED).setPackage(packageName),
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE))
                } else {
                    sendBroadcast(Intent(UsbSerialManager.ACTION_ON_DEVICE_CONNECTED)
                        .putExtra(UsbManager.EXTRA_DEVICE, drv.device)
                        .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)
                        .setPackage(packageName))
                }
            }
        }
    }

    private fun invalidateHomeProgress(progress: Float) {
        val posProgress = 0f
        for (i in refBadges.indices) {
            val j = refBadges.size - 1 - i
            val beb = 0.3f
            val pr = (maxOf(posProgress, beb * j) - beb * j) / (1f - beb * j)

            val badge = refBadges[i] ?: continue
            badge.setProgress(pr)

            val fX = -ViewUtils.dp(9) + badgesLayout.width -
                    badgesLayout.paddingLeft - badgesLayout.paddingRight -
                    ViewUtils.dp(22 + 18) * (i + 1) - ViewUtils.dp(8) * i
            val tX = 0f

            val fY = 0f
            val tY = ViewUtils.dp(92) + ViewUtils.dp(22 + 18 + 10) * i

            badge.translationX = ViewUtils.lerp(fX.toFloat(), tX, pr)
            badge.translationY = ViewUtils.lerp(fY, tY.toFloat(), pr)
        }
        titleView.translationX = 0f
        titleView.translationY = 0f

        logoView.scaleX = 1f
        logoView.scaleY = 1f
        logoView.translationX = 0f
        logoView.translationY = 0f

        val negProgress = minOf(0f, progress)
        listCardView.translationY = negProgress * ViewUtils.dp(92 + (22 + 18) * refBadges.size + 10 * (refBadges.size - 1))
        listCardView.alpha = 1f + negProgress

        preferencesView.setProgress(-negProgress)

        if (isTV) {
            if (progress >= 0 && preferencesView.isFocusable) {
                preferencesView.isFocusable = false
                preferencesView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            } else if (progress < 0 && !preferencesView.isFocusable) {
                preferencesView.isFocusable = true
                preferencesView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }

            if (progress <= 0 && badgesLayout.isFocusable) {
                badgesLayout.isFocusable = false
                badgesLayout.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            } else if (progress > 0 && !badgesLayout.isFocusable) {
                badgesLayout.isFocusable = true
                badgesLayout.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        }
    }
}
