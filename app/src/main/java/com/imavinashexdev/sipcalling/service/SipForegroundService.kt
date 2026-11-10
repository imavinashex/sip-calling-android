package com.imavinashexdev.sipcalling.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.imavinashexdev.sipcalling.R
import com.imavinashexdev.sipcalling.core.SipManager
import com.imavinashexdev.sipcalling.ui.CallActivity
import com.imavinashexdev.sipcalling.ui.IncomingCallActivity
import com.imavinashexdev.sipcalling.util.SoundManager

class SipForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "SIP Call"
        val callerUri = intent?.getStringExtra(EXTRA_CALLER_URI) ?: ""

        when (action) {
            ACTION_INCOMING_CALL -> {
                SoundManager.startRingtoneAndVibration(this)
                val notification = buildIncomingCallNotification(callerName, callerUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

            ACTION_START_CALL -> {
                val notification = buildActiveCallNotification(callerName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

            ACTION_ACCEPT_CALL -> {
                SoundManager.stopRingtoneAndVibration(this)
                SipManager.acceptCall()
                val callIntent = Intent(this, CallActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(callIntent)
                val notification = buildActiveCallNotification(callerName)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, notification)
            }

            ACTION_DECLINE_CALL -> {
                SoundManager.stopRingtoneAndVibration(this)
                SipManager.terminateCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_STOP_CALL -> {
                SoundManager.stopRingtoneAndVibration(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_TERMINATE -> {
                SoundManager.stopRingtoneAndVibration(this)
                SipManager.terminateCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildIncomingCallNotification(callerName: String, callerUri: String): Notification {
        // 1. Full Screen Intent to wake up screen and open IncomingCallActivity immediately
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_URI", callerUri)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Accept Action Intent
        val acceptIntent = Intent(this, SipForegroundService::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        val acceptPendingIntent = PendingIntent.getService(
            this,
            102,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Decline Action Intent
        val declineIntent = Intent(this, SipForegroundService::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            103,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (callerName.isNotBlank() && callerName != callerUri) callerName else callerUri
        val displaySubtitle = if (callerName.isNotBlank() && callerName != callerUri) callerUri else getString(R.string.incoming_call_title)

        return NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(displayTitle)
            .setContentText(displaySubtitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVibrate(longArrayOf(0))
            .setSound(null)
            .setDefaults(0)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(
                R.drawable.ic_call,
                getString(R.string.action_accept),
                acceptPendingIntent
            )
            .addAction(
                R.drawable.ic_call_end,
                getString(R.string.action_decline),
                declinePendingIntent
            )
            .build()
    }

    private fun buildActiveCallNotification(callerName: String): Notification {
        val openCallIntent = Intent(this, CallActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openCallPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, SipForegroundService::class.java).apply {
            action = ACTION_TERMINATE
        }
        val endCallPendingIntent = PendingIntent.getService(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(getString(R.string.notification_active_call))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openCallPendingIntent)
            .addAction(
                R.drawable.ic_call_end,
                getString(R.string.action_end_call),
                endCallPendingIntent
            )
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Clean up legacy incoming call channels so old channel settings don't persist
            try {
                manager?.deleteNotificationChannel("sip_incoming_calls_channel")
                manager?.deleteNotificationChannel("sip_incoming_calls_channel_v2")
            } catch (_: Exception) {
            }

            // High priority channel for incoming calls (heads up / full-screen intent)
            // Explicitly set sound and vibration to null/disabled on the notification channel
            // so Android system ringtone handled by SoundManager plays cleanly without overlap or suppression.
            val incomingChannel = NotificationChannel(
                INCOMING_CHANNEL_ID,
                "Incoming VoIP Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows incoming SIP calls with answer/decline controls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
            }
            manager?.createNotificationChannel(incomingChannel)

            // Ongoing call channel
            val activeChannel = NotificationChannel(
                ACTIVE_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
            manager?.createNotificationChannel(activeChannel)
        }
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SipCallingApp::CallWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // 30 min max timeout
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager?.createWifiLock(
            wifiMode,
            "SipCallingApp::CallWifiLock"
        )?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        SoundManager.stopRingtoneAndVibration(this)
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val INCOMING_CHANNEL_ID = "sip_incoming_calls_channel_v3"
        const val ACTIVE_CHANNEL_ID = "sip_call_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_INCOMING_CALL = "com.imavinashexdev.sipcalling.INCOMING_CALL"
        const val ACTION_START_CALL = "com.imavinashexdev.sipcalling.START_CALL"
        const val ACTION_ACCEPT_CALL = "com.imavinashexdev.sipcalling.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.imavinashexdev.sipcalling.DECLINE_CALL"
        const val ACTION_STOP_CALL = "com.imavinashexdev.sipcalling.STOP_CALL"
        const val ACTION_TERMINATE = "com.imavinashexdev.sipcalling.TERMINATE"

        const val EXTRA_CALLER_NAME = "EXTRA_CALLER_NAME"
        const val EXTRA_CALLER_URI = "EXTRA_CALLER_URI"
    }
}
