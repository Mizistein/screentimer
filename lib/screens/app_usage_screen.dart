import 'package:flutter/material.dart';
import 'dart:convert';
import '../services/app_usage_service.dart';
import 'settings_screen.dart';

class AppUsageScreen extends StatefulWidget {
  const AppUsageScreen({super.key});

  @override
  State<AppUsageScreen> createState() => _AppUsageScreenState();
}

class _AppUsageScreenState extends State<AppUsageScreen> {
  final AppUsageService _appUsageService = AppUsageService();
  List<AppUsageInfo> _usageStats = [];
  bool _hasPermission = false;
  Map<String, int> _perAppLimits = {};

  @override
  void initState() {
    super.initState();
    _checkPermissionAndLoad();
    _ensureMonitorIfEnabled();
  }

  Future<void> _checkPermissionAndLoad() async {
    final hasPermission = await _appUsageService.hasUsagePermission();
    setState(() {
      _hasPermission = hasPermission;
    });
    if (hasPermission) {
      await Future.wait([_loadUsageStats(), _loadPerAppLimits()]);
    }
  }

  Future<void> _loadUsageStats() async {
    final stats = await _appUsageService.getUsageStats();
    setState(() {
      _usageStats = stats;
    });
  }

  Future<void> _loadPerAppLimits() async {
    final list = await _appUsageService.getAllPerAppLimits();
    setState(() {
      _perAppLimits = {for (final e in list) e.packageName: e.minutes};
    });
  }

  Future<void> _openPerAppLimitDialog(AppUsageInfo app) async {
    final currentMinutes = _perAppLimits[app.packageName];
    double valueHours = (currentMinutes != null ? currentMinutes / 60.0 : 2.0)
        .clamp(0.5, 6.0);
    final result = await showDialog<_PerLimitResult>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: Text('Limit for ${app.appName}'),
          content: StatefulBuilder(
            builder: (ctx, setState) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (currentMinutes != null)
                    Text(
                      'Current: ${(currentMinutes / 60).toStringAsFixed(1)} h',
                    ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('New limit (hours)'),
                      Text(valueHours.toStringAsFixed(1)),
                    ],
                  ),
                  Slider(
                    min: 0.5,
                    max: 6.0,
                    divisions: 11,
                    value: valueHours,
                    onChanged: (v) => setState(() => valueHours = v),
                  ),
                ],
              );
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, _PerLimitResult.clear()),
              child: const Text('Use global'),
            ),
            ElevatedButton(
              onPressed: () => Navigator.pop(
                ctx,
                _PerLimitResult.setMinutes((valueHours * 60).round()),
              ),
              child: const Text('Save'),
            ),
          ],
        );
      },
    );

    if (!mounted || result == null) return;
    if (result.clear) {
      await _appUsageService.removePerAppLimit(app.packageName);
      await _loadPerAppLimits();
    } else if (result.minutes != null) {
      await _appUsageService.setPerAppLimitMinutes(
        app.packageName,
        result.minutes!,
      );
      await _loadPerAppLimits();
    }
  }

  Future<void> _ensureMonitorIfEnabled() async {
    // Restore monitor service on app start if user enabled it previously
    final enabled = await _appUsageService.getMonitorEnabled();
    if (!enabled) return;
    final running = await _appUsageService.isMonitorServiceRunning();
    if (!running) {
      await _appUsageService.startMonitorService();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('App Usage Statistics'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SettingsScreen()),
              );
            },
          ),
        ],
      ),
      body: _hasPermission
          ? Column(
              children: [
                Expanded(
                  child: ListView.builder(
                    itemCount: _usageStats.length,
                    itemBuilder: (context, index) {
                      final usage = _usageStats[index];
                      final leading = usage.iconBase64 != null
                          ? Image.memory(
                              base64Decode(usage.iconBase64!),
                              width: 40,
                              height: 40,
                            )
                          : const Icon(Icons.android, size: 40);
                      final custom = _perAppLimits[usage.packageName];
                      return ListTile(
                        leading: leading,
                        title: Row(
                          children: [
                            Expanded(child: Text(usage.appName)),
                            if (custom != null)
                              Padding(
                                padding: const EdgeInsets.only(left: 8.0),
                                child: Chip(
                                  label: Text(
                                    '${(custom / 60).toStringAsFixed(1)}h',
                                  ),
                                  backgroundColor: Theme.of(
                                    context,
                                  ).colorScheme.secondaryContainer,
                                ),
                              ),
                          ],
                        ),
                        subtitle: Text(
                          'Last used: ${usage.lastTimeUsed.toString()}\n'
                          'Total time: ${usage.totalTimeInForeground.inHours}h '
                          '${usage.totalTimeInForeground.inMinutes % 60}m',
                        ),
                        trailing: IconButton(
                          icon: const Icon(Icons.timer),
                          tooltip: 'Set per-app limit',
                          onPressed: () => _openPerAppLimitDialog(usage),
                        ),
                      );
                    },
                  ),
                ),
              ],
            )
          : Center(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text(
                      'Usage access is not granted. To see today\'s app usage, grant "Usage access" for this app.',
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 12),
                    ElevatedButton(
                      onPressed: () async {
                        await _appUsageService.requestUsagePermission();
                      },
                      child: const Text('Open Usage Settings'),
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _checkPermissionAndLoad,
                      child: const Text('Refresh'),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}

class _PerLimitResult {
  final bool clear;
  final int? minutes;
  _PerLimitResult({required this.clear, this.minutes});
  factory _PerLimitResult.clear() => _PerLimitResult(clear: true);
  factory _PerLimitResult.setMinutes(int m) =>
      _PerLimitResult(clear: false, minutes: m);
}
