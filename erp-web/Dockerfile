# ec-erp 前端镜像(#28):node 构建 dist → nginx:alpine 托管 + /api 反代
# packageManager 钉 pnpm@11.8.0(带 sha512),corepack 按字段取版
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine
ENV TZ=Asia/Shanghai
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
