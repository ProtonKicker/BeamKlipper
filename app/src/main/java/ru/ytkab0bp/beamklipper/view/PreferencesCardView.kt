package ru.ytkab0bp.beamklipper.view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var frontendRow = 0
    private var firmwareRow = 0
    private var languageRow = 0
    private var cameraHeaderRow = 0
    private var cameraEnabledRow = 0
    private var usbHeaderRow = 0
    private var usbNamingRow = 0
    private var listUsbRow = 0
    private var otherHeaderRow = 0
    private var getMCUFirmwareRow = 0

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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(ViewUtils.resolveColor(context, android.R.attr.textColorSecondary))
            typeface = ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM)
            gravity = Gravity.CENTER
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ll.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(64)))

        updateRows()
        listView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v: android.view.View = when (viewType) {
                    VIEW_TYPE_HEADER -> PreferenceHeaderView(context)
                    VIEW_TYPE_SWITCH -> PreferenceSwitchView(context)
                    VIEW_TYPE_PREFERENCE -> PreferenceView(context)
                    VIEW_TYPE_PREF_VALUE -> PreferenceValueView(context)
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
                                cameraHeaderRow -> R.string.Camera
                                usbHeaderRow -> R.string.USB
                                generalHeaderRow -> R.string.General
                                otherHeaderRow -> R.string.Other
                                else -> 0
                            }
                        )
                    }
                    VIEW_TYPE_SWITCH -> {
                        val sw = holder.itemView as PreferenceSwitchView
                        if (position == cameraEnabledRow) {
                            sw.bind(context.getString(R.string.EnableCamera), null, Prefs.isCameraEnabled)
                            sw.setOnClickListener { v ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                    ContextCompat.checkSelfPermission(v.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.CAMERA), 0)
                                    return@setOnClickListener
                                }
                                sw.isChecked = !sw.isChecked
                                Prefs.isCameraEnabled = sw.isChecked
                                KlipperInstance.onCameraConfigChanged(sw.isChecked)
                            }
                        }
                    }
                    VIEW_TYPE_PREFERENCE -> {
                        val pref = holder.itemView as PreferenceView
                        when (position) {
                            listUsbRow -> {
                                pref.bind(context.getString(R.string.ListUSB), null)
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
                                pref.bind(context.getString(R.string.SystemSettings), null)
                                pref.setOnClickListener {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                            getMCUFirmwareRow -> {
                                pref.bind(context.getString(R.string.OtherGetFirmware), null)
                                pref.setOnClickListener {
                                    QRCodeAlertDialog(context, "https://github.com/utkabobr/klipper/releases/tag/prebuilt-v0.12.0").show()
                                }
                            }
                        }
                    }
                    VIEW_TYPE_PREF_VALUE -> {
                        val v = holder.itemView as PreferenceValueView
                        when (position) {
                            usbNamingRow -> {
                                v.bind(
                                    KlipperApp.INSTANCE.getString(R.string.USBDeviceNaming),
                                    KlipperApp.INSTANCE.getString(
                                        if (Prefs.usbDeviceNaming == Prefs.USB_DEVICE_NAMING_BY_PATH) R.string.USBDeviceNamingByPath
                                        else R.string.USBDeviceNamingByVidPid
                                    )
                                )
                                v.setOnClickListener {
                                    MaterialAlertDialogBuilder(it.context)
                                        .setTitle(R.string.USBDeviceNaming)
                                        .setItems(arrayOf(
                                            KlipperApp.INSTANCE.getString(R.string.USBDeviceNamingByPath),
                                            KlipperApp.INSTANCE.getString(R.string.USBDeviceNamingByVidPid)
                                        ), DialogInterface.OnClickListener { dialog, which ->
                                            Prefs.usbDeviceNaming = which
                                            adapter.notifyItemChanged(holder.adapterPosition)
                                        })
                                        .show()
                                }
                            }
                            frontendRow -> {
                                v.bind(
                                    KlipperApp.INSTANCE.getString(R.string.WebFrontend),
                                    frontendTitle(Prefs.webFrontend)
                                )
                                v.setOnClickListener {
                                    MaterialAlertDialogBuilder(it.context)
                                        .setTitle(R.string.WebFrontend)
                                        .setItems(arrayOf(
                                            KlipperApp.INSTANCE.getString(R.string.Fluidd),
                                            KlipperApp.INSTANCE.getString(R.string.Mainsail)
                                        ), DialogInterface.OnClickListener { dialog, which ->
                                            Prefs.webFrontend = when (which) {
                                                0 -> Prefs.FRONTEND_FLUIDD
                                                else -> Prefs.FRONTEND_MAINSAIL
                                            }
                                            adapter.notifyItemChanged(holder.adapterPosition)
                                        })
                                        .show()
                                }
                            }
                            firmwareRow -> {
                                v.bind(
                                    KlipperApp.INSTANCE.getString(R.string.FirmwareEngine),
                                    firmwareTitle(Prefs.engine)
                                )
                                v.setOnClickListener {
                                    MaterialAlertDialogBuilder(it.context)
                                        .setTitle(R.string.FirmwareEngine)
                                        .setItems(
                                            arrayOf(
                                                KlipperApp.INSTANCE.getString(R.string.Klipper),
                                                KlipperApp.INSTANCE.getString(R.string.Kalico)
                                            )
                                        ) { _, which ->
                                            val engine = if (which == 0) Prefs.ENGINE_KLIPPER else Prefs.ENGINE_KALICO
                                            if (engine == Prefs.ENGINE_KALICO &&
                                                !File(KlipperApp.INSTANCE.filesDir, "kalico/klippy/klippy.py").exists()
                                            ) {
                                                MaterialAlertDialogBuilder(it.context)
                                                    .setTitle(R.string.Error)
                                                    .setMessage(R.string.EngineNotBundled)
                                                    .setPositiveButton(android.R.string.ok, null)
                                                    .show()
                                                return@setItems
                                            }

                                            Prefs.engine = engine
                                            adapter.notifyItemChanged(holder.adapterPosition)
                                            if (KlipperInstance.getInstances().any { inst -> inst.getState() == KlipperInstance.State.RUNNING }) {
                                                MaterialAlertDialogBuilder(it.context)
                                                    .setTitle(R.string.FirmwareEngine)
                                                    .setMessage(R.string.EngineRestartRequired)
                                                    .setPositiveButton(android.R.string.ok, null)
                                                    .show()
                                            }
                                        }
                                        .show()
                                }
                            }
                            languageRow -> {
                                v.bind(
                                    KlipperApp.INSTANCE.getString(R.string.AppLanguage),
                                    languageTitle(Prefs.appLanguage)
                                )
                                v.setOnClickListener {
                                    MaterialAlertDialogBuilder(it.context)
                                        .setTitle(R.string.AppLanguage)
                                        .setItems(
                                            arrayOf(
                                                KlipperApp.INSTANCE.getString(R.string.LanguageSystem),
                                                KlipperApp.INSTANCE.getString(R.string.LanguageEnglish),
                                                KlipperApp.INSTANCE.getString(R.string.LanguageRussian),
                                                KlipperApp.INSTANCE.getString(R.string.LanguageChineseSimplified),
                                                KlipperApp.INSTANCE.getString(R.string.LanguageChineseTraditional)
                                            )
                                        ) { _, which ->
                                            Prefs.appLanguage = when (which) {
                                                0 -> Prefs.LANGUAGE_SYSTEM
                                                1 -> Prefs.LANGUAGE_ENGLISH
                                                2 -> Prefs.LANGUAGE_RUSSIAN
                                                3 -> Prefs.LANGUAGE_CHINESE_SIMPLIFIED
                                                else -> Prefs.LANGUAGE_CHINESE_TRADITIONAL
                                            }
                                            Prefs.applyAppLanguage()
                                            adapter.notifyItemChanged(holder.adapterPosition)
                                            (it.context as? AppCompatActivity)?.recreate()
                                        }
                                        .show()
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
                    cameraHeaderRow, usbHeaderRow, generalHeaderRow, otherHeaderRow -> VIEW_TYPE_HEADER
                    listUsbRow, systemSettingsRow, getMCUFirmwareRow -> VIEW_TYPE_PREFERENCE
                    usbNamingRow, frontendRow, firmwareRow, languageRow -> VIEW_TYPE_PREF_VALUE
                    else -> 0
                }
            }
        }
        listView.adapter = adapter
        ll.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(ll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

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
        title.scaleX = ViewUtils.lerp(1f, 0.5f, progress)
        title.scaleY = ViewUtils.lerp(1f, 0.5f, progress)
        header.alpha = 1f - progress
        header.invalidate()
        listView.alpha = progress
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
