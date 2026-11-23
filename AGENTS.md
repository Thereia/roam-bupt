# AGENTS.md — roam-bupt

> 给在本仓库工作的 agent。规则只在这里写一份，别处的文档只做指针。

## 项目一句话

校园地点社区：帖子挂多个地点主题，地点页聚合内容与评分，按帖子里的地点顺序生成路线。
后端在 `backend/`（Spring Boot 3.5.16 / Java 17，四模块 `roam-api → roam-core → roam-service → roam-web`），前端在 `frontend/`（Vue 3 + Vite），两边平级、互不嵌套。

## 开工流程（按顺序，别跳）

0. **先查清单**。开工前打开 `docs/实现清单.md`，把对应行改成 🚧；表里没有的功能，先加行再动手。**新增行必须写批次（B0…）和依赖**——顺序按表头的五条规则定，脚本会检查依赖是否排在更早批次。
1. **域设计先行**。每个域动代码前，先写 `docs/域设计-<domain>.md`，写五样：
   - 调用关系：谁调谁，画成缩进树；
   - 接口：接口名 + 方法签名 + 入参/出参类型名；
   - 数据与字段：用到的表、关键字段、缓存 key；
   - **协调**：我从谁那儿拿数据、我给谁提供数据、时序与幂等怎么保证、跨域失败怎么处理；
   - 失败语义：每种失败返回什么码、为什么。

   **完成判据**：五节都有内容才写第一行业务代码；只有行为描述、没有签名和协调的，算没写。

2. **契约先行**。改接口先改 `docs/API-契约.md`；改分层/错误码/事务/失败语义先改 `docs/代码级设计基线.md`。

3. **TDD 切片**。一个切片 = 一条失败测试 + 最小实现 + 通过。接缝固定三个：HTTP（MockMvc）、service（真 DB + 假 SessionStore）、建表（Liquibase）。
   **完成判据**：新测试先红后绿，后端 `mvn -pl roam-web -am -DskipTests compile` 通过。

4. **一刀一提交**：`<type>(<scope>): <subject>`，scope 用域名（auth/post/topic/space）。

5. **出现第二个实现才抽接口**。只有一个实现的接口不算扩展点，算负担。

6. **收工前跑校验**：`node scripts/check-docs.mjs`。它会检查：✅ 行的验收是否具体、点名的测试类是否存在、每份域设计是否被清单引用、域设计里是否又写了状态。红了先修再提交。

## 写代码时的固定规则

- 方法签名、字段顺序在 TDD 切片里定；域设计文档不预先猜它们。
- 跨域调用只走对方的 `*QueryService` / `*CommandService`，不 import 对方的 `repository.*`。
- 事务方法里先写库；发事件、写 Redis、调 MQ 一律放到 `AFTER_COMMIT`。
- 所有 Redis key 带 TTL；TTL 写进接口签名（`SessionStore` / `CounterStore` / `TtlCache`）。
- 认证依赖不可用返回 **503**；token 本身无效才是 **401**。
- 日志只打 `uid`、业务 id、`jti` 前 8 位；密码与完整 token 不出现在任何日志里。
- **状态只有一处**：`docs/实现清单.md`。域设计顶部只有一行指回清单的指针，不写 ✅/🚧。
- 清单只留「一句话做法 + 验收」；展开写域设计，**不要把设计文档抄进表里**。

## 环境

- `JAVA_HOME=E:\develop\java`（17.0.12）；Maven `E:\develop\apache-maven-3.9.11`；本地仓库 `E:\develop\maven-rep`
- 后端命令都要先 `cd backend`：
  - 编译：`mvn -pl roam-web -am -DskipTests compile`
  - 起服务：`mvn -pl roam-web -am -DskipTests install` 然后 `mvn -pl roam-web spring-boot:run`
- 前端在 `frontend/`：`npm install`、`npm run dev`、`npm run build`；连真实后端用 `VITE_USE_MOCK=false`
- MySQL：`roam_bupt`（localhost:3306，root/123456）
- Redis：VM `192.168.153.129:7001`，密码 `123456`（已写进 `/usr/local/src/redis-7001.conf`，重启不会丢）

## 文档索引（按需读，不要全读）

| 要解决什么 | 读哪份 |
|---|---|
| **还剩什么、每项怎么做、做到什么算完** | `docs/实现清单.md` |
| 产品定位、范围、不做清单 | `docs/项目章程.md` |
| 分层、错误码、事务、失败语义、扩展点 | `docs/代码级设计基线.md` |
| 接口路径、入参出参、状态机 | `docs/API-契约.md` |
| 模块与包结构、提交顺序 | `docs/骨架规划.md` |
| 前端技术栈、目录、mock 策略 | `docs/前端约定.md` |
| 某个域的调用关系与失败语义 | `docs/域设计-<domain>.md` |
| 从 taco 搬哪些文件、怎么改 | `docs/复用清单.md` |
| 地点/路网数据从哪来 | `docs/数据导出说明.md` |

## 评审

跨域或结构性变更（新增域、改分层、改扩展点）开工前开一轮多 agent 评审；单域设计自己写，不必多 agent。

## 交差前检查（每次交付必跑）

1. 后端测试：`mvn -pl roam-web -am test`，必须全绿；
2. 用户看得见的改动：跑布局检查 `node frontend/e2e/check-layout.js <baseUrl>`（需要放在装了 playwright 的目录里跑），它会在首页塞一张 800×600 的图，检查「图片有没有撑破缩略图 / 卡片有没有互相压 / 1280 与 375 有没有横向溢出」；
3. 回复里附测试输出和截图路径。

| 规则 | 由什么拦住 |
|---|---|
| 图片必须被容器框住（曾溢出压住下一张卡片） | `check-layout.js`：删掉 `.thumb img` 规则会报 2 个问题 |
| 页面不能横向溢出 | 同上，1280/375 双宽度扫全页 |
| 交付必须有证据 | 回复模板：做了什么 / 测试 / 截图 / 没验证到的 |
