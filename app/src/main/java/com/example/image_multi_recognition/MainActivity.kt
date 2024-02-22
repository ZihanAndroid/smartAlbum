package com.example.image_multi_recognition

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.image_multi_recognition.compose.navigation.Home
import com.example.image_multi_recognition.permission.PermissionAccessor
import com.example.image_multi_recognition.ui.theme.Image_multi_recognitionTheme
import com.example.image_multi_recognition.util.ScopedThumbNailStorage
import com.example.image_multi_recognition.util.getCallSiteInfo
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
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Fatal Error")
                    .setMessage("Cannot access app scoped storage!")
                    .setPositiveButton("OK") { _, _ -> finishAndRemoveTask() }
                    .setOnCancelListener { finishAndRemoveTask() }
                    .create().show()
                checkResult = false
            }
        }
        Log.d(getCallSiteInfo(), "ScopedThumbNailStorage: ${ScopedThumbNailStorage.imageStorage}")
        if(checkResult) {
            setContent {
                Image_multi_recognitionTheme {
                    // A surface container using the 'background' color from the theme
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Home()
                    }
                }
            }
        }
    }
}


