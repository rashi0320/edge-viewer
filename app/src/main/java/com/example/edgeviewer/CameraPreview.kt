package com.example.edgeviewer

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.Camera
import java.nio.ByteBuffer

class CameraPreview(
    private val activity: android.app.Activity,
    private val surfaceTexture: android.graphics.SurfaceTexture,
    private val width: Int,
    private val height: Int,
    private val onProcessed: (Bitmap) -> Unit
) {
    private var camera: Camera? = null
    private val previewCallback = Camera.PreviewCallback { data, cam ->
        val params = cam.parameters
        val psize = params.previewSize
        val w = psize.width
        val h = psize.height

        // Native processing: NV21 -> RGBA bytes
        val rgba = nativeProcessFrame(data, w, h)

        // Convert RGBA bytes -> Bitmap (ARGB_8888)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.wrap(rgba)
        bmp.copyPixelsFromBuffer(buffer)
        onProcessed(bmp)
    }

    fun start() {
        camera = Camera.open()
        camera?.let { cam ->
            val params = cam.parameters
            val psize = params.previewSize ?: Camera.Size(width, height)
            params.previewSize = psize
            params.previewFormat = ImageFormat.NV21
            cam.parameters = params
            try {
                cam.setPreviewTexture(surfaceTexture)
                cam.setPreviewCallback(previewCallback)
                cam.startPreview()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun stop() {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    private external fun nativeProcessFrame(nv21: ByteArray, w: Int, h: Int): ByteArray
}
