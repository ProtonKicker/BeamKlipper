package ru.ytkab0bp.beamklipper

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDexApplication
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.cloud.AndroidPlatform
import ru.ytkab0bp.beamklipper.cloud.CloudController
import ru.ytkab0bp.beamklipper.db.BeamDB
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.eventbus.EventBus
import ru.ytkab0bp.remotebeamlib.RemoteBeam

class KlipperApp : MultiDexApplication() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            val apkFile = File(applicationInfo.sourceDir)
            val loader = javaClass.classLoader ?: return
            val secondaryBytes = readSecondaryDexBytes(apkFile) ?: return

            val inmemLoader = dalvik.system.InMemoryDexClassLoader(
                java.nio.ByteBuffer.allocateDirect(secondaryBytes.size).put(secondaryBytes).also { it.flip() },
                loader
            )

            var pathListClass: Class<*> = loader::class.java
            while (pathListClass != null && pathListClass.declaredFields.none { it.name == "pathList" }) {
                pathListClass = pathListClass.superclass
            }
            val pathListField = pathListClass!!.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(loader)

            var localPathListClass: Class<*> = inmemLoader::class.java
            while (localPathListClass != null && localPathListClass.declaredFields.none { it.name == "pathList" }) {
                localPathListClass = localPathListClass.superclass
            }
            val localPathListField = localPathListClass!!.getDeclaredField("pathList")
            localPathListField.isAccessible = true
            val localPathList = localPathListField.get(inmemLoader)
            val localElementsField = localPathList.javaClass.getDeclaredField("dexElements")
            localElementsField.isAccessible = true
            val localElements = localElementsField.get(localPathList) as Array<*>

            val existingElementsField = pathList.javaClass.getDeclaredField("dexElements")
            existingElementsField.isAccessible = true
            val existingElements = existingElementsField.get(pathList) as Array<*>

            val elementType = existingElements.javaClass.componentType
            val combined = java.lang.reflect.Array.newInstance(elementType, existingElements.size + localElements.size)
            System.arraycopy(existingElements, 0, combined, 0, existingElements.size)
            System.arraycopy(localElements, 0, combined, existingElements.size, localElements.size)
            existingElementsField.set(pathList, combined)
        } catch (e: Exception) {
            android.util.Log.w("KlipperApp", "Failed secondary DEX install", e)
        }
    }

    private fun readSecondaryDexBytes(apk: File): ByteArray? {
        val zipFile = java.util.zip.ZipFile(apk)
        try {
            val entry = zipFile.getEntry("classes2.dex") ?: return null
            return zipFile.getInputStream(entry).use { it.readBytes() }
        } finally {
            zipFile.close()
        }
    }

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
        Handler(Looper.getMainLooper()).post {
            try {
                CloudController.init()
            } catch (e: NoClassDefFoundError) {
                android.util.Log.w("KlipperApp", "Cloud API not available (secondary DEX not loaded)", e)
            }
        }

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

        lateinit var INSTANCE: KlipperApp
        lateinit var DATABASE: BeamDB
        @JvmField
        var EVENT_BUS: EventBus = EventBus.newBus("main")
        @JvmField
        var hasUpdateInfo = false
    }
}
