package tech.vasker.vector.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import tech.vasker.vector.MainActivity
import tech.vasker.vector.R
import tech.vasker.vector.TalonApplication
import java.util.Locale

class RecordingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                (applicationContext as TalonApplication).tripHolder.stopTrip(userInitiated = true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    startForeground(NOTIFICATION_ID, buildNotification(), serviceType)
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                scheduleNotificationUpdate()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
        TripNotificationState.clear()
        super.onDestroy()
    }

    private fun scheduleNotificationUpdate() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = Runnable {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
            updateRunnable?.let { handler.postDelayed(it, NOTIFICATION_UPDATE_MS) }
        }
        handler.postDelayed(updateRunnable!!, NOTIFICATION_UPDATE_MS)
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        val stopIntent = Intent(this, RecordingService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlag(),
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlag(),
        )
        val contentText = formatNotificationContent()
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.talonlogo)
            .setContentTitle("Talon — Trip")
            .setContentText(contentText)
            .setSubText("Tap to open • Stop to end trip")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun formatNotificationContent(): String {
        val d = TripNotificationState.distanceMi
        val gal = TripNotificationState.gallonsBurned
        val parts = mutableListOf<String>()
        parts.add(String.format(Locale.US, "%.2f mi", d))
        if (gal != null) parts.add(String.format(Locale.US, "%.3f gal", gal))
        return parts.ifEmpty { listOf("Recording…") }.joinToString(" • ")
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    private fun pendingFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        private const val CHANNEL_ID = "talon_recording"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_UPDATE_MS = 2000L
        private const val ACTION_START = "tech.vasker.vector.trip.action.START"
        private const val ACTION_STOP = "tech.vasker.vector.trip.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
