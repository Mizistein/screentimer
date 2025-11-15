import 'package:flutter/material.dart';
import 'screens/app_usage_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    final seed = Colors.indigo;
    final light = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.light,
    );
    final dark = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.dark,
    );

    return MaterialApp(
      title: 'Screen Timer',
      themeMode: ThemeMode.dark,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: light,
        scaffoldBackgroundColor: light.surface,
        listTileTheme: ListTileThemeData(iconColor: light.onSurfaceVariant),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        colorScheme: dark,
        scaffoldBackgroundColor: dark.surface,
        appBarTheme: AppBarTheme(
          backgroundColor: dark.surface,
          foregroundColor: dark.onSurface,
          elevation: 0,
        ),
        listTileTheme: ListTileThemeData(iconColor: dark.onSurfaceVariant),
        cardColor: dark.surfaceContainerHighest,
      ),
      home: const AppUsageScreen(),
    );
  }
}

// Removed template counter page; app launches directly to AppUsageScreen with forced dark theme.
