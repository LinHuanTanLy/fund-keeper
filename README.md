# Fund Keeper

面向多平台基金持仓的管理工具。项目当前已完成认证、平台账户、基金资料、手动买入、手动卖出、持仓快照导入和批量买入导入等后端纵向切片：用户可以登录后按金额维护交易，并在自己的数据空间内查看账户级持仓。

## 当前技术栈

- Java 21、Spring Boot 4.1、Spring Security
- Spring Data JPA、MySQL、Flyway
- Redis（验证码、发送频率和错误次数控制）
- JWT Access Token + 不透明 Refresh Token
- Mailpit（本地接收开发邮件）

产品边界和验收规则见 [产品需求文档](docs/product-requirements.md)，技术规格见 [项目规格](docs/project-spec.md)。

## 本地启动

要求：Java 21、Docker Desktop。

```bash
cp .env.example .env
```

分别执行两次 `openssl rand -base64 32`，将结果填入 `.env` 的两个密钥配置。随后启动基础设施：

```bash
docker compose up -d mysql redis mailpit
docker compose ps
```

加载环境变量并启动后端：

```bash
set -a
source .env
set +a
cd backend
./mvnw spring-boot:run
```

- API：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- 开发邮件箱：`http://localhost:8025`

## 认证 API

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/v1/auth/email-codes` | 发送注册或重置密码验证码 |
| POST | `/api/v1/auth/register` | 验证邮箱并注册 |
| POST | `/api/v1/auth/login` | 登录并签发两类 Token |
| POST | `/api/v1/auth/refresh` | 轮换 Refresh Token |
| POST | `/api/v1/auth/logout` | 注销当前会话 |
| POST | `/api/v1/auth/password-reset` | 重置密码并注销全部旧会话 |
| GET | `/api/v1/auth/me` | 获取当前用户 |

发送注册验证码：

```bash
curl -X POST http://localhost:8080/api/v1/auth/email-codes \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","purpose":"REGISTER"}'
```

验证码会出现在 Mailpit，不会写入应用日志。

## 平台账户 API

以下接口都需要携带 `Authorization: Bearer <accessToken>`：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/accounts` | 查询当前用户的活跃账户 |
| GET | `/api/v1/accounts?includeArchived=true` | 查询当前用户的全部账户 |
| GET | `/api/v1/accounts/{accountId}` | 查询当前用户的指定账户 |
| POST | `/api/v1/accounts` | 创建平台账户 |
| PUT | `/api/v1/accounts/{accountId}` | 修改账户名称和平台类型 |
| POST | `/api/v1/accounts/{accountId}/archive` | 归档空账户 |

创建账户示例：

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"支付宝账户","platform":"ALIPAY"}'
```

平台类型支持 `ALIPAY`、`TIANTIAN_FUND`、`BANK` 和 `OTHER`。同一用户不能创建同名的活跃账户；归档账户不能再修改或承接新业务；不同用户即使猜到对方的账户 ID，也只能得到“不存在”响应。

## 基金、交易与持仓 API

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/funds/{fundCode}` | 按六位代码查询支持的基金 |
| POST | `/api/v1/transactions/buys` | 按金额录入一笔买入 |
| POST | `/api/v1/transactions/sells` | 录入部分卖出或全部卖出 |
| GET | `/api/v1/transactions/{transactionId}` | 查询当前用户的一笔交易 |
| GET | `/api/v1/transactions/requests/{requestId}` | 超时后按幂等键查询原结果 |
| GET | `/api/v1/positions` | 查询当前用户的账户级持仓 |
| GET | `/api/v1/positions?accountId={accountId}` | 按平台账户筛选持仓 |
| GET | `/api/v1/positions/valuations` | 查询带盘中估值或正式净值降级的持仓 |
| POST | `/api/v1/imports/position-snapshots/preflight` | 预检持仓快照 JSON，不修改正式业务数据 |
| POST | `/api/v1/imports/position-snapshots/{batchId}/commit` | 确认最后一次成功预检并原子写入 |
| POST | `/api/v1/imports/transaction-batches/preflight` | 预检交易流水 JSON；当前支持批量买入 |
| POST | `/api/v1/imports/transaction-batches/{batchId}/commit` | 原子提交最后一次成功的交易预检 |

买入示例：

```bash
curl -X POST http://localhost:8080/api/v1/transactions/buys \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "requestId": "buy-20260724-001",
    "accountId": "账户公开ID",
    "fundCode": "005827",
    "amount": 5000.00,
    "submittedDate": "2026-07-24",
    "submittedPeriod": "BEFORE_15",
    "confirmedShares": null,
    "confirmedDate": null,
    "note": "手工录入"
  }'
```

