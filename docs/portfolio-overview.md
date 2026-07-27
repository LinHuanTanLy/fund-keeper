# 首页资产与收益总览

## 1. API

```http
GET /api/v1/portfolio/overview
Authorization: Bearer <accessToken>
```

可通过 `accountId` 只汇总一个有效账户。默认汇总当前用户的全部有效账户；
归档账户的历史交易仍能在交易记录中查询，但不进入首页。

## 2. 核心口径

```text
当前持有收益 = 当前市值 - 当前剩余成本
当前持有收益率 = 当前持有收益 ÷ 已纳入估值的持仓成本 × 100%
累计收益 = 当前持有收益 + 已确认卖出收益
累计收益率 = 累计收益 ÷ returnCostBasis × 100%
今日预估收益 = 当前份额 × (盘中预估净值 - 上一官方净值)
```

`returnCostBasis` 由已纳入估值的当前持仓成本与已确认卖出的移除成本组成。
它是成本基础收益率，不是年化收益率、内部收益率或时间加权收益率。

只有 `SELL + CONFIRMED` 进入 `realizedProfit`。待确认、预估、撤销和冲正
记录不得冒充真实已实现收益。

## 3. 缺价与部分汇总

响应同时返回：

- `positionCount`：当前持仓数；
- `valuedPositionCount`：成功取得盘中估值或正式净值的持仓数；
- `missingValuationCount`：未取得任何可用价格的持仓数；
- `valuationComplete`：是否所有持仓都已纳入金额汇总；
- `containsEstimatedData`：是否包含预估持仓、盘中估值或未结卖出。

部分基金缺价时，`currentMarketValue`、`currentHoldingProfit` 和
`cumulativeProfit` 是可估值部分的结果，并明确返回
`valuationComplete=false`。全部基金缺价时，这些字段返回 `null`，
不能使用 `0` 假装没有收益或亏损；已确认的 `realizedProfit` 仍单独返回。

无持仓时市值和持有收益为 `0`，这是确定的空集合结果，不属于缺价。

## 4. 估值状态

- 盘中 `LIVE` 或 `DELAYED` 估值用于当前市值和今日预估收益；
- `STALE`、`MARKET_CLOSED` 或 `UNAVAILABLE` 时回退最近正式净值；
- `todayEstimatedProfit` 只在至少一只基金存在有效盘中估值时返回；
- `todayEstimateComplete` 表示全部持仓都进入今日预估收益；
- `dataDate` 和 `observedAt` 使用汇总中最早的数据时间，避免把部分较新
  数据的时间冒充整个组合的更新时间。

连续持有天数从当前持仓中最早的 `holdingStartDate` 起按自然日计算，
买入当天计为第 1 天；加仓和部分卖出不重置，全部清仓后不再返回持有天数。

## 5. 跨账户基金卡片

```http
GET /api/v1/portfolio/funds
Authorization: Bearer <accessToken>
```

同样支持可选的 `accountId`。未指定账户时，同一只基金在多个有效账户中的
持仓合并为一张卡片，响应包含：

- 基金代码、名称和参与聚合的账户数；
- 是否已经形成当前持仓、未完成交易数和待确认买入金额；
- 总份额、当前持仓成本和当前市值；
- 当前持有收益与收益率；
- 该基金的已实现收益和累计收益；
- 今日盘中预估收益、连续持有天数和估值状态。

底层持仓和交易流水不会因展示聚合而合并。点击卡片后仍可按账户查看明细。
已全部清仓且没有未完成交易的基金不出现在当前基金卡片列表，其历史收益保留在
首页总览和交易记录。

只有 `PENDING`、`ESTIMATED` 或 `NEEDS_CALIBRATION` 买入、尚未形成任何
持仓时，基金仍以待处理卡片展示：

- `hasCurrentPosition=false`；
- `pendingBuyAmount` 展示这些未完成买入的已提交金额；
- `openTransactionCount` 展示该基金全部未完成交易数；
- `totalShares`、`holdingCost`、`currentMarketValue`、
  `currentHoldingProfit`、收益率和今日收益均为 `null`。

待确认买入与已有持仓分布在不同账户时仍合并成同一张卡片。`accountCount`
包含持仓账户和未完成交易账户；份额、成本和收益仍只计算真实存在的持仓，
不能把待确认金额当成当前资产。

列表按可用当前市值从高到低排序，市值相同时按基金代码升序；完全缺价的基金
排在最后。卖出收益由数据库一次按 `fundId` 分组聚合，不能为每张卡片单独查询。

## 6. 基金详情

```http
GET /api/v1/portfolio/funds/000001?page=0&size=20
Authorization: Bearer <accessToken>
```

该接口面向首页当前持仓卡片，一次返回四部分：

- `summary`：该基金在全部有效账户中的汇总，口径与首页卡片一致；
- `accounts`：按账户拆分的份额、成本、市值、持有收益、已实现收益、
  今日预估收益和估值状态；
- `openTransactions`：有效账户中该基金的 `PENDING`、`ESTIMATED` 或
  `NEEDS_CALIBRATION` 未完成记录，不受历史分页影响；
- `transactions`：该基金的完整历史交易分页，包含归档账户留下的历史记录。

`accounts` 中的已实现收益由数据库一次按 `accountId` 分组统计，不能逐账户
循环查询。顶部和账户明细只统计有效账户，因此其收益数字不会混入已归档账户；
历史分页保留归档账户记录是为了满足审计和追溯需要。

历史分页仍使用 `createdAt DESC, id DESC` 的稳定顺序，`page` 从 0 开始，
`size` 范围为 1～100。当前用户没有该基金持仓时返回
`POSITION_NOT_FOUND`；如果存在未完成交易则仍可打开详情。纯待确认买入的
详情中 `accounts` 为空，账户与待处理原因由 `openTransactions` 提供。
这些规则不会暴露其他用户是否持有该基金。

待确认买入的确认、撤销和安全边界见
[手动买入与确认](manual-buy.md)。
