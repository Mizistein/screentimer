package com.example.screentimer

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.*

class AppUsagePlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "com.example.screentimer/app_usage")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getUsageStats" -> handleGetUsageStats(call, result)
            "requestUsagePermission" -> handleRequestPermission(result)
            "hasUsagePermission" -> handleHasUsagePermission(result)
            "hasNotificationPermission" -> handleHasNotificationPermission(result)
            "requestNotificationPermission" -> handleRequestNotificationPermission(result)
            "hasBatteryOptimizationDisabled" -> handleHasBatteryOptimizationDisabled(result)
            "requestBatteryOptimizationDisable" -> handleRequestBatteryOptimizationDisable(result)
            "hasExactAlarmPermission" -> handleHasExactAlarmPermission(result)
            "requestExactAlarmPermission" -> handleRequestExactAlarmPermission(result)
            "hasOverlayPermission" -> handleHasOverlayPermission(result)
            "requestOverlayPermission" -> handleRequestOverlayPermission(result)
            "startMonitorService" -> handleStartMonitorService(result)
            "stopMonitorService" -> handleStopMonitorService(result)
            "isMonitorServiceRunning" -> handleIsMonitorServiceRunning(result)
            "getMonitorEnabled" -> handleGetMonitorEnabled(result)
            "getUsageLimitMinutes" -> handleGetUsageLimitMinutes(result)
            "setUsageLimitMinutes" -> handleSetUsageLimitMinutes(call, result)
            // Per-app limits
            "setPerAppLimitMinutes" -> handleSetPerAppLimitMinutes(call, result)
            "getPerAppLimitMinutes" -> handleGetPerAppLimitMinutes(call, result)
            "removePerAppLimit" -> handleRemovePerAppLimit(call, result)
            "getAllPerAppLimits" -> handleGetAllPerAppLimits(result)
            // App groups
            "createOrUpdateAppGroup" -> handleCreateOrUpdateAppGroup(call, result)
            "deleteAppGroup" -> handleDeleteAppGroup(call, result)
            "getAllAppGroups" -> handleGetAllAppGroups(result)
            "setAppGroupLimitMinutes" -> handleSetAppGroupLimitMinutes(call, result)
            else -> result.notImplemented()
        }
    }

    private fun prefs() = context.getSharedPreferences("screentimer_prefs", Context.MODE_PRIVATE)

    private fun handleGetUsageLimitMinutes(result: MethodChannel.Result) {
        try {
            val minutes = prefs().getInt("usage_limit_minutes", 120)
            result.success(minutes)
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }

    private fun handleSetUsageLimitMinutes(call: MethodCall, result: MethodChannel.Result) {
        try {
            val minutes = (call.arguments as? Int)
                ?: (call.argument<Int>("minutes") ?: 120)
            prefs().edit().putInt("usage_limit_minutes", minutes).apply()
            result.success(true)
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }

    // region Per-app limits (stored as JSON map in SharedPreferences)
    private fun perAppKey() = "per_app_limits_json"

    private fun readPerAppLimits(): MutableMap<String, Int> {
        val raw = prefs().getString(perAppKey(), "{}") ?: "{}"
        return try {
            val json = org.json.JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            json.keys().forEach { k ->
                map[k] = json.optInt(k, 0)
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writePerAppLimits(map: Map<String, Int>) {
        val json = org.json.JSONObject()
        for ((k,v) in map) json.put(k, v)
        prefs().edit().putString(perAppKey(), json.toString()).apply()
    }

    private fun handleSetPerAppLimitMinutes(call: MethodCall, result: MethodChannel.Result) {
        try {
            val pkg = call.argument<String>("packageName") ?: run {
                result.error("ARG_ERROR", "packageName required", null); return
            }
            val minutes = call.argument<Int>("minutes") ?: run {
                result.error("ARG_ERROR", "minutes required", null); return
            }
            val map = readPerAppLimits()
            map[pkg] = minutes
            writePerAppLimits(map)
            result.success(true)
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }

    private fun handleGetPerAppLimitMinutes(call: MethodCall, result: MethodChannel.Result) {
        try {
            val pkg = call.argument<String>("packageName") ?: run {
                result.error("ARG_ERROR", "packageName required", null); return
            }
            val map = readPerAppLimits()
            result.success(map[pkg])
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }

    private fun handleRemovePerAppLimit(call: MethodCall, result: MethodChannel.Result) {
        try {
            val pkg = call.argument<String>("packageName") ?: run {
                result.error("ARG_ERROR", "packageName required", null); return
            }
            val map = readPerAppLimits()
            map.remove(pkg)
            writePerAppLimits(map)
            result.success(true)
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }

    private fun handleGetAllPerAppLimits(result: MethodChannel.Result) {
        try {
            val map = readPerAppLimits()
            val list = map.entries.map { e -> mapOf("packageName" to e.key, "minutes" to e.value) }
            result.success(list)
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }
    // endregion

    // region App Groups (stored as JSON map groupId -> object)
    private fun appGroupsKey() = "app_groups_json"

    data class GroupRecord(
        val id: String,
        val name: String,
        val packageNames: List<String>,
        val limitMinutes: Int?
    )

    private fun readAppGroups(): MutableMap<String, GroupRecord> {
        val raw = prefs().getString(appGroupsKey(), "{}") ?: "{}"
        return try {
            val json = org.json.JSONObject(raw)
            val map = mutableMapOf<String, GroupRecord>()
            json.keys().forEach { gid ->
                val obj = json.getJSONObject(gid)
                val pkgsArr = obj.optJSONArray("packageNames") ?: org.json.JSONArray()
                val pkgs = mutableListOf<String>()
                for (i in 0 until pkgsArr.length()) pkgs.add(pkgsArr.getString(i))
                val limit = if (obj.has("limitMinutes") && !obj.isNull("limitMinutes")) obj.optInt("limitMinutes") else null
                map[gid] = GroupRecord(
                    id = gid,
                    name = obj.optString("name", gid),
                    packageNames = pkgs,
                    limitMinutes = limit
                )
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAppGroups(map: Map<String, GroupRecord>) {
        val root = org.json.JSONObject()
        for ((gid, gr) in map) {
            val obj = org.json.JSONObject()
            obj.put("id", gr.id)
            obj.put("name", gr.name)
            obj.put("packageNames", org.json.JSONArray(gr.packageNames))
            if (gr.limitMinutes != null) obj.put("limitMinutes", gr.limitMinutes) else obj.put("limitMinutes", org.json.JSONObject.NULL)
            root.put(gid, obj)
        }
        prefs().edit().putString(appGroupsKey(), root.toString()).apply()
    }

    private fun handleCreateOrUpdateAppGroup(call: MethodCall, result: MethodChannel.Result) {
        try {
            val groupId = call.argument<String>("groupId") ?: run {
                result.error("ARG_ERROR", "groupId required", null); return
            }
            val name = call.argument<String>("groupName") ?: groupId
            val packageNames = call.argument<List<String>>("packageNames") ?: emptyList()
            val limitMinutes = call.argument<Int?>("limitMinutes")
            val map = readAppGroups()
            map[groupId] = GroupRecord(groupId, name, packageNames, limitMinutes)
            writeAppGroups(map)
            result.success(true)
        } catch (e: Exception) {
            result.error("GROUP_ERROR", e.message, null)
        }
    }

    private fun handleDeleteAppGroup(call: MethodCall, result: MethodChannel.Result) {
        try {
            val groupId = call.argument<String>("groupId") ?: run {
                result.error("ARG_ERROR", "groupId required", null); return
            }
            val map = readAppGroups()
            map.remove(groupId)
            writeAppGroups(map)
            result.success(true)
        } catch (e: Exception) {
            result.error("GROUP_ERROR", e.message, null)
        }
    }

    private fun handleGetAllAppGroups(result: MethodChannel.Result) {
        try {
            val map = readAppGroups()
            val list = map.values.map { gr ->
                mapOf(
                    "id" to gr.id,
                    "name" to gr.name,
                    "packageNames" to gr.packageNames,
                    "limitMinutes" to gr.limitMinutes
                )
            }
            result.success(list)
        } catch (e: Exception) {
            result.error("GROUP_ERROR", e.message, null)
        }
    }

    private fun handleSetAppGroupLimitMinutes(call: MethodCall, result: MethodChannel.Result) {
        try {
            val groupId = call.argument<String>("groupId") ?: run {
                result.error("ARG_ERROR", "groupId required", null); return
            }
            val minutes = call.argument<Int?>("minutes") // null removes limit
            val map = readAppGroups()
            val existing = map[groupId] ?: run {
                result.error("GROUP_ERROR", "Group not found", null); return
            }
            map[groupId] = existing.copy(limitMinutes = minutes)
            writeAppGroups(map)
            result.success(true)
        } catch (e: Exception) {
            result.error("GROUP_ERROR", e.message, null)
        }
    }
    // endregion

    private fun handleGetUsageStats(call: MethodCall, result: MethodChannel.Result) {
        // No interval required now; default to today (midnight) until now
        val usageStatsManager = context.getSystemService("usagestats") as? UsageStatsManager
        if (usageStatsManager == null) {
            result.error("UNAVAILABLE", "Usage stats service not available", null)
            return
        }

        val endTime = System.currentTimeMillis()
        // Use device's local timezone for midnight calculation
        val calendar = Calendar.getInstance() // Already uses default timezone
        calendar.timeInMillis = endTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList<UsageStats>()

        // Aggregate by package to avoid duplicates returned by UsageStatsManager
        val aggregate = mutableMapOf<String, Pair<Long, Long>>()
        for (u in stats) {
            if (u.totalTimeInForeground <= 0) continue
            val key = u.packageName
            val prev = aggregate[key]
            if (prev == null) {
                aggregate[key] = Pair(u.totalTimeInForeground, u.lastTimeUsed)
            } else {
                val total = prev.first + u.totalTimeInForeground
                val last = if (u.lastTimeUsed > prev.second) u.lastTimeUsed else prev.second
                aggregate[key] = Pair(total, last)
            }
        }

        val pm = context.packageManager
        val usageList = aggregate.entries
            .map { (pkg, totals) ->
                val (totalMs, lastUsed) = totals
                val appName = try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    pkg
                }
                val iconBase64 = try {
                    val drawable = pm.getApplicationIcon(pkg)
                    val bmp = drawableToBitmap(drawable)
                    val stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val bytes = stream.toByteArray()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } catch (e: Exception) {
                    null
                }
                mapOf(
                    "packageName" to pkg,
                    "appName" to appName,
                    "iconBase64" to iconBase64,
                    "lastTimeUsed" to lastUsed,
                    "totalTimeInForeground" to totalMs
                )
            }
            // Sort by total foreground time descending for a better UX
            .sortedByDescending { it["totalTimeInForeground"] as Long }

        // Return empty list if no data; Flutter side will show empty list
        result.success(usageList)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun handleRequestPermission(result: MethodChannel.Result) {
        val activity = this.activity
        if (activity == null) {
            result.error("ACTIVITY_UNAVAILABLE", "Activity is not available", null)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            activity.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.error("PERMISSION_ERROR", e.message, null)
        }
    }

    private fun handleHasUsagePermission(result: MethodChannel.Result) {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps == null) {
                result.success(false)
                return
            }
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            result.success(mode == AppOpsManager.MODE_ALLOWED)
        } catch (e: Exception) {
            result.error("CHECK_ERROR", e.message, null)
        }
    }

    private fun getIntervalConstant(interval: String): Int = when (interval.lowercase(Locale.ROOT)) {
        "daily" -> Calendar.DAY_OF_YEAR
        "weekly" -> Calendar.WEEK_OF_YEAR
        "monthly" -> Calendar.MONTH
        "yearly" -> Calendar.YEAR
        else -> Calendar.DAY_OF_YEAR
    }

    // Notification permission
    private fun handleHasNotificationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            result.success(granted)
        } else {
            // Notifications are granted by default on Android < 13
            result.success(true)
        }
    }

    private fun handleRequestNotificationPermission(result: MethodChannel.Result) {
        val activity = this.activity
        if (activity == null) {
            result.error("ACTIVITY_UNAVAILABLE", "Activity is not available", null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
            result.success(true)
        } else {
            result.success(true)
        }
    }

    // Battery optimization
    private fun handleHasBatteryOptimizationDisabled(result: MethodChannel.Result) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            result.success(isIgnoring)
        } catch (e: Exception) {
            result.error("CHECK_ERROR", e.message, null)
        }
    }

    private fun handleRequestBatteryOptimizationDisable(result: MethodChannel.Result) {
        val activity = this.activity
        if (activity == null) {
            result.error("ACTIVITY_UNAVAILABLE", "Activity is not available", null)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")
            activity.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.error("PERMISSION_ERROR", e.message, null)
        }
    }

    // Exact alarm permission
    private fun handleHasExactAlarmPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canSchedule = alarmManager?.canScheduleExactAlarms() ?: false
            result.success(canSchedule)
        } else {
            // Granted by default on Android < 12
            result.success(true)
        }
    }

    private fun handleRequestExactAlarmPermission(result: MethodChannel.Result) {
        val activity = this.activity
        if (activity == null) {
            result.error("ACTIVITY_UNAVAILABLE", "Activity is not available", null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:${context.packageName}")
                activity.startActivity(intent)
                result.success(true)
            } catch (e: Exception) {
                result.error("PERMISSION_ERROR", e.message, null)
            }
        } else {
            result.success(true)
        }
    }

    // Display overlay permission
    private fun handleHasOverlayPermission(result: MethodChannel.Result) {
        val canDraw = Settings.canDrawOverlays(context)
        result.success(canDraw)
    }

    private fun handleRequestOverlayPermission(result: MethodChannel.Result) {
        val activity = this.activity
        if (activity == null) {
            result.error("ACTIVITY_UNAVAILABLE", "Activity is not available", null)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            activity.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.error("PERMISSION_ERROR", e.message, null)
        }
    }

    // Monitor service control
    private fun handleStartMonitorService(result: MethodChannel.Result) {
        try {
            val intent = Intent(context, UsageMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            // persist enabled flag so UI can restore state on next launch
            prefs().edit().putBoolean("monitor_enabled", true).apply()
            result.success(true)
        } catch (e: Exception) {
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun handleStopMonitorService(result: MethodChannel.Result) {
        try {
            val intent = Intent(context, UsageMonitorService::class.java)
            context.stopService(intent)
            // persist disabled state
            prefs().edit().putBoolean("monitor_enabled", false).apply()
            result.success(true)
        } catch (e: Exception) {
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun handleIsMonitorServiceRunning(result: MethodChannel.Result) {
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (UsageMonitorService::class.java.name == service.service.className) {
                    result.success(true)
                    return
                }
            }
            result.success(false)
        } catch (e: Exception) {
            result.error("CHECK_ERROR", e.message, null)
        }
    }

    private fun handleGetMonitorEnabled(result: MethodChannel.Result) {
        try {
            val enabled = prefs().getBoolean("monitor_enabled", false)
            result.success(enabled)
        } catch (e: Exception) {
            result.error("PREFS_ERROR", e.message, null)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}