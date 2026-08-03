import 'package:flutter/material.dart';
import 'package:fund_keeper/app/shell/app_shell.dart';
import 'package:fund_keeper/features/account/presentation/account_page.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';
import 'package:fund_keeper/features/auth/presentation/credential_setup_page.dart';
import 'package:fund_keeper/features/auth/presentation/login_page.dart';
import 'package:fund_keeper/features/auth/presentation/session_gate_page.dart';
import 'package:fund_keeper/features/entry/presentation/json_import_page.dart';
import 'package:fund_keeper/features/entry/presentation/manual_buy_page.dart';
import 'package:fund_keeper/features/entry/presentation/manual_sell_page.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_home_page.dart';
import 'package:fund_keeper/features/transaction/presentation/transaction_history_page.dart';
import 'package:go_router/go_router.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'router.g.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>(
  debugLabel: 'rootNavigator',
);

@Riverpod(keepAlive: true)
GoRouter appRouter(Ref ref) {
  final authState = ref.watch(authSessionControllerProvider);
  final router = GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/session',
    redirect: (context, state) {
      final location = state.matchedLocation;
      const publicLocations = {'/login', '/register', '/forgot-password'};
      if (authState.isLoading || authState.hasError) {
        return location == '/session' ? null : '/session';
      }

      final isAuthenticated = authState.value != null;
      if (!isAuthenticated) {
        return publicLocations.contains(location) ? null : '/login';
      }
      if (publicLocations.contains(location) || location == '/session') {
        return '/home';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/session',
        name: 'session',
        builder: (context, state) => const SessionGatePage(),
      ),
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => LoginPage(
          initialEmail: state.uri.queryParameters['email'] ?? '',
          notice: state.uri.queryParameters['notice'],
        ),
      ),
      GoRoute(
        path: '/register',
        name: 'register',
        builder: (context, state) =>
            const CredentialSetupPage(purpose: EmailCodePurpose.register),
      ),
      GoRoute(
        path: '/forgot-password',
        name: 'forgot-password',
        builder: (context, state) =>
            const CredentialSetupPage(purpose: EmailCodePurpose.resetPassword),
      ),
      GoRoute(
        path: '/entries/manual-buy',
        name: 'manual-buy',
        builder: (context, state) => const ManualBuyPage(),
      ),
      GoRoute(
        path: '/entries/manual-sell',
        name: 'manual-sell',
        builder: (context, state) => const ManualSellPage(),
      ),
      GoRoute(
        path: '/entries/json-import',
        name: 'json-import',
        builder: (context, state) => const JsonImportPage(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return AppShell(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/home',
                name: 'home',
                builder: (context, state) => const PortfolioHomePage(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/records',
                name: 'records',
                builder: (context, state) => const TransactionHistoryPage(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/profile',
                name: 'profile',
                builder: (context, state) => const AccountPage(),
              ),
            ],
          ),
        ],
      ),
    ],
  );

  ref.onDispose(router.dispose);
  return router;
}
