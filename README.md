# 社区药箱 · 过期药回收称重兑换与环保交接服务

面向社区过期药品回收场景的全流程服务：居民预约 → 回收员现场扫码登记、分类称重封袋 →
高风险药品人工复核 → 暂存柜管理 → 环保公司交接 → 批次去向追溯。预约、称重、暂存、复核、
积分、交接与异常处理全部串联在同一**回收批次**上，保证环保交接前每袋药品都有来源与分类。

## 技术栈

- Java 17 · Spring Boot 3.3（Web / Data JPA / Validation）
- PostgreSQL 15（Flyway 管理 schema，测试用 H2 PostgreSQL 模式）
- 前端演示页：`src/main/resources/static/index.html`（原生 JS，零构建）

## 快速开始

```bash
# 1. 启动本地 PostgreSQL（在项目 .pgdata 目录初始化独立实例，端口 5433，不依赖系统服务）
scripts/dev-db.sh start        # 另有 stop / reset / status

# 2. 启动服务（默认连 jdbc:postgresql://localhost:5433/med_recycle，可用环境变量覆盖）
mvn spring-boot:run
# 或： mvn package -DskipTests && java -jar target/med-recycle-1.0.0.jar

# 3. 打开演示页面
#    http://localhost:8080/         （居民/回收员/管理员三种角色界面）
```

数据库连接可用环境变量覆盖：`DB_URL`、`DB_USER`、`DB_PASSWORD`、`SERVER_PORT`。

## 测试账号（首次启动自动初始化）

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 社区管理员（看板/复核/交接/异常处理） |
| `collector01` | `collector123` | 回收员（开批次/登记/称重/入柜/异常上报） |
| `resident01` | `resident123` | 居民（预约/积分/兑换/追溯） |
| `resident02` | `resident123` | 居民 |

认证方式：`POST /api/auth/login` 获取 token，之后请求头携带 `Authorization: Bearer <token>`。

## 验证方式（均已实际执行通过）

```bash
# 1. 单元/集成测试（H2，无需外部依赖）：5 个测试全部通过
mvn test
#    - 积分规则计算、兑换成功/余额不足、20 线程并发兑换不超扣
#    - 端到端主流程（预约→称重→复核→入柜→异常→双人交接→追溯）
#    - 活动日批量称重、暂存柜容量预警、超期预警

# 2. 端到端冒烟脚本（需服务已启动）：30 项断言全部通过
scripts/smoke.sh                 # 默认 http://localhost:8080
```

smoke 脚本覆盖：登录鉴权 → 预约（含处方药标志）→ 开批次 → 扫码登记 → 分类称重
（低风险即时积分 / 高风险进复核）→ 复核发放积分 → 入暂存柜 → 称重争议异常阻塞交接 →
复称调整积分 → 双人交接 → 交接报告（去向/重量/签收人/积分发放结果）→ 居民追溯 →
积分兑换（成功 + 余额不足兜底）→ 活动日大批量称重 → 管理看板。

## 核心业务规则

- **药品分类**：普通片剂 TABLET / 抗生素 ANTIBIOTIC / 精神类 PSYCHOTROPIC /
  注射器 SYRINGE / 外用药 EXTERNAL / 液体药 LIQUID，每类对应积分规则（分/克，管理员可维护）。
- **高风险人工复核**：抗生素、精神类、注射器，或预约标记"含处方药"的称重，
  封袋后进入 `REVIEW_HOLD`，复核通过才发放积分；复核拒绝则袋标记 REJECTED 并自动生成异常记录。
- **双人交接**：交接批次中含精神类/注射器袋时，必须填写第二签收人才能完成交接。
- **交接前置校验**：每袋必须有来源预约与分类；相关批次存在未解决异常时禁止交接。
- **异常类型**：未过期药混入 / 处方信息暴露 / 针剂未安全封装 / 液体泄漏 / 称重争议 /
  积分兑换失败 / 环保公司拒收 / 复核不通过，全部挂到批次上串联处理；
  称重争议解决时可复称定重并按差额自动调整积分；环保拒收的袋标记 REJECTED 退出交接。
- **看板预警**：暂存超期（默认入柜 14 天，`app.storage-limit-days` 可配）、
  暂存柜容量预警（默认 80%，`app.cabinet-warn-ratio`）、
  环保上门量（社区点 `pickup_threshold_grams`，默认 50kg）、待复核数、未解决异常数、双人交接提示。
- **峰值处理**：积分扣减使用原子 SQL（`balance >= ?` 条件更新），并发兑换不超扣；
  兑换失败生成失败订单并自动记录异常；活动日支持 `/weigh/batch` 批量称重（单条失败不影响整批）。

## API 一览

| 模块 | 接口 |
|---|---|
| 认证 | `POST /api/auth/register` · `POST /api/auth/login` |
| 公共 | `GET /api/public/community-points` · `GET /api/public/points-rules` · `GET /api/public/activity-days` |
| 居民 | `POST/GET /api/resident/appointments` · `POST /api/resident/appointments/{id}/cancel` · `GET /api/resident/points` · `GET /api/resident/points/transactions` · `POST /api/resident/points/redeem` · `GET /api/resident/trace/{batchNo}` |
| 回收员 | `POST /api/collector/batches` · `POST /api/collector/batches/{id}/checkin` · `POST .../weigh` · `POST .../weigh/batch` · `POST .../store` · `POST .../exceptions` · `POST .../close` · `GET .../bags` |
| 管理员 | `GET /api/admin/dashboard` · `GET/POST /api/admin/reviews[/{id}/decision]` · `POST/GET /api/admin/handovers` · `POST /api/admin/handovers/{id}/complete` · `GET /api/admin/handovers/{id}/report` · `GET/POST /api/admin/exceptions[/{id}/resolve]` · `PUT /api/admin/points-rules` · `POST/GET /api/admin/cabinets` · `POST /api/admin/activity-days` · `GET /api/admin/trace/{batchNo}` |

## 项目结构

```
src/main/java/com/community/medrecycle/
├── auth/        # token 认证、角色拦截器、当前用户注入
├── common/      # 统一响应、业务异常、全局异常处理、单号生成
├── config/      # DataInitializer（演示数据，幂等）
├── domain/      # 16 个 JPA 实体 + 药品分类枚举
├── dto/         # 请求/响应对象
├── repo/        # Spring Data JPA 仓库（含原子积分更新）
├── service/     # 预约/批次称重/复核/积分/交接/看板/异常/追溯
└── web/         # REST 控制器
src/main/resources/db/migration/V1__init.sql   # Flyway schema
scripts/dev-db.sh   # 本地 PostgreSQL 实例管理
scripts/smoke.sh    # 端到端冒烟验证（30 项断言）
```
