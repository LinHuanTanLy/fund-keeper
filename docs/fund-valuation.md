# 盘中基金估值

> 核验日期：2026-07-24  
> 当前定位：个人学习和开发验证，不代表可商用的数据授权

## 1. 数据链路

```text
持仓 / PENDING 买入 / 预热代码
              ↓
     交易时段与数据库租约
              ↓
IntradayValuationProvider
              ↓
首次全量分页索引 → 后续只请求命中分页
              ↓
          Redis TTL
              ↓
GET /api/v1/positions/valuations
```

盘中估值只写 Redis，不写 `fund_navs`，也不参与交易份额确认。MySQL 中的
正式净值仍是事实数据和降级依据。

## 2. 本地启用

```dotenv
FUND_VALUATION_PROVIDER=eastmoney-public
FUND_VALUATION_REFRESH_ENABLED=true
FUND_VALUATION_REFRESH_DELAY_MS=60000
FUND_VALUATION_FUND_CODES=005827
```

任务只在数据库交易日历判定开市，并且处于
`09:30–11:30` 或 `13:00–15:00` 时请求上游。默认每 60 秒运行一次；
多个应用实例通过数据库租约保证只有一个实例刷新。

首次刷新会下载完整估值列表并在 Redis 建立“基金代码 → 页码”索引。索引
默认保留 24 小时；后续只请求活跃基金所在分页。单轮最多请求 10 个分页，
相邻请求至少间隔 300ms。找不到的基金也会负缓存，避免每分钟重复全量抓取。

## 3. 状态语义

| 状态 | 含义 |
|---|---|
| `LIVE` | 交易时段、估值日期为今天，并且本系统抓取时间不超过 90 秒 |
| `DELAYED` | 估值日期为今天，抓取时间超过 90 秒、但不超过 3 分钟 |
| `STALE` | 有盘中估值，但日期不对或抓取时间超过 3 分钟；持仓接口降级到正式净值 |
| `MARKET_CLOSED` | 非交易日、午休或收盘后，接口使用最近正式净值 |
| `UNAVAILABLE` | 交易时段无估值或 Redis 不可用，尝试使用正式净值 |

返回字段 `observedAt` 是 Fund Keeper 的抓取时间，不冒充第三方精确更新时间。
`priceType=ESTIMATED` 表示盘中估算；`priceType=OFFICIAL` 表示正式净值降级。

## 4. 使用边界

东方财富公开页面当前说明盘中估值在交易时段更新，但列表接口没有正式 SLA，
也没有按基金代码过滤能力。实测全量响应约 12.6MB，因此不能每分钟全量轮询。
本项目用分页索引降低请求量，并默认关闭估值任务。

公开可访问不等于获得商业授权。公开部署前必须改接有授权、限流说明和 SLA
的数据供应商；业务层只依赖 `IntradayValuationProvider`，替换供应商时无需
修改持仓计算。
