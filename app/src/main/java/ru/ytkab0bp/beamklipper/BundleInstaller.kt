package ru.ytkab0bp.beamklipper

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object BundleInstaller {
    @JvmStatic
    fun init(ctx: Context) {
        val prefs = ctx.getSharedPreferences("installation", 0)
        val assets = ctx.assets
        try {
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(ctx.packageName, 0)
            var ver = readString(assets, "bundle_version") + "_beam-" + info.versionName

            val root = ctx.filesDir
            if (prefs.getString("version", "") != ver) {
                val index = JSONObject(readString(assets, "index.json"))
                unpack(assets, index, root, "klipper")
                unpack(assets, index, root, "moonraker")
                prefs.edit().putString("version", ver).apply()
            }

            val nativeDir = File(info.applicationInfo.nativeLibraryDir)
            val lib = File(nativeDir, "libklippy_chelper.so")

            var str = readString(assets, "klipper/klippy/chelper/__init__.py")
            str = str.replace("\${DEST_LIB}", lib.absolutePath)
            if (prefs.getString("native_lib", "") != lib.absolutePath) {
                FileOutputStream(File(root, "klipper/klippy/chelper/__init__.py")).use {
                    it.write(str.toByteArray(Charsets.UTF_8))
                }
                prefs.edit().putString("native_lib", lib.absolutePath).apply()
            }

            str = readString(assets, "moonraker/moonraker/utils/sysfs_devs.py")
            str = str.replace("TTY_PATH = \"/sys/class/tty\"",
                "TTY_PATH = \"" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "\"")
            FileOutputStream(File(root, "moonraker/moonraker/utils/sysfs_devs.py")).use {
                it.write(str.toByteArray(Charsets.UTF_8))
            }

            str = readString(assets, "klipper/klippy/extras/resonance_tester.py")
            str = str.replace("\${TEMP_PATH}", File(KlipperApp.INSTANCE.cacheDir, "resonances").absolutePath)
            FileOutputStream(File(root, "klipper/klippy/extras/resonance_tester.py")).use {
                it.write(str.toByteArray(Charsets.UTF_8))
            }

            str = readString(assets, "klipper/klippy/mcu.py")
            str = str.replace("\${TTY_PATH}",
                "'" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "'")
            FileOutputStream(File(root, "klipper/klippy/mcu.py")).use {
                it.write(str.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun deleteRecur(f: File) {
        if (f.isDirectory) {
            f.listFiles()?.forEach { deleteRecur(it) }
        }
        f.delete()
    }

    private fun unpack(assets: android.content.res.AssetManager, index: JSONObject, root: File, key: String) {
        val dir = File(root, key)
        deleteRecur(dir)

        val arr = index.optJSONArray(key)
        for (i in 0 until arr.length()) {
            val file = arr.optString(i)
            val into = File(dir, file)
            into.parentFile?.mkdirs()
            assets.open("$key/$file").use { inp ->
                FileOutputStream(into).use { fos ->
                    val buffer = ByteArray(10240)
                    var c: Int
                    while (inp.read(buffer).also { c = it } != -1) {
                        fos.write(buffer, 0, c)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun readString(assets: android.content.res.AssetManager, key: String): String {
        return assets.open(key).use { inp ->
            ByteArrayOutputStream().use { bos ->
                val buffer = ByteArray(10240)
                var c: Int
                while (inp.read(buffer).also { c = it } != -1) {
                    bos.write(buffer, 0, c)
                }
                bos.toString()
            }
        }
    }
}
