# 持仓快照 JSON 导入

## 1. 为什么采用两阶段导入

快照可能同时创建账户、调整多只基金，`FULL_ACCOUNT` 还可能清空未列出的
持仓。如果收到 JSON 后立即写入，用户无法在危险操作前检查结果。

因此接口分成两个阶段：

```text
原始 JSON
→ 语法 / Schema / 业务预检
→ portfolio_import_batches（暂存）
→ 用户确认
→ 单事务写入账户、调整流水和当前持仓
```

预检只写暂存批次，不修改 `fund_accounts`、`fund_transactions` 或
`fund_positions`。

## 2. API

### 2.1 预检

```http
POST /api/v1/imports/position-snapshots/preflight
Content-Type: application/json
Authorization: Bearer <accessToken>
```

请求体直接使用产品协议中的 `POSITION_SNAPSHOT` JSON。返回状态：

- `READY_TO_COMMIT`：无错误，可以确认；
- `PREFLIGHT_FAILED`：查看 `issues` 和逐行 `rows[].issues`；
- `COMMITTED`：相同批次和相同内容已经成功提交。

行操作包括 `ADD`、`UNCHANGED`、`CALIBRATE`、`CLEAR` 和 `REJECT`。
缺少 `confirmedShares` 时，服务端使用快照日期之前最近的正式净值，根据
`currentAmount` 推算份额，并返回 `SHARES_ESTIMATED` 警告。

### 2.2 确认

```http
POST /api/v1/imports/position-snapshots/{batchId}/commit
Authorization: Bearer <accessToken>
```

确认前服务端会重新计算计划指纹。如果账户、持仓或快照边界在预检后发生变化，
确认会被拒绝，用户必须重新预检。相同 `batchId` 重复确认返回第一次结果，
不会重复创建流水。

## 3. FULL_ACCOUNT 与 PARTIAL

- `FULL_ACCOUNT`：未出现在 JSON 中的现有基金生成 `CLEAR` 警告；确认后删除
  当前持仓投影，同时保留一笔 `POSITION_ADJUSTMENT` 审计流水。
- `PARTIAL`：只处理列出的基金，其他持仓保持不变。

快照成功后会形成账户时间边界。有效交易日不晚于该快照日期的新交易会返回
`TRANSACTION_BEFORE_SNAPSHOT`，避免重复计算；更早的历史交易需要未来的
历史重建流程处理。

## 4. 一致性与安全

- `batchId` 在用户范围内唯一；
- 内容哈希防止已提交批次被替换；
- 账户创建和所有持仓调整处于同一数据库事务；
- 任意一行写入失败，整个确认批次回滚；
- JSON 未定义字段会被拒绝，客户端不能指定内部 `status`；
- 最多 500 条持仓，金额最多 4 位小数，份额最多 8 位小数。
