package com.example.edgeviewer

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.SurfaceView
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.OutputStream

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: JavaCameraView
    private lateinit var tvStatus: TextView
    private lateinit var tvThresholdValue: TextView
    private lateinit var seekThreshold: SeekBar
    private lateinit var btnCapture: Button
    private lateinit var btnSwitchCamera: Button
    private lateinit var btnToggleEdges: Button

    private var rgbaMat: Mat? = null
    private var grayMat: Mat? = null
    private var edgesMat: Mat? = null

    private var lastFrame: Mat? = null

    private var cannyThreshold: Double = 80.0
    private var showEdges: Boolean = true
    private var useFrontCamera: Boolean = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101

        init {
            // Native lib for JNI
            System.loadLibrary("native-lib")
        }
    }

    // JNI function implemented in native-lib.cpp
    external fun stringFromJNI(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.camera_view)
        tvStatus = findViewById(R.id.tvStatus)
        tvThresholdValue = findViewById(R.id.tvThresholdValue)
        seekThreshold = findViewById(R.id.seekThreshold)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnToggleEdges = findViewById(R.id.btnToggleEdges)

        // Show JNI message once
        tvStatus.text = "JNI says: ${stringFromJNI()}\nInitializing OpenCV..."

        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)

        setupSeekBar()
        setupButtons()

        if (hasCameraPermission()) {
            // Important: tell OpenCV view that permission is granted
            cameraView.setCameraPermissionGranted()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    private fun setupSeekBar() {
        tvThresholdValue.text = cannyThreshold.toInt().toString()
        seekThreshold.progress = cannyThreshold.toInt()

        seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                cannyThreshold = if (progress < 10) 10.0 else progress.toDouble()
                tvThresholdValue.text = cannyThreshold.toInt().toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        btnToggleEdges.setOnClickListener {
            showEdges = !showEdges
            btnToggleEdges.text = if (showEdges) "Edges ON" else "Edges OFF"
        }

        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        btnCapture.setOnClickListener {
            captureCurrentFrame()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun switchCamera() {
        cameraView.disableView()

        useFrontCamera = !useFrontCamera
        val newIndex = if (useFrontCamera) {
            CameraBridgeViewBase.CAMERA_ID_FRONT
        } else {
            CameraBridgeViewBase.CAMERA_ID_BACK
        }

        cameraView.setCameraIndex(newIndex)
        tvStatus.text = if (useFrontCamera) "Front camera" else "Back camera"

        if (hasCameraPermission()) {
            cameraView.setCameraPermissionGranted()
            cameraView.enableView()
        }
    }

    private fun captureCurrentFrame() {
        val frame = lastFrame
        if (frame == null || frame.empty()) {
            Toast.makeText(this, "No frame to capture yet", Toast.LENGTH_SHORT).show()
            return
        }

        val matForSave = frame.clone()
        try {
            saveMatToGallery(matForSave)
            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            matForSave.release()
        }
    }

    private fun saveMatToGallery(mat: Mat) {
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)

        val filename = "edge_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/EdgeViewer"
                )
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw RuntimeException("Failed to create MediaStore record")

        var out: OutputStream? = null
        try {
            out = resolver.openOutputStream(uri)
                ?: throw RuntimeException("Failed to open output stream")
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            out?.close()
        }
    }

    // ---- OpenCV lifecycle ----

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            tvStatus.text = "OpenCV loaded (JNI: ${stringFromJNI()}). Starting camera..."

            if (hasCameraPermission()) {
                cameraView.setCameraPermissionGranted()
                cameraView.enableView()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST
                )
            }
        } else {
            tvStatus.text = "OpenCV init failed"
        }
    }

    override fun onPause() {
        super.onPause()
        if (::cameraView.isInitialized) {
            cameraView.disableView()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraView.isInitialized) {
            cameraView.disableView()
        }

        rgbaMat?.release()
        grayMat?.release()
        edgesMat?.release()
        lastFrame?.release()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        rgbaMat = Mat(height, width, CvType.CV_8UC4)
        grayMat = Mat(height, width, CvType.CV_8UC1)
        edgesMat = Mat(height, width, CvType.CV_8UC1)
        tvStatus.text = "Camera started. Showing edges..."
    }

    override fun onCameraViewStopped() {
        rgbaMat?.release()
        grayMat?.release()
        edgesMat?.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val rgba = inputFrame?.rgba() ?: return Mat()

        val gray = grayMat!!
        val edges = edgesMat!!

        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(gray, edges, cannyThreshold, cannyThreshold * 3)

        val output = if (showEdges) {
            val edgesColor = Mat()
            Imgproc.cvtColor(edges, edgesColor, Imgproc.COLOR_GRAY2RGBA)
            rgba.setTo(Scalar(0.0, 0.0, 0.0, 255.0))
            edgesColor.copyTo(rgba)
            edgesColor.release()
            rgba
        } else {
            rgba
        }

        lastFrame?.release()
        lastFrame = output.clone()

        return output
    }

    // ---- Permission result ----

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                cameraView.setCameraPermissionGranted()
                cameraView.enableView()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
