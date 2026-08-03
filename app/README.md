# Fund Keeper Flutter Client

Android/iOS 客户端，使用 Riverpod、Dio、GoRouter、Decimal 和受控代码生成。
后端是业务事实来源，客户端不提供离线写入。

## 本地运行

先在仓库根目录启动后端。Android 模拟器访问宿主机：

```bash
flutter run --flavor dev --dart-define-from-file=config/dev.android.json
```

iOS 模拟器：

```bash
flutter run --flavor dev --dart-define-from-file=config/dev.ios.json
```

真机调试时，复制对应开发配置并将 `API_BASE_URL` 改为局域网内可访问的
HTTPS/HTTP 地址；不要提交包含秘密的配置。

## 生成与检查

```bash
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
dart format lib test
flutter analyze
flutter test
```

生成的 `.g.dart` 和本地化 Dart 文件需要提交，禁止手工修改。

## 认证行为

- Access Token 与轮换后的 Refresh Token 作为一个会话写入系统安全存储。
- 启动时通过 `/api/v1/auth/me` 验证会话，过期或被撤销后返回登录页。
- 业务请求自动携带 Bearer Token；并发 401 只刷新一次，并最多重放一次原请求。
- 退出登录始终清除本机凭据；服务端撤销失败时会向用户显示提示。

当前客户端已接入邮箱密码登录、验证码注册和找回密码。开发环境的验证码可在
Mailpit 中查看；客户端的 60 秒重发倒计时与后端默认冷却时间保持一致，最终
是否允许重发仍以后端响应为准。

## 生产构建

先将 `config/prod.json` 的保留域名替换为真实 HTTPS API：

```bash
flutter build apk --flavor prod --dart-define-from-file=config/prod.json
flutter build ios --flavor prod --dart-define-from-file=config/prod.json
```
