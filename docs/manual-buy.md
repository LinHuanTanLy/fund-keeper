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

场内 ETF 同样允许只填写金额，但实时行情只能用于当前市值，不能证明历史
成交结果。缺少券商最终成交份额时只能形成 `ESTIMATED` 或 `PENDING` 记录；
补充最终成交份额和确认日期后，才可以按平台事实校准为 `CONFIRMED`。

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

`PENDING` 和具备前置持仓快照的 `ESTIMATED` 买入都可以确认成
`CONFIRMED`。确认 `PENDING` 时，平台份额和买入金额加入当前持仓；校准
`ESTIMATED` 时，服务端先恢复买入前持仓，再用平台最终份额重建该笔买入，
不会把最终份额重复累加到估算份额上。

买入金额仍作为持仓成本，并据此重新计算移动平均单位成本。新建持仓的连续持有
起点采用确认日期；加仓取原持有起点和本次确认日期中的较早值。

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

`PENDING` 买入尚未形成持仓，因此撤销只更新流水。撤销 `ESTIMATED` 买入时，
服务端使用前置快照恢复原份额、成本、状态和持有日期；如果它原本是首笔买入，
则删除该笔估算产生的持仓。两种撤销都保留 `CANCELLED` 交易及原因、时间，
重复撤销幂等返回。

## 4. 安全边界

- 校准或撤销前，当前持仓必须仍等于该笔估算买入执行后的结果；
- 该笔买入之后存在其他已影响持仓的交易时，必须执行历史重建，不能局部修改；
- 功能上线前产生且缺少前置快照的历史估算买入不能自动处理；
- 同账户同基金存在未完成卖出时，不能确认买入；
- 有效交易日早于或等于最近已生效持仓快照时，不能确认并改写持仓；
- 账户、交易和持仓都按当前登录用户隔离；
- 已确认或已撤销买入不能反向改变状态。

确认和撤销在同一数据库事务内更新持仓与流水。当前切片只覆盖平台份额驱动的
`ESTIMATED` 买入校准；`NEEDS_CALIBRATION` 快照差异确认和已确认交易冲正
仍属于后续历史重建功能。
