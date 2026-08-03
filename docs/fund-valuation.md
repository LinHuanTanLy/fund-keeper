# 基金价格与净值

> 核验日期：2026-07-28
> 当前方案：场内 ETF 免费公开行情 + 场外基金正式净值

## 1. 为什么分两条链路

场内 ETF 在交易所二级市场成交，持仓市值应使用市场价格；场外基金按基金
公司公布的单位净值确认，不能用 ETF 行情或猜测值替代。系统通过
`funds.trading_mode` 区分：

| 类型 | 价格来源 | `priceType` |
|---|---|---|
| `EXCHANGE_TRADED` | 东方财富公开市场行情 | `MARKET` |
| `OFF_EXCHANGE` | `fund_navs` 中最近正式净值 | `OFFICIAL` |
| 混合组合 | 两种来源共同汇总 | `MIXED` |

历史 `ESTIMATED` 枚举和适配器暂时保留用于协议兼容，但默认不启用。原公开
场外基金估值列表已于核验日返回“暂无数据”，不能把最近正式净值冒充盘中
估值。

## 2. 场内 ETF 行情链路

```text
活跃场内 ETF → 交易时段判断 → MarketQuoteProvider
             → Redis TTL → 持仓市值与今日收益
```

`eastmoney-market` 批量读取最新成交价、昨收、涨跌幅和上游行情时间。默认
每 60 秒刷新，多个实例通过数据库租约避免重复任务。Redis 只保存短期行情；
交易、持仓和正式净值仍以 MySQL 为准。

```dotenv
FUND_VALUATION_PROVIDER=eastmoney-market
FUND_VALUATION_REFRESH_ENABLED=true
FUND_VALUATION_REFRESH_DELAY_MS=60000
FUND_VALUATION_FUND_CODES=
```

`FUND_VALUATION_FUND_CODES` 仅用于可选预热，真实场内持仓会自动加入。场内
ETF 目前按代码段（深市 `15`、沪市 `5`）、名称含 `ETF` 且不含“联接”
识别。

## 3. 状态与降级

| 状态 | 含义 |
|---|---|
| `LIVE` | 开市且上游行情时间不超过 90 秒 |
| `DELAYED` | 开市，行情超过 90 秒但不超过 3 分钟 |
| `STALE` | 开市但行情日期不对或超过 3 分钟，降级正式净值 |
| `OFFICIAL` | 场外基金使用最近正式净值 |
| `MARKET_CLOSED` | 休市、午休或收盘，保留已缓存的最近市场价格 |
| `UNAVAILABLE` | 行情和正式净值均不可用 |

`observedAt` 是上游行情时间；正式净值使用 `dataDate`。场内“今日收益”按
`份额 ×（市场价格 - 昨收）` 计算。场外基金在当日正式净值尚未公布时，不
伪造今日收益。

## 4. 免费方案边界

东方财富行情端点可免费访问，但不是带 SLA 的正式开放 API，只适合个人学习
和自用；公开部署或商业分发前必须确认数据授权、限流和再分发条款。接口失败
时系统保留缓存并降级，绝不生成价格。Tushare `fund_nav` 是正式日净值；
其实时 ETF/IOPV 能力需要额外权限，因此不是当前零成本方案。
