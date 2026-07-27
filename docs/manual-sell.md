# 手动卖出

## 1. API

```http
POST /api/v1/transactions/sells
Authorization: Bearer <accessToken>
Content-Type: application/json
```

部分卖出示例：

```json
{
  "requestId": "sell-20260724-001",
  "accountId": "账户公开ID",
  "fundCode": "005827",
  "sellMode": "PARTIAL",
  "expectedAmount": 2000.00,
  "actualReceivedAmount": null,
  "submittedDate": "2026-07-24",
  "submittedPeriod": "BEFORE_15",
  "confirmedShares": null,
  "confirmedDate": null,
  "note": "部分卖出"
}
```

全部卖出将 `sellMode` 改为 `FULL`。全部卖出不要求
`expectedAmount`；取得平台结果后填写 `actualReceivedAmount`。

## 2. 状态与持仓影响

| 场景 | 状态 | 持仓影响 |
|---|---|---|
| 部分卖出，仅预计金额且正式净值可用 | `ESTIMATED` | 按净值估算份额并减少预估持仓 |
| 部分卖出，份额与实际到账均已提供 | `CONFIRMED` | 按确认份额减少持仓 |
| 部分卖出，缺少正式净值且没有确认份额 | `PENDING` | 不修改持仓 |
| 全部卖出，尚无实际到账金额 | `PENDING` | 不删除持仓 |
| 全部卖出，已有实际到账金额 | `CONFIRMED` | 清空持仓并结束持有周期 |

部分卖出使用移动平均成本：

```text
移出成本 = 卖出份额 × 卖出前平均单位成本
剩余成本 = 原剩余成本 - 移出成本
已实现收益 = 到账金额 - 移出成本
```

响应中的 `removedCost` 和 `realizedProfit` 会明确展示上述结果。

## 3. 一致性规则

- `requestId` 在用户范围内幂等；
- 账户、基金和持仓都按当前登录用户隔离；
- 卖出流水与持仓更新处于同一数据库事务；
- 份额不能超过当前持仓，部分卖出不能等同于清仓；
- 预计金额超过正式净值计算的持仓市值时拒绝提交；
- 同一账户、同一基金最多存在一笔 `PENDING` 或 `ESTIMATED` 卖出；
- 卖出有效交易日必须晚于最近一次生效快照。

## 4. 补充确认

```http
POST /api/v1/transactions/{transactionId}/sell-confirmation
```

```json
{
  "actualReceivedAmount": 1980.00,
  "confirmedShares": 1650.12345678,
  "confirmedDate": "2026-07-27"
}
```

`actualReceivedAmount` 必填。部分卖出还必须填写平台确认份额，不能只凭预计金额
把交易标记为确认；全部卖出可省略份额，服务端使用卖出前的全部持仓份额。确认时
服务端先还原卖出前基线，再根据最终份额重新计算移出成本、剩余持仓和已实现收益。
相同结果重复提交会幂等返回，不能用该接口覆盖已经确认的结果。

## 5. 撤销未成交卖出

```http
POST /api/v1/transactions/{transactionId}/cancel
```

```json
{
  "reason": "平台最终未成交"
}
```

只有 `PENDING` 或 `ESTIMATED` 卖出可以撤销。撤销预估部分卖出会恢复卖出前
份额、成本、确认状态和持有开始日期；交易记录保留为 `CANCELLED`，并记录原因
与时间。`CONFIRMED` 交易不能撤销，后续应通过独立更正流程处理。

未决卖出处理完成前，同账户同基金不能继续买入，账户也不能覆盖持仓快照。若持仓
已被外部修改，确认或撤销会返回 `SELL_STATE_CONFLICT`，避免用猜测数据回滚。
V7 之前创建且缺少卖出前快照的历史未决记录同样需要人工校准。

## 6. 查询与汇总

交易记录分页、条件筛选和累计已实现收益的统计口径见
[交易记录查询与卖出收益汇总](transaction-history.md)。只有 `CONFIRMED`
卖出进入已实现收益，预估、待确认和已撤销记录不会混入真实收益。
