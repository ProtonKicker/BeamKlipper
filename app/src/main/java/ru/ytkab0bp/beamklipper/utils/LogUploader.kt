package ru.ytkab0bp.beamklipper.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import okhttp3.*
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarHeader
import org.kamranzafar.jtar.TarOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

object LogUploader {
    private const val TAG = "logs_uploader"
    private val httpClient = OkHttpClient.Builder().followRedirects(false).build()

    @JvmStatic
    fun uploadLogs(instance: KlipperInstance) {
        val dir = instance.publicDirectory
        val klippyLog = File(dir, "logs/klippy.log")
        val moonrakerLog = File(dir, "logs/moonraker.log")

        try {
            val bos = ByteArrayOutputStream()
            val xz = XZOutputStream(bos, LZMA2Options())
            val tar = TarOutputStream(xz)
            val buffer = ByteArray(10240)
            val tempStream = ByteArrayOutputStream()

            if (klippyLog.exists()) {
                tar.putNextEntry(TarEntry(object : TarHeader() {
                    init {
                        mode = 0o100644
                        name.append("klippy.log")
                        size = klippyLog.length()
                    }
                }))
                FileInputStream(klippyLog).use { fis ->
                    var c: Int
                    while (fis.read(buffer).also { c = it } != -1) {
                        tempStream.write(buffer, 0, c)
                    }
                }
                tar.write(tempStream.toByteArray())
            }
            if (moonrakerLog.exists()) {
                tempStream.reset()
                tar.putNextEntry(TarEntry(object : TarHeader() {
                    init {
                        mode = 0o100644
                        name.append("moonraker.log")
                        size = moonrakerLog.length()
                    }
                }))
                FileInputStream(moonrakerLog).use { fis ->
                    var c: Int
                    while (fis.read(buffer).also { c = it } != -1) {
                        tempStream.write(buffer, 0, c)
                    }
                }
                tar.write(tempStream.toByteArray())
            }

            tar.close()
            xz.close()
            bos.close()

            httpClient.newCall(Request.Builder()
                .url("https://coderus.openrepos.net/klipper_logs/upload")
                .post(MultipartBody.Builder()
                    .setType(MultipartBody.MIXED)
                    .addFormDataPart("tarfile", "logs.tar.xz",
                        RequestBody.create(MediaType.get("application/x-gtar"), bos.toByteArray()))
                    .build())
                .build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: java.io.IOException) {
                        Log.e(TAG, "Failed to upload logs", e)
                        ViewUtils.postOnMainThread {
                            Toast.makeText(KlipperApp.INSTANCE, R.string.UploadFailed, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.header("Location") != null) {
                            ViewUtils.postOnMainThread {
                                Toast.makeText(KlipperApp.INSTANCE, R.string.UploadSuccess, Toast.LENGTH_SHORT).show()
                                var loc = response.header("Location")!!
                                if (!loc.startsWith("https://")) {
                                    loc = "https://coderus.openrepos.net$loc"
                                }
                                val i = Intent(Intent.ACTION_VIEW, Uri.parse(loc)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                KlipperApp.INSTANCE.startActivity(i)

                                val clipboard = KlipperApp.INSTANCE.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("beam", loc)
                                clipboard.setPrimaryClip(clip)
                            }
                            response.close()
                        } else {
                            response.close()
                            onFailure(call, java.io.IOException("Not a redirect"))
                        }
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pack logs", e)
        }
    }
}
