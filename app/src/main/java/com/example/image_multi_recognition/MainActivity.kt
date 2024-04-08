package com.example.image_multi_recognition

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.image_multi_recognition.compose.navigation.Home
import com.example.image_multi_recognition.permission.PermissionAccessor
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.ui.theme.AppTheme
import com.example.image_multi_recognition.util.ScopedThumbNailStorage
import com.example.image_multi_recognition.util.getCallSiteInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionAccessor: PermissionAccessor

    @Inject
    lateinit var repository: ImageRepository

    @Inject
    lateinit var settingRepository: UserSettingRepository

    private lateinit var initialSetting: AppData


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
                    AppTheme {
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
        val settingJob = lifecycleScope.launch {
            initialSetting = settingRepository.settingFlow.first()
        }
        lifecycleScope.launch {
            // wait for initialSetting is set
            settingJob.join()
            if (checkResult) {
                setContent {
                    val appDataSetting by settingRepository.settingFlow.collectAsStateWithLifecycle(initialSetting)
                    // set system bar transparent
                    // Problems with multiple Scaffold in a screen when using enableEdgeToEdge
                    // https://slack-chats.kotlinlang.org/t/16057136/with-enableedgetoedge-the-height-of-the-system-navbar-gestur
                    // Solution: if a scaffold does not have topAppBar or bottomAppBar, do not pass the top padding or bottom padding to its children
                    // Because after applying "enableEdgeToEdge", for each Scaffold, a fixed padding is attached no matter whether the topAppBar or bottomAppBar is empty or not
                    enableEdgeToEdge(
                        statusBarStyle = if (isThemeDark(appDataSetting.themeSetting)) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, MaterialTheme.colorScheme.onSurface.toArgb()),
                        navigationBarStyle = if (isThemeDark(appDataSetting.themeSetting)) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, MaterialTheme.colorScheme.onSurface.toArgb())
                    )
                    // val themeState = rememberUpdatedState()
                    // DisposableEffect(appDataSetting.themeSetting) {
                    //     //val darkTheme = isThemeDark(appDataSetting.themeSetting)
                    //     enableEdgeToEdge(
                    //         statusBarStyle =
                    //         SystemBarStyle.auto(
                    //             Color.TRANSPARENT,
                    //             Color.TRANSPARENT,
                    //         ) { true }
                    //         // navigationBarStyle = SystemBarStyle.auto(
                    //         //     lightScrim,
                    //         //     darkScrim,
                    //         // ) { darkTheme },
                    //     )
                    //     onDispose {}
                    // }

                    // It seems that the only way to get the initial values in datastore file
                    // is to set a flow in MainActivity to pass the latest value when "SettingScreen" composable is called
                    AppTheme(
                        useDarkTheme = isThemeDark(appDataSetting.themeSetting)
                    ) {
                        // A surface container using the 'background' color from the theme
                        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 2.dp) {
                            Home(
                                // photoViewModel = viewModel(),
                                refreshAllImages = { repository.resetAllImages() },
                                provideInitialSetting = { appDataSetting }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun isThemeDark(theme: AppData.Theme): Boolean {
        return when (theme) {
            AppData.Theme.SYSTEM_DEFAULT -> isSystemInDarkTheme()
            AppData.Theme.DARK -> true
            AppData.Theme.LIGHT -> false
            else -> false
        }
    }
}


