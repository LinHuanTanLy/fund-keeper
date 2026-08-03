import 'package:fund_keeper/core/config/app_environment.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'app_environment_provider.g.dart';

@Riverpod(keepAlive: true)
AppEnvironment appEnvironment(Ref ref) {
  throw StateError('AppEnvironment must be overridden during bootstrap.');
}
