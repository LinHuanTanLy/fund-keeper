# 批量交易 JSON 导入

## 1. 当前范围

`TRANSACTION_BATCH` 用于把多笔交易一次性导入同一平台账户。当前实现完成
批量买入、部分卖出和全部卖出。

批量交易与单笔交易共用买入、卖出计划器，因此交易日、15:00 截止、正式
净值、费用、平台确认份额、移动平均成本和已实现收益的计算口径一致。

## 2. 两阶段 API

```http
POST /api/v1/imports/transaction-batches/preflight
Authorization: Bearer <accessToken>
Content-Type: application/json
```

预检只暂存导入批次，不创建账户、交易或持仓。无错误时返回
`READY_TO_COMMIT`，并逐行展示有效交易日、费用、净金额、份额和状态。

```http
POST /api/v1/imports/transaction-batches/{batchId}/commit
Authorization: Bearer <accessToken>
```

确认前会重新计算计划。账户、行情数据或快照边界发生变化时，服务端拒绝确认，
要求重新预检。账户创建、全部交易和持仓更新在同一事务中完成，任意一行失败
整批回滚。相同 `batchId` 重复确认返回首次结果，不重复记账。

## 3. 请求示例

```json
{
  "schemaVersion": "1.0",
  "importType": "TRANSACTION_BATCH",
  "batchId": "transactions-20260724-001",
  "account": {
    "name": "我的支付宝",
    "platform": "ALIPAY"
  },
  "transactions": [
    {
      "rowId": "row-001",
      "fundCode": "005827",
      "type": "BUY",
      "amount": 5000.00,
      "submittedDate": "2026-07-24",
      "submittedPeriod": "BEFORE_15"
    },
    {
      "rowId": "row-002",
      "fundCode": "000001",
      "type": "SELL",
      "sellMode": "PARTIAL",
      "expectedAmount": 1000.00,
      "actualReceivedAmount": 980.00,
      "submittedDate": "2026-07-24",
      "submittedPeriod": "BEFORE_15",
      "confirmedShares": 812.34567890,
      "confirmedDate": "2026-07-24"
    }
  ]
}
```

`rowId` 在批次内唯一。买入使用 `amount`；卖出使用 `sellMode`、
`expectedAmount` 和 `actualReceivedAmount`。部分卖出必须提供预计到账金额，
全部卖出不要求份额。金额最多 4 位小数，份额最多 8 位小数，单批最多 500 条。
协议未定义字段会被拒绝，调用方不能传入内部 `status`。

若目标账户中的同一基金存在 `PENDING` 或 `ESTIMATED` 卖出，该交易行返回
`OPEN_SELL_CONFLICT`。先补充确认或撤销卖出，再重新预检。

## 4. 行顺序和持仓投影

预检按照 `transactions` 数组顺序模拟持仓，确认也严格使用相同顺序。例如，
第一行确认买入 500 份，第二行可以基于这 500 份执行部分卖出。预检中的
`calculatedShares`、`removedCost` 和 `realizedProfit` 都来自这一投影。

产生 `PENDING` 或 `ESTIMATED` 卖出后，同一批次内该基金的后续买入或卖出返回
`OPEN_SELL_CONFLICT`。任意一行失败时批次不可确认；确认过程中任意一行失败，
账户、全部交易和持仓修改整批回滚。

## 5. 快照边界

持仓快照成功后会形成账户时间边界。有效交易日不晚于最近快照日期的交易返回
`HISTORY_REBUILD_REQUIRED`，不能直接追加，以免把快照已经包含的资产再计算
一次。历史重建能力尚未实现。
