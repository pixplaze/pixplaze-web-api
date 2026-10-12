# Runtime-only образ.
#
# Hermetic-сборка внутри docker build НЕВОЗМОЖНА: jOOQ codegen на фазе generate-sources
# подключается к живой, уже мигрированной Postgres (env PIXPLAZE_DB_*), а Flyway-плагин
# применяет миграции. Поэтому jar собирается на хосте (см. README, раздел «Сборка»),
# а образ лишь упаковывает готовый артефакт. ext-api и прочие зависимости уже внутри
# fat-jar (spring-boot repackage), отдельно их класть не нужно.
#
# Имя jar без версии (finalName в pom), поэтому Dockerfile от версии не зависит.
# Тег образа задаёт сборка: scripts/build-image.sh или docker build -t pixplaze-web-api:<версия>.

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Непривилегированный пользователь для рантайма
RUN groupadd --system app && useradd --system --gid app --uid 1001 app

COPY --chown=app:app target/pixplaze-web-api.jar app.jar

USER app
EXPOSE 8080

# Доп. JVM-флаги пробрасываются через JAVA_TOOL_OPTIONS (JVM читает её сама).
ENTRYPOINT ["java", "-jar", "app.jar"]
