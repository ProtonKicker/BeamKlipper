package ru.ytkab0bp.beamklipper.cloud

import android.util.Log
import com.google.gson.Gson
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.events.CloudFeaturesUpdatedEvent
import ru.ytkab0bp.beamklipper.events.CloudLoginStateUpdatedEvent
import ru.ytkab0bp.beamklipper.events.CloudNeedQREvent
import ru.ytkab0bp.beamklipper.events.CloudUserInfoUpdatedEvent
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import ru.ytkab0bp.sapil.APICallback
import ru.ytkab0bp.sapil.APIRequestHandle

object CloudController {
    private const val TAG = "cloud"
    private const val MIN_SYNC_FEATURES_DELTA = 12 * 60 * 60 * 1000L // Once in 12 hours

    private var userInfo: CloudAPI.UserInfo? = null
    private var userFeatures: CloudAPI.UserFeatures? = null

    private var isLoggingIn = false
    private var beginLoginHandle: APIRequestHandle? = null
    private var loginSessionId: String? = null

    private val loginAutoCancel = Runnable {
        loginSessionId = null
        isLoggingIn = false
        KlipperApp.EVENT_BUS.fireEvent(CloudLoginStateUpdatedEvent())
    }

    private val loginCheck: Runnable
        get() {
            val r = Runnable {
                CloudAPI.INSTANCE.loginCheck(loginSessionId!!, object : APICallback<CloudAPI.LoginState> {
                    override fun onResponse(response: CloudAPI.LoginState) {
                        if (response.loggedIn) {
                            Prefs.cloudApiToken = response.bearer
                            loadUserInfo()
                            ViewUtils.removeCallbacks(loginAutoCancel)
                        } else if (isLoggingIn) {
                            ViewUtils.postOnMainThread(this@CloudController.loginCheck, 5000)
                        }
                    }

                    override fun onException(e: Exception) {
                        Log.e(TAG, "Failed to check login state", e)
                        if (isLoggingIn) {
                            ViewUtils.postOnMainThread(this@CloudController.loginCheck, 5000)
                        }
                    }
                })
            }
            return r
        }

    private val gson = Gson()

    @JvmStatic
    fun initCached() {
        if (Prefs.cloudCachedUserFeatures != null) {
            userFeatures = gson.fromJson(Prefs.cloudCachedUserFeatures, CloudAPI.UserFeatures::class.java)
        }
        if (Prefs.cloudApiToken != null && Prefs.cloudCachedUserInfo != null) {
            userInfo = gson.fromJson(Prefs.cloudCachedUserInfo, CloudAPI.UserInfo::class.java)
        }
    }

    @JvmStatic
    fun init() {
        val now = System.currentTimeMillis()
        val needSyncInfo = userFeatures == null || now - Prefs.cloudLastFeaturesSync > MIN_SYNC_FEATURES_DELTA
        if (needSyncInfo) {
            checkUserFeatures()
        }
        if (Prefs.cloudApiToken != null) {
            if (needSyncInfo || userInfo == null) {
                loadUserInfo()
            }
        }
    }

    private fun loadUserInfo() {
        CloudAPI.INSTANCE.userGetInfo(object : APICallback<CloudAPI.UserInfo> {
            override fun onResponse(response: CloudAPI.UserInfo) {
                userInfo = response
                if (userInfo?.id == "null") {
                    userInfo = null
                    Prefs.cloudApiToken = null
                    Prefs.cloudCachedUserInfo = null
                } else {
                    Prefs.cloudCachedUserInfo = gson.toJson(userInfo)
                }
                KlipperApp.EVENT_BUS.fireEvent(CloudUserInfoUpdatedEvent())
                if (isLoggingIn) {
                    isLoggingIn = false
                    KlipperApp.EVENT_BUS.fireEvent(CloudLoginStateUpdatedEvent())
                }
                Prefs.cloudLastFeaturesSync = System.currentTimeMillis()
            }

            override fun onException(e: Exception) {
                Log.e(TAG, "Failed to get user info", e)
                ViewUtils.postOnMainThread({ init() }, 15000)
            }
        })
    }

    @JvmStatic
    fun isLoggingIn(): Boolean = isLoggingIn

    private fun beginLogin0() {
        beginLoginHandle = CloudAPI.INSTANCE.loginBegin(object : APICallback<CloudAPI.LoginData> {
            override fun onResponse(response: CloudAPI.LoginData) {
                loginSessionId = response.sessionId
                ViewUtils.postOnMainThread(loginAutoCancel, response.expiresAt * 1000L - System.currentTimeMillis())
                ViewUtils.postOnMainThread(loginCheck, 5000)
                ViewUtils.postOnMainThread { KlipperApp.EVENT_BUS.fireEvent(CloudNeedQREvent(response.url)) }
            }

            override fun onException(e: Exception) {
                ViewUtils.postOnMainThread({ beginLogin0() }, 15000)
            }
        })
    }

    @JvmStatic
    fun beginLogin() {
        isLoggingIn = true
        KlipperApp.EVENT_BUS.fireEvent(CloudLoginStateUpdatedEvent())
        beginLogin0()
    }

    @JvmStatic
    fun cancelLogin() {
        isLoggingIn = false
        KlipperApp.EVENT_BUS.fireEvent(CloudLoginStateUpdatedEvent())
        if (loginSessionId != null) {
            CloudAPI.INSTANCE.loginCancel(loginSessionId!!) {}
        }
        if (beginLoginHandle != null && beginLoginHandle!!.isRunning) {
            beginLoginHandle!!.cancel()
            beginLoginHandle = null
        }
        ViewUtils.removeCallbacks(loginCheck)
        ViewUtils.removeCallbacks(loginAutoCancel)
        loginSessionId = null
    }

    @JvmStatic
    fun logout() {
        for (inst in KlipperInstance.getInstances()) {
            if (inst.remoteId != null) {
                CloudAPI.INSTANCE.remoteDeletePrinter(inst.remoteId ?: continue) {}
                inst.remoteId = null
                inst.remoteToken = null
                KlipperApp.DATABASE.update(inst)
            }
        }
        CloudAPI.INSTANCE.logout {}
        Prefs.cloudApiToken = null
        userInfo = null
        KlipperApp.EVENT_BUS.fireEvent(CloudLoginStateUpdatedEvent())
        KlipperApp.EVENT_BUS.fireEvent(CloudUserInfoUpdatedEvent())
    }

    @JvmStatic
    fun checkUserFeatures() {
        CloudAPI.INSTANCE.userGetFeatures(object : APICallback<CloudAPI.UserFeatures> {
            override fun onResponse(response: CloudAPI.UserFeatures) {
                userFeatures = response
                Prefs.cloudCachedUserFeatures = gson.toJson(userFeatures)
                if (Prefs.cloudApiToken == null) {
                    Prefs.cloudLastFeaturesSync = System.currentTimeMillis()
                }
                KlipperApp.EVENT_BUS.fireEvent(CloudFeaturesUpdatedEvent())
            }

            override fun onException(e: Exception) {
                Log.e(TAG, "Failed to get user features", e)
                ViewUtils.postOnMainThread({ checkUserFeatures() }, 15000)
            }
        })
    }

    @JvmStatic
    fun getUserInfo(): CloudAPI.UserInfo? = userInfo

    @JvmStatic
    fun getUserFeatures(): CloudAPI.UserFeatures? = userFeatures

    @JvmStatic
    fun hasAccountFeatures(): Boolean {
        val f = userFeatures ?: return false
        return f.levels != null && f.levels.isNotEmpty()
    }
}
