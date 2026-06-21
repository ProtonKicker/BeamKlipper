package ru.ytkab0bp.beamklipper.cloud

import androidx.annotation.Nullable
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.sapil.APICallback
import ru.ytkab0bp.sapil.APILibrary
import ru.ytkab0bp.sapil.APIRequestHandle
import ru.ytkab0bp.sapil.APIRunner
import ru.ytkab0bp.sapil.Arg
import ru.ytkab0bp.sapil.Method

interface CloudAPI : APIRunner {
    companion object {
        @JvmField
        val INSTANCE: CloudAPI = APILibrary.newRunner(CloudAPI::class.java, object : RunnerConfig() {
            private val headers = HashMap<String, String>()

            override fun getBaseURL(): String = "https://api.beam3d.ru/v1/"

            override fun getDefaultUserAgent(): String = "BeamKlipper v${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}"

            override fun getDefaultHeaders(): Map<String, String> {
                headers.clear()
                Prefs.cloudApiToken?.let { headers["Authorization"] = "Bearer $it" }
                return headers
            }
        })
    }

    @Method("login/begin")
    fun loginBegin(callback: APICallback<LoginData>): APIRequestHandle

    @Method("login/check")
    fun loginCheck(@Arg("sessionId") sessionId: String, callback: APICallback<LoginState>)

    @Method("login/cancel")
    fun loginCancel(@Arg("sessionId") sessionId: String, callback: APICallback<Boolean>)

    @Method("user/getInfo")
    fun userGetInfo(callback: APICallback<UserInfo>)

    @Method("user/getFeatures")
    fun userGetFeatures(callback: APICallback<UserFeatures>)

    @Method("remote/getPrinters")
    fun remoteGetPrinters(callback: APICallback<List<RemotePrinter>>)

    @Method("remote/createPrinter")
    fun remoteCreatePrinter(@Arg("name") name: String, callback: APICallback<RemotePrinter>)

    @Method("remote/deletePrinter")
    fun remoteDeletePrinter(@Arg("id") id: String, callback: APICallback<Boolean>)

    @Method("logout")
    fun logout(callback: APICallback<Boolean>)

    data class LoginData(
        @JvmField var url: String = "",
        @JvmField var sessionId: String = "",
        @JvmField var expiresAt: Long = 0L
    )

    data class LoginState(
        @JvmField var loggedIn: Boolean = false,
        @JvmField var bearer: String? = null
    )

    data class UserFeatures(
        @JvmField var earlyAccessLevel: Int = 0,
        @JvmField var syncRequiredLevel: Int = 0,
        @JvmField var aiGeneratorRequiredLevel: Int = 0,
        @JvmField var aiGeneratorModelsPerMonth: Int = 0,
        @JvmField var remoteAccessLevel: Int = 0,
        @JvmField var remoteAccessPrintersLimit: Int = 0,
        @JvmField var alreadySubscribedInfoUrl: String? = null,
        @JvmField var levels: MutableList<SubscriptionLevel> = ArrayList()
    )

    data class SubscriptionLevel(
        @JvmField var level: Int = 0,
        @JvmField var title: String? = null,
        @JvmField var price: String? = null,
        @JvmField var subscribeOrUpgradeUrl: String? = null,
        @JvmField var manageUrl: String? = null
    )

    data class UserInfo(
        @JvmField var id: String = "",
        @JvmField var displayName: String = "",
        @JvmField @Nullable var avatarUrl: String? = null,
        @JvmField var currentLevel: Int = 0
    )

    data class RemotePrinter(
        @JvmField var id: String = "",
        @JvmField var name: String = "",
        @JvmField var token: String = "",
        @JvmField var publicUrl: String = ""
    )
}
