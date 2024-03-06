package com.example.image_multi_recognition

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.image_multi_recognition.compose.navigation.Home
import com.example.image_multi_recognition.permission.PermissionAccessor
import com.example.image_multi_recognition.ui.theme.Image_multi_recognitionTheme
import com.example.image_multi_recognition.util.ScopedThumbNailStorage
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionAccessor: PermissionAccessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(permissionAccessor) { runApp(::afterAllPermissionGranted) }

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        Log.i("MainActivity", "imageDir: ${dir.absolutePath}")
    }

    // App main logic is here
    private fun afterAllPermissionGranted() {
        var checkResult = true
        with(ScopedThumbNailStorage) {
            if (!setupScopedStorage()) {
                setContent {
                    Image_multi_recognitionTheme {
                        AlertDialog(
                            title = {
                                Text(
                                    text = stringResource(R.string.fatal_error),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.fatal_error_message),
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
                checkResult = false
            }
        }
        Log.d(getCallSiteInfo(), "ScopedThumbNailStorage: ${ScopedThumbNailStorage.imageStorage}")
        if (checkResult) {
            setContent {
                Image_multi_recognitionTheme {
                    // A surface container using the 'background' color from the theme
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Home(photoViewModel = viewModel())
                        // TestComposable()
                    }
                }
            }
        }
    }
}


