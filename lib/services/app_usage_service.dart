import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class AppUsageService {
  static const platform = MethodChannel('com.example.screentimer/app_usage');

  Future<List<AppUsageInfo>> getUsageStats() async {
    try {
      final List<dynamic> result = await platform.invokeMethod('getUsageStats');
      return result
          .map((item) => AppUsageInfo.fromMap(Map<String, dynamic>.from(item)))
          .toList();
    } on PlatformException catch (e) {
      debugPrint('Failed to get usage stats: ${e.message}');
      return [];
    }
  }

  Future<bool> requestUsagePermission() async {
    try {
      final bool hasPermission = await platform.invokeMethod(
        'requestUsagePermission',
      );
      return hasPermission;
    } on PlatformException catch (e) {
      debugPrint('Failed to request permission: ${e.message}');
      return false;
    }
  }

  Future<bool> hasUsagePermission() async {
    try {
      final bool granted = await platform.invokeMethod('hasUsagePermission');
      return granted;
    } on PlatformException catch (e) {
      debugPrint('Failed to check usage permission: ${e.message}');
      return false;
    }
  }

  // Notification permission
  Future<bool> hasNotificationPermission() async {
    try {
      final bool granted = await platform.invokeMethod(
        'hasNotificationPermission',
      );
      return granted;
    } on PlatformException catch (e) {
      debugPrint('Failed to check notification permission: ${e.message}');
      return false;
    }
  }

  Future<bool> requestNotificationPermission() async {
    try {
      await platform.invokeMethod('requestNotificationPermission');
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to request notification permission: ${e.message}');
      return false;
    }
  }

  // Battery optimization
  Future<bool> hasBatteryOptimizationDisabled() async {
    try {
      final bool disabled = await platform.invokeMethod(
        'hasBatteryOptimizationDisabled',
      );
      return disabled;
    } on PlatformException catch (e) {
      debugPrint('Failed to check battery optimization: ${e.message}');
      return false;
    }
  }

  Future<bool> requestBatteryOptimizationDisable() async {
    try {
      await platform.invokeMethod('requestBatteryOptimizationDisable');
      return true;
    } on PlatformException catch (e) {
      debugPrint(
        'Failed to request battery optimization disable: ${e.message}',
      );
      return false;
    }
  }

  // Exact alarm permission
  Future<bool> hasExactAlarmPermission() async {
    try {
      final bool granted = await platform.invokeMethod(
        'hasExactAlarmPermission',
      );
      return granted;
    } on PlatformException catch (e) {
      debugPrint('Failed to check exact alarm permission: ${e.message}');
      return false;
    }
  }

  Future<bool> requestExactAlarmPermission() async {
    try {
      await platform.invokeMethod('requestExactAlarmPermission');
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to request exact alarm permission: ${e.message}');
      return false;
    }
  }

  // Display overlay permission
  Future<bool> hasOverlayPermission() async {
    try {
      final bool granted = await platform.invokeMethod('hasOverlayPermission');
      return granted;
    } on PlatformException catch (e) {
      debugPrint('Failed to check overlay permission: ${e.message}');
      return false;
    }
  }

  Future<bool> requestOverlayPermission() async {
    try {
      await platform.invokeMethod('requestOverlayPermission');
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to request overlay permission: ${e.message}');
      return false;
    }
  }

  // Monitor service control
  Future<bool> startMonitorService() async {
    try {
      await platform.invokeMethod('startMonitorService');
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to start monitor service: ${e.message}');
      return false;
    }
  }

  Future<bool> stopMonitorService() async {
    try {
      await platform.invokeMethod('stopMonitorService');
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to stop monitor service: ${e.message}');
      return false;
    }
  }

  Future<bool> isMonitorServiceRunning() async {
    try {
      final bool running = await platform.invokeMethod(
        'isMonitorServiceRunning',
      );
      return running;
    } on PlatformException catch (e) {
      debugPrint('Failed to check monitor service status: ${e.message}');
      return false;
    }
  }

  Future<bool> getMonitorEnabled() async {
    try {
      final bool enabled = await platform.invokeMethod('getMonitorEnabled');
      return enabled;
    } on PlatformException catch (e) {
      debugPrint('Failed to get monitor enabled flag: ${e.message}');
      return false;
    }
  }

  // Usage limit (minutes)
  Future<int> getUsageLimitMinutes() async {
    try {
      final int minutes = await platform.invokeMethod('getUsageLimitMinutes');
      return minutes;
    } on PlatformException catch (e) {
      debugPrint('Failed to get usage limit: ${e.message}');
      return 120; // default 2 hours
    }
  }

  Future<bool> setUsageLimitMinutes(int minutes) async {
    try {
      await platform.invokeMethod('setUsageLimitMinutes', minutes);
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to set usage limit: ${e.message}');
      return false;
    }
  }

  // Per-app limits
  Future<bool> setPerAppLimitMinutes(String packageName, int minutes) async {
    try {
      await platform.invokeMethod('setPerAppLimitMinutes', {
        'packageName': packageName,
        'minutes': minutes,
      });
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to set per-app limit: ${e.message}');
      return false;
    }
  }

  Future<int?> getPerAppLimitMinutes(String packageName) async {
    try {
      final int? minutes = await platform.invokeMethod(
        'getPerAppLimitMinutes',
        {'packageName': packageName},
      );
      return minutes;
    } on PlatformException catch (e) {
      debugPrint('Failed to get per-app limit: ${e.message}');
      return null;
    }
  }

  Future<bool> removePerAppLimit(String packageName) async {
    try {
      await platform.invokeMethod('removePerAppLimit', {
        'packageName': packageName,
      });
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to remove per-app limit: ${e.message}');
      return false;
    }
  }

  Future<List<PerAppLimit>> getAllPerAppLimits() async {
    try {
      final List<dynamic> raw = await platform.invokeMethod(
        'getAllPerAppLimits',
      );
      return raw
          .map((e) => PerAppLimit.fromMap(Map<String, dynamic>.from(e)))
          .toList();
    } on PlatformException catch (e) {
      debugPrint('Failed to get all per-app limits: ${e.message}');
      return [];
    }
  }

  // App groups
  Future<bool> createOrUpdateAppGroup({
    required String groupId,
    required String groupName,
    required List<String> packageNames,
    int? limitMinutes,
  }) async {
    try {
      await platform.invokeMethod('createOrUpdateAppGroup', {
        'groupId': groupId,
        'groupName': groupName,
        'packageNames': packageNames,
        'limitMinutes': limitMinutes,
      });
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to create/update app group: ${e.message}');
      return false;
    }
  }

  Future<List<AppGroup>> getAllAppGroups() async {
    try {
      final List<dynamic> raw = await platform.invokeMethod('getAllAppGroups');
      return raw
          .map((e) => AppGroup.fromMap(Map<String, dynamic>.from(e)))
          .toList();
    } on PlatformException catch (e) {
      debugPrint('Failed to get app groups: ${e.message}');
      return [];
    }
  }

  Future<bool> deleteAppGroup(String groupId) async {
    try {
      await platform.invokeMethod('deleteAppGroup', {'groupId': groupId});
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to delete app group: ${e.message}');
      return false;
    }
  }

  Future<bool> setAppGroupLimitMinutes(String groupId, int? minutes) async {
    try {
      await platform.invokeMethod('setAppGroupLimitMinutes', {
        'groupId': groupId,
        'minutes': minutes,
      });
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to set app group limit: ${e.message}');
      return false;
    }
  }
}

