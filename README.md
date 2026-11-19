# roam-bupt

校园地点社区：**内容挂在地点上，地点能变成路线**。

帖子可以挂在多个地点/分类主题上；地点页聚合内容、评分与入口；按帖子里的地点顺序可以直接算出路线（Dijkstra + 多方案 + 拥挤度）。

## 目录

```
roam-bupt/
├─ backend/      Spring Boot 多模块（Maven）
├─ frontend/     Vue 3 + Vite
├─ docs/         章程、设计基线、接口契约、域设计
└─ AGENTS.md     给 agent 的工作规则
```

## 后端模块（`backend/`）

| 模块 | 职责 | 依赖 |
|---|---|---|
| `roam-api` | VO / DTO / 枚举 / 异常 / 上下文（零外部依赖） | — |
| `roam-core` | 会话、计数、缓存、工具、JWT、敏感词接口 | api |
| `roam-service` | 领域包、DAO、Mapper | core |
| `roam-web` | 入口、Controller、拦截器、Liquibase | service |

依赖方向单向：`web → service → core → api`。

## 环境

- JDK **17**（不要用 21，Lombok 注解处理器会崩）
- Maven 3.9.x（本机 `E:\develop\apache-maven-3.9.11`，本地仓库 `E:\develop\maven-rep`）
- MySQL 8（库名 `roam_bupt`）
- Redis（`192.168.153.129:7001`，密码 `123456`）
- Node 22 / npm 10（前端）
- Elasticsearch 7.17（P1 起需要）

## 构建与启动

```bash
# 后端
cd backend
mvn -pl roam-web -am -DskipTests compile
mvn -pl roam-web -am -DskipTests install
mvn -pl roam-web spring-boot:run

# 前端
cd frontend
npm install
npm run dev                        # 默认走 mock
VITE_USE_MOCK=false npm run dev    # 连真实后端
```

数据库与 Redis 地址走环境变量，默认值见 `backend/roam-web/src/main/resources/application-dev.yml`：

`ROAM_DB_HOST` / `ROAM_DB_PORT` / `ROAM_DB_NAME` / `ROAM_DB_USER` / `ROAM_DB_PASSWORD` / `ROAM_REDIS_HOST` / `ROAM_REDIS_PORT` / `ROAM_REDIS_PASSWORD`

## 文档

| 文件 | 内容 |
|---|---|
| `docs/项目章程.md` | 定位、范围、数据模型、分阶段验收 |
| `docs/骨架规划.md` | 模块划分、包结构、依赖、提交顺序 |
| `docs/代码级设计基线.md` | 分层、错误码、事务、幂等、失败语义、扩展点 |
| `docs/API-契约.md` | 全部接口（认证/用户/主题/帖子/评论/通知/搜索/导航/后台） |
| `docs/前端约定.md` | 前端技术栈、请求层、mock 策略、视觉基线 |
| `docs/域设计-*.md` | 每个域的调用关系、接口、失败语义 |
| `docs/数据导出说明.md` | 地点/路网数据来源与导入 |
| `docs/复用清单.md` | 从 taco 搬运的文件级清单 |
| `docs/mockup.html` | 六屏界面示意 |

## 进度

- [x] P0-1 多模块骨架
- [x] P0-2 Liquibase 基线表与地点 seed（152 个主题，74 个可见）
- [x] P0-3 注册登录与 JWT 会话（10 条测试）
- [x] P0-4 发帖与地点页最小链路（8 条测试）
- [x] P0-5 前端骨架与三个页面（登录对接真实接口）
- [x] P0-6 前后端联调（关 mock）
- [x] P1-1 评论：两级结构、列表、删除、帖子评论数（8 条测试）
- [x] P1-2 互动：点赞/收藏幂等、计数、myLiked（7 条测试）
- [x] P1-3 通知：评论/回复/点赞/收藏通知、未读数、已读（5 条测试）
- [x] P1-4 搜索：ES 召回 + MySQL 兜底、索引同步与启动重建（4 条兜底测试）
