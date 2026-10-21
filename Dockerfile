# 백엔드 이미지. 팀원은 Java 없이 docker compose up으로 띄울 수 있습니다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
# compose 헬스체크용
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 app
COPY --from=build /app/build/libs/*.jar app.jar
COPY seed seed
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
