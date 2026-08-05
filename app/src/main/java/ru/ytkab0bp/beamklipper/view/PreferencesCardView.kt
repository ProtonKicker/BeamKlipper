package ru.ytkab0bp.beamklipper.view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.ytkab0bp.beamklipper.*
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.beamklipper.view.preferences.*
import java.io.File

class PreferencesCardView(context: Context) : FrameLayout(context) {
    companion object {
        private const val MIN_HEIGHT_DP = 64
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SWITCH = 1
        private const val VIEW_TYPE_PREFERENCE = 2
        private const val VIEW_TYPE_PREF_VALUE = 3
        private const val VIEW_TYPE_SEGMENT_CHOICE = 5
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimmPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val header: LinearLayout
    private val title: TextView
    private var progress = 0f
    val listView: RecyclerView
    private val path = Path()
    private lateinit var adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>

    private var itemsCount = 0
    private var generalHeaderRow = 0
    private var systemSettingsRow = 0
    private var cameraHeaderRow = 0
    private var cameraEnabledRow = 0
    private var frontendRow = 0
    private var firmwareRow = 0
    private var languageRow = 0
    private var usbHeaderRow = 0
    private var usbNamingRow = 0
    private var listUsbRow = 0
    private var otherHeaderRow = 0
    private var getMCUFirmwareRow = 0
    private var doneButtonRow = 0

    private val doneButton: MaterialCardView

    init {
        dimmPaint.color = Color.BLACK
        outlinePaint.style = Paint.Style.FILL
        outlinePaint.color = ViewUtils.resolveColor(context, R.attr.cardOutlineColor)
        bgPaint.color = ViewUtils.resolveColor(context, android.R.attr.windowBackground)

        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            strokeWidth = ViewUtils.dp(4f).toFloat()
            color = ViewUtils.resolveColor(context, R.attr.dividerColor)
        }
        header = object : LinearLayout(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = ViewUtils.dp(8) + paint.strokeWidth - ViewUtils.dp(32) * progress
                val len = ViewUtils.dp(32)
                canvas.drawLine(cx - len / 2f, cy, cx + len / 2f, cy, paint)
            }
        }.apply {
            setPadding(ViewUtils.dp(21), ViewUtils.dp(8), ViewUtils.dp(21), 0)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setWillNotDraw(false)
        }

