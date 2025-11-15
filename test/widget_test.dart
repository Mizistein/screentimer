// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

// ignore_for_file: unused_import
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:screentimer/main.dart';

// The original template counter test no longer applies because the app launches
// directly into AppUsageScreen without a counter. Replace with a smoke test
// that ensures the root widget builds and the expected title text appears.

void main() {
  testWidgets('App boots and shows usage screen title', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());
    // Pump a few frames to allow async init.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    // Verify the AppUsageScreen title renders.
    expect(find.text('App Usage Statistics'), findsOneWidget);
  });
}
