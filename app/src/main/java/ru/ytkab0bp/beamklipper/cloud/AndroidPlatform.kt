package ru.ytkab0bp.beamklipper.cloud

import android.util.Base64
import android.util.Log
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.remotebeamlib.IPlatform
import java.util.concurrent.Executors

object AndroidPlatform : IPlatform {
    private val IO_POOL = Executors.newCachedThreadPool()

    override fun schedule(r: Runnable, delay: Long) {
        ViewUtils.postOnMainThread(r, delay)
    }

    override fun scheduleNetwork(r: Runnable) {
        IO_POOL.submit(r)
    }

    override fun logD(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun encodeBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    override fun decodeBase64(str: String): ByteArray {
        return Base64.decode(str, 0)
    }
}