        title = TextView(context).apply {
            setText(R.string.Settings)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorPrimary))
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ViewUtils.dp(4)
        })
        ll.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(72)))

        updateRows()
        listView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
            setPadding(0, 0, 0, ViewUtils.dp(140))
        }
        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v: View = when (viewType) {
                    VIEW_TYPE_HEADER -> PreferenceHeaderView(context)
                    VIEW_TYPE_SWITCH -> PreferenceSwitchView(context)
                    VIEW_TYPE_PREFERENCE -> PreferenceView(context)
                    VIEW_TYPE_PREF_VALUE -> PreferenceValueView(context)
                    VIEW_TYPE_SEGMENT_CHOICE -> SegmentChoiceView(context)
                    else -> PreferenceHeaderView(context)
                }
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> {
                        val h = holder.itemView as PreferenceHeaderView
                        h.setText(
                            when (position) {
                                usbHeaderRow -> R.string.USB
                                generalHeaderRow -> R.string.General
                                cameraHeaderRow -> R.string.Camera
                                otherHeaderRow -> R.string.Other
                                else -> 0
                            }
                        )
                    }
                    VIEW_TYPE_SWITCH -> {
                        val sw = holder.itemView as PreferenceSwitchView
                        if (position == cameraEnabledRow) {
                            sw.bind(
                                context.getString(R.string.EnableCamera),
                                context.getString(R.string.CameraDescription),
                                Prefs.isCameraEnabled,
                                R.drawable.ic_camera_outline_24
                            )
                            sw.setOnClickListener { v ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                    ContextCompat.checkSelfPermission(v.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.CAMERA), 0)
                                    return@setOnClickListener
                                }
                                sw.checked = !sw.checked
                                Prefs.isCameraEnabled = sw.checked
                                KlipperInstance.onCameraConfigChanged(sw.checked)
                            }
                        }
                    }
                    VIEW_TYPE_PREFERENCE -> {
                        val pref = holder.itemView as PreferenceView
                        when (position) {
                            listUsbRow -> {
                                pref.bind(
                                    context.getString(R.string.ListUSB),
                                    "Show all connected USB-to-serial adapters",
                                    R.drawable.ic_usb_outline_24
                                )
                                pref.setOnClickListener {
                                    val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                                    val list = mutableListOf<String>()
                                    for (dev in manager.deviceList.values) {
                                        val drv = KlipperProbeTable.getInstance().findDriver(dev)
                                        list.add(
                                            Integer.toHexString(dev.vendorId) + "/" + Integer.toHexString(dev.productId) +
                                                    " - " + dev.deviceName +
                                                    (if (drv != null) " - " + drv.name + "\n" +
                                                            File(KlipperApp.INSTANCE.filesDir, "serial/" + UsbSerialManager.getUID(dev)).absolutePath else "")
                                        )
                                    }
                                    val b = MaterialAlertDialogBuilder(context).setTitle(R.string.ListUSBTitle)
                                    if (list.isEmpty()) {
                                        b.setMessage(R.string.ListUSBNoDevices)
                                    } else {
                                        b.setItems(list.toTypedArray(), null)
                                    }
                                    b.setPositiveButton(android.R.string.ok, null).show()
                                }
                            }
                            systemSettingsRow -> {
                                pref.bind(
                                    context.getString(R.string.SystemSettings),
                                    "Open Android system settings",
                                    R.drawable.ic_settings_outline_24
                                )
                                pref.setOnClickListener {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                            getMCUFirmwareRow -> {
                                pref.bind(
                                    context.getString(R.string.OtherGetFirmware),
                                    "Pre-built Klipper firmware for MCUs",
                                    R.drawable.ic_chip_outline_24
                                )
                                pref.setOnClickListener {
                                    QRCodeAlertDialog(context, "https://github.com/utkabobr/klipper/releases/tag/prebuilt-v0.12.0").show()
                                }
                            }
                        }
                    }
                    VIEW_TYPE_SEGMENT_CHOICE -> {
                        val seg = holder.itemView as SegmentChoiceView
                        when (position) {
                            frontendRow -> {
                                seg.bind(
                                    title = context.getString(R.string.WebFrontend),
                                    subtitle = "Locally-hosted control interface for Klipper",
                                    options = listOf(
                                        context.getString(R.string.Fluidd),
                                        context.getString(R.string.Mainsail)
                                    ),
                                    value = frontendTitle(Prefs.webFrontend),
                                    icon = R.drawable.ic_globe_outline_28,
                                    optionIcons = listOf(
                                        R.drawable.ic_globe_outline_28,
                                        R.drawable.ic_grid_layout_outline_28
                                    )
                                ) { selected, idx ->
                                    Prefs.webFrontend = when (idx) {
                                        0 -> Prefs.FRONTEND_FLUIDD
                                        else -> Prefs.FRONTEND_MAINSAIL
                                    }
                                }
                            }
                            firmwareRow -> {
                                val klipperExists = File(KlipperApp.INSTANCE.filesDir, "klipper/klippy/klippy.py").exists()
                                val kalicoExists = File(KlipperApp.INSTANCE.filesDir, "kalico/klippy/klippy.py").exists()
                                seg.bind(
                                    title = context.getString(R.string.FirmwareEngine),
                                    subtitle = "Print engine backend that runs G-code",
                                    options = listOf(
                                        context.getString(R.string.Klipper),
                                        context.getString(R.string.Kalico)
                                    ),
                                    value = firmwareTitle(Prefs.engine),
                                    icon = R.drawable.ic_printer_outline_28,
                                    optionIcons = listOf(
                                        R.drawable.ic_printer_outline_28,
                                        R.drawable.ic_brain_outline_28
                                    )
                                ) { selected, idx ->
                                    val engine = if (idx == 0) Prefs.ENGINE_KLIPPER else Prefs.ENGINE_KALICO
                                    if (engine == Prefs.ENGINE_KALICO && !kalicoExists) {
                                        MaterialAlertDialogBuilder(context)
                                            .setTitle(R.string.Error)
                                            .setMessage(R.string.EngineNotBundled)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                        seg.post { adapter.notifyItemChanged(holder.adapterPosition) }
                                        return@bind
                                    }
                                    Prefs.engine = engine
                                    if (KlipperInstance.getInstances().any { inst -> inst.getState() == KlipperInstance.State.RUNNING }) {
                                        MaterialAlertDialogBuilder(context)
                                            .setTitle(R.string.FirmwareEngine)
                                            .setMessage(R.string.EngineRestartRequired)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                    }
                                }
                            }
                            languageRow -> {
                                seg.bind(
                                    title = context.getString(R.string.AppLanguage),
                                    subtitle = "Display language for the app",
                                    options = listOf(
                                        context.getString(R.string.LanguageSystem),
                                        context.getString(R.string.LanguageEnglish),
                                        context.getString(R.string.LanguageRussian),
                                        context.getString(R.string.LanguageChineseSimplified),
                                        context.getString(R.string.LanguageChineseTraditional)
                                    ),
                                    value = languageTitle(Prefs.appLanguage),
                                    icon = R.drawable.ic_language_outline_24,
                                    optionIcons = emptyList()
                                ) { selected, idx ->
                                    Prefs.appLanguage = when (idx) {
                                        0 -> Prefs.LANGUAGE_SYSTEM
                                        1 -> Prefs.LANGUAGE_ENGLISH
                                        2 -> Prefs.LANGUAGE_RUSSIAN
                                        3 -> Prefs.LANGUAGE_CHINESE_SIMPLIFIED
                                        else -> Prefs.LANGUAGE_CHINESE_TRADITIONAL
                                    }
                                    Prefs.applyAppLanguage()
                                    (context as? AppCompatActivity)?.recreate()
                                }
                            }
                            usbNamingRow -> {
                                seg.bind(
                                    title = context.getString(R.string.USBDeviceNaming),
                                    subtitle = "By path (/dev/ttyUSB0) recommended",
                                    options = listOf(
                                        context.getString(R.string.USBDeviceNamingByPath),
                                        context.getString(R.string.USBDeviceNamingByVidPid)
                                    ),
                                    value = if (Prefs.usbDeviceNaming == Prefs.USB_DEVICE_NAMING_BY_PATH)
                                        context.getString(R.string.USBDeviceNamingByPath)
                                    else
                                        context.getString(R.string.USBDeviceNamingByVidPid),
                                    icon = R.drawable.ic_usb_outline_24,
                                    optionIcons = listOf(
                                        R.drawable.ic_external_link_outline_24,
                                        R.drawable.ic_info_outline_24
                                    )
                                ) { _, idx ->
                                    Prefs.usbDeviceNaming = idx
                                }
                            }
                        }
                    }
                }
            }

            override fun getItemCount(): Int = itemsCount

            override fun getItemViewType(position: Int): Int {
                return when (position) {
                    cameraEnabledRow -> VIEW_TYPE_SWITCH
                    generalHeaderRow, cameraHeaderRow, usbHeaderRow, otherHeaderRow -> VIEW_TYPE_HEADER
                    listUsbRow, systemSettingsRow, getMCUFirmwareRow -> VIEW_TYPE_PREFERENCE
                    frontendRow, firmwareRow, languageRow, usbNamingRow -> VIEW_TYPE_SEGMENT_CHOICE
                    else -> 0
                }
            }
        }
        listView.adapter = adapter
        ll.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        doneButton = MaterialCardView(context).apply {
            radius = ViewUtils.dp(14f).toFloat()
            cardElevation = 0f
            strokeWidth = ViewUtils.dp(1)
            strokeColor = 0x00000000
            isClickable = true
            isFocusable = true
            setCardBackgroundColor(0xFF000000.toInt())
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = ViewUtils.dp(56)
            }
            val doneTv = TextView(context).apply {
                text = "Done"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
                setTextColor(-0x1)
                isAllCaps = true
                letterSpacing = 0.08f
                setPadding(ViewUtils.dp(20), ViewUtils.dp(4), ViewUtils.dp(20), ViewUtils.dp(4))
            }
            content.addView(doneTv)
            addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            foreground = ViewUtils.resolveDrawable(context, androidx.appcompat.R.attr.selectableItemBackground)
            ViewUtils.applyPressFeel(this, 0.98f, 4f)
            setOnClickListener {
                if (context is MainActivity) {
                    context.closePreferences()
                }
            }
        }
        val doneLP = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = ViewUtils.dp(20)
            rightMargin = ViewUtils.dp(20)
            bottomMargin = ViewUtils.dp(28)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        addView(ll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(doneButton, doneLP)

        setWillNotDraw(false)
        fitsSystemWindows = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        KlipperApp.EVENT_BUS.registerListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        KlipperApp.EVENT_BUS.unregisterListener(this)
    }

    private fun updateRows() {
        itemsCount = 0
        generalHeaderRow = itemsCount++
        systemSettingsRow = if (context is MainActivity && (context as MainActivity).isCurrentLauncher()) itemsCount++ else -1
        frontendRow = itemsCount++
        firmwareRow = itemsCount++
        languageRow = itemsCount++
        cameraHeaderRow = itemsCount++
        cameraEnabledRow = itemsCount++
        usbHeaderRow = itemsCount++
        usbNamingRow = itemsCount++
        listUsbRow = itemsCount++
        otherHeaderRow = itemsCount++
        getMCUFirmwareRow = itemsCount++
    }

    private fun frontendTitle(frontend: String): String {
        val resId = when (frontend) {
            Prefs.FRONTEND_FLUIDD -> R.string.Fluidd
            else -> R.string.Mainsail
        }
        return KlipperApp.INSTANCE.getString(resId)
    }

    private fun firmwareTitle(engine: String): String {
        return KlipperApp.INSTANCE.getString(
            if (engine == Prefs.ENGINE_KALICO) R.string.Kalico else R.string.Klipper
        )
    }

    private fun languageTitle(language: String): String {
        return KlipperApp.INSTANCE.getString(
            when (language) {
                Prefs.LANGUAGE_ENGLISH -> R.string.LanguageEnglish
                Prefs.LANGUAGE_RUSSIAN -> R.string.LanguageRussian
                Prefs.LANGUAGE_CHINESE_SIMPLIFIED -> R.string.LanguageChineseSimplified
                Prefs.LANGUAGE_CHINESE_TRADITIONAL -> R.string.LanguageChineseTraditional
                else -> R.string.LanguageSystem
            }
        )
    }

    override fun draw(canvas: Canvas) {
        val radius = (1f - progress) * ViewUtils.dp(32)
        path.rewind()
        path.addRoundRect(
            0f,
            ViewUtils.lerp(height - ViewUtils.dp(MIN_HEIGHT_DP) - paddingBottom.toFloat(), 0f, progress),
            width.toFloat(),
            height + radius,
            radius, radius,
            Path.Direction.CW
        )
        if (progress > 0) {
            canvas.save()
            canvas.clipPath(path, Region.Op.DIFFERENCE)
            dimmPaint.alpha = (0x33 * progress).toInt()
            canvas.drawPaint(dimmPaint)
            canvas.restore()
        }
        canvas.save()
        canvas.clipPath(path)
        val alpha = outlinePaint.alpha
        outlinePaint.alpha = ((1f - progress) * alpha).toInt()
        val stroke = outlinePaint.strokeWidth / 2f
        canvas.drawPaint(bgPaint)
        canvas.drawRoundRect(
            stroke,
            ViewUtils.lerp(height - ViewUtils.dp(MIN_HEIGHT_DP) - paddingBottom + stroke, 0f, progress),
            width - stroke,
            height + radius,
            radius, radius,
            outlinePaint
        )
        outlinePaint.alpha = alpha
        super.draw(canvas)
        canvas.restore()
    }

    private fun invalidateProgress() {
        title.scaleX = ViewUtils.lerp(1f, 0.92f, progress)
        title.scaleY = ViewUtils.lerp(1f, 0.92f, progress)
        title.translationX = ViewUtils.lerp(0f, ViewUtils.dp(8).toFloat(), progress)
        header.alpha = 1f - progress
        header.invalidate()
        listView.alpha = progress
        doneButton.alpha = progress
        doneButton.scaleX = ViewUtils.lerp(0.94f, 1f, progress)
        doneButton.scaleY = ViewUtils.lerp(0.94f, 1f, progress)
        doneButton.translationY = ViewUtils.lerp(ViewUtils.dp(24).toFloat(), 0f, progress)
        for (i in 0 until childCount) {
            getChildAt(i).translationY = ViewUtils.lerp(
                height - ViewUtils.dp(MIN_HEIGHT_DP) - paddingTop - paddingBottom.toFloat(), 0f, progress
            )
        }

        if (context is MainActivity) {
            val w = (context as MainActivity).window
            w.navigationBarColor = ColorUtils.blendARGB(
                ViewUtils.resolveColor(context, R.attr.navbarColor),
                ViewUtils.resolveColor(context, android.R.attr.windowBackground),
                progress
            )
        }
    }

    fun setProgress(progress: Float) {
        this.progress = progress
        invalidateProgress()
        invalidate()
    }
}
