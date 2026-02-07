package tech.vasker.vector.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import tech.vasker.vector.MainActivity
import tech.vasker.vector.R

class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                TripManager.get()?.stopTrip(userInitiated = true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
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
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.talonlogo)
            .setContentTitle("Talon")
            .setContentText("Recording trip")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopPending)
            .build()
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
