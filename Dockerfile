# Runtime-only образ.
#
# Hermetic-сборка внутри docker build НЕВОЗМОЖНА: jOOQ codegen на фазе generate-sources
# подключается к живой, уже мигрированной Postgres (env PIXPLAZE_DB_*), а Flyway-плагин
# применяет миграции. Поэтому jar собирается на хосте (см. README, раздел «Сборка»),
# а образ лишь упаковывает готовый артефакт. ext-api и прочие зависимости уже внутри
# fat-jar (spring-boot repackage), отдельно их класть не нужно.
#
# Артефакт по умолчанию — target/pixplaze-web-api-<version>.jar; версию можно переопределить:
#   docker build --build-arg JAR=target/pixplaze-web-api-1.2.3.jar .

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Непривилегированный пользователь для рантайма
RUN groupadd --system app && useradd --system --gid app --uid 1001 app

ARG JAR=target/pixplaze-web-api-1.0.0.jar
COPY --chown=app:app ${JAR} app.jar

USER app
EXPOSE 8080

# Доп. JVM-флаги пробрасываются через JAVA_TOOL_OPTIONS (JVM читает её сама).
ENTRYPOINT ["java", "-jar", "app.jar"]
