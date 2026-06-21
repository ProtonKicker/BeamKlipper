package ru.ytkab0bp.beamklipper

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.cloud.AndroidPlatform
import ru.ytkab0bp.beamklipper.cloud.CloudController
import ru.ytkab0bp.beamklipper.db.BeamDB
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.eventbus.EventBus
import ru.ytkab0bp.remotebeamlib.RemoteBeam

class KlipperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        Prefs.init(this)
        DATABASE = BeamDB(this)
        KlipperInstance.onInstancesLoadedFromDB(DATABASE.getInstances())
        EventBus.registerImpl(this)
        BundleInstaller.init(this)
        RemoteBeam.init(AndroidPlatform)
        CloudController.initCached()
        CloudController.init()

        hasUpdateInfo = try {
            assets.open("update.json").close()
            true
        } catch (_: java.io.IOException) {
            false
        }
        try {
            BeamServerData.SERVER_DATA = BeamServerData.ServerData(JSONObject(Prefs.beamServerData))
        } catch (e: org.json.JSONException) {
            throw RuntimeException(e)
        }
        if (System.currentTimeMillis() - Prefs.getLastCheckedInfo() >= 86400000L) {
            ViewUtils.postOnMainThread { BeamServerData.load() }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(SERVICES_CHANNEL, getString(R.string.ServicesChannel), NotificationManager.IMPORTANCE_LOW))
        }

        if (getProcessNameCompat() == packageName) {
            UsbSerialManager.init(this)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getProcessNameCompat(): String {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentProcessName")
            method.invoke(null) as String
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        @JvmField
        val PERMISSION = BuildConfig.APPLICATION_ID + ".permission.INTERNAL_BROADCASTS"
        @JvmField
        val SERVICES_CHANNEL = "services"

        @JvmField
        var INSTANCE: KlipperApp = null!!
        @JvmField
        var DATABASE: BeamDB = null!!
        @JvmField
        var EVENT_BUS: EventBus = EventBus.newBus("main")
        @JvmField
        var hasUpdateInfo = false
    }
}
