package ru.ytkab0bp.beamklipper.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager

import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.events.WebFrontendChangedEvent
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager

object Prefs {
    const val USB_DEVICE_NAMING_BY_PATH = 0
    const val USB_DEVICE_NAMING_BY_VID_PID = 1

    private lateinit var mPrefs: SharedPreferences

    fun init(ctx: Context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    }

    @get:JvmName("getCloudAPIToken")
    @set:JvmName("setCloudAPIToken")
    var cloudApiToken: String?
        get() = mPrefs.getString("cloud_api_token", null)
        set(value) {
            val e = mPrefs.edit()
            if (value == null) e.remove("cloud_api_token")
            else e.putString("cloud_api_token", value)
            e.apply()
        }

    var cloudCachedUserFeatures: String?
        get() = mPrefs.getString("cloud_cached_user_features", null)
        set(value) {
            val e = mPrefs.edit()
            if (value == null) e.remove("cloud_cached_user_features")
            else e.putString("cloud_cached_user_features", value)
            e.apply()
        }

    var cloudCachedUserInfo: String?
        get() = mPrefs.getString("cloud_cached_user_info", null)
        set(value) {
            val e = mPrefs.edit()
            if (value == null) e.remove("cloud_cached_user_info")
            else e.putString("cloud_cached_user_info", value)
            e.apply()
        }

    var cloudLastFeaturesSync: Long
        get() = mPrefs.getLong("cloud_last_features_sync", 0)
        set(value) { mPrefs.edit().putLong("cloud_last_features_sync", value).apply() }

    var cloudLastSync: Long
        get() = mPrefs.getLong("cloud_last_sync", 0)
        set(value) { mPrefs.edit().putLong("cloud_last_sync", value).apply() }

    var cloudLocalLastSentModified: Long
        get() = mPrefs.getLong("cloud_local_last_sent_modified", 0)
        set(value) { mPrefs.edit().putLong("cloud_local_last_sent_modified", value).apply() }

    var cloudLocalLastModified: Long
        get() = mPrefs.getLong("cloud_local_last_modified", 0)
        set(value) { mPrefs.edit().putLong("cloud_local_last_modified", value).apply() }

    var cloudRemoteLastModified: Long
        get() = mPrefs.getLong("cloud_remote_last_modified", 0)
        set(value) { mPrefs.edit().putLong("cloud_remote_last_modified", value).apply() }

    var beamServerData: String
        get() = mPrefs.getString("beam_server_data", "{}")!!
        set(value) { mPrefs.edit().putString("beam_server_data", value).apply() }

    var isRussianIP: Boolean
        get() = mPrefs.getBoolean("russian_ip", false)
        set(value) { mPrefs.edit().putBoolean("russian_ip", value).apply() }

    var isMainsailEnabled: Boolean
        get() = mPrefs.getBoolean("mainsail", true)
        set(value) {
            mPrefs.edit().putBoolean("mainsail", value).apply()
            KlipperApp.EVENT_BUS.fireEvent(WebFrontendChangedEvent())
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

    fun getLastCheckedInfo(): Long = mPrefs.getLong("last_checked_info", 0)

    fun setLastCheckedInfo() {
        mPrefs.edit().putLong("last_checked_info", System.currentTimeMillis()).apply()
    }
}
