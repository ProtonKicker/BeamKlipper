package ru.ytkab0bp.beamklipper

import android.util.Log
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.cloud.CloudController
import ru.ytkab0bp.beamklipper.events.BeamServerDataUpdatedEvent
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.util.Locale

object BeamServerData {
    private const val TAG = "BeamServerData"
    private const val DATA_URL = "https://beam3d.ru/slicebeam.php?act=get_data"
    private const val RUSSIA_CHECK_URL = "https://beam3d.ru/check_russia.txt"
    private val client = AsyncHttpClient()

    @JvmField
    var SERVER_DATA: ServerData? = null

    init {
        client.userAgent = String.format(Locale.ROOT, "BeamKlipper/%s-%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        client.isEnableRedirects = true
        client.setLoggingEnabled(false)
    }

    class ServerData(json: JSONObject) {
        @JvmField
        val boostySubscribers = mutableListOf<String>()

        init {
            val arr = json.optJSONArray("boosty_subscribers")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    boostySubscribers.add(arr.optString(i))
                }
            }
        }
    }

    @JvmStatic
    fun isBoostyAvailable(): Boolean {
        return !BuildConfig.IS_GOOGLE_PLAY || Prefs.isRussianIP()
    }

    @JvmStatic
    fun isCloudAvailable(): Boolean {
        return isBoostyAvailable() && CloudController.hasAccountFeatures()
    }

    @JvmStatic
    fun load() {
        client.get(DATA_URL, object : AsyncHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Array<out cz.msebera.android.httpclient.Header>?, responseBody: ByteArray?) {
                val str = String(responseBody!!, Charsets.UTF_8)
                Prefs.setBeamServerData(str)
                Prefs.setLastCheckedInfo()

                SERVER_DATA = ServerData(JSONObject(str))

                if (BuildConfig.IS_GOOGLE_PLAY) {
                    client.get(RUSSIA_CHECK_URL, object : AsyncHttpResponseHandler() {
                        override fun onSuccess(statusCode: Int, headers: Array<out cz.msebera.android.httpclient.Header>?, responseBody: ByteArray?) {
                            setIsRussia(String(responseBody!!, Charsets.UTF_8) == "true")
                        }

                        override fun onFailure(statusCode: Int, headers: Array<out cz.msebera.android.httpclient.Header>?, responseBody: ByteArray?, error: Throwable?) {
                            setIsRussia(false)
                        }

                        private fun setIsRussia(v: Boolean) {
                            Prefs.setRussianIP(v)
                            KlipperApp.EVENT_BUS.fireEvent(BeamServerDataUpdatedEvent())
                        }
                    })
                } else {
                    KlipperApp.EVENT_BUS.fireEvent(BeamServerDataUpdatedEvent())
                }
            }

            override fun onFailure(statusCode: Int, headers: Array<out cz.msebera.android.httpclient.Header>?, responseBody: ByteArray?, error: Throwable?) {
                Log.e(TAG, "Failed to update server data", error)
            }
        })
    }
}
