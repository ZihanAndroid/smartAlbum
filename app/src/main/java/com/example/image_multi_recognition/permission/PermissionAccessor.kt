package com.example.image_multi_recognition.permission

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

// Do not want to hold a Context reference in another class like:
//  val activity: ComponentActivity
//  So, use "ComponentActivity.runApp()" to define method runApp()
@Singleton
class PermissionAccessor @Inject constructor() {
    //String represents the permission String, for multiple permission, you use Array<String>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private val permissionList =
        mutableListOf<String>().apply {
            addAll(
                // https://apilevels.com/
                // TIRAMISU: Android SDK 33
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
        }.apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.apply {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

    private fun ComponentActivity.hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun ComponentActivity.getNotGrantedPermissions(permissions: List<String>): List<String> =
        permissions.filterNot { hasPermission(it) }

    private fun ComponentActivity.hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    private fun ComponentActivity.showPermissionRationale(permissions: List<String>, positiveAction: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app will not work without the following permissions: $permissions")
            .setPositiveButton("Grant Permission") { _, _ -> positiveAction() }
            .setNegativeButton("Cancel") { dialog, _ -> finalPermissionPrompt(permissions) }
            .setOnCancelListener { finalPermissionPrompt(permissions) }
            .create().show()
    }

    private fun ComponentActivity.finalPermissionPrompt(permissions: List<String>) {
        if (!hasAllPermissions(permissions)) {
            AlertDialog.Builder(this)
                .setTitle("No Permission")
                .setMessage("$permissions ${if (permissions.size > 1) "are" else "is"} not granted, the app will stop running")
                .setPositiveButton("OK") { _, _ -> finishAndRemoveTask() }
                .setOnCancelListener { finishAndRemoveTask() }
                .create().show()
        }
    }

    fun ComponentActivity.runApp(afterAllPermissionGranted: () -> Unit) {
        // Ask for permission
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionMap ->
                if (permissionMap.all { it.value }) {
                    Log.i(this::class.simpleName, "All permissions are granted!")
                    afterAllPermissionGranted()
                } else {
                    val notGrantedPermission = permissionMap.filter { !it.value }.map { it.key }
                    showPermissionRationale(notGrantedPermission) {
                        requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
                    }
                }
            }

        if (hasAllPermissions(permissionList)) {
            afterAllPermissionGranted()
        } else {
            val notGrantedPermission = getNotGrantedPermissions(permissionList)
            if (notGrantedPermission.all { shouldShowRequestPermissionRationale(it) }) {
                showPermissionRationale(notGrantedPermission) {
                    requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
                }
            } else {
                requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
            }
        }
    }
}