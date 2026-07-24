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

当前阶段尚未提供对未决卖出的补充确认、取消和校准接口。需要立即完成清仓记录
时，应在取得平台实际到账金额后录入；后续阶段会补充状态维护和历史重算能力。
