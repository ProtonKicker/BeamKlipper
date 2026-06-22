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
            Log.w(TAG, "Failed to create instance directory ($id)")
            stop()
            return
        }
        if (!publicDirectory.exists() && !publicDirectory.mkdirs()) {
            Log.w(TAG, "Failed to create public instance directory ($id)")
            stop()
            return
        }
        val config = File(publicDirectory, "printer_data")
        if (!config.exists() && !config.mkdirs()) {
            Log.w(TAG, "Failed to create data directory ($id)")
            stop()
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
            throw IllegalStateException("Can't start $id: out of slots")
        }
        slots[this] = slot
        try {
            val kIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.KlippyService_$slot"))
            klippyIntent = kIntent
            kIntent.putExtra(BasePythonService.KEY_INSTANCE, id)
            KlipperApp.INSTANCE.bindService(kIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    klippyConnected = true
                    if (moonrakerConnected) {
                        notifyStateChanged(State.RUNNING)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            }.also { klippyConnection = it }, Context.BIND_AUTO_CREATE)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        }
        try {
            val mIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.MoonrakerService_$slot"))
            moonrakerIntent = mIntent
            mIntent.putExtra(BasePythonService.KEY_INSTANCE, id)
            KlipperApp.INSTANCE.bindService(mIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    moonrakerConnected = true
                    if (klippyConnected) {
                        notifyStateChanged(State.RUNNING)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            }.also { moonrakerConnection = it }, Context.BIND_AUTO_CREATE)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
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
        if (state != State.RUNNING) return
        notifyStateChanged(State.STOPPING)

        val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (klippyConnection != null) {
            KlipperApp.INSTANCE.unbindService(klippyConnection!!)
            KlipperApp.INSTANCE.stopService(klippyIntent)
            onKlippyUnbound()
            nm.cancel(BaseKlippyService.BASE_ID + slot)
        }
        if (moonrakerConnection != null) {
            KlipperApp.INSTANCE.unbindService(moonrakerConnection!!)
            KlipperApp.INSTANCE.stopService(moonrakerIntent)
            onMoonrakerUnbound()
            nm.cancel(BaseMoonrakerService.BASE_ID + slot)
        }
        remoteBeamConnection?.disconnect()
        remoteBeamConnection = null
    }

    private fun onKlippyUnbound() {
        klippyConnection = null
        klippyConnected = false
        if (!moonrakerConnected) {
            notifyStateChanged(State.IDLE)
        }
    }

    private fun onMoonrakerUnbound() {
        moonrakerConnection = null
        moonrakerConnected = false
        if (!klippyConnected) {
            notifyStateChanged(State.IDLE)
        }
    }

    private fun notifyStateChanged(state: State) {
        this.state = state
        KlipperApp.EVENT_BUS.fireEvent(InstanceStateChangedEvent(requireNotNull(id), state))

        if (state == State.IDLE) {
            slots.remove(this)
            if (slots.isEmpty()) {
                if (webServerConnection != null) {
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.STOPPING))
                    KlipperApp.INSTANCE.unbindService(webServerConnection!!)
                    KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, WebService::class.java))
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.IDLE))
                    webServerConnection = null
                }
                if (cameraServerConnection != null) {
                    KlipperApp.INSTANCE.unbindService(cameraServerConnection!!)
                    KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java))
                    cameraServerConnection = null
                }
            }
        } else if (state == State.RUNNING) {
            if (webServerConnection == null) {
                KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.STARTING))
                KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, WebService::class.java), object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(State.RUNNING))
                    }

                    override fun onServiceDisconnected(name: ComponentName) {}
                }.also { webServerConnection = it }, Context.BIND_AUTO_CREATE)
            }

            if (Prefs.isCameraEnabled) {
                if (cameraServerConnection == null) {
                    KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                        override fun onServiceDisconnected(name: ComponentName) {}
                    }.also { cameraServerConnection = it }, Context.BIND_AUTO_CREATE)
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
            override fun get(key: Any?): KlipperInstance? {
                var inst = super.get(key)
                if (inst == null) {
                    for (i in instances) {
                        if (key == i.id) {
                            put(key as String, i)
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
                KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                    override fun onServiceDisconnected(name: ComponentName) {}
                }.also { cameraServerConnection = it }, Context.BIND_AUTO_CREATE)
            } else if (cameraServerConnection != null && !enable) {
                KlipperApp.INSTANCE.unbindService(cameraServerConnection!!)
                KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java))
                cameraServerConnection = null
            }
        }
    }
}
