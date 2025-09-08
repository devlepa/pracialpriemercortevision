package com.example.parcial

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: JavaCameraView
    private lateinit var galleryView: ImageView

    private lateinit var btnNone: Button
    private lateinit var btnGray: Button
    private lateinit var btnCanny: Button
    private lateinit var btnSepia: Button
    private lateinit var btnCartoon: Button
    private lateinit var btnGallery: Button

    private var currentFilter = Filter.NONE

    private var rgba: Mat? = null

    private var galleryBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            galleryBitmap = bmp
            applyFilterToGallery()
        }
    }

    enum class Filter { NONE, GRAY, CANNY, SEPIA, CARTOON }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Views
        cameraView = findViewById(R.id.cameraView)
        galleryView = findViewById(R.id.galleryView)

        btnNone = findViewById(R.id.btnNone)
        btnGray = findViewById(R.id.btnGray)
        btnCanny = findViewById(R.id.btnCanny)
        btnSepia = findViewById(R.id.btnSepia)
        btnCartoon = findViewById(R.id.btnCartoon)
        btnGallery = findViewById(R.id.btnGallery)

        // OpenCV Camera
        cameraView.visibility = JavaCameraView.VISIBLE
        cameraView.setCameraPermissionGranted()
        cameraView.setCvCameraViewListener(this)
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)

        // Listeners
        btnNone.setOnClickListener { setFilter(Filter.NONE) }
        btnGray.setOnClickListener { setFilter(Filter.GRAY) }
        btnCanny.setOnClickListener { setFilter(Filter.CANNY) }
        btnSepia.setOnClickListener { setFilter(Filter.SEPIA) }
        btnCartoon.setOnClickListener { setFilter(Filter.CARTOON) }
        btnGallery.setOnClickListener { pickImageLauncher.launch("image/*") }

        checkPermissions()
    }

    private fun setFilter(f: Filter) {
        currentFilter = f
        applyFilterToGallery()
        Toast.makeText(this, "Filtro: $f", Toast.LENGTH_SHORT).show()
    }

    // ======== Galería ========
    private fun applyFilterToGallery() {
        val bmp = galleryBitmap ?: return

        val src = Mat()
        Utils.bitmapToMat(bmp, src)

        // Asegurar RGBA
        when (src.channels()) {
            1 -> Imgproc.cvtColor(src, src, Imgproc.COLOR_GRAY2RGBA)
            3 -> Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGBA)
        }

        val outMat = applyCurrentFilter(src)
        val outBmp = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outMat, outBmp)
        galleryView.setImageBitmap(outBmp)

        src.release()
        outMat.release()
    }

    // Aplica el filtro seleccionado a una Mat (se usa tanto para cámara como para galería)
    private fun applyCurrentFilter(inputRgba: Mat): Mat {
        val dst = Mat()
        when (currentFilter) {
            Filter.NONE -> inputRgba.copyTo(dst)

            Filter.GRAY -> {
                val g = Mat()
                Imgproc.cvtColor(inputRgba, g, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.cvtColor(g, dst, Imgproc.COLOR_GRAY2RGBA)
                g.release()
            }

            Filter.CANNY -> {
                val g = Mat()
                Imgproc.cvtColor(inputRgba, g, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.GaussianBlur(g, g, Size(5.0, 5.0), 0.0)
                val edges = Mat()
                Imgproc.Canny(g, edges, 80.0, 150.0)
                Imgproc.cvtColor(edges, dst, Imgproc.COLOR_GRAY2RGBA)
                g.release(); edges.release()
            }

            Filter.SEPIA -> {
                val kernel = Mat(4, 4, CvType.CV_32F)
                val data = floatArrayOf(
                    0.272f, 0.534f, 0.131f, 0f,
                    0.349f, 0.686f, 0.168f, 0f,
                    0.393f, 0.769f, 0.189f, 0f,
                    0f,     0f,     0f,     1f
                )
                kernel.put(0, 0, data)
                Core.transform(inputRgba, dst, kernel)
                kernel.release()
            }

            Filter.CARTOON -> {
                val g = Mat()
                Imgproc.cvtColor(inputRgba, g, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.medianBlur(g, g, 7)
                val edges = Mat()
                Imgproc.adaptiveThreshold(
                    g, edges, 255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY, 9, 2.0
                )
                val color = Mat()
                Imgproc.bilateralFilter(inputRgba, color, 9, 75.0, 75.0)
                val edgesColor = Mat()
                Imgproc.cvtColor(edges, edgesColor, Imgproc.COLOR_GRAY2RGBA)
                Core.bitwise_and(color, edgesColor, dst)
                g.release(); edges.release(); color.release(); edgesColor.release()
            }
        }
        return dst
    }

    // ======== OpenCV Lifecycle ========
    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            cameraView.enableView()
        } else {
            Toast.makeText(this, "OpenCV no cargó", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }

    // ======== Camera callbacks ========
    override fun onCameraViewStarted(width: Int, height: Int) {
        rgba = Mat(height, width, CvType.CV_8UC4)
    }

    override fun onCameraViewStopped() {
        rgba?.release(); rgba = null
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val frame = inputFrame.rgba()
        // Aplicar el filtro actual al frame
        val out = applyCurrentFilter(frame)
        return out
    }

    // ======== Permisos ========
    private fun checkPermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA)
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1001)
        }
    }
}
