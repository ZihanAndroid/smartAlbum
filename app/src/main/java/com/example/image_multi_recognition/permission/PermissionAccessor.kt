package com.example.image_multi_recognition.permission

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.ui.theme.AppTheme
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
        }.apply {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

    private fun ComponentActivity.hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun ComponentActivity.getNotGrantedPermissions(permissions: List<String>): List<String> =
        permissions.filterNot { hasPermission(it) }

    private fun ComponentActivity.hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    private fun ComponentActivity.showPermissionRationale(
        permissions: List<String>,
        permissionRequestDenied: Boolean = false, // whether showPermissionRationale is called because the user has denied the permission request
        positiveAction: () -> Unit
    ) {
        setContent {
            AppTheme {
                var showPermissionDialog by rememberSaveable { mutableStateOf(1) }

                if (showPermissionDialog == 1 && !permissionRequestDenied) {
                    AlertDialog(
                        title = {
                            Text(
                                text = stringResource(R.string.permission_required),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(
                                    R.string.permission_required_message,
                                    permissions.map { it.split(".").last() }),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPermissionDialog = 0 // hide the dialog is the user choose to grant permissions
                                    positiveAction()
                                }
                            ) {
                                Text(text = stringResource(R.string.grant_permission))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showPermissionDialog = 2 }
                            ) {
                                Text(text = stringResource(R.string.cancel))
                            }
                        },
                        onDismissRequest = { showPermissionDialog = 2 }
                    )
                } else if (showPermissionDialog == 2 || permissionRequestDenied) {
                    if (!hasAllPermissions(permissions)) {
                        AlertDialog(
                            title = {
                                Text(
                                    text = stringResource(R.string.no_permission),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(
                                        if (permissionRequestDenied) R.string.no_permission_message_denied else R.string.no_permission_message_cancelled,
                                        permissions.map { it.split(".").last() }),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { finishAndRemoveTask() }
                                ) {
                                    Text(text = stringResource(R.string.ok))
                                }
                            },
                            onDismissRequest = { finishAndRemoveTask() }
                        )
                    }
                }
            }
        }
    }

    fun ComponentActivity.runApp(afterAllPermissionGranted: () -> Unit) {
        // Ask for permission
        var permissionRequestDenied = false
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionMap ->
                // If the user has denied permission twice, this callback is called directly without showing grant permission window first
                if (permissionMap.all { it.value }) {
                    Log.i(this::class.simpleName, "All permissions are granted!")
                    afterAllPermissionGranted()
                } else {
                    val notGrantedPermission = permissionMap.filter { !it.value }.map { it.key }
                    showPermissionRationale(notGrantedPermission, permissionRequestDenied) {
                        requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
                    }
                    permissionRequestDenied = true
                }
            }

        if (hasAllPermissions(permissionList)) {
            afterAllPermissionGranted()
        } else {
            val notGrantedPermission = getNotGrantedPermissions(permissionList)
            if (notGrantedPermission.all { shouldShowRequestPermissionRationale(it) }) {
                showPermissionRationale(notGrantedPermission, permissionRequestDenied) {
                    permissionRequestDenied = true
                    requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
                }
            } else {
                requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
            }
        }
    }
}