# ec-erp 后端镜像(#28):多阶段构建,运行物只有 erp-api jar(单进程含全部 Job)
# 构建参数:MAVEN_MIRROR_URL 可选,国内网络可设 https://maven.aliyun.com/repository/public
# 红线:镜像内不烘焙任何密钥(.dockerignore 已排除 local.properties/.env)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 可选国内镜像源:构建时 --build-arg MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG MAVEN_MIRROR_URL=""
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 && \
      printf '<settings><mirrors><mirror><id>mirror</id><url>%s</url><mirrorOf>*</mirrorOf></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi

# 先拷全部 pom 拉依赖,利用层缓存(源码改动不重拉依赖)
COPY pom.xml ./
COPY erp-common/pom.xml erp-common/
COPY erp-contract/pom.xml erp-contract/
COPY erp-system/pom.xml erp-system/
COPY erp-shop/pom.xml erp-shop/
COPY erp-goods/pom.xml erp-goods/
COPY erp-order/pom.xml erp-order/
COPY erp-inventory/pom.xml erp-inventory/
COPY erp-purchase/pom.xml erp-purchase/
COPY erp-warehouse/pom.xml erp-warehouse/
COPY erp-fulfill/pom.xml erp-fulfill/
COPY erp-aftersale/pom.xml erp-aftersale/
COPY erp-finance/pom.xml erp-finance/
COPY erp-ads/pom.xml erp-ads/
COPY erp-report/pom.xml erp-report/
COPY erp-platform-sdk/pom.xml erp-platform-sdk/
COPY erp-ai/pom.xml erp-ai/
COPY erp-api/pom.xml erp-api/
COPY erp-worker/pom.xml erp-worker/
COPY erp-codegen/pom.xml erp-codegen/
RUN mvn -B -ntp -DskipTests dependency:go-offline || true

COPY . .
RUN mvn -B -ntp -DskipTests package

# ---------- 运行阶段 ----------
# TZ=Asia/Shanghai:全部 Job cron 按本地时区调度,错时区会导致 Job cron 与日期轴错位
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""
# healthcheck 探测用(基础镜像不带 wget)
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/erp-api/target/erp-api-0.1.0-SNAPSHOT.jar /app/erp-api.jar
# 知识库向量索引(data/ai/kb-index.json,相对工作目录)与运行期落盘数据;compose 挂卷持久化
RUN mkdir -p /app/data
EXPOSE 8088
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/erp-api.jar"]
