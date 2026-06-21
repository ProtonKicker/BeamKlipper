package ru.ytkab0bp.beamklipper.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.system.Os
import android.util.Log
import ru.ytkab0bp.beamklipper.BundleInstaller
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

open class BaseMoonrakerService(private val num: Int) : BasePythonService() {
    companion object {
        const val BASE_ID = 200000
        @JvmField val MOONRAKER_PORT_PATTERN = Pattern.compile("port: (\\d+)")
    }

    override fun onBind(intent: Intent?): IBinder? {
        val b = super.onBind(intent) ?: return null
        val inst = instance ?: return null
        val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
        else
            Notification.Builder(this)
        not.setContentTitle(getString(R.string.MoonrakerTitle, inst.name))
            .setContentText(getString(R.string.MoonrakerDescription))
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
            val logs = File(inst.publicDirectory, "logs/moonraker.log")
            logs.parentFile?.mkdirs()
            val config = File(inst.publicDirectory, "config")
            val timelapseOutputDir = File(inst.publicDirectory, "timelapses")
            val socket = File(inst.directory, "klippy_uds")
            val tempFramesDir = File(inst.directory, "timelapse_frames")
            val moonSocket = File(inst.directory, "moonraker_uds")

            val resonancesLink = File(config, "beam_resonances")
            val fromResonances = File(KlipperApp.INSTANCE.cacheDir, "resonances")
            if (!resonancesLink.exists()) {
                fromResonances.mkdirs()
                Os.symlink(fromResonances.absolutePath, resonancesLink.absolutePath)
            }

            val moonrakerCfg = File(config, "moonraker.conf")
            if (!moonrakerCfg.exists()) {
                moonrakerCfg.parentFile?.mkdirs()
                var freePort = 7125
                for (otherInst in KlipperInstance.getInstances()) {
                    val f = File(otherInst.publicDirectory, "config/moonraker.conf")
                    if (f.exists()) {
                        val str = readString(f)
                        val m = MOONRAKER_PORT_PATTERN.matcher(str)
                        if (m.find()) {
                            val otherPort = Integer.parseInt(m.group(1))
                            if (otherPort == freePort) {
                                freePort++
                            }
                        }
                    }
                }

                val fos = FileOutputStream(moonrakerCfg)
                fos.write(BundleInstaller.readString(KlipperApp.INSTANCE.assets, "moonraker/default.conf")
                    .replace("\${KLIPPY_UDS}", socket.absolutePath)
                    .replace("\${MOONRAKER_PORT}", freePort.toString())
                    .replace("\${TIMELAPSE_FRAME_PATH}", tempFramesDir.absolutePath)
                    .replace("\${TIMELAPSE_OUTPUT}", timelapseOutputDir.absolutePath)
                    .toByteArray(StandardCharsets.UTF_8))
                fos.close()
            }
            val timelapseCfg = File(config, "timelapse.cfg")
            if (!timelapseCfg.exists()) {
                val fos = FileOutputStream(timelapseCfg)
                fos.write(BundleInstaller.readString(KlipperApp.INSTANCE.assets, "moonraker/timelapse.cfg").toByteArray(StandardCharsets.UTF_8))
                fos.close()
            }

            runPython(File(KlipperApp.INSTANCE.filesDir, "moonraker"), "bootstrap", "moonraker.py", "-u", moonSocket.absolutePath, "-l", logs.absolutePath, "-d", inst.publicDirectory.absolutePath, "-c", moonrakerCfg.absolutePath)
        } catch (e: Exception) {
            Log.e("moonraker_$num", "Failed to start moonraker", e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readString(file: File): String {
        val input = FileInputStream(file)
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(10240)
        var c: Int
        while (input.read(buffer).also { c = it } != -1) {
            bos.write(buffer, 0, c)
        }
        input.close()
        bos.close()
        return bos.toString()
    }
}
