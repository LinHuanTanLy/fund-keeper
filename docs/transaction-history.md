# 交易记录查询与卖出收益汇总

## 1. 交易记录

```http
GET /api/v1/transactions?page=0&size=20
Authorization: Bearer <accessToken>
```

可选筛选参数：

| 参数 | 示例 | 说明 |
|---|---|---|
| `accountId` | 账户公开 ID | 只能查询当前用户的账户 |
| `fundCode` | `000001` | 基金代码 |
| `type` | `BUY`、`SELL` | 交易类型，不区分大小写 |
| `status` | `CONFIRMED` | 交易状态，不区分大小写 |
| `page` | `0` | 从 0 开始 |
| `size` | `20` | 1～100，默认 20 |

响应示例：

```json
{
  "code": "SUCCESS",
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

列表固定按 `createdAt DESC, id DESC` 排序。第二排序字段用于解决两条记录
创建时间相同时顺序不确定的问题，从而保证分页稳定。所有查询都强制包含当前登录
用户 ID；传入其他用户的账户 ID 会返回 `ACCOUNT_NOT_FOUND`，而不是空列表。

## 2. 卖出收益汇总

```http
GET /api/v1/transactions/summary?accountId=<账户ID>&fundCode=000001
Authorization: Bearer <accessToken>
```

`accountId` 与 `fundCode` 均可省略。响应字段：

```json
{
  "confirmedSellCount": 1,
  "totalActualReceivedAmount": 390.0000,
  "totalRemovedCost": 400.0000,
  "totalRealizedProfit": -10.0000,
  "openSellCount": 1
}
```

统计口径：

- 金额和已实现收益只汇总 `SELL + CONFIRMED`；
- `totalRealizedProfit = totalActualReceivedAmount - totalRemovedCost`；
- `openSellCount` 只统计 `PENDING` 与 `ESTIMATED`；
- `CANCELLED`、`REVERSED`、预估金额和买入记录不进入已实现收益；
- 撤销未确认卖出只会减少 `openSellCount`，不会改变历史已实现收益。

汇总由数据库执行条件聚合，应用层不加载全部交易后再计算，避免数据量增长后占用
过多内存。
