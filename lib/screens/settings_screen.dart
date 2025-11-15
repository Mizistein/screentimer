import 'package:flutter/material.dart';
import '../services/app_usage_service.dart';
import 'groups_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final AppUsageService _service = AppUsageService();

  bool _hasNotificationPermission = false;
  bool _hasBatteryOptimizationDisabled = false;
  bool _hasExactAlarmPermission = false;
  bool _hasOverlayPermission = false;
  bool _isMonitorServiceRunning = false;
  bool _isLoading = true;
  double _usageLimitHours = 2.0; // default

  @override
  void initState() {
    super.initState();
    _loadPermissionStatuses();
  }

  Future<void> _loadPermissionStatuses() async {
    setState(() => _isLoading = true);

    final notifications = await _service.hasNotificationPermission();
    final battery = await _service.hasBatteryOptimizationDisabled();
    final alarm = await _service.hasExactAlarmPermission();
    final overlay = await _service.hasOverlayPermission();
    final serviceRunning = await _service.isMonitorServiceRunning();
    final persistedEnabled = await _service.getMonitorEnabled();
    final limitMinutes = await _service.getUsageLimitMinutes();

    setState(() {
      _hasNotificationPermission = notifications;
      _hasBatteryOptimizationDisabled = battery;
      _hasExactAlarmPermission = alarm;
      _hasOverlayPermission = overlay;
      // If serviceRunning differs from persisted flag (e.g. service died), prefer actual running state.
      _isMonitorServiceRunning = serviceRunning ? true : persistedEnabled;
      _usageLimitHours = limitMinutes / 60.0;
      _isLoading = false;
    });
  }

  Future<void> _requestNotificationPermission() async {
    await _service.requestNotificationPermission();
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissionStatuses();
  }

  Future<void> _requestBatteryOptimization() async {
    await _service.requestBatteryOptimizationDisable();
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissionStatuses();
  }

  Future<void> _requestExactAlarmPermission() async {
    await _service.requestExactAlarmPermission();
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissionStatuses();
  }

  Future<void> _requestOverlayPermission() async {
    await _service.requestOverlayPermission();
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissionStatuses();
  }

  Future<void> _toggleMonitorService() async {
    if (_isMonitorServiceRunning) {
      await _service.stopMonitorService();
    } else {
      // Check if all required permissions are granted
      if (!_hasOverlayPermission) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Display Overlay permission required'),
            ),
          );
        }
        await _requestOverlayPermission();
        return;
      }
      await _service.startMonitorService();
    }
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadPermissionStatuses();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadPermissionStatuses,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                const Padding(
                  padding: EdgeInsets.all(16.0),
                  child: Text(
                    'Permissions',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ),
                _buildPermissionTile(
                  title: 'Notifications',
                  subtitle: 'Allow app to send notifications',
                  isGranted: _hasNotificationPermission,
                  onRequest: _requestNotificationPermission,
                ),
                _buildPermissionTile(
                  title: 'Battery Optimization',
                  subtitle: 'Disable battery optimization for background tasks',
                  isGranted: _hasBatteryOptimizationDisabled,
                  onRequest: _requestBatteryOptimization,
                ),
                _buildPermissionTile(
                  title: 'Alarms & Reminders',
                  subtitle: 'Schedule exact alarms and reminders',
                  isGranted: _hasExactAlarmPermission,
                  onRequest: _requestExactAlarmPermission,
                ),
                _buildPermissionTile(
                  title: 'Display Overlay',
                  subtitle: 'Display over other apps',
                  isGranted: _hasOverlayPermission,
                  onRequest: _requestOverlayPermission,
                ),
                const Divider(height: 32),
                const Padding(
                  padding: EdgeInsets.all(16.0),
                  child: Text(
                    'App Blocking',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.group_work),
                  title: const Text('Manage App Groups'),
                  subtitle: const Text('Group apps and set shared limits'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(builder: (c) => const GroupsScreen()),
                    );
                  },
                ),
                SwitchListTile(
                  title: const Text('Enable App Blocker'),
                  subtitle: Text(
                    _isMonitorServiceRunning
                        ? 'Blocking apps with ${_usageLimitHours.toStringAsFixed(1)}+ hours usage'
                        : 'Service stopped',
                  ),
                  value: _isMonitorServiceRunning,
                  onChanged: (value) => _toggleMonitorService(),
                  secondary: Icon(
                    _isMonitorServiceRunning
                        ? Icons.block
                        : Icons.check_circle_outline,
                    color: _isMonitorServiceRunning ? Colors.red : Colors.grey,
                  ),
                ),
                if (_isMonitorServiceRunning) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16.0,
                      vertical: 4,
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text('Usage Limit (hours)'),
                        Text(
                          '${_usageLimitHours.toStringAsFixed(1)}h',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                  ),
                  Slider(
                    min: 0.5,
                    max: 6.0,
                    divisions: 11, // 0.5 steps
                    label: _usageLimitHours.toStringAsFixed(1),
                    value: _usageLimitHours,
                    onChanged: (v) {
                      setState(() => _usageLimitHours = v);
                    },
                    onChangeEnd: (v) async {
                      final messenger = ScaffoldMessenger.of(context);
                      final minutes = (v * 60).round();
                      await _service.setUsageLimitMinutes(minutes);
                      // reload to ensure persistence
                      await _loadPermissionStatuses();
                      messenger.showSnackBar(
                        SnackBar(
                          content: Text(
                            'Usage limit set to ${v.toStringAsFixed(1)}h',
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ],
            ),
    );
  }

  Widget _buildPermissionTile({
    required String title,
    required String subtitle,
    required bool isGranted,
    required VoidCallback onRequest,
  }) {
    return ListTile(
      leading: Icon(
        isGranted ? Icons.check_circle : Icons.cancel,
        color: isGranted ? Colors.green : Colors.red,
      ),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: isGranted
          ? const Chip(
              label: Text('Granted'),
              backgroundColor: Colors.green,
              labelStyle: TextStyle(color: Colors.white),
            )
          : ElevatedButton(onPressed: onRequest, child: const Text('Grant')),
    );
  }
}
