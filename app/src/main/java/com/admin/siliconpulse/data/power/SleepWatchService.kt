package com.admin.siliconpulse.data.power

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.admin.siliconpulse.MainActivity
import com.admin.siliconpulse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que mantiene el proceso vivo (sin wakelock) para
 * registrar transiciones de pantalla y medir deep sleep. No retiene ningun
 * WakeLock a proposito: si lo hiciera, falsearia la medicion.
 */
class SleepWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> SleepTracker.onScreenOff()
                Intent.ACTION_SCREEN_ON -> SleepTracker.onScreenOn()
            }
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        createChannel()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        ticker = scope.launch {
            while (isActive) {
                delay(15_000)
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        runCatching { unregisterReceiver(screenReceiver) }
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val partial = SleepTracker.currentPartialWindow()
        val last = SleepTracker.state.value.lastCompletedWindow
        val text = when {
            partial != null -> "Pantalla apagada: deep sleep %.0f%% (%d s)".format(partial.sleepPercent, partial.durationMs / 1000)
            last != null -> "Ultima ventana: deep sleep %.0f%% en %d s".format(last.sleepPercent, last.durationMs / 1000)
            else -> "Esperando a que apagues la pantalla"
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, SleepWatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("SiliconPulse sigue midiendo el deep sleep")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setSubText("Toca Detener cuando termines")
            .addAction(Notification.Action.Builder(null, "Detener", stopIntent).build())
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Monitor de deep sleep", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mide el tiempo de suspension del SoC con la pantalla apagada"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private val _running = kotlinx.coroutines.flow.MutableStateFlow(false)
        /** Verdadero mientras el servicio existe; la UI lo usa para mostrar "activo en segundo plano". */
        val running: kotlinx.coroutines.flow.StateFlow<Boolean> = _running

        const val CHANNEL_ID = "sleep_watch"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.admin.siliconpulse.action.STOP_SLEEP_WATCH"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SleepWatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SleepWatchService::class.java))
        }
    }
}
