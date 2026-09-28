package com.aielectronics.builder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Base44BridgeKeepAliveService : Service() {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val servicePreferences by lazy {
        getSharedPreferences(
            SERVICE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
    }
    private val credentialStore by lazy {
        Base44BridgeCredentialStore(
            getSharedPreferences(
                "base44_hardware_bridge",
                Context.MODE_PRIVATE,
            )
        )
    }
    private val bridgeClient by lazy {
        Base44HardwareBridgeClient(BuildConfig.BASE44_BRIDGE_URL)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        intent
            ?.getStringExtra(EXTRA_LOCAL_PROJECT_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { localProjectId ->
                servicePreferences.edit()
                    .putString(
                        ACTIVE_LOCAL_PROJECT_ID,
                        localProjectId,
                    )
                    .apply()
            }

        startForeground(
            NOTIFICATION_ID,
            notification(
                title = "CircuitFlow接続を維持中",
                text = "Android Hardware Bridgeを待機しています。",
            ),
        )
        startHeartbeatLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                val localProjectId =
                    servicePreferences.getString(
                        ACTIVE_LOCAL_PROJECT_ID,
                        null,
                    )

                if (localProjectId.isNullOrBlank()) {
                    updateNotification(
                        "CircuitFlow待機中",
                        "接続中のプロジェクトがありません。",
                    )
                    delay(HEARTBEAT_INTERVAL_MS)
                    continue
                }

                val credentials =
                    credentialStore.load(localProjectId)

                if (credentials == null) {
                    updateNotification(
                        "CircuitFlow再接続が必要です",
                        "接続コードを発行してAndroidを再接続してください。",
                    )
                    delay(HEARTBEAT_INTERVAL_MS)
                    continue
                }

                val online =
                    bridgeClient.heartbeat(credentials)

                if (online) {
                    updateNotification(
                        "CircuitFlow接続中",
                        "バックグラウンドでもBridge接続を維持しています。",
                    )
                } else {
                    updateNotification(
                        "CircuitFlowへ再接続中",
                        "ネットワークまたは認証状態を確認しています。",
                    )
                }

                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val manager =
            getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "CircuitFlow Bridge",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "CircuitFlowとAndroid Hardware Bridgeの接続を維持します。"
            }
        )
    }

    private fun notification(
        title: String,
        text: String,
    ): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun updateNotification(
        title: String,
        text: String,
    ) {
        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                notification(title, text),
            )
    }

    companion object {
        private const val CHANNEL_ID =
            "circuitflow_bridge"
        private const val NOTIFICATION_ID = 4401
        private const val SERVICE_PREFERENCES =
            "base44_bridge_service"
        private const val ACTIVE_LOCAL_PROJECT_ID =
            "active_local_project_id"
        private const val EXTRA_LOCAL_PROJECT_ID =
            "local_project_id"
        private const val HEARTBEAT_INTERVAL_MS =
            10_000L

        fun start(
            context: Context,
            localProjectId: String,
        ) {
            val intent =
                Intent(
                    context,
                    Base44BridgeKeepAliveService::class.java,
                ).putExtra(
                    EXTRA_LOCAL_PROJECT_ID,
                    localProjectId,
                )
            runCatching {
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(
                    Intent(
                        context,
                        Base44BridgeKeepAliveService::class.java,
                    )
                )
            }
        }
    }
}
