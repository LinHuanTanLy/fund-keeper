# 基金参考数据接入

> 核验日期：2026-07-24  
> 当前用途：个人学习、开发验证和面试展示

## 1. 为什么先同步到本地

买入接口不会在用户请求中直接调用第三方数据源，而是只查询 MySQL：

```text
东方财富公开页面 ──→ 基金目录、正式净值
上交所休市安排 ───→ 当前年度交易日历
Tushare（可选） ──→ 统一 Provider 接口
                         ↓
                      幂等 Store
                         ↓
funds / fund_provider_identifiers / fund_trading_days / fund_navs
                         ↓
                    买入与持仓业务
```

这样第三方超时不会拖垮用户请求；同步失败时继续保留上次成功的数据。数据库中的 `data_source` 记录实际来源。

## 2. 当前数据边界

| 数据 | 当前来源 | 处理方式 |
|---|---|---|
| 基金目录 | 东方财富 `fundcode_search.js` | 精确白名单：股票型、混合型、股票指数型 |
| 交易日历 | 上交所年度休市安排 | 当前年度工作日减去官方休市区间 |
| 正式净值 | 东方财富 `pingzhongdata/{code}.js` | 只同步配置的基金代码与日期范围 |
| 确认延迟 | 当前来源未提供 | 保持未知，不统一假设为 T+1 |
| 申购费率 | 暂无统一可靠来源 | 不猜测；缺失时买入保持 `PENDING` |
| 盘中估值 | 尚未接入 | 后续作为独立数据链路，不能覆盖正式净值 |

实现参考 AKShare 当前的
[公募基金接口](https://akshare.akfamily.xyz/data/fund/fund_public.html)，
但 Fund Keeper 直接用 Java 读取原始页面数据，不需要运行 Python 服务。
上交所来源为官方[年度休市安排](https://www.sse.com.cn/disclosure/dealinstruc/closed/)。

免费 Provider 只生成上交所已经公布的当前年度日历，不推测下一年度节假日。
尚未公布的日期在业务层按“数据不可用”处理，而不是默认开市。

## 3. 本地启用

先在 `.env` 中配置：

```dotenv
FUND_REFERENCE_DATA_PROVIDER=eastmoney-public
FUND_REFERENCE_DATA_SYNC_ON_STARTUP=true
FUND_REFERENCE_DATA_FUND_CODES=005827,000001
```

然后按 README 的方式启动后端。同步会：

1. 同步符合 V1 范围的基金目录；
2. 同步配置范围与上交所当前年度重合的交易日历；
3. 同步指定基金最近 10 天的净值；
4. 输出写入数量和失败摘要。

所有写入都是更新或插入；重复启动不会生成重复记录。首次真实验证写入
16,172 只 V1 基金、191 个日历日和基金 `005827` 的 7 条近期净值。
完整目录首次同步约需一分钟，完成后应关闭启动同步。时间范围可通过
`FUND_REFERENCE_DATA_*` 环境变量调整。

## 4. 定时增量同步

首次全量同步成功后，可以启用后台定时任务：

```dotenv
FUND_REFERENCE_DATA_SYNC_ON_STARTUP=false
FUND_REFERENCE_DATA_SCHEDULE_ENABLED=true
FUND_REFERENCE_DATA_DAILY_NAV_CRON="0 30 20 * * MON-FRI"
FUND_REFERENCE_DATA_FULL_REFRESH_CRON="0 0 6 * * SAT"
```

- 工作日 20:30 更新全部现有持仓、待处理买入以及
  `FUND_REFERENCE_DATA_FUND_CODES` 补充基金的近期正式净值；
- 周六 06:00 刷新基金目录、当前年度交易日历和配置基金净值；
- Cron 默认使用 `Asia/Shanghai`；
- 每次仍回看最近 10 天并幂等写入，能够补回短暂失败或延迟公布的数据。

两个任务共用数据库租约，同一时间只有一个应用实例能够执行。租约默认
10 分钟，进程异常退出后可自动过期。表 `reference_data_sync_jobs` 持久化
上次开始时间、完成时间、`SUCCESS/PARTIAL/FAILED` 状态和摘要，可用于排查。
单个数据源失败时任务记为 `PARTIAL`，已有数据不会被删除。

`FUND_REFERENCE_DATA_FUND_CODES` 不再承担生产持仓清单的职责。它只是开发和
预热入口；用户新增持仓或产生待处理买入后，基金代码会被自动加入后续净值
同步范围。

可选的 Tushare 配置：

```dotenv
FUND_REFERENCE_DATA_PROVIDER=tushare
FUND_REFERENCE_DATA_SYNC_ON_STARTUP=true
TUSHARE_TOKEN=替换为自己的Token
```

Tushare 的[基金列表](https://tushare.pro/document/2?doc_id=19)、
[基金净值](https://tushare.pro/document/2?doc_id=119)和
[交易日历](https://tushare.pro/document/2?doc_id=26)
当前均要求至少 2000 积分。

## 5. 生产使用边界

东方财富入口属于可公开访问的页面数据，不等于对外承诺兼容性或授予商业数据许可；AKShare 也明确将相关数据定位为学术研究用途。Tushare 的默认服务协议同样包含个人使用和禁止转授权等限制。公开或商业部署前，必须取得适合产品用途的数据许可，不能因为接口可访问就默认可以商用。

届时实现新的 `FundReferenceDataProvider`，业务层和数据库读取逻辑无需改动。免费源字段变化或暂时不可用时，同步记录失败摘要并保留上次成功数据。

费率尤其不能从管理费、托管费或基金名称推算。申购费还会受到金额档位、销售平台折扣和活动影响；没有可追溯规则时，系统宁可等待确认，也不生成虚假份额。
