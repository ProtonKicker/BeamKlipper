package ru.ytkab0bp.beamklipper

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import ru.ytkab0bp.beamklipper.events.InstanceStateChangedEvent
import ru.ytkab0bp.beamklipper.events.WebStateChangedEvent
import ru.ytkab0bp.beamklipper.service.*
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.remotebeamlib.RemoteBeamConnection
import java.io.File
import java.io.IOException

class KlipperInstance {
    @JvmField
    var name: String = ""
    @JvmField
    var id: String? = null
    @JvmField
    var icon: InstanceIcon = InstanceIcon.PRINTER
    @JvmField
    var autostart = false
    @JvmField
    var remoteId: String? = null
    @JvmField
    var remoteToken: String? = null

    private var state: State = State.IDLE
    private var remoteBeamConnection: RemoteBeamConnection? = null
    private var klippyIntent: Intent? = null
    private var klippyConnection: ServiceConnection? = null
    private var klippyConnected = false
    private var moonrakerIntent: Intent? = null
    private var moonrakerConnection: ServiceConnection? = null
    private var moonrakerConnected = false
    private var slot = 0

    fun getState(): State = state

    val directory: File
        get() = File(KlipperApp.INSTANCE.filesDir, "instance${File.separator}$id")

    val publicDirectory: File
        get() = File(directory, "public")

