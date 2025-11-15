import 'package:flutter/material.dart';
import '../services/app_usage_service.dart';

class GroupsScreen extends StatefulWidget {
  const GroupsScreen({super.key});

  @override
  State<GroupsScreen> createState() => _GroupsScreenState();
}

class _GroupsScreenState extends State<GroupsScreen> {
  final AppUsageService _service = AppUsageService();
  bool _loading = true;
  List<AppGroup> _groups = [];
  List<AppUsageInfo> _apps = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final groups = await _service.getAllAppGroups();
    final apps = await _service.getUsageStats();
    setState(() {
      _groups = groups;
      _apps = apps;
      _loading = false;
    });
  }

  Future<void> _createGroup() async {
    final nameController = TextEditingController();
    final selected = <String>{};
    int? limitMinutes;
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('New Group'),
          content: StatefulBuilder(
            builder: (ctx, setState) {
              // Wrap in SingleChildScrollView to avoid overflow when keyboard shows.
              return SingleChildScrollView(
                padding: EdgeInsets.zero,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    // Limit max height to portion of screen to allow scrolling
                    maxHeight: MediaQuery.of(ctx).size.height * 0.7,
                    maxWidth: 400,
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      TextField(
                        controller: nameController,
                        decoration: const InputDecoration(
                          labelText: 'Group Name',
                          hintText: 'e.g. Social Media',
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Group Limit (optional)'),
                          Text(
                            limitMinutes == null
                                ? 'None'
                                : '${(limitMinutes! / 60).toStringAsFixed(1)}h',
                          ),
                        ],
                      ),
                      Slider(
                        min: 0.5,
                        max: 6.0,
                        divisions: 11,
                        value: (limitMinutes ?? 120) / 60.0,
                        onChanged: (v) => setState(() {
                          limitMinutes = (v * 60).round();
                        }),
                      ),
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          onPressed: () => setState(() => limitMinutes = null),
                          child: const Text('Clear limit'),
                        ),
                      ),
                      const Divider(),
                      const Text('Select Apps:'),
                      Flexible(
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: _apps.length,
                          itemBuilder: (c, i) {
                            final app = _apps[i];
                            return CheckboxListTile(
                              dense: true,
                              title: Text(app.appName),
                              value: selected.contains(app.packageName),
                              onChanged: (checked) {
                                setState(() {
                                  if (checked == true) {
                                    selected.add(app.packageName);
                                  } else {
                                    selected.remove(app.packageName);
                                  }
                                });
                              },
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () {
                if (nameController.text.trim().isNotEmpty &&
                    selected.isNotEmpty) {
                  Navigator.pop(ctx, true);
                }
              },
              child: const Text('Create'),
            ),
          ],
        );
      },
    );
    if (result == true) {
      final id = DateTime.now().millisecondsSinceEpoch.toString();
      await _service.createOrUpdateAppGroup(
        groupId: id,
        groupName: nameController.text.trim(),
        packageNames: selected.toList(),
        limitMinutes: limitMinutes,
      );
      await _load();
    }
  }

  Future<void> _editGroup(AppGroup group) async {
    final nameController = TextEditingController(text: group.name);
    final selected = group.packageNames.toSet();
    int? limitMinutes = group.limitMinutes;
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('Edit Group'),
          content: StatefulBuilder(
            builder: (ctx, setState) {
              return SingleChildScrollView(
                padding: EdgeInsets.zero,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxHeight: MediaQuery.of(ctx).size.height * 0.7,
                    maxWidth: 400,
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      TextField(
                        controller: nameController,
                        decoration: const InputDecoration(
                          labelText: 'Group Name',
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Group Limit'),
                          Text(
                            limitMinutes == null
                                ? 'None'
                                : '${(limitMinutes! / 60).toStringAsFixed(1)}h',
                          ),
                        ],
                      ),
                      Slider(
                        min: 0.5,
                        max: 6.0,
                        divisions: 11,
                        value: (limitMinutes ?? 120) / 60.0,
                        onChanged: (v) =>
                            setState(() => limitMinutes = (v * 60).round()),
                      ),
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          onPressed: () => setState(() => limitMinutes = null),
                          child: const Text('Clear limit'),
                        ),
                      ),
                      const Divider(),
                      const Text('Apps in Group:'),
                      Flexible(
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: _apps.length,
                          itemBuilder: (c, i) {
                            final app = _apps[i];
                            return CheckboxListTile(
                              dense: true,
                              title: Text(app.appName),
                              value: selected.contains(app.packageName),
                              onChanged: (checked) {
                                setState(() {
                                  if (checked == true) {
                                    selected.add(app.packageName);
                                  } else {
                                    selected.remove(app.packageName);
                                  }
                                });
                              },
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () {
                if (nameController.text.trim().isNotEmpty &&
                    selected.isNotEmpty) {
                  Navigator.pop(ctx, true);
                }
              },
              child: const Text('Save'),
            ),
          ],
        );
      },
    );
    if (result == true) {
      await _service.createOrUpdateAppGroup(
        groupId: group.id,
        groupName: nameController.text.trim(),
        packageNames: selected.toList(),
        limitMinutes: limitMinutes,
      );
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('App Groups'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _createGroup,
        child: const Icon(Icons.add),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _groups.isEmpty
          ? const Center(child: Text('No groups yet'))
          : ListView.builder(
              itemCount: _groups.length,
              itemBuilder: (c, i) {
                final g = _groups[i];
                return Card(
                  margin: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  child: ListTile(
                    title: Text(g.name),
                    subtitle: Text(
                      '${g.packageNames.length} apps • ' +
                          (g.limitMinutes == null
                              ? 'No limit'
                              : '${(g.limitMinutes! / 60).toStringAsFixed(1)}h limit'),
                    ),
                    onTap: () => _editGroup(g),
                    trailing: IconButton(
                      icon: const Icon(Icons.delete),
                      onPressed: () async {
                        final ok = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Delete Group'),
                            content: Text('Remove group "${g.name}"?'),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, false),
                                child: const Text('Cancel'),
                              ),
                              ElevatedButton(
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('Delete'),
                              ),
                            ],
                          ),
                        );
                        if (ok == true) {
                          await _service.deleteAppGroup(g.id);
                          await _load();
                        }
                      },
                    ),
                  ),
                );
              },
            ),
    );
  }
}
