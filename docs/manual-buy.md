# 手动买入与确认

## 1. 创建买入

```http
POST /api/v1/transactions/buys
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "requestId": "buy-20260727-001",
  "accountId": "账户公开ID",
  "fundCode": "005827",
  "amount": 1000.00,
  "submittedDate": "2026-07-27",
  "submittedPeriod": "BEFORE_15",
  "confirmedShares": null,
  "confirmedDate": null,
  "note": "手动买入"
}
```

`requestId` 在用户范围内幂等。系统能取得有效净值和费率时可生成
`ESTIMATED` 持仓；信息不足时保存为 `PENDING`，且不提前修改持仓。
如果平台份额和确认日期均已提供，则直接生成 `CONFIRMED` 记录。

## 2. 确认待处理买入

```http
POST /api/v1/transactions/{transactionId}/buy-confirmation
```

```json
{
  "confirmedShares": 486.12345678,
  "confirmedDate": "2026-07-28"
}
```

当前只允许将 `PENDING` 买入确认成 `CONFIRMED`。确认后，平台最终份额加入
当前持仓，买入金额加入剩余成本，并据此重新计算移动平均单位成本。新建持仓的
连续持有起点采用确认日期；确认日期未知时，持有天数也保持未知。

相同份额和日期重复提交会幂等返回，不会重复增加持仓。已经确认的交易不能用不同
结果覆盖。

## 3. 撤销待处理买入

买入和卖出共用撤销入口：

```http
POST /api/v1/transactions/{transactionId}/cancel
```

```json
{
  "reason": "平台最终未成交"
}
```

只有 `PENDING` 买入可以直接撤销。该状态尚未形成持仓，因此撤销只保留
`CANCELLED` 交易及原因、时间，不执行持仓补偿；重复撤销幂等返回。

## 4. 安全边界

- `ESTIMATED` 买入已经影响移动平均成本，暂不支持自动确认或撤销，必须进入后续
  的持仓校准流程，不能用简单相减猜测历史状态；
- 同账户同基金存在未完成卖出时，不能确认买入；
- 有效交易日早于或等于最近已生效持仓快照时，不能确认并改写持仓；
- 账户、交易和持仓都按当前登录用户隔离；
- 已确认或已撤销买入不能反向改变状态。

确认时对交易、账户和未决卖出重新校验，并在同一数据库事务内更新持仓与流水。
接口的正常路径、幂等、用户隔离、日期、卖出和快照冲突均由集成测试覆盖。
