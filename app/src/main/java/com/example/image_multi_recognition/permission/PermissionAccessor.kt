package com.example.image_multi_recognition.permission

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    // String represents the permission String, for multiple permission, you use Array<String>
    // compulsory permissions
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    // these are permissions that indispensable for running this app, without them, the app should not run
    // for other permissions that are optional like POST_NOTIFICATIONS, we can request this permission for the first time we need it
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
            // if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            //     add(Manifest.permission.POST_NOTIFICATIONS)
            // }
        }.apply {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.apply {
            // add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

    fun ComponentActivity.hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun ComponentActivity.getNotGrantedPermissions(permissions: List<String>): List<String> =
        permissions.filterNot { hasPermission(it) }

    private fun ComponentActivity.hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    private fun ComponentActivity.showPermissionRationale(
        permissions: List<String>,
        permissionRationalDisplayed: Boolean = false, // whether showPermissionRationale is called because the user has denied the permission request
        positiveAction: () -> Unit,
    ) {
        setContent {
            AppTheme {
                var showPermissionDialog by rememberSaveable { mutableIntStateOf(1) }

                if (showPermissionDialog == 1 && !permissionRationalDisplayed) {
                    PermissionAlert(
                        title = stringResource(R.string.permission_required),
                        content = stringResource(
                            R.string.permission_required_message,
                            permissions.map { it.split(".").last() }),
                        confirmButtonText = stringResource(R.string.grant_permission),
                        onConfirm = {
                            showPermissionDialog = 0 // hide the dialog is the user choose to grant permissions
                            positiveAction()
                        },
                        dismissButtonText = stringResource(R.string.cancel),
                        onDismiss = { showPermissionDialog = 2 }
                    )
                } else if (showPermissionDialog == 2 || permissionRationalDisplayed) {
                    if (!hasAllPermissions(permissions)) {
                        PermissionAlert(
                            title = stringResource(R.string.no_permission),
                            content = stringResource(
                                if (permissionRationalDisplayed) R.string.no_permission_message_denied else R.string.no_permission_message_cancelled,
                                permissions.map { it.split(".").last() }),
                            confirmButtonText = stringResource(R.string.ok),
                            onConfirm = { finishAndRemoveTask() },
                            onDismiss = { finishAndRemoveTask() }
                        )
                    }
                }
            }
        }
    }

    fun ComponentActivity.runApp(afterAllPermissionGranted: () -> Unit) {
        // Ask for permission
        var permissionRationalDisplayed = false
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionMap ->
                // If the user has denied permission twice, this callback is called directly without showing grant permission window first
                if (permissionMap.all { it.value }) {
                    Log.i(this::class.simpleName, "All permissions are granted!")
                    afterAllPermissionGranted()
                } else {
                    val notGrantedPermission = permissionMap.filter { !it.value }.map { it.key }
                    showPermissionRationale(notGrantedPermission, permissionRationalDisplayed) {
                        requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
                    }
                    permissionRationalDisplayed = true
                }
            }

        if (hasAllPermissions(permissionList)) {
            afterAllPermissionGranted()
        } else {
            val notGrantedPermission = getNotGrantedPermissions(permissionList)
            // when first time the app is started, shouldShowRequestPermissionRationale returns false,
            // after that if the user reject permission, it returns true
            if (notGrantedPermission.all { shouldShowRequestPermissionRationale(it) }) {
                showPermissionRationale(notGrantedPermission, permissionRationalDisplayed) {
                    // once you reached here, it means that user has denied the permissions at least once
                    permissionRationalDisplayed = true
                    requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
                }
            } else {
                // the first you start app, goes here, the user has not made any decision about granting permissions
                requestPermissionLauncher.launch(notGrantedPermission.toTypedArray())
            }
        }
    }

    // for permission on-demand
    lateinit var permissionRequester: ActivityResultLauncher<String>

    @SuppressLint("ComposableNaming")
    @Composable
    private fun setPermissionRequester(
        afterPermissionGranted: () -> Unit,
        permissionDenied: () -> Unit,
    ) {
        permissionRequester =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
                if (permissionGranted) {
                    afterPermissionGranted()
                } else {
                    // Note PermissionRationaleForPostNotification() is a composable, and you cannot call it directly here
                    // Instead you should change certain State in "permissionDenied()" to show PermissionRationaleForPostNotification
                    permissionDenied()
                }
            }
    }

    // Not compulsory permission
    @Composable
    private fun PermissionRationaleForPostNotification(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        PermissionAlert(
            title = stringResource(R.string.no_permission),
            content = stringResource(R.string.require_notification_permission),
            confirmButtonText = stringResource(R.string.grant_permission),
            dismissButtonText = stringResource(R.string.cancel),
            onConfirm = onConfirm,
            // hide this window
            onDismiss = onCancel
        )
    }

    @SuppressLint("ComposableNaming")
    @Composable
    fun ComponentActivity.setupPermissionRequest(
        permission: String,
        provideShowPermissionRational: ()->Boolean,
        setShowPermissionRational: (Boolean)->Unit,
        onPermissionGranted: ()->Unit,
        onPermissionDenied: ()->Unit,
    ) {
        var permissionRationalDisplayed by remember { mutableStateOf(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // avoid resetting requester due to recomposition
            setPermissionRequester(
                afterPermissionGranted = {
                    onPermissionGranted()
                },
                permissionDenied = {
                    // Note when you have already denied permission twice before,
                    // the next time "permissionRequester" is started, the "permissionDenied" runs directly by system without showing permission request window
                    if (!permissionRationalDisplayed && this.shouldShowRequestPermissionRationale(permission)) {
                        // Note that you must call shouldShowRequestPermissionRationale() every time
                        // before showing your own PermissionRationale to get things right.
                        // To know that, we can use shouldShowRequestPermissionRationale()
                        setShowPermissionRational(true)
                    } else {
                        onPermissionDenied()
                    }
                }
            )
        }
        if (provideShowPermissionRational()) {
            permissionRationalDisplayed = true
            PermissionRationaleForPostNotification(
                onCancel = {
                    setShowPermissionRational(false)
                    onPermissionDenied()
                },
                onConfirm = {
                    setShowPermissionRational(false)
                    permissionRequester.launch(permission)
                }
            )
        }
    }
}

@Composable
fun PermissionAlert(
    title: String,
    content: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    // optional dismiss button
    dismissButtonText: String = "",
) {
    AlertDialog(
        modifier = Modifier.padding(12.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        text = {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(text = confirmButtonText)
            }
        },
        dismissButton = if (dismissButtonText.isNotEmpty()) {
            {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(text = dismissButtonText)
                }
            }
        } else null,
        onDismissRequest = onDismiss
    )
}