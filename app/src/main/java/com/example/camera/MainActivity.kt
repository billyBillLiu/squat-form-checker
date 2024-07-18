package com.example.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private lateinit var requestPermissionsButton: Button
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        } else {
            Log.i("requestPermissionLauncher", "No Permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsButton = findViewById(R.id.requestPermissionButton)
        requestPermissionsButton.setOnClickListener {
            requestPermissions()
        }
        if (permissionGranted()) {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        }
    }

    private fun permissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }


}