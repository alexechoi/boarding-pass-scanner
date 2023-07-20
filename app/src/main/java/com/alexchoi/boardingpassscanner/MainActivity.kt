package com.alexchoi.boardingpassscanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.view.PreviewView
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.BarcodeFormat
import java.nio.ByteBuffer
import android.view.Menu
import androidx.appcompat.app.AlertDialog
import android.view.MenuItem

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val REQUEST_CODE_PERMISSIONS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        val scanButton: Button = findViewById(R.id.scanButton)
        scanButton.setOnClickListener {
            imageCapture?.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Scanning is done in the BarcodeAnalyzer class now, so we don't need to scan here
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Capture failed: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                        Log.e("CameraX", "Capture failed: ${exception.localizedMessage}", exception)
                    }
                }
            })
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).getSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder()
                .setBufferFormat(ImageFormat.YUV_420_888)
                .build()

            // ImageAnalysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(this, Handler(Looper.getMainLooper())) { barcode ->
                        runOnUiThread {
                            Toast.makeText(this, barcode, Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, ResultsActivity::class.java)
                            intent.putExtra("scannedInfo", barcode)
                            startActivity(intent)
                        }
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis)

            } catch(exc: Exception) {
                Toast.makeText(this, "Use case binding failed: ${exc.localizedMessage}", Toast.LENGTH_SHORT).show()
                Log.e("CameraX", "Use case binding failed: ${exc.localizedMessage}", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private class BarcodeAnalyzer(
        private val context: Context,
        private val uiHandler: Handler,
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {
        private val reader = MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.PDF_417)
            ))
        }

        override fun analyze(image: ImageProxy) {
            val result = scanBarcodeFromImage(image)
            if (result != null) {
                onBarcodeDetected(result)
            } else {
                uiHandler.post {
                    Toast.makeText(context, "No barcode detected", Toast.LENGTH_SHORT).show()
                }
            }
            image.close()
        }

        private fun scanBarcodeFromImage(image: ImageProxy): String? {
            val nv21 = yuv420888ToNv21(image).map { it.toInt() }.toIntArray()
            val source = RGBLuminanceSource(image.width, image.height, nv21)

            return try {
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decodeWithState(binaryBitmap)
                result.text
            } catch (e: NotFoundException) {
                null
            }
        }

        private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
            val pixelCount = image.cropRect.width() * image.cropRect.height()
            val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
            if(image.format != ImageFormat.YUV_420_888) {
                uiHandler.post {
                    Toast.makeText(context, "Image format must be YUV_420_888", Toast.LENGTH_SHORT).show()
                    Log.e("CameraX", "Image format must be YUV_420_888, but got " + image.format)
                }
                return outputBuffer
            }
            imageToByteBuffer(image, outputBuffer, pixelCount)
            return outputBuffer
        }

        private fun imageToByteBuffer(image: ImageProxy, outputBuffer: ByteArray, pixelCount: Int) {
            val imageCrop = image.cropRect
            val imagePlanes = image.planes

            imagePlanes.forEachIndexed { planeIndex, plane ->
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride

                val planeCrop = if (planeIndex == 0) {
                    imageCrop
                } else {
                    Rect(
                        imageCrop.left / 2,
                        imageCrop.top / 2,
                        imageCrop.right / 2,
                        imageCrop.bottom / 2
                    )
                }

                val planeWidth = planeCrop.width()
                val planeHeight = planeCrop.height()

                val rowData = ByteArray(rowStride)

                var channelOffset = 0
                val outputStride = 1.shl(planeIndex)
                for (row in 0 until planeHeight) {
                    val rowOffset = planeIndex * pixelCount / 2 + row * outputStride

                    if (pixelStride == 1 && outputStride == 1) {
                        val width = Math.min(planeWidth, buffer.remaining())
                        buffer.get(outputBuffer, rowOffset, width)
                        buffer.position(buffer.position() + rowStride - width)
                    } else {
                        buffer.get(rowData, 0, rowStride)

                        for (col in 0 until planeWidth) {
                            outputBuffer[rowOffset + col] = rowData[col * pixelStride + channelOffset]
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                showInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Instructions")
            .setMessage("Place the boarding pass inside the frame to scan it. Make sure the pass is well lit and clearly visible.")
            .setPositiveButton("Got it", null)
            .show()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
