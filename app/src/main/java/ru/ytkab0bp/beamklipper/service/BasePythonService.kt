package ru.ytkab0bp.beamklipper.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import ru.ytkab0bp.beamklipper.KlipperInstance

open class BasePythonService : Service() {
    companion object {
        const val KEY_INSTANCE = "instance"
    }

    private val TAG = "python_service"
    private var py: Python? = null
    private var pythonThread: HandlerThread? = null
    private var pythonHandler: Handler? = null
    protected var instance: KlipperInstance? = null
    protected lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        instance = KlipperInstance.getInstance(intent!!.getStringExtra(KEY_INSTANCE)!!)
        pythonHandler?.post { onStartPython() }
        return Binder()
    }

    override fun onCreate() {
        super.onCreate()
        android.os.Process.setThreadPriority(-20)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        pythonThread = HandlerThread(javaClass.name).also { it.start() }
        pythonHandler = Handler(pythonThread!!.looper)
        pythonHandler?.post {
            android.os.Process.setThreadPriority(-20)
            val platform = AndroidPlatform(this@BasePythonService)
            while (true) {
                try {
                    platform.path
                    Python.start(platform)
                    py = Python.getInstance()
                    break
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pythonThread?.quitSafely()
        pythonThread = null
        pythonHandler = null
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    protected open fun onStartPython() {}

    protected fun runPython(dir: File, module: String, vararg args: String) {
        py!!.getModule("sys")["path"].callAttr("append", dir.absolutePath)
        val pyModule = Python.getInstance().getModule(module)
        val argv = pyModule["sys"]["argv"].asList<PyObject>()
        argv.clear()
        for (arg in args) argv.add(PyObject.fromJava(arg))
        try {
            pyModule.callAttr("main")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start $module", e)
        }
    }
}