class AppUsageInfo {
  final String packageName;
  final String appName;
  final DateTime lastTimeUsed;
  final Duration totalTimeInForeground;
  final String? iconBase64;

  AppUsageInfo({
    required this.packageName,
    required this.appName,
    required this.lastTimeUsed,
    required this.totalTimeInForeground,
    this.iconBase64,
  });

  factory AppUsageInfo.fromMap(Map<String, dynamic> map) {
    return AppUsageInfo(
      packageName: map['packageName'] as String,
      appName: (map['appName'] as String?) ?? (map['packageName'] as String),
      lastTimeUsed: DateTime.fromMillisecondsSinceEpoch(
        map['lastTimeUsed'] as int,
      ),
      totalTimeInForeground: Duration(
        milliseconds: map['totalTimeInForeground'] as int,
      ),
      iconBase64: map['iconBase64'] as String?,
    );
  }
}

class PerAppLimit {
  final String packageName;
  final int minutes;
  PerAppLimit({required this.packageName, required this.minutes});
  factory PerAppLimit.fromMap(Map<String, dynamic> map) => PerAppLimit(
    packageName: map['packageName'] as String,
    minutes: map['minutes'] as int,
  );
}

// Imported from models
class AppGroup {
  final String id;
  final String name;
  final List<String> packageNames;
  final int? limitMinutes;
  AppGroup({
    required this.id,
    required this.name,
    required this.packageNames,
    this.limitMinutes,
  });
  factory AppGroup.fromMap(Map<String, dynamic> map) => AppGroup(
    id: map['id'] as String,
    name: (map['name'] as String?) ?? map['id'] as String,
    packageNames: List<String>.from(map['packageNames'] as List),
    limitMinutes: map['limitMinutes'] == null
        ? null
        : (map['limitMinutes'] as int),
  );
}
