package ru.ytkab0bp.beamklipper.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import ru.ytkab0bp.beamklipper.BundleInstaller
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

open class BaseKlippyService(private val num: Int) : BasePythonService() {
    companion object {
        const val BASE_ID = 100000
    }

    override fun onBind(intent: Intent?): IBinder? {
        val b = super.onBind(intent)
        val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
        else
            Notification.Builder(this)
        not.setContentTitle(getString(R.string.KlippyTitle, instance!!.name))
            .setContentText(getString(R.string.KlippyDescription))
            .setSmallIcon(R.drawable.icon_adaptive_foreground)
            .setOngoing(true)
        notificationManager.notify(BASE_ID + num, not.build())
        startForeground(BASE_ID + num, not.build())
        return b
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        notificationManager.cancel(BASE_ID + num)
    }

    override fun onStartPython() {
        val inst = instance ?: return
        try {
            val logs = File(inst.publicDirectory, "logs/klippy.log")
            val config = File(inst.publicDirectory, "config")
            val socket = File(inst.directory, "klippy_uds")
            val virtualInput = File(inst.directory, "vinput")
            virtualInput.createNewFile()
            logs.parentFile?.mkdirs()
            val printerCfg = File(config, "printer.cfg")
            try {
                val fis = FileInputStream(printerCfg)
                val bos = ByteArrayOutputStream()
                val buffer = ByteArray(10240)
                var c: Int
                while (fis.read(buffer).also { c = it } != -1) {
                    bos.write(buffer, 0, c)
                }
                bos.close()
                fis.close()

                var str = bos.toString()
                var changed = false

                val pattern = Pattern.compile("\\[virtual_sdcard][\\r\\n ]+path: ([^\\r\\n]+)", Pattern.DOTALL)
                val m = pattern.matcher(str)
                if (m.find()) {
                    val path = m.group(1)
                    if (!path.startsWith(inst.publicDirectory.absolutePath)) {
                        str = str.substring(0, m.start()) + str.substring(m.end() + 1)
                    }
                }
                if (!str.contains("[virtual_sdcard]")) {
                    str += "\n[virtual_sdcard]\npath: " + File(inst.publicDirectory, "gcodes").absolutePath + "\n"
                    changed = true
                }
                if (changed) {
                    val fos = FileOutputStream(printerCfg)
                    fos.write(str.toByteArray(StandardCharsets.UTF_8))
                    fos.close()
                }

                val beeperCfg = File(config, "beam_beeper.cfg")
                if (!beeperCfg.exists()) {
                    val fos = FileOutputStream(beeperCfg)
                    fos.write(BundleInstaller.readString(KlipperApp.INSTANCE.assets, "klipper/beam_beeper.cfg").toByteArray(StandardCharsets.UTF_8))
                    fos.close()
                }
            } catch (_: Exception) {}
            runPython(File(KlipperApp.INSTANCE.filesDir, "klipper/klippy"), "klippy", "klippy.py", "-B", virtualInput.absolutePath, "-l", logs.absolutePath, "-a", socket.absolutePath, printerCfg.absolutePath)
        } catch (e: Exception) {
            Log.e("klippy_$num", "Failed to start klippy", e)
        }
    }
}
