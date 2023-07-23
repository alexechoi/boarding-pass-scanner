package com.alexchoi.boardingpassscanner

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.alexchoi.boardingpassscanner.databinding.ActivityMainBinding
import com.google.zxing.ResultPoint
import org.json.JSONException
import org.json.JSONObject
import android.content.Intent
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isScanning = false
    private var isTorchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Request camera permissions
        if (allPermissionsGranted()) {
            setupScanner()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for scan button
        binding.scanButton.setOnClickListener {
            if (!isScanning) {
                it.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(200)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200)
                            performAction()
                        }
                    })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            binding.barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeView.pause()
    }

    private fun setupScanner() {
        binding.barcodeView.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                // handle the scanned barcode
                try {
                    // Converting the data to json format
                    val obj = JSONObject(result.text)

                    // Show values in UI.
                    Toast.makeText(this@MainActivity, "Barcode Result = ${obj.toString(4)}", Toast.LENGTH_LONG).show()

                } catch (e: JSONException) {
                    e.printStackTrace()

                    // Data not in the expected format. So, whole object as toast message.
                    Toast.makeText(this@MainActivity, result.text, Toast.LENGTH_LONG).show()
                }
            }

            override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
        })
    }

    private fun performAction() {
        isScanning = true
        binding.progressBar.visibility = View.VISIBLE  // Show the ProgressBar
        val callback = object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                runOnUiThread {
                    val intent = Intent(this@MainActivity, ResultsActivity::class.java)
                    intent.putExtra("scannedInfo", result.text)
                    startActivity(intent)
                    isScanning = false
                    binding.progressBar.visibility = View.GONE  // Hide the ProgressBar
                }
            }

            override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
        }
        binding.barcodeView.decodeSingle(callback)

        // Set a timer to set isScanning to false after 10 seconds if no barcode is found
        Handler(Looper.getMainLooper()).postDelayed({
            if (isScanning) {
                isScanning = false
                binding.progressBar.visibility = View.GONE  // Hide the ProgressBar
                Toast.makeText(this, "No boarding pass could be found", Toast.LENGTH_LONG).show()
            }
        }, 10000)  // 10 seconds
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_flash -> {
                if (isTorchOn) {
                    binding.barcodeView.setTorchOff()
                } else {
                    binding.barcodeView.setTorchOn()
                }
                isTorchOn = !isTorchOn  // Update the torch state
                true
            }
            R.id.action_info -> {
                // handle info item click
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Instructions")
                builder.setMessage("1. Place your boarding pass inside the scanner viewfinder.\n" +
                        "2. The app will automatically scan the barcode.\n" +
                        "3. Make sure there's enough light and the barcode is clear to the camera.\n" +
                        "4. If your device has a flash, you can use it in low light conditions.\n" +
                        "5. The app works offline, so you can scan your boarding pass without an internet connection.")
                builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                builder.create().show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
