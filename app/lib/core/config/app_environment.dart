enum AppFlavor {
  dev,
  prod;

  static AppFlavor parse(String value) {
    return switch (value.trim().toLowerCase()) {
      'dev' => AppFlavor.dev,
      'prod' => AppFlavor.prod,
      _ => throw ArgumentError.value(
        value,
        'APP_FLAVOR',
        'Only dev and prod are supported.',
      ),
    };
  }
}

class AppEnvironment {
  factory AppEnvironment({
    required AppFlavor flavor,
    required String apiBaseUrl,
    required int pollingIntervalSeconds,
    required int requestTimeoutSeconds,
    required bool enableNetworkLogs,
    String? nativeFlavor,
  }) {
    if (nativeFlavor != null &&
        nativeFlavor.isNotEmpty &&
        AppFlavor.parse(nativeFlavor) != flavor) {
      throw ArgumentError.value(
        nativeFlavor,
        'FLUTTER_APP_FLAVOR',
        'Native and Dart flavors must match.',
      );
    }
    final parsedBaseUrl = Uri.tryParse(apiBaseUrl);
    if (parsedBaseUrl == null ||
        !parsedBaseUrl.isAbsolute ||
        parsedBaseUrl.host.isEmpty) {
      throw ArgumentError.value(
        apiBaseUrl,
        'API_BASE_URL',
        'A valid absolute URL is required.',
      );
    }
    if (flavor == AppFlavor.prod && parsedBaseUrl.scheme != 'https') {
      throw ArgumentError.value(
        apiBaseUrl,
        'API_BASE_URL',
        'Production requires HTTPS.',
      );
    }
    if (pollingIntervalSeconds < 5 || pollingIntervalSeconds > 300) {
      throw ArgumentError.value(
        pollingIntervalSeconds,
        'POLLING_INTERVAL_SECONDS',
        'Polling interval must be between 5 and 300 seconds.',
      );
    }
    if (requestTimeoutSeconds < 1 || requestTimeoutSeconds > 120) {
      throw ArgumentError.value(
        requestTimeoutSeconds,
        'REQUEST_TIMEOUT_SECONDS',
        'Request timeout must be between 1 and 120 seconds.',
      );
    }

    return AppEnvironment._(
      flavor: flavor,
      apiBaseUrl: parsedBaseUrl,
      pollingInterval: Duration(seconds: pollingIntervalSeconds),
      requestTimeout: Duration(seconds: requestTimeoutSeconds),
      enableNetworkLogs: flavor == AppFlavor.dev && enableNetworkLogs,
    );
  }

  factory AppEnvironment.fromDartDefines() {
    return AppEnvironment(
      flavor: AppFlavor.parse(
        const String.fromEnvironment('APP_FLAVOR', defaultValue: 'dev'),
      ),
      apiBaseUrl: const String.fromEnvironment(
        'API_BASE_URL',
        defaultValue: 'http://127.0.0.1:8080',
      ),
      pollingIntervalSeconds: const int.fromEnvironment(
        'POLLING_INTERVAL_SECONDS',
        defaultValue: 30,
      ),
      requestTimeoutSeconds: const int.fromEnvironment(
        'REQUEST_TIMEOUT_SECONDS',
        defaultValue: 15,
      ),
      enableNetworkLogs: const bool.fromEnvironment(
        'ENABLE_NETWORK_LOGS',
        defaultValue: false,
      ),
      nativeFlavor: const String.fromEnvironment('FLUTTER_APP_FLAVOR'),
    );
  }

  const AppEnvironment._({
    required this.flavor,
    required this.apiBaseUrl,
    required this.pollingInterval,
    required this.requestTimeout,
    required this.enableNetworkLogs,
  });

  final AppFlavor flavor;
  final Uri apiBaseUrl;
  final Duration pollingInterval;
  final Duration requestTimeout;
  final bool enableNetworkLogs;

  bool get isProduction => flavor == AppFlavor.prod;
}
