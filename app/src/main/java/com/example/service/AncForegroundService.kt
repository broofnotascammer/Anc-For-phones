package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.AncApplication
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioDeviceManager
import com.example.data.AncMode
import com.example.detection.DeviceMonitor
import com.example.dsp.DspEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AncForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        var dspEngineInstance: DspEngine? = null
            private set
        var deviceManagerInstance: AudioDeviceManager? = null
            private set
        private var deviceMonitor: DeviceMonitor? = null

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TOGGLE_MODE = "com.example.service.ACTION_TOGGLE_MODE"
        const val EXTRA_MODE = "extra_mode"

        fun startService(context: Context) {
            val intent = Intent(context, AncForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AncForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val devMgr = deviceManagerInstance ?: AudioDeviceManager(applicationContext).also {
            deviceManagerInstance = it
        }
        val dspEng = dspEngineInstance ?: DspEngine(devMgr).also {
            dspEngineInstance = it
        }

        deviceMonitor = DeviceMonitor(applicationContext, devMgr) {
            dspEng.stop()
            dspEng.start()
            updateNotification()
        }.also { it.start() }

        dspEng.metrics.onEach {
            // Update notification title on mode change
            updateNotification()
        }.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                dspEngineInstance?.start()
            }
            ACTION_STOP -> {
                dspEngineInstance?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE)
                if (modeStr != null) {
                    try {
                        val mode = AncMode.valueOf(modeStr)
                        dspEngineInstance?.setMode(mode)
                    } catch (_: Exception) {}
                }
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(AncApplication.NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(AncApplication.NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(AncApplication.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AncForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentMode = dspEngineInstance?.currentParams?.mode ?: AncMode.OFF
        val inDev = deviceManagerInstance?.selectedInput?.value?.displayName ?: "Default Mic"
        val outDev = deviceManagerInstance?.selectedOutput?.value?.displayName ?: "Default Output"

        return NotificationCompat.Builder(this, AncApplication.CHANNEL_ID)
            .setContentTitle("Software ANC: ${currentMode.displayName}")
            .setContentText("🎤 $inDev  ➜  🎧 $outDev")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop ANC", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceMonitor?.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
