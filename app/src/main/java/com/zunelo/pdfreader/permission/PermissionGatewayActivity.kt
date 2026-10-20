package com.zunelo.pdfreader.permission

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.*
import com.zunelo.pdfreader.common.*
import com.zunelo.pdfreader.data.*
import com.zunelo.pdfreader.dialog.*
import com.zunelo.pdfreader.home.*
import com.zunelo.pdfreader.onboarding.*
import com.zunelo.pdfreader.pdf.*
import com.zunelo.pdfreader.permission.*
import com.zunelo.pdfreader.startup.*
import com.zunelo.pdfreader.tools.*
import com.zunelo.pdfreader.viewer.*

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class PermissionGatewayActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_DESTINATION = "destination"
        private const val DESTINATION_ALL_FILES = "all_files"
        private const val DESTINATION_APP_DETAILS = "app_details"

        fun allFilesIntent(context: Context) = Intent(context, PermissionGatewayActivity::class.java)
            .putExtra(EXTRA_DESTINATION, DESTINATION_ALL_FILES)

        fun appDetailsIntent(context: Context) = Intent(context, PermissionGatewayActivity::class.java)
            .putExtra(EXTRA_DESTINATION, DESTINATION_APP_DETAILS)
    }

    private var settingsLaunched = false
    private var returnRequested = false
    private val permissionHandler = Handler(Looper.getMainLooper())
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (DocumentStore.hasAccess(this@PermissionGatewayActivity)) requestReturnToApp()
            else permissionHandler.postDelayed(this, 250L)
        }
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finishWithPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        settingsLaunched = savedInstanceState?.getBoolean("settings_launched") ?: false
        if (DocumentStore.hasAccess(this)) {
            finishWithPermissionState()
        } else if (!settingsLaunched) {
            openSettings()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settings_launched", settingsLaunched)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (DocumentStore.hasAccess(this)) finishWithPermissionState()
    }

    override fun onDestroy() {
        permissionHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun openSettings() {
        val destination = intent.getStringExtra(EXTRA_DESTINATION)
        val packageUri = Uri.parse("package:$packageName")
        val primary = when (destination) {
            DESTINATION_APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            }
        }
        val fallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            Intent(Settings.ACTION_APPLICATION_SETTINGS)
        }
        settingsLaunched = true
        permissionHandler.post(permissionCheck)
        if (runCatching { settingsLauncher.launch(primary) }.isFailure && runCatching { settingsLauncher.launch(fallback) }.isFailure) {
            finishWithPermissionState()
        }
    }

    private fun requestReturnToApp() {
        if (returnRequested) return
        returnRequested = true
        val returnIntent = Intent(this, PermissionGatewayActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        runCatching { startActivity(returnIntent) }.onFailure { returnRequested = false }
        permissionHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                returnRequested = false
                permissionHandler.post(permissionCheck)
            }
        }, 800L)
    }

    private fun finishWithPermissionState() {
        permissionHandler.removeCallbacksAndMessages(null)
        setResult(if (DocumentStore.hasAccess(this)) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
        overridePendingTransition(0, 0)
    }
}