    fun start() {
        if (state != State.IDLE) return
        notifyStateChanged(State.STARTING)

        if (!directory.exists() && !directory.mkdirs()) {
            failStart("Failed to create instance directory ($id)")
            return
        }
        if (!publicDirectory.exists() && !publicDirectory.mkdirs()) {
            failStart("Failed to create public instance directory ($id)")
            return
        }
        val config = File(publicDirectory, "printer_data")
        if (!config.exists() && !config.mkdirs()) {
            failStart("Failed to create data directory ($id)")
            return
        }

        slot = -1
        if (slots.isEmpty()) {
            slot = 0
        } else if (slots.size < SLOTS_COUNT) {
            val cl = slots.values
            for (i in 0 until SLOTS_COUNT) {
                if (!cl.contains(i)) {
                    slot = i
                    break
                }
            }
        } else {
            failStart("Can't start $id: out of slots")
            return
        }
        slots[this] = slot
        try {
            val kIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.KlippyService_$slot"))
            klippyIntent = kIntent
            kIntent.putExtra(BasePythonService.KEY_INSTANCE, id)
            val bound = KlipperApp.INSTANCE.bindService(kIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    klippyConnected = true
                    if (state == State.STARTING && moonrakerConnected) {
                        notifyStateChanged(State.RUNNING)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    onKlippyUnbound()
                }
            }.also { klippyConnection = it }, Context.BIND_AUTO_CREATE)
            if (!bound) {
                failStart("Failed to bind Klippy service for $id")
                return
            }
        } catch (e: ClassNotFoundException) {
            failStart("Klippy service class is missing for slot $slot", e)
            return
        }
        try {
            val mIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.MoonrakerService_$slot"))
            moonrakerIntent = mIntent
            mIntent.putExtra(BasePythonService.KEY_INSTANCE, id)
            val bound = KlipperApp.INSTANCE.bindService(mIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    moonrakerConnected = true
                    if (state == State.STARTING && klippyConnected) {
                        notifyStateChanged(State.RUNNING)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    onMoonrakerUnbound()
                }
            }.also { moonrakerConnection = it }, Context.BIND_AUTO_CREATE)
            if (!bound) {
                failStart("Failed to bind Moonraker service for $id")
                return
            }
        } catch (e: ClassNotFoundException) {
            failStart("Moonraker service class is missing for slot $slot", e)
            return
        }
        if (remoteId != null) {
            try {
                val f = File(publicDirectory, "config/moonraker.conf")
                val s = BaseMoonrakerService.readString(f)
                val m = BaseMoonrakerService.MOONRAKER_PORT_PATTERN.matcher(s)
                if (m.find()) {
                    val port = m.group(1)?.toInt() ?: throw IOException("No port group")
                    remoteBeamConnection = RemoteBeamConnection(remoteToken, "http://127.0.0.1:8888", "127.0.0.1:$port", object : RemoteBeamConnection.EventListener {
                        override fun onConnected(conn: RemoteBeamConnection) {
                            Log.d(TAG, "Remote connected")
                        }

                        override fun onError(conn: RemoteBeamConnection, e: Exception) {
                            Log.e(TAG, "Remote error", e)
                        }

                        override fun onServerRejected(conn: RemoteBeamConnection, message: String) {
                            Log.d(TAG, "Server rejected: $message")
                        }

                        override fun onDisconnected(conn: RemoteBeamConnection) {
                            Log.d(TAG, "Remote disconnected")
                        }
                    })
                    remoteBeamConnection?.connect()
                } else {
                    throw IOException("No match")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to parse port", e)
            }
        }
    }

    fun stop() {
        if (state == State.IDLE || state == State.STOPPING) return
        notifyStateChanged(State.STOPPING)

        val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentSlot = slot
        cleanupBoundServices()
        if (currentSlot >= 0) {
            nm.cancel(BaseKlippyService.BASE_ID + currentSlot)
            nm.cancel(BaseMoonrakerService.BASE_ID + currentSlot)
        }
        remoteBeamConnection?.disconnect()
        remoteBeamConnection = null
        notifyStateChanged(State.IDLE)
    }

    private fun onKlippyUnbound() {
        klippyConnection = null
        klippyConnected = false
        if (moonrakerConnected && state != State.IDLE && state != State.STOPPING) {
            stop()
            return
        }
        if (!moonrakerConnected && state != State.IDLE) {
            notifyStateChanged(State.IDLE)
        }
    }

    private fun onMoonrakerUnbound() {
        moonrakerConnection = null
        moonrakerConnected = false
        if (klippyConnected && state != State.IDLE && state != State.STOPPING) {
            stop()
            return
        }
        if (!klippyConnected && state != State.IDLE) {
            notifyStateChanged(State.IDLE)
        }
    }

    private fun cleanupBoundServices() {
        safeUnbindService(klippyConnection)
        safeUnbindService(moonrakerConnection)
        klippyConnection = null
        moonrakerConnection = null
        klippyConnected = false
        moonrakerConnected = false
        klippyIntent?.let { KlipperApp.INSTANCE.stopService(it) }
        moonrakerIntent?.let { KlipperApp.INSTANCE.stopService(it) }
        klippyIntent = null
        moonrakerIntent = null
    }

    private fun failStart(message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(TAG, message, error)
        } else {
            Log.w(TAG, message)
        }
        cleanupBoundServices()
        remoteBeamConnection?.disconnect()
        remoteBeamConnection = null
        notifyStateChanged(State.IDLE)
    }

    private fun notifyStateChanged(state: State) {
        this.state = state
        KlipperApp.EVENT_BUS.fireEvent(InstanceStateChangedEvent(requireNotNull(id), state))

        if (state == State.IDLE) {
            slots.remove(this)
            slot = -1
            if (slots.isEmpty()) {
                if (webServerConnection != null) {
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.STOPPING))
                    safeUnbindService(webServerConnection)
                    KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, WebService::class.java))
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.IDLE))
                    webServerConnection = null
                }
                if (cameraServerConnection != null) {
                    safeUnbindService(cameraServerConnection)
                    KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java))
                    cameraServerConnection = null
                }
            }
        } else if (state == State.RUNNING) {
            if (webServerConnection == null) {
                KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.STARTING))
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.RUNNING))
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        webServerConnection = null
                        KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.IDLE))
                    }
                }
                webServerConnection = connection
                if (!KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, WebService::class.java), connection, Context.BIND_AUTO_CREATE)) {
                    webServerConnection = null
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.IDLE))
                    Log.e(TAG, "Failed to bind web service")
                }
            }

            if (Prefs.isCameraEnabled) {
                if (cameraServerConnection == null) {
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                        override fun onServiceDisconnected(name: ComponentName) {
                            cameraServerConnection = null
                        }
                    }
                    cameraServerConnection = connection
                    if (!KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), connection, Context.BIND_AUTO_CREATE)) {
                        cameraServerConnection = null
                        Log.e(TAG, "Failed to bind camera service")
                    }
                }
            }
        }
    }

    enum class State {
        IDLE, STARTING, RUNNING, STOPPING
    }

    companion object {
        const val SLOTS_COUNT = 4
        private const val TAG = "beam_instance"

        private val slots = HashMap<KlipperInstance, Int>()
        private var webServerConnection: ServiceConnection? = null
        private var cameraServerConnection: ServiceConnection? = null
        private var instances: List<KlipperInstance> = emptyList()
        private val instanceMap = object : HashMap<String, KlipperInstance>() {
            override fun get(key: String): KlipperInstance? {
                var inst = super.get(key)
                if (inst == null) {
                    for (i in instances) {
                        if (key == i.id) {
                            put(key, i)
                            inst = i
                            break
                        }
                    }
                }
                return inst
            }
        }

        @JvmStatic
        fun onInstancesLoadedFromDB(loaded: List<KlipperInstance>) {
            for (inst in loaded) {
                val was = getInstance(inst.id ?: continue)
                if (was != null) {
                    inst.state = was.state
                    inst.klippyConnection = was.klippyConnection
                    inst.klippyConnected = was.klippyConnected
                    inst.klippyIntent = was.klippyIntent
                    inst.moonrakerConnection = was.moonrakerConnection
                    inst.moonrakerConnected = was.moonrakerConnected
                    inst.moonrakerIntent = was.moonrakerIntent
                    inst.slot = was.slot
                    slots.remove(was)
                    slots[inst] = inst.slot
                }
            }
            instances = loaded
            instanceMap.clear()

            for (inst in instances) {
                if (inst.autostart && inst.getState() == State.IDLE) {
                    inst.start()
                }
            }
        }

        @JvmStatic
        fun getInstance(id: String): KlipperInstance? = instanceMap[id]

        @JvmStatic
        fun getInstances(): List<KlipperInstance> = instances

        @JvmStatic
        fun hasFreeSlots(): Boolean = slots.size < SLOTS_COUNT

        @JvmStatic
        fun isWebServerRunning(): Boolean = webServerConnection != null

        @JvmStatic
        fun onCameraConfigChanged(enable: Boolean) {
            if (cameraServerConnection == null && slots.isNotEmpty() && enable) {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                    override fun onServiceDisconnected(name: ComponentName) {
                        cameraServerConnection = null
                    }
                }
                cameraServerConnection = connection
                if (!KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), connection, Context.BIND_AUTO_CREATE)) {
                    cameraServerConnection = null
                    Log.e(TAG, "Failed to bind camera service")
                }
            } else if (cameraServerConnection != null && !enable) {
                safeUnbindService(cameraServerConnection)
                KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java))
                cameraServerConnection = null
            }
        }

        private fun safeUnbindService(connection: ServiceConnection?) {
            if (connection == null) return
            try {
                KlipperApp.INSTANCE.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service was not bound", e)
            }
        }
    }
}
