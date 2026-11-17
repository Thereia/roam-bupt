# roam-bupt

校园地点社区：**内容挂在地点上，地点能变成路线**。

帖子可以挂在多个地点/分类主题上；地点页聚合内容、评分与入口；按帖子里的地点顺序可以直接算出路线（Dijkstra + 多方案 + 拥挤度）。

## 模块

| 模块 | 职责 | 依赖 |
|---|---|---|
| `roam-api` | VO / DTO / 枚举 / 异常 / 上下文（零外部依赖） | — |
| `roam-core` | Redis、工具、权限、敏感词、JWT | api |
| `roam-service` | 领域包、DAO、Mapper XML | core |
| `roam-web` | 入口、Controller、全局配置、Liquibase | service |
| `roam-vue` | 前端（Vue 3 + Vite） | — |

依赖方向单向：`web → service → core → api`。

## 环境

- JDK **17**（不要用 21，Lombok 注解处理器会崩）
- Maven 3.9.x（本机 `E:\develop\apache-maven-3.9.11`，本地仓库 `E:\develop\maven-rep`）
- MySQL 8（库名 `roam_bupt`）
- Redis
- Elasticsearch 7.17（P1 起需要）

## 构建与启动

```bash
export JAVA_HOME=E:/develop/java
mvn -pl roam-web -am -DskipTests compile

# 起服务（需要先 install 各模块）
mvn -pl roam-web -am -DskipTests install
mvn -pl roam-web spring-boot:run
```

数据库与 Redis 地址走环境变量，默认值见 `application-dev.yml`：

`ROAM_DB_HOST` / `ROAM_DB_PORT` / `ROAM_DB_NAME` / `ROAM_DB_USER` / `ROAM_DB_PASSWORD` / `ROAM_REDIS_HOST` / `ROAM_REDIS_PORT` / `ROAM_REDIS_PASSWORD`

## 文档

| 文件 | 内容 |
|---|---|
| `docs/项目章程.md` | 定位、范围、数据模型、分阶段验收 |
| `docs/骨架规划.md` | 模块划分、包结构、依赖、提交顺序 |
| `docs/数据导出说明.md` | 地点/路网数据来源、质量与导入映射 |
| `docs/复用清单.md` | 从 taco 搬运的文件级清单与改动点 |
| `docs/mockup.html` | 六屏界面示意 |

## 进度

- [x] P0-1 多模块骨架
- [ ] P0-2 Liquibase 基线表与地点 seed
- [ ] P0-3 注册登录与 JWT 会话
- [ ] P0-4 发帖与地点页最小链路
- [ ] P0-5 前端骨架与联调
