package ru.czalarm

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var scheduler: AlarmScheduler
    private var screenState by mutableStateOf<AlarmScreenState?>(null)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshScreen() }

    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshScreen()
            showRingingAlarm()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduler = AlarmScheduler(this)
        RingService.ensureChannel(this)
        val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
        setContent {
            screenState?.let { state ->
                AlarmScreen(
                    state = state,
                    onEdit = ::editAlarm,
                    onToggle = ::toggleAlarm,
                    onSound = { startActivity(Intent(this, SoundSettingsActivity::class.java)) },
                    onUpdates = { startActivity(Intent(this, UpdateActivity::class.java)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Ui.watch(
            this, changes, AlarmScheduler.CHANGED, RingService.RING_CHANGED,
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )
    }

    override fun onResume() {
        super.onResume()
        scheduler.restore(false)
        refreshScreen()
        showRingingAlarm()
        if (Build.VERSION.SDK_INT >= 33 && RingService.session() == null &&
            !getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            !getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_ASKED, false)
        ) {
            requestNotifications()
        }
    }

    override fun onStop() {
        unregisterReceiver(changes)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showRingingAlarm()
    }

    private fun showRingingAlarm() {
        val session = RingService.session()
        if (session != null && hasWindowFocus()) startActivity(RingService.screen(this, session))
    }

    private fun refreshScreen() {
        screenState = AlarmScreenState(scheduler.load(), LocalDate.now(), permissionPrompt())
    }

    private fun editAlarm(alarm: Alarm?) {
        AlarmEditor(this, scheduler, alarm).show()
    }

    private fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        scheduler.update(
            Alarm(alarm.id, alarm.label, alarm.hour, alarm.minute, alarm.days, alarm.interval, enabled)
        )
    }

    private fun permissionPrompt(): PermissionPrompt? {
        val manager = getSystemService(NotificationManager::class.java)
        val app = Uri.fromParts("package", packageName, null)
        return when {
            !scheduler.canSchedule() -> PermissionPrompt("Разрешить точные будильники") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app))
            }
            !manager.areNotificationsEnabled() -> PermissionPrompt(
                "Разрешить уведомления будильника", ::requestNotifications
            )
            RingService.ensureChannel(this).importance < NotificationManager.IMPORTANCE_HIGH ->
                PermissionPrompt("Включить показ сигнала на экране") {
                    startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, RingService.CHANNEL)
                    )
                }
            needsFullScreenPermission(manager) ->
                PermissionPrompt("Разрешить будильник поверх блокировки") {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app))
                }
            Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this) ->
                PermissionPrompt("Показывать будильник поверх других приложений") {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, app))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "Настройка недоступна на этом устройстве", Toast.LENGTH_SHORT).show()
                    }
                }
            else -> null
        }
    }

    private fun needsFullScreenPermission(manager: NotificationManager): Boolean =
        Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent()

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_ASKED, false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            getPreferences(MODE_PRIVATE).edit { putBoolean(NOTIFICATION_ASKED, true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private companion object {
        const val NOTIFICATION_ASKED = "notificationAsked"
    }
}
