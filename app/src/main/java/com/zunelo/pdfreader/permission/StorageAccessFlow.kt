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

import android.Manifest
import android.os.Build
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

class StorageAccessFlow(
    private val fragment: Fragment,
    private val onGranted: () -> Unit,
    private val onDenied: () -> Unit,
    private val requireWriteAccess: Boolean = false
) {
    private val preferences by lazy { fragment.requireContext().getSharedPreferences("storage_access", 0) }
    private var waitingForGrant = false
    private val legacyLauncher = fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        handlePermissionResult()
    }
    private val settingsLauncher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) handlePermissionResult() else denyPermissionRequest()
    }

    fun request() {
        if (hasRequiredAccess()) return onGranted()
        waitingForGrant = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) openAllFilesSettings() else requestLegacyAccess()
    }

    fun onResume() = Unit

    private fun openAllFilesSettings() {
        (fragment.requireActivity() as? MainActivity)?.suppressNextPageAdRefresh()
        if (runCatching { settingsLauncher.launch(PermissionGatewayActivity.allFilesIntent(fragment.requireContext())) }.isFailure) {
            denyPermissionRequest()
        }
    }

    private fun requestLegacyAccess() {
        val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val requestedBefore = preferences.getBoolean("legacy_requested", false)
        val permanentlyDenied = requestedBefore && permissions.any { !fragment.shouldShowRequestPermissionRationale(it) }
        if (permanentlyDenied) {
            (fragment.requireActivity() as? MainActivity)?.suppressNextPageAdRefresh()
            settingsLauncher.launch(PermissionGatewayActivity.appDetailsIntent(fragment.requireContext()))
        } else {
            preferences.edit().putBoolean("legacy_requested", true).apply()
            legacyLauncher.launch(permissions)
        }
    }

    private fun handlePermissionResult() {
        if (hasRequiredAccess()) {
            if (waitingForGrant) SmartPdfSignalHub.recordCustomSignal("file_permission_success")
            waitingForGrant = false
            onGranted()
        } else {
            denyPermissionRequest()
        }
    }

    private fun denyPermissionRequest() {
        waitingForGrant = false
        onDenied()
    }

    private fun hasRequiredAccess(): Boolean = if (requireWriteAccess) {
        DocumentStore.canModifyStorage(fragment.requireContext())
    } else {
        DocumentStore.hasAccess(fragment.requireContext())
    }
}
