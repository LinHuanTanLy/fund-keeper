# 批量交易 JSON 导入

## 1. 当前范围

`TRANSACTION_BATCH` 用于把多笔交易一次性导入同一平台账户。当前实现完成
批量买入；卖出字段已保留在协议中，但 `SELL` 会在预检时返回
`SELL_NOT_SUPPORTED_YET`，不会静默忽略或写入。

批量买入与单笔买入共用同一个买入计划器，因此交易日、15:00 截止、正式
净值、申购费率、平台确认份额和持仓成本的计算口径一致。

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
      "type": "BUY",
      "amount": 1000.00,
      "submittedDate": "2026-07-23",
      "submittedPeriod": "AFTER_15",
      "confirmedShares": 812.34567890,
      "confirmedDate": "2026-07-24"
    }
  ]
}
```

`rowId` 在批次内唯一。金额最多 4 位小数，份额最多 8 位小数，单批最多
500 条。协议未定义字段会被拒绝，调用方不能传入内部 `status`。

## 4. 快照边界

持仓快照成功后会形成账户时间边界。有效交易日不晚于最近快照日期的交易返回
`HISTORY_REBUILD_REQUIRED`，不能直接追加，以免把快照已经包含的资产再计算
一次。历史重建能力尚未实现。
