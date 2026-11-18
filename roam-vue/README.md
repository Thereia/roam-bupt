# roam-vue

roam.bupt 前端：Vue 3 + Vite 5 + vue-router 4 + axios，不引 UI 库和 Pinia。

## 跑起来

```bash
npm install
npm run dev      # http://localhost:5173，/api 代理到 http://localhost:8080
npm run build
npm run preview
```

## Mock 开关

- 帖子与地点接口默认走 `src/mock/` 的 fixture，带 200ms 延迟；后端接口就绪后：

```bash
VITE_USE_MOCK=false npm run dev
```

- 开关只出现在 `src/api/post.js`、`src/api/topic.js`，组件里没有任何 mock 判断。
- 登录、注册、登出始终请求真实接口 `/api/v1/auth/*`。

## 本期范围

- 已做：首页信息流、地点页、帖子详情（mock 数据）；登录 / 注册（真实接口）；请求层、登录态、路由占位。
- 占位：发布、搜索、路线、通知、个人主页、后台，页面显示「建设中」。
- 未做：发帖、评论提交、图片上传，以及任何后端改动。

## 约定

- 接口路径与字段只认 `docs/API-契约.md`。
- 颜色、字号只取 `docs/前端约定.md` 的 CSS 变量，字号四档 12 / 14 / 16 / 22。
- 页面截图在 `docs/shots/`，1280 与 375 各三张。
