package com.example.edgeviewer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    companion object {
        init { System.loadLibrary("native-lib") }
    }

    private lateinit var textureView: TextureView
    private lateinit var glRenderer: GLTextureRenderer
    private var cameraPreview: CameraPreview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        textureView = TextureView(this)
        textureView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(textureView)

        val btn = Button(this).apply {
            text = "Start"
            setOnClickListener { startCapture(root) }
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(btn, lp)

        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }

        glRenderer = GLTextureRenderer(this)
    }

    private fun startCapture(root: ViewGroup) {
        if (!textureView.isAvailable) {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                    cameraPreview = CameraPreview(this@MainActivity, surfaceTexture, width, height) { bmp ->
                        glRenderer.updateBitmap(bmp)
                    }
                    cameraPreview?.start()
                    glRenderer.attachTo(root)
                }
                override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(s: android.graphics.SurfaceTexture): Boolean {
                    cameraPreview?.stop()
                    return true
                }
                override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {}
            }
        } else {
            val surface = textureView.surfaceTexture
            cameraPreview = CameraPreview(this, surface!!, textureView.width, textureView.height) { bmp ->
                glRenderer.updateBitmap(bmp)
            }
            cameraPreview?.start()
            glRenderer.attachTo(root)
        }
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission granted. Tap Start.", Toast.LENGTH_SHORT).show()
        } else super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
