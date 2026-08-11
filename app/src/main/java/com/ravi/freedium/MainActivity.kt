package com.ravi.freedium

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ravi.freedium.nav.NotificationNavHost
import com.ravi.freedium.store.AppDatabase
import com.ravi.freedium.ui.theme.FreediumTheme
import com.ravi.freedium.utils.prefs.FreediumPrefs
import com.ravi.freedium.viewmodel.NotificationViewModel
import com.ravi.freedium.work.CleanupScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: NotificationViewModel by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        NotificationViewModel(database.notificationDao(), database.cleanupLogDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreediumPrefs.init(applicationContext)
        // Idempotent - KEEP policy means an existing schedule is left alone.
        CleanupScheduler.schedule(applicationContext)
        applySecureFlag()
        enableEdgeToEdge()
        setContent {
            FreediumTheme {
                var isNotificationServiceEnabled by remember {
                    mutableStateOf(isNotificationServiceEnabled())
                }

                // Granting notification access happens in Settings, so the answer can only
                // have changed while we were away - re-ask every time we come back.
                LifecycleResumeEffect(Unit) {
                    isNotificationServiceEnabled = isNotificationServiceEnabled()
                    onPauseOrDispose { }
                }

                if (isNotificationServiceEnabled) {
                    NotificationNavHost(viewModel = viewModel)
                } else {
                    PermissionRequestScreen(onCheckAgain = {
                        isNotificationServiceEnabled = isNotificationServiceEnabled()
                    })
                }
            }
        }
    }

    /**
     * Re-applied on every resume so toggling the setting takes effect without a restart.
     * FLAG_SECURE keeps this screen - which lists other apps' notification content - out of
     * screenshots, screen recordings and the recents thumbnail.
     */
    private fun applySecureFlag() {
        if (FreediumPrefs.secureScreen.value) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureFlag()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

@Composable
fun PermissionRequestScreen(onCheckAgain: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text("Notification access is required to capture Medium notifications.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("Enable Permission")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCheckAgain) {
            Text("I've enabled it, check again")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FreediumTheme {
        PermissionRequestScreen(onCheckAgain = {})
    }
}
