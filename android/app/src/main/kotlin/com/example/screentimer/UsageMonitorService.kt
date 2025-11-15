package com.example.screentimer

import android.app.*
import android.app.usage.UsageStats
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.*

class UsageMonitorService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var currentBlockedPackage: String? = null
    @Volatile private var usageLimitMs: Long = 2 * 60 * 60 * 1000L // default 2h

    companion object {
        const val CHANNEL_ID = "usage_monitor_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Load persisted limit from SharedPreferences if available
        try {
            val prefs = getSharedPreferences("screentimer_prefs", Context.MODE_PRIVATE)
            val minutes = prefs.getInt("usage_limit_minutes", 120)
            usageLimitMs = minutes.toLong() * 60_000L
        } catch (_: Exception) {}
        startMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage and enforces limits"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Timer Active")
            .setContentText("Monitoring app usage")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        monitoringRunnable = object : Runnable {
            override fun run() {
                checkCurrentApp()
                handler.postDelayed(this, 3000) // Throttled to every 3 seconds
            }
        }
        handler.post(monitoringRunnable!!)
    }

    private fun checkCurrentApp() {
        // Refresh usage limit from SharedPreferences so changes take effect without restarting
        try {
            val prefs = getSharedPreferences("screentimer_prefs", Context.MODE_PRIVATE)
            val minutes = prefs.getInt("usage_limit_minutes", 120)
            val newLimit = minutes.toLong() * 60_000L
            if (newLimit != usageLimitMs) {
                usageLimitMs = newLimit
            }
        } catch (_: Exception) {}

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) return

        val endTime = System.currentTimeMillis()
        // Use device's local timezone (e.g., Europe/Berlin)
        val calendar = Calendar.getInstance() // Already uses default timezone
        calendar.timeInMillis = endTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        // Debug log: verify midnight calculation (uncomment to check timezone)
        // Log.d("UsageMonitor", "Querying from ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(java.util.Date(startTime))} to now in ${java.util.TimeZone.getDefault().id}")

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return

        // Aggregate usage stats by package
        val aggregate = mutableMapOf<String, Long>()
        for (u in stats) {
            if (u.totalTimeInForeground <= 0) continue
            val key = u.packageName
            aggregate[key] = (aggregate[key] ?: 0L) + u.totalTimeInForeground
        }

        // Get current foreground app using UsageEvents for higher accuracy
        val currentApp = getCurrentForegroundApp(usageStatsManager) ?: run {
            // Fallback to lastTimeUsed if no events are available
            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } ?: return

        // Skip our own app
        if (currentApp == packageName) {
            hideOverlay()
            return
        }

        // Determine effective limit: per-app override (minutes) or global
        val prefs = getSharedPreferences("screentimer_prefs", Context.MODE_PRIVATE)
        val perJson = prefs.getString("per_app_limits_json", "{}") ?: "{}"
        var perAppMinutes: Int? = null
        try {
            val obj = org.json.JSONObject(perJson)
            if (obj.has(currentApp)) perAppMinutes = obj.optInt(currentApp)
        } catch (_: Exception) {}
        val effectiveLimitMs = (perAppMinutes?.toLong() ?: (usageLimitMs / 60_000L)).let { minutes ->
            minutes * 60_000L
        }

        // Check if current app exceeds per-app or global limit
        val currentUsage = aggregate[currentApp] ?: 0L
        if (currentUsage > effectiveLimitMs) {
            if (currentBlockedPackage != currentApp) {
                currentBlockedPackage = currentApp
                showOverlay(currentApp, currentUsage)
            } else if (overlayView == null) {
                showOverlay(currentApp, currentUsage)
            }
            return
        }

        // Group enforcement: if app belongs to a group with a limit and the SUM of group's apps usage exceeds limit, block.
        try {
            val prefsGroups = getSharedPreferences("screentimer_prefs", Context.MODE_PRIVATE)
            val rawGroups = prefsGroups.getString("app_groups_json", "{}") ?: "{}"
            val groupsJson = org.json.JSONObject(rawGroups)
            var blockedByGroup: String? = null
            var groupUsageMs: Long = 0L
            var groupLimitMs: Long = 0L
            var groupName: String? = null

            groupsJson.keys().forEach { gid ->
                val gObj = groupsJson.getJSONObject(gid)
                val pkgsArr = gObj.optJSONArray("packageNames") ?: org.json.JSONArray()
                var containsCurrent = false
                val groupPkgs = mutableListOf<String>()
                for (i in 0 until pkgsArr.length()) {
                    val pkg = pkgsArr.getString(i)
                    groupPkgs.add(pkg)
                    if (pkg == currentApp) containsCurrent = true
                }
                if (!containsCurrent) return@forEach
                if (gObj.isNull("limitMinutes") || !gObj.has("limitMinutes")) return@forEach
                val limitMinutes = gObj.optInt("limitMinutes", -1)
                if (limitMinutes <= 0) return@forEach
                val sumUsage = groupPkgs.sumOf { aggregate[it] ?: 0L }
                if (sumUsage > limitMinutes * 60_000L) {
                    blockedByGroup = gid
                    groupUsageMs = sumUsage
                    groupLimitMs = limitMinutes * 60_000L
                    groupName = gObj.optString("name", gid)
                    return@forEach
                }
            }

            if (blockedByGroup != null) {
                if (currentBlockedPackage != currentApp || overlayView == null) {
                    currentBlockedPackage = currentApp
                    showOverlayForGroup(currentApp, groupUsageMs, groupName ?: blockedByGroup!!, groupLimitMs)
                }
                return
            }
        } catch (_: Exception) { }

        // Not blocked by per-app/global or group; if previously blocked remove overlay
        if (currentBlockedPackage == currentApp) {
            hideOverlay()
            currentBlockedPackage = null
        }
    }

    private fun getCurrentForegroundApp(usm: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        // Look back a short window to capture the most recent foreground transition
        val events = usm.queryEvents(now - 60_000, now)
        val event = UsageEvents.Event()

        var lastForegroundPkg: String? = null
        var lastTimestamp = -1L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                // On Android Q+ more granular events exist
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (event.timeStamp >= lastTimestamp) {
                        lastTimestamp = event.timeStamp
                        lastForegroundPkg = event.packageName
                    }
                }
            }
        }
        return lastForegroundPkg
    }

    private fun showOverlay(packageName: String, usageMs: Long) {
        if (!Settings.canDrawOverlays(this)) return

        // Skip blocking for system-critical apps
        if (packageName.startsWith("com.android.systemui") ||
            packageName == "com.android.settings" ||
            packageName.contains("launcher", ignoreCase = true)) {
            return
        }

        // If we're already showing an overlay for this package, no-op
        if (overlayView != null && currentBlockedPackage == packageName) {
            return
        }

        // Remove existing overlay if any
        hideOverlay()

        val wmParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
        }

        wmParams.gravity = Gravity.CENTER

        // Create overlay UI with message and a button to go Home
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val appLabel = try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName
        }

        val hours = usageMs / (60 * 60 * 1000)
        val minutes = (usageMs / (60 * 1000)) % 60

        val message = TextView(this).apply {
            text = "Time's up for $appLabel\n($hours h ${minutes} m today)"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
        }

        val button = Button(this).apply {
            text = "Go to Home"
            setOnClickListener {
                    hideOverlay()
                    currentBlockedPackage = null
                try {
                    val startMain = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(startMain)
                } catch (_: Exception) {}
            }
        }

        container.addView(message)
    val spacer = View(this).apply { this.layoutParams = android.widget.LinearLayout.LayoutParams(0, 24) }
        container.addView(spacer)
        container.addView(button)

        overlayView = container

        try {
            windowManager.addView(overlayView, wmParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showOverlayForGroup(packageName: String, groupUsageMs: Long, groupName: String, groupLimitMs: Long) {
        if (!Settings.canDrawOverlays(this)) return
        if (packageName.startsWith("com.android.systemui") ||
            packageName == "com.android.settings" ||
            packageName.contains("launcher", ignoreCase = true)) {
            return
        }
        hideOverlay()
        val wmParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
        }
        wmParams.gravity = Gravity.CENTER
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val appLabel = try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) { packageName }
        val hours = groupUsageMs / (60 * 60 * 1000)
        val minutes = (groupUsageMs / (60 * 1000)) % 60
        val limitHours = groupLimitMs / (60 * 60 * 1000)
        val limitMinutes = (groupLimitMs / (60 * 1000)) % 60
        val message = TextView(this).apply {
            text = "Group '$groupName' limit reached\n($hours h ${minutes} m vs ${limitHours} h ${limitMinutes} m limit)\nApp: $appLabel"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val button = Button(this).apply {
            text = "Go to Home"
            setOnClickListener {
                hideOverlay()
                currentBlockedPackage = null
                try {
                    val startMain = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(startMain)
                } catch (_: Exception) {}
            }
        }
        container.addView(message)
        val spacer = View(this).apply { this.layoutParams = android.widget.LinearLayout.LayoutParams(0, 24) }
        container.addView(spacer)
        container.addView(button)
        overlayView = container
        try { windowManager.addView(overlayView, wmParams) } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        hideOverlay()
    }
}
