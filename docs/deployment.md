# 服务端部署选择

> 核验日期：2026-07-29

## 项目实际需要

服务端不是单个 Java 进程，而是四类能力：

| 能力 | 当前实现 | 是否必须持久化 |
|---|---|---|
| API 与定时任务 | Spring Boot 4 / Java 21 | 否 |
| 交易与持仓 | MySQL 8.4 | 是 |
| 登录状态与行情缓存 | Redis 7.4 | 登录状态需要 |
| 验证码与重置密码 | SMTP | 需要真实邮件服务 |

因此，只有“免费 Web Service”但没有长期 MySQL/Redis 的平台并不适合。

## 推荐顺序

### 1. 学习部署：Zeabur

适合第一次把完整后端放上公网。一个项目中可创建 Java、MySQL、Redis，
支持 Git 自动部署、HTTPS、环境变量和数据库模板。Dev 计划为
US$5/月并提供 14 天试用；资源消耗仍应在控制台查看。Dev 计划还提供邮件
服务额度，可以减少一个外部依赖。

### 2. 长期自用：腾讯云轻量服务器

推荐至少 2 核 2GB；当前中国内地入门规格为 45 元/月。使用
`compose.production.yaml` 可在一台服务器运行 API、MySQL 和 Redis，价格
固定，但需要自己负责系统更新、HTTPS、备份和告警。

中国内地节点对外提供网站或 App 服务需要完成 ICP 备案；香港或其他境外
节点不需要备案，但国内网络质量需要实际测试。

### 3. 真正零服务器费：Oracle Cloud Always Free

当前 Always Free ARM 总额度为 2 OCPU、12GB 内存，足够运行本项目的三个
容器。缺点是注册、实例配额、防火墙、备份和日常运维都更复杂，不建议作为
第一次部署的唯一路径。

### 4. 备选：Railway

Spring Boot、MySQL、Redis 都有官方部署文档或模板，开发体验很好。Hobby
计划 US$5/月且包含 US$5 用量，但内存、CPU和存储按量计费；本项目三个常驻
服务通常会超过最低费用。

Render 免费层不推荐：Web Service 空闲 15 分钟会休眠；免费 PostgreSQL
30 天到期且不是当前 MySQL；免费 Key Value 重启可能丢失数据。

## 通用部署包

后端镜像：

```bash
docker build -t fund-keeper-backend ./backend
```

VPS 部署：

```bash
cp .env.production.example .env.production
# 修改全部密码、JWT 密钥和 SMTP 配置
docker compose -f compose.production.yaml up -d --build
curl http://127.0.0.1:8080/actuator/health
```

PaaS 部署时将服务根目录设为 `backend`，平台会使用其中的 `Dockerfile`。
必须配置 `DB_URL`、数据库账号、Redis、SMTP 和两个认证密钥。平台注入的
`PORT` 会被 Spring Boot 自动读取。

## 上线前不可省略

- MySQL 和 Redis 都要使用持久化存储，并定期导出备份。
- 不公开 3306、6379，只让 API 通过内网访问。
- 公网只开放 HTTPS；VPS 可使用 Caddy 或 Nginx 终止 TLS。
- 不把 `.env.production`、SMTP 密码或 JWT 密钥提交到 Git。
- 配置账单上限、健康检查和磁盘空间告警。

## 核验来源

- [Zeabur Dev Plan](https://zeabur.com/docs/en-US/pricing/dev-plan)
- [Zeabur 数据库与部署最佳实践](https://zeabur.com/docs/en-US/get-started/best-practices)
- [Railway 计划与资源计费](https://docs.railway.com/pricing/plans)
- [Railway MySQL 模板](https://docs.railway.com/databases/mysql)
- [Oracle Cloud Free Tier](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm)
- [腾讯云轻量应用服务器](https://cloud.tencent.com/product/lighthouse)
- [腾讯云备案云资源要求](https://cloud.tencent.com/document/product/243/18908)
- [Render 免费层限制](https://render.com/docs/free)
- [Brevo 免费邮件额度](https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans)
