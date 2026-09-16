FROM node:22-alpine AS build
WORKDIR /build
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 \
  CMD wget -qO- http://127.0.0.1:80/ >/dev/null 2>&1 || exit 1
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
