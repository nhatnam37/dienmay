# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
# pom thay đổi mới tải lại dependency; cache Maven còn được dùng khi source thay đổi.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp dependency:resolve
COPY src/main ./src/main
# Test được chạy riêng bằng verify với Docker; không cần Docker socket trong builder.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/dienmay-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Duser.timezone=Asia/Ho_Chi_Minh"
COPY --chown=app:app --chmod=0555 docker/app-entrypoint.sh /app/entrypoint.sh
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=300s --retries=6 \
    CMD curl --fail --silent --max-time 3 http://127.0.0.1:8081/actuator/health > /dev/null || exit 1
ENTRYPOINT ["/app/entrypoint.sh"]
