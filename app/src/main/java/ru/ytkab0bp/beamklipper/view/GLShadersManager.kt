package ru.ytkab0bp.beamklipper.view

import android.util.Log
import ru.ytkab0bp.beamklipper.KlipperApp
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.*

class GLShadersManager {
    companion object {
        const val KEY_INTRO = "beam_intro"
    }

    private val shaders = HashMap<String, GLShader>()
    internal val shaderStack = Stack<GLShader>()

    private fun read(input: java.io.InputStream): String {
        val buffer = ByteArray(10240)
        val bos = ByteArrayOutputStream()
        var c: Int
        while (input.read(buffer).also { c = it } != -1) {
            bos.write(buffer, 0, c)
        }
        input.close()
        bos.close()
        return String(bos.toByteArray(), StandardCharsets.UTF_8)
    }

    fun get(key: String): GLShader {
        var shader = shaders[key]
        if (shader == null) {
            var tries = 0
            while (tries <= 30) {
                try {
                    shader = GLShader(this,
                        read(KlipperApp.INSTANCE.assets.open("shaders/$key.vs")),
                        read(KlipperApp.INSTANCE.assets.open("shaders/$key.fs")))
                    break
                } catch (e: Exception) {
                    Log.w("GLShaders", "Failed to load shader $key", e)
                    tries++
                }
            }
            if (shader != null) shaders[key] = shader
        }
        return shader!!
    }

    fun getCurrent(): GLShader? = if (shaderStack.isEmpty()) null else shaderStack.peek()

    fun release() {
        for (shader in shaders.values) shader.release()
        shaders.clear()
        shaderStack.clear()
    }
}
