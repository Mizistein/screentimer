class AppGroup {
  final String id;
  final String name;
  final List<String> packageNames;
  final int? limitMinutes; // null means inherit global/per-app limits

  AppGroup({
    required this.id,
    required this.name,
    required this.packageNames,
    this.limitMinutes,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'packageNames': packageNames,
    'limitMinutes': limitMinutes,
  };

  factory AppGroup.fromMap(Map<String, dynamic> map) => AppGroup(
    id: map['id'] as String,
    name: (map['name'] as String?) ?? map['id'] as String,
    packageNames: List<String>.from(map['packageNames'] as List),
    limitMinutes: map['limitMinutes'] == null
        ? null
        : (map['limitMinutes'] as int),
  );
}

extension AppGroupDisplay on AppGroup {
  String get displayLimit => limitMinutes == null
      ? 'No group limit'
      : '${(limitMinutes! / 60).toStringAsFixed(1)}h';
}