`requestId` 在当前用户范围内唯一。相同内容重复提交会返回第一笔结果；相同 `requestId` 携带不同内容会返回 `IDEMPOTENCY_CONFLICT`。

- 有官方净值和可追溯费率：创建 `ESTIMATED` 流水并更新持仓。
- 用户填写平台确认份额：创建 `CONFIRMED` 流水并更新持仓。
- 暂缺净值或费率：保留为 `PENDING`，不伪造份额、不更新持仓。

手动卖出支持 `PARTIAL` 和 `FULL`。部分卖出可以按预计到账金额和正式净值
估算减少份额，也可以填写平台确认份额和实际到账金额；服务端使用移动平均
成本计算移出成本和已实现收益。全部卖出只有取得实际到账金额后才确认清仓，
否则保留为 `PENDING` 且不删除持仓。详细契约见
[手动卖出](docs/manual-sell.md)。

持仓快照 JSON 使用两阶段导入。预检会返回将新增、校准、保持或清仓的逐行
结果；只有 `READY_TO_COMMIT` 批次才能确认。相同 `batchId` 重复确认不会
重复记账，任意一行写入失败会整批回滚。协议和示例见
[持仓快照 JSON 导入](docs/position-snapshot-import.md)。

交易流水 JSON 同样使用两阶段导入，并与单笔买入共用交易日、15:00 截止、
净值、费率和持仓计算规则。当前切片只支持 `BUY`；`SELL` 会在预检中明确返回
`SELL_NOT_SUPPORTED_YET`，不会写入。协议和示例见
[批量交易 JSON 导入](docs/transaction-batch-import.md)。

基金目录、逐日交易日历和正式净值已经提供可替换的数据同步层。个人开发环境默认可使用免费的东方财富公开页面数据和上交所休市安排；Tushare 作为可选适配器保留。业务请求始终读取本地 MySQL，第三方同步失败时保留旧数据，不会清空或伪造数据。申购费率仍需独立、可追溯的数据来源；缺失时交易保持 `PENDING`。

### 可选：同步真实参考数据

免费开发方案不需要 Token。首次同步或需要刷新时，在 `.env` 中设置：

```dotenv
FUND_REFERENCE_DATA_PROVIDER=eastmoney-public
FUND_REFERENCE_DATA_SYNC_ON_STARTUP=true
FUND_REFERENCE_DATA_FUND_CODES=005827,000001
```

重新启动后端后会同步：

- V1 支持范围内的基金目录；
- 上交所当前年度逐日交易日历；
- 指定基金最近 10 天的正式净值。

首次同步完整目录约需一分钟。完成后建议将
`FUND_REFERENCE_DATA_SYNC_ON_STARTUP` 改回 `false`，避免每次开发启动都刷新。
启用 `FUND_REFERENCE_DATA_SCHEDULE_ENABLED=true` 后，系统会在工作日晚上增量
更新现有持仓、待处理买入和配置补充基金的净值，并在周末刷新全量目录和
交易日历。任务使用数据库租约防止多实例重复执行，详情见
[`docs/fund-reference-data.md`](docs/fund-reference-data.md)。

东方财富页面数据不是承诺稳定的正式开放 API，只用于个人学习和开发验证。若使用 Tushare，可将 Provider 改为 `tushare` 并配置 `TUSHARE_TOKEN`。完整的数据流、授权边界和配置说明见 [基金参考数据接入](docs/fund-reference-data.md)。

### 可选：盘中估值

盘中估值默认关闭。个人开发环境可配置：

```dotenv
FUND_VALUATION_PROVIDER=eastmoney-public
FUND_VALUATION_REFRESH_ENABLED=true
FUND_VALUATION_REFRESH_DELAY_MS=60000
FUND_VALUATION_FUND_CODES=005827
```

首次刷新建立分页索引，后续只请求现有持仓、待处理买入和预热基金所在分页。
估值保存到 Redis：90 秒后标记 `DELAYED`，3 分钟后标记 `STALE`；
休市、收盘或估值不可用时，
`/api/v1/positions/valuations` 使用最近正式净值并明确返回状态。详情见
[盘中基金估值](docs/fund-valuation.md)。

## 测试

测试使用 H2 和内存验证码适配器，不依赖 Docker：

```bash
cd backend
./mvnw test
```

集成测试覆盖认证生命周期、账户隔离、15:00 截止、周末交易日、基金范围、正式数据缺失降级、平台确认份额、买入与卖出的移动平均成本、已实现收益、全量清仓、跨用户资产隔离、账户归档约束、并发重复请求不重复记账、快照与批量买入的预检/幂等/原子回滚，以及第三方协议解析、参考数据幂等同步、估值分页索引、实时/过期状态和正式净值降级。
