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
        private const val VIEW_TYPE_SECTION = 1
        private const val VIEW_TYPE_EMPTY = 2
        private const val VIEW_TYPE_INSTANCE = 3
        private val NOTIFY_LIVE = Any()
    }

    private lateinit var fl: FrameLayout
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
    private lateinit var newOrEditContinue: com.google.android.material.card.MaterialCardView
    private lateinit var newOrEditNameEt: EditText
    private lateinit var configPillLabel: TextView
    private lateinit var configPillRow: com.google.android.material.card.MaterialCardView
    private var selectedIconOrdinal: Int = 0
    private lateinit var iconTiles: Array<com.google.android.material.card.MaterialCardView>
    private lateinit var tileOrdinals: IntArray
    private lateinit var autostartTitle: TextView
    private lateinit var autostartSubtitle: TextView
    private lateinit var autostartSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var editFolderTitle: TextView
    private lateinit var editFolderSubtitle: TextView
    private lateinit var editFolderRow: com.google.android.material.card.MaterialCardView
    private lateinit var fab: MaterialCardView

    private lateinit var preferencesView: PreferencesCardView

    private lateinit var noPermsLayout: MaterialCardView
    private var responsiveLayoutToken: Int = -1
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

        fl = FrameLayout(this)
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
            setCardBackgroundColor(0x00000000)
            cardElevation = 0f
            radius = 0f
            strokeWidth = 0
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
            private fun instanceOffset(): Int = if (visibleInstances.isEmpty()) 2 else 2

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v: View = when (viewType) {
                    VIEW_TYPE_HEADER -> {
                        MainHeaderView(this@MainActivity).apply {
                            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        }
                    }
                    VIEW_TYPE_SECTION -> SectionHeaderView(this@MainActivity)
                    VIEW_TYPE_EMPTY -> EmptyStateView(this@MainActivity)
                    VIEW_TYPE_INSTANCE -> KlipperInstanceView(this@MainActivity)
                    else -> throw IllegalStateException("Unknown viewType: $viewType")
                }
                return object : RecyclerView.ViewHolder(v) {}
            }

            @Suppress("UNCHECKED_CAST")
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
                if (payloads.contains(NOTIFY_LIVE)) {
                    val view = holder.itemView as KlipperInstanceView
                    view.bind(visibleInstances[position - instanceOffset()])
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
                    VIEW_TYPE_SECTION -> {
                        val sec = holder.itemView as SectionHeaderView
                        val n = visibleInstances.size
                        sec.title.text = getString(R.string.Instances).uppercase(Locale.ROOT)
                        sec.setCount(n)
                        sec.visibility = View.VISIBLE
                    }
                    VIEW_TYPE_EMPTY -> {
                        val empty = holder.itemView as EmptyStateView
                        empty.setOnClickListener { openNewInstanceSheet() }
                        ViewUtils.applyPressFeel(empty, 0.99f, 3f)
                    }
                    VIEW_TYPE_INSTANCE -> {
                        val offset = instanceOffset()
                        val view = holder.itemView as KlipperInstanceView
                        view.bind(visibleInstances[position - offset])
                        view.setOnClickListener {
                            val inst = visibleInstances[position - offset]
                            newOrEditTitle.setText(R.string.EditInstance)
                            editInstance = inst
                            editFolderRow.visibility = View.VISIBLE
                            autostartSwitch.isChecked = inst.autostart
                            newOrEditNameEt.setText(inst.name)
                            newOrEditNameEt.setSelection(newOrEditNameEt.text?.length ?: 0)
                            configPillRow.visibility = View.GONE
                            selectedIconOrdinal = inst.icon.ordinal
                            refreshTileSelection()
                            val saveLbl = (newOrEditContinue.getChildAt(0) as? TextView)
                            saveLbl?.setText(R.string.SaveProfile)
                            animateNewOrEditLayout(true)
                        }
                        view.setOnLongClickListener {
                            val inst = visibleInstances[position - offset]
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
                return when {
                    position == 0 -> VIEW_TYPE_HEADER
                    visibleInstances.isEmpty() && position == 1 -> VIEW_TYPE_EMPTY
                    position == 1 -> VIEW_TYPE_SECTION
                    else -> VIEW_TYPE_INSTANCE
                }
            }

            override fun getItemCount(): Int {
                return if (visibleInstances.isEmpty()) 2 else 2 + visibleInstances.size
            }
        }
        listView.adapter = instancesAdapter
        resizeFrame.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val gridIcons: Array<InstanceIcon> = InstanceIcon.values().copyOfRange(0, 8)
        tileOrdinals = IntArray(8) { gridIcons[it].ordinal }
        fun fgFor(icon: InstanceIcon): Int = 0xFF000000.toInt()

        newOrEditLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ViewUtils.dp(20), ViewUtils.dp(8), ViewUtils.dp(20), ViewUtils.dp(16))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val handleWrap = FrameLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, ViewUtils.dp(6), 0, ViewUtils.dp(8))
        }
        val handleView = View(this@MainActivity).apply {
            background = ViewUtils.makeRoundRectDrawable(0xFF000000.toInt(), ViewUtils.dp(999))
        }
        handleWrap.addView(handleView, FrameLayout.LayoutParams(ViewUtils.dp(40), ViewUtils.dp(6), Gravity.CENTER_HORIZONTAL))
        newOrEditLayout.addView(handleWrap)

        val headerRow = FrameLayout(this@MainActivity).apply {
            setPadding(0, ViewUtils.dp(4), 0, ViewUtils.dp(16))
        }
        newOrEditTitle = TextView(this@MainActivity).apply {
            setTextColor(0xFF000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, ViewUtils.dp(8), ViewUtils.dp(56), ViewUtils.dp(8))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(newOrEditTitle)
        val closeBtn = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_cross_outline_28)
            setColorFilter(0xFF8A8F98.toInt())
            setPadding(ViewUtils.dp(10), ViewUtils.dp(10), ViewUtils.dp(10), ViewUtils.dp(10))
            background = ViewUtils.resolveDrawable(this@MainActivity, androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
            isClickable = true; isFocusable = true
            setOnClickListener { animateNewOrEditLayout(false) }
            layoutParams = FrameLayout.LayoutParams(ViewUtils.dp(44), ViewUtils.dp(44)).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        }
        headerRow.addView(closeBtn)
        newOrEditLayout.addView(headerRow)

        fun sectionLabel(@Suppress("SameParameterValue") s: String): TextView {
            return TextView(this@MainActivity).apply {
                text = s
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                letterSpacing = 0.12f
                setTextColor(0xFF000000.toInt())
                isAllCaps = true
                setPadding(ViewUtils.dp(4), ViewUtils.dp(16), ViewUtils.dp(4), ViewUtils.dp(12))
            }
        }
        fun wrapMargins(v: View): View {
            val wrap = FrameLayout(this@MainActivity)
            wrap.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            wrap.setPadding(ViewUtils.dp(4), 0, ViewUtils.dp(4), 0)
            return wrap
        }

        newOrEditLayout.addView(sectionLabel(getString(R.string.LabelName)))
        val colorSurfaceContainerHigh = ViewUtils.resolveColor(this@MainActivity, R.attr.colorSurfaceContainerHigh)
        newOrEditNameEt = EditText(this@MainActivity).apply {
            background = ViewUtils.makeRoundRectDrawable(colorSurfaceContainerHigh, ViewUtils.dp(22))
            setPadding(ViewUtils.dp(20), ViewUtils.dp(18), ViewUtils.dp(20), ViewUtils.dp(18))
            setHint(R.string.NewProfileHint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
            setHintTextColor(0xFF8A8F98.toInt())
            minHeight = ViewUtils.dp(60)
            gravity = Gravity.CENTER_VERTICAL
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isSingleLine = true
        }
        newOrEditLayout.addView(wrapMargins(newOrEditNameEt))

        newOrEditLayout.addView(sectionLabel(getString(R.string.KlipperConfigTemplate)))
        configPillRow = MaterialCardView(this@MainActivity).apply {
            radius = ViewUtils.dp(22).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(colorSurfaceContainerHigh)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val config = File(KlipperApp.INSTANCE.filesDir, "klipper/config")
                val filesList = config.listFiles()?.map { it.name }?.sorted() ?: emptyList()
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.InstanceConfig)
                    .setItems(filesList.toTypedArray()) { _, which ->
                        configPillLabel.setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
                        configPillLabel.text = filesList[which]
                    }
                    .show()
            }
            foreground = ViewUtils.resolveDrawable(this@MainActivity, androidx.appcompat.R.attr.selectableItemBackground)
            val contentLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(60))
            val contentRow = FrameLayout(this@MainActivity).apply {
                setPadding(ViewUtils.dp(20), 0, ViewUtils.dp(20), 0)
                configPillLabel = TextView(this@MainActivity).apply {
                    setTextColor(ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setCompoundDrawables(null, null, null, null)
                }
                addView(configPillLabel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = ViewUtils.dp(32)
                })
                val chevron = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_chevron_down_24)
                    layoutParams = FrameLayout.LayoutParams(ViewUtils.dp(24), ViewUtils.dp(24)).apply {
                        gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    }
                }
                addView(chevron)
                layoutParams = contentLp
            }
            addView(contentRow)
            ViewUtils.applyPressFeel(this, 0.992f, 2f)
        }
        newOrEditLayout.addView(wrapMargins(configPillRow))

        newOrEditLayout.addView(sectionLabel(getString(R.string.LabelIcon)))
        val iconGrid = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val neutralTileBg = 0xFFFFFFFF.toInt()
        iconTiles = Array(8) { idx ->
            MaterialCardView(this@MainActivity).apply {
                radius = ViewUtils.dp(16).toFloat()
                cardElevation = 0f
                strokeWidth = ViewUtils.dp(1)
                strokeColor = 0x1A000000
                setCardBackgroundColor(neutralTileBg)
                val iv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(gridIcons[idx].drawable)
                    setColorFilter(fgFor(gridIcons[idx]))
                    setPadding(ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12))
                }
                addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                isClickable = true; isFocusable = true
                foreground = ViewUtils.resolveDrawable(this@MainActivity, androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
                layoutParams = LinearLayout.LayoutParams(0, ViewUtils.dp(64), 1f).apply {
                    marginStart = ViewUtils.dp(6); marginEnd = ViewUtils.dp(6)
                }
                ViewUtils.applyPressFeel(this, 0.95f, 2f)
                setOnClickListener {
                    selectedIconOrdinal = gridIcons[idx].ordinal
                    refreshTileSelection()
                }
            }
        }
        val rowParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(64)).apply {
            topMargin = ViewUtils.dp(4); bottomMargin = ViewUtils.dp(4)
        }
        for (r in 0 until 2) {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(ViewUtils.dp(2), 0, ViewUtils.dp(2), 0)
                layoutParams = rowParams
            }
            for (c in 0 until 4) {
                row.addView(iconTiles[r * 4 + c])
            }
            iconGrid.addView(row)
        }
        refreshTileSelection()
        newOrEditLayout.addView(iconGrid)

        val spacing24 = View(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(8))
        }
        newOrEditLayout.addView(spacing24)

        editFolderRow = MaterialCardView(this@MainActivity).apply {
            radius = ViewUtils.dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = ViewUtils.dp(1)
            strokeColor = 0x1A000000
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ViewUtils.dp(4); rightMargin = ViewUtils.dp(4); topMargin = ViewUtils.dp(12)
            }
            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ViewUtils.dp(18), ViewUtils.dp(16), ViewUtils.dp(14), ViewUtils.dp(16))
            }
            val textCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            editFolderTitle = TextView(this@MainActivity).apply {
                text = getString(R.string.ProfileFolder)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR)
                setTextColor(0xFF000000.toInt())
                includeFontPadding = false
            }
            textCol.addView(editFolderTitle)
            editFolderSubtitle = TextView(this@MainActivity).apply {
                text = getString(R.string.EditConfigFiles)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFF666666.toInt())
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = ViewUtils.dp(4)
                }
            }
            textCol.addView(editFolderSubtitle)
            inner.addView(textCol)
            inner.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_folder_outline_28)
                setColorFilter(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)).apply {
                    marginEnd = ViewUtils.dp(4)
                }
            }, LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)).apply {
                marginEnd = ViewUtils.dp(4)
            })
            inner.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right_28)
                setColorFilter(0xFF98A2B3.toInt())
            })
            addView(inner)
            foreground = ViewUtils.resolveDrawable(this@MainActivity, androidx.appcompat.R.attr.selectableItemBackground)
            ViewUtils.applyPressFeel(this, 0.992f, 2f)
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
        newOrEditLayout.addView(editFolderRow)

        val autoRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ViewUtils.dp(4); rightMargin = ViewUtils.dp(4); topMargin = ViewUtils.dp(14); bottomMargin = ViewUtils.dp(18)
            }
        }
        val autoTextCol = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        autostartTitle = TextView(this@MainActivity).apply {
            text = getString(R.string.Autostart)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_REGULAR)
            setTextColor(0xFF000000.toInt())
            includeFontPadding = false
        }
        autoTextCol.addView(autostartTitle)
        autostartSubtitle = TextView(this@MainActivity).apply {
            text = getString(R.string.AutostartSubtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF666666.toInt())
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ViewUtils.dp(4)
            }
        }
        autoTextCol.addView(autostartSubtitle)
        autoRow.addView(autoTextCol)
        autostartSwitch = com.google.android.material.switchmaterial.SwitchMaterial(this@MainActivity).apply {
            setPadding(0, 0, 0, 0)
        }
        autoRow.addView(autostartSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        newOrEditLayout.addView(autoRow)

        val primaryColor = 0xFF000000.toInt()
        newOrEditContinue = MaterialCardView(this@MainActivity).apply {
            radius = ViewUtils.dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = ViewUtils.dp(1)
            strokeColor = 0x00000000
            isClickable = true; isFocusable = true
            setCardBackgroundColor(primaryColor)
            val lbl = TextView(this@MainActivity).apply {
                setText(R.string.SaveProfile)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                setTextColor(-0x1)
                gravity = Gravity.CENTER
                isAllCaps = true
                letterSpacing = 0.08f
            }
            addView(lbl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(58)))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(64)).apply {
                leftMargin = ViewUtils.dp(4); rightMargin = ViewUtils.dp(4)
            }
            foreground = ViewUtils.resolveDrawable(this@MainActivity, androidx.appcompat.R.attr.selectableItemBackground)
            ViewUtils.applyPressFeel(this, 0.99f, 4f)
            var saving = false
            setOnClickListener {
                if (saving) return@setOnClickListener
                val nameText = newOrEditNameEt.text.toString().trim()
                val cfgText = configPillLabel.text.toString().trim()
                val selectedIcon = InstanceIcon.values()[selectedIconOrdinal]
                if (TextUtils.isEmpty(nameText)) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.Error)
                        .setMessage(R.string.ErrorNameEmpty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setOnClickListener
                }

                if (editInstance != null) {
                    val editing = editInstance!!
                    editing.name = nameText
                    editing.autostart = autostartSwitch.isChecked
                    editing.icon = selectedIcon
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

                if (TextUtils.isEmpty(cfgText)) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.Error)
                        .setMessage(R.string.ErrorConfigEmpty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setOnClickListener
                }

                val inst = KlipperInstance().apply {
                    id = UUID.randomUUID().toString()
                    name = nameText
                    autostart = autostartSwitch.isChecked
                    icon = selectedIcon
                }
                val cfg = File(inst.publicDirectory, "config/printer.cfg")
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

        val fabSize = ViewUtils.dp(56)
        val fabPrimary = 0xFF000000.toInt()
        fab = MaterialCardView(this@MainActivity).apply {
            radius = ViewUtils.dp(18).toFloat()
            cardElevation = ViewUtils.dp(6).toFloat()
            strokeWidth = 0
            setCardBackgroundColor(fabPrimary)
            isClickable = true; isFocusable = true
            val wrap = FrameLayout(this@MainActivity)
            val iv = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_add_plus_28)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setColorFilter(-0x1)
            }
            wrap.addView(iv, FrameLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28), Gravity.CENTER))
            addView(wrap, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            foreground = ViewUtils.resolveDrawable(this@MainActivity, androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
            ViewUtils.applyPressFeel(this, 0.9f, 8f)
            setOnClickListener { openNewInstanceSheet() }
        }
        homeView.addView(fab, FrameLayout.LayoutParams(fabSize, fabSize, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = ViewUtils.dp(24)
            bottomMargin = ViewUtils.dp(24)
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
        fl.addOnLayoutChangeListener { _, _, _, _, _, oldL, oldT, oldR, oldB ->
            val w = fl.width
            val h = fl.height
            val token = (w shl 16) or h
            if (token != responsiveLayoutToken) {
                responsiveLayoutToken = token
                applyResponsiveLayout()
            }
        }
        applyResponsiveLayout()

        processIntent(intent)
        instances = ArrayList(KlipperInstance.getInstances())
        applyFilter()
        KlipperApp.EVENT_BUS.registerListener(this)

        if (Prefs.getLastCommit() != BuildConfig.COMMIT && KlipperApp.hasUpdateInfo) {
            Prefs.setLastCommit()
            ChangeLogBottomSheet(this@MainActivity).show()
        }

        if (intent.getBooleanExtra("open_settings", false)) {
            preferencesView.postDelayed({
                homeView.animateTo(-1f)
            }, 300)
        }
        if (intent.getBooleanExtra("open_new", false)) {
            resizeFrame.postDelayed({
                openNewInstanceSheet()
            }, 350)
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

    fun closePreferences() {
        homeView.animateTo(0f)
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
        listView.adapter?.notifyItemChanged(0)
    }

    @EventHandler(runOnMainThread = true)
    fun onFrontendChanged(e: WebFrontendChangedEvent) {
        listView.adapter?.notifyItemChanged(0)
    }

    @EventHandler(runOnMainThread = true)
    fun onEngineChanged(e: EngineChangedEvent) {
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
            listView.adapter?.notifyItemChanged(idx + 2, NOTIFY_LIVE)
        }
    }

    private fun applyFilter() {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        visibleInstances = if (q.isEmpty()) instances else instances.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        listView.adapter?.notifyDataSetChanged()
    }

    private fun openNewInstanceSheet() {
        newOrEditTitle.setText(R.string.NewPrinterProfile)
        editInstance = null
        editFolderRow.visibility = View.GONE
        autostartSwitch.isChecked = false
        newOrEditNameEt.text = null
        newOrEditNameEt.clearFocus()
        val filesDir = File(KlipperApp.INSTANCE.filesDir, "klipper/config")
        val filesList = filesDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        val defaultCfg = filesList.firstOrNull { it.contains("ender", ignoreCase = true) }
            ?: filesList.firstOrNull().orEmpty()
        configPillLabel.setTextColor(if (defaultCfg.isEmpty()) 0xFF8A8F98.toInt() else ViewUtils.resolveColor(this@MainActivity, android.R.attr.textColorPrimary))
        configPillLabel.text = defaultCfg.ifEmpty { getString(R.string.InstanceConfig) }
        configPillRow.visibility = View.VISIBLE
        selectedIconOrdinal = 0
        refreshTileSelection()
        val saveLbl = (newOrEditContinue.getChildAt(0) as? TextView)
        saveLbl?.setText(R.string.CreateProfile)
        newOrEditNameEt.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(newOrEditNameEt, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        animateNewOrEditLayout(true)
    }

    private fun refreshTileSelection() {
        val selOrd = selectedIconOrdinal
        val selectedBg = 0xFF000000.toInt()
        val selectedStroke = 0xFF000000.toInt()
        val neutralBg = 0xFFFFFFFF.toInt()
        val neutralStroke = 0x1A000000.toInt()
        for (i in 0 until 8) {
            val tile = iconTiles[i]
            val selected = tileOrdinals[i] == selOrd
            tile.setCardBackgroundColor(if (selected) selectedBg else neutralBg)
            tile.strokeColor = if (selected) selectedStroke else neutralStroke
            tile.cardElevation = 0f
            val iv = tile.getChildAt(0) as? ImageView
            iv?.setColorFilter(if (selected) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
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
        val isWebRunning = KlipperInstance.isWebServerRunning()
        val webUrlStr = if (isWebRunning) {
            val wm = KlipperApp.INSTANCE.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            "http://$ip:${ru.ytkab0bp.beamklipper.service.WebService.PORT}/"
        } else {
            ""
        }
        val activeCount = instances.count { it.getState() == KlipperInstance.State.RUNNING }
        header.bind(
            webUrlString = webUrlStr,
            isWebRunning = isWebRunning,
            activeCount = activeCount,
            instancesTotal = instances.size
        )
    }

    private fun applyResponsiveLayout() {
        val ctx = this@MainActivity
        val sw = ViewUtils.screenWidthDp(ctx)
        val sh = ViewUtils.screenHeightDp(ctx)
        val landscape = ViewUtils.isLandscape(ctx)
        val wide = ViewUtils.isWideScreen(ctx)

        val listCardMaxWidth = when {
            wide && landscape -> 640
            wide -> 560
            sw >= 420 -> 440
            else -> Int.MAX_VALUE
        }
        if (listCardMaxWidth < Int.MAX_VALUE) {
            ViewUtils.applyMaxWidth(listCardView, listCardMaxWidth, Gravity.CENTER, 16)
        } else {
            val lp = listCardView.layoutParams as FrameLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            val baseSide = if (sw < 360) 12 else 21
            lp.leftMargin = ViewUtils.dp(baseSide)
            lp.rightMargin = ViewUtils.dp(baseSide)
            lp.gravity = Gravity.CENTER
            listCardView.layoutParams = lp
        }

        val noPermsMax = if (wide) 520 else Int.MAX_VALUE
        if (noPermsMax < Int.MAX_VALUE) {
            ViewUtils.applyMaxWidth(noPermsLayout, noPermsMax, Gravity.CENTER, 16)
        } else {
            val lp = noPermsLayout.layoutParams as FrameLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.CENTER
            noPermsLayout.layoutParams = lp
        }

        val prefMax = when {
            wide && landscape -> 620
            wide -> 540
            else -> Int.MAX_VALUE
        }
        if (prefMax < Int.MAX_VALUE) {
            ViewUtils.applyMaxWidth(preferencesView, prefMax, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 16)
        } else {
            val lp = preferencesView.layoutParams as FrameLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            preferencesView.layoutParams = lp
        }

        val topBarsMargin = if (landscape || sh < 640) {
            40
        } else if (sh < 750) {
            56
        } else {
            64
        }
        val bottomMargin = if (landscape || sh < 640) {
            48
        } else {
            72
        }
        val lp = listCardView.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = ViewUtils.dp(topBarsMargin)
        lp.bottomMargin = ViewUtils.dp(bottomMargin)
        listCardView.layoutParams = lp
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
                    newOrEditNameEt.requestFocus()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        responsiveLayoutToken = -1
        applyResponsiveLayout()
    }

    override fun onResume() {
        super.onResume()
        applyResponsiveLayout()
        batteryRow.isChecked = PermissionsChecker.hasBatteryPerm()
        hideServicesChannelRow?.isChecked = PermissionsChecker.isNotificationsChannelHidden()
        brokenBySDCardRow?.isChecked = PermissionsChecker.isNotBrokenBySDCard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        if (intent != null) {
            if (intent.getBooleanExtra("open_settings", false)) {
                preferencesView.postDelayed({
                    homeView.animateTo(-1f)
                }, 300)
            }
            if (intent.getBooleanExtra("open_new", false)) {
                resizeFrame.postDelayed({
                    openNewInstanceSheet()
                }, 350)
            }
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
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
