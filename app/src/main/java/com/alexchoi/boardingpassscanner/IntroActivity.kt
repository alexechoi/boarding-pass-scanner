package com.alexchoi.boardingpassscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class IntroActivity : AppCompatActivity() {

    private val startForImageResult = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // Change the target activity to MainActivity and pass the image URI
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("selectedImage", uri.toString())
            startActivity(intent)
        } else {
            Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        // Request for permissions when the activity is created
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        val startScanningButton: Button = findViewById(R.id.start_scanning_button)
        startScanningButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        val selectImageButton: Button = findViewById(R.id.select_image_button)
        selectImageButton.setOnClickListener {
            if (allPermissionsGranted()) {
                startForImageResult.launch("image/*")
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Show an explanation to the user
                    Toast.makeText(this, "This app needs camera and storage access to function properly.", Toast.LENGTH_LONG).show()
                    // Request the permissions again after the rationale has been shown
                    ActivityCompat.requestPermissions(
                        this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                    )
                } else {
                    Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
