import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/config/app_environment.dart';

void main() {
  group('AppEnvironment', () {
    test('allows local HTTP in dev', () {
      final environment = AppEnvironment(
        flavor: AppFlavor.dev,
        apiBaseUrl: 'http://127.0.0.1:8080',
        pollingIntervalSeconds: 30,
        requestTimeoutSeconds: 15,
        enableNetworkLogs: true,
      );

      expect(environment.apiBaseUrl.host, '127.0.0.1');
      expect(environment.enableNetworkLogs, isTrue);
    });

    test('requires HTTPS in production', () {
      expect(
        () => AppEnvironment(
          flavor: AppFlavor.prod,
          apiBaseUrl: 'http://api.example.com',
          pollingIntervalSeconds: 30,
          requestTimeoutSeconds: 15,
          enableNetworkLogs: false,
        ),
        throwsArgumentError,
      );
    });

    test('disables network logs in production', () {
      final environment = AppEnvironment(
        flavor: AppFlavor.prod,
        apiBaseUrl: 'https://api.example.com',
        pollingIntervalSeconds: 30,
        requestTimeoutSeconds: 15,
        enableNetworkLogs: true,
      );

      expect(environment.enableNetworkLogs, isFalse);
    });

    test('rejects mismatched native and Dart flavors', () {
      expect(
        () => AppEnvironment(
          flavor: AppFlavor.prod,
          apiBaseUrl: 'https://api.example.com',
          pollingIntervalSeconds: 30,
          requestTimeoutSeconds: 15,
          enableNetworkLogs: false,
          nativeFlavor: 'dev',
        ),
        throwsArgumentError,
      );
    });
  });
}
