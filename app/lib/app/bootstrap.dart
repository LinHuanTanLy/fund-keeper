import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/app/app.dart';
import 'package:fund_keeper/core/config/app_environment.dart';
import 'package:fund_keeper/core/config/app_environment_provider.dart';

void bootstrap() {
  WidgetsFlutterBinding.ensureInitialized();

  final environment = AppEnvironment.fromDartDefines();

  runApp(
    ProviderScope(
      overrides: [appEnvironmentProvider.overrideWithValue(environment)],
      child: const FundKeeperApp(),
    ),
  );
}
