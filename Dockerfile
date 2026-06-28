# ---- Stage 1: build ----
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .

#RUN ./install-libs.sh
#
## Создаём директорию для Maven
#RUN mkdir -p /root/.m2/repository
#
## Копируем только нужные файлы из bind mount
#RUN cp -r /libs /root/.m2/repository/ || true
#
## Загружаем все зависимости Maven оффлайн
#RUN --mount=type=cache,target=/root/.m2 \
#    mvn -B -e -DskipTests dependency:go-offline \

COPY .m2/repository/com/pixplaze/api/pixplaze-ext-api/1.0.0/pixplaze-ext-api-1.0.0.jar /tmp/
RUN mvn install:install-file \
  -Dfile=/tmp/pixplaze-ext-api-1.0.0.jar \
  -DgroupId=com.pixplaze.api \
  -DartifactId=pixplaze-ext-api \
  -Dversion=1.0.0 \
  -Dpackaging=jar

#RUN --mount=type=cache,target=/root/.m2 \
#    mvn -X -B -e -DskipTests dependency:go-offline

COPY src ./src

RUN mvn -q -DskipTests package


# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY --from=build /app/target/pixplaze-web-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]