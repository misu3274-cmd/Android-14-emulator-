package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.DeviceInfoProvider
import com.example.data.firebase.NetworkMonitor
import com.example.data.firebase.signOutUser
import com.example.data.model.DeviceSettings
import com.example.data.model.ThemeMode
import com.example.data.repository.DeviceSyncRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.WorkbenchApp
import com.example.ui.theme.DeviceSyncTheme
import com.example.ui.viewmodel.DeviceSyncViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        DeviceSyncTheme(themeMode = ThemeMode.SYSTEM, dynamicColor = false) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        }
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: DeviceSyncViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        DeviceSyncViewModel(
                            repository = DeviceSyncRepository(app),
                            networkMonitor = NetworkMonitor(app),
                            deviceInfoProvider = DeviceInfoProvider(app),
                            currentUserId = uid
                        )
                    }
                }
            )
            val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
            val settings = (settingsState as? UiState.Success)?.data?.data ?: DeviceSettings()

            DeviceSyncTheme(
                themeMode = settings.resolvedThemeMode,
                dynamicColor = settings.dynamicColorEnabled
            ) {
                WorkbenchApp(
                    viewModel = viewModel,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = { currentUser = Firebase.auth.currentUser }
                        )
                    }
                )
            }
        }
    }
}
