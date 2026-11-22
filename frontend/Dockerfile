# 用 Debian 版而不是 Alpine：rollup/esbuild 的 glibc 原生包更稳，Alpine(musl) 上偶发缺原生绑定
FROM node:20-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
# npm ci 在跨平台构建时可能跳过 rollup/esbuild 的平台可选依赖，
# 缺了会退回 JS 解析器并误报 "Code is nested too deeply"，所以显式带上 optional
RUN npm install --include=optional --no-audit --no-fund
COPY . .
# 默认走真实接口；显式开关留着给本地调试
ARG VITE_USE_MOCK=false
ENV VITE_USE_MOCK=$VITE_USE_MOCK
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
