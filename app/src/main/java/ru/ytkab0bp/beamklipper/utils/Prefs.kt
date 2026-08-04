package ru.ytkab0bp.beamklipper.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.events.EngineChangedEvent
import ru.ytkab0bp.beamklipper.events.WebFrontendChangedEvent
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager

object Prefs {
    const val USB_DEVICE_NAMING_BY_PATH = 0
    const val USB_DEVICE_NAMING_BY_VID_PID = 1
    const val ENGINE_KLIPPER = "klipper"
    const val ENGINE_KALICO = "kalico"
    const val FRONTEND_FLUIDD = "fluidd"
    const val FRONTEND_MAINSAIL = "mainsail"
    const val FRONTEND_KALICO = "kalico_frontend"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_CHINESE_SIMPLIFIED = "zh-CN"
    const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"

    private lateinit var mPrefs: SharedPreferences

    fun init(ctx: Context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    }

    var webFrontend: String
        get() {
            val legacyMainsail = mPrefs.contains("mainsail")
            return if (legacyMainsail) {
                if (mPrefs.getBoolean("mainsail", true)) FRONTEND_MAINSAIL else FRONTEND_FLUIDD
            } else {
                mPrefs.getString("web_frontend", FRONTEND_MAINSAIL) ?: FRONTEND_MAINSAIL
            }
        }
        set(value) {
            mPrefs.edit().putString("web_frontend", value).remove("mainsail").apply()
            KlipperApp.EVENT_BUS.fireEvent(WebFrontendChangedEvent())
        }

    var engine: String
        get() = mPrefs.getString("engine", ENGINE_KLIPPER) ?: ENGINE_KLIPPER
        set(value) {
            mPrefs.edit().putString("engine", value).apply()
            KlipperApp.EVENT_BUS.fireEvent(EngineChangedEvent())
        }

    val engineKey: String
        get() = engine

    var appLanguage: String
        get() = mPrefs.getString("app_language", LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        set(value) {
            mPrefs.edit().putString("app_language", value).apply()
        }

    val cameraWidth: Int
        get() = mPrefs.getInt("camera_width", 1280)

    val cameraHeight: Int
        get() = mPrefs.getInt("camera_height", 720)

    val cameraId: String?
        get() = mPrefs.getString("camera_id", null)

    var isCameraEnabled: Boolean
        get() = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || KlipperApp.INSTANCE.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
                mPrefs.getBoolean("camera_enabled", false)
        set(value) { mPrefs.edit().putBoolean("camera_enabled", value).apply() }

    var usbDeviceNaming: Int
        get() = mPrefs.getInt("usb_device_naming", USB_DEVICE_NAMING_BY_PATH)
        set(value) {
            UsbSerialManager.disconnectAll()
            mPrefs.edit().putInt("usb_device_naming", value).apply()
            UsbSerialManager.connectAll()
        }

    var isFlashlightEnabled: Boolean
        get() = mPrefs.getBoolean("flashlight", false)
        set(value) { mPrefs.edit().putBoolean("flashlight", value).apply() }

    var isAutofocusEnabled: Boolean
        get() = mPrefs.getBoolean("autofocus", false)
        set(value) { mPrefs.edit().putBoolean("autofocus", value).apply() }

    var focusDistance: Float
        get() = mPrefs.getFloat("focus", 0f)
        set(value) { mPrefs.edit().putFloat("focus", value).apply() }

    fun getLastCommit(): String? = mPrefs.getString("last_commit", null)

    fun setLastCommit() {
        mPrefs.edit().putString("last_commit", BuildConfig.COMMIT).apply()
    }

    fun applyAppLanguage() {
        val locales = if (appLanguage == LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(appLanguage)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
