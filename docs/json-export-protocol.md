# JSON 导出与可移植恢复协议

## 1. 结论

现有 `POSITION_SNAPSHOT 1.0` 和 `TRANSACTION_BATCH 1.0` 面向用户录入，不能
直接承担完整备份：它们一次只处理一个账户，交易协议只接受 `BUY`、`SELL`，
并会根据当前净值、费率和交易日重新计算结果。

V1 新增独立的 `PORTFOLIO_TRANSFER 1.0` 协议。导出文件必须能原样进入该协议
的预检接口；不把校准、取消或冲正记录伪装成普通买卖。

## 2. 导出范围

请求必须显式选择：

- `contentType`：`POSITION_SNAPSHOT` 或 `FULL_HISTORY`；
- `accountScope`：`ALL_ACCOUNTS` 或 `SINGLE_ACCOUNT`；
- `accountId`：只在单账户导出时使用，且必须属于当前登录用户。

服务端生成新的 `transferId`，不复用历史导入批次 ID。文件包含协议版本、导出
时间、内容类型、账户范围和账户数据，不包含用户 ID、邮箱、密码、Token、
验证码、权限或服务密钥。

## 3. 文件结构

```json
{
  "schemaVersion": "1.0",
  "importType": "PORTFOLIO_TRANSFER",
  "transferId": "01J...",
  "exportedAt": "2026-08-03T12:00:00Z",
  "contentType": "POSITION_SNAPSHOT",
  "accountScope": "ALL_ACCOUNTS",
  "accounts": [
    {
      "accountRef": "account-001",
      "name": "我的支付宝",
      "platform": "ALIPAY",
      "positions": [
        {
          "fundCode": "005827",
          "shares": 486.12345678,
          "costAmount": 1000.0000,
          "status": "ESTIMATED",
          "holdingStartDate": "2026-07-24"
        }
      ]
    }
  ]
}
```

`FULL_HISTORY` 将 `positions` 替换为按时间和稳定序号排序的 `events`。事件保留
`BUY`、`SELL`、`POSITION_ADJUSTMENT` 类型，以及 `PENDING`、`ESTIMATED`、
`CONFIRMED`、`CANCELLED`、`REVERSED` 状态和关联关系。恢复时使用导出结果，
不得再次按最新行情推算历史。

## 4. 预检与确认

```http
POST /api/v1/imports/portfolio-transfers/preflight
POST /api/v1/imports/portfolio-transfers/{transferId}/commit
```

预检验证 Schema、基金代码、账户冲突、事件关联、份额/成本连续性和最终持仓。
它只写暂存批次，不修改正式数据，并逐账户返回新增、校准、清仓、拒绝数量。

- 快照可创建账户，也可校准同名同平台的现有账户；
- 完整历史只能恢复到新账户或没有持仓和交易的空账户；
- 一个账户失败时整份文件不可确认；
- 确认前重新验证计划指纹，全部账户在同一事务中写入；
- 相同 `transferId` 和内容重复确认返回首次结果；相同 ID、不同内容直接拒绝。

## 5. 实现与验收顺序

1. 先实现当前持仓快照的全部/单账户导出、预检和恢复。
2. 为历史事件定义稳定关联 ID 和状态恢复规则。
3. 实现完整历史导出及空账户恢复，不开放覆盖已有历史。
4. 增加往返测试：导出 → 预检 → 确认 → 再导出，比较账户、交易、持仓和状态。
5. 扫描导出 JSON，验证不包含敏感字段；记录操作类型和数量，不记录资产正文。

在完整历史往返测试通过前，客户端只开放“当前持仓快照”导出，不展示不可用的
“完整交易流水”按钮。
