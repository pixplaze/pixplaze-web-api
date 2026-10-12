# Pixplaze Web API

Бэкенд авторизации и интеграции с Minecraft-серверами Pixplaze: выдаёт и проверяет
ES256-JWT (логин/пароль, OAuth 2.0 Device Flow по RFC 8628, refresh), управляет связями
профиль <-> игрок <-> сервер.

## Стек

- **Java 17**, **Spring Boot 4**, Spring Security, Spring WebMVC
- **PostgreSQL 16**, **Flyway** (миграции), **jOOQ** (codegen из схемы БД)
- ES256-JWT (`jjwt`), MapStruct, Lombok, springdoc-openapi (Swagger UI)

## Модель развёртывания и требования

Образ собирается на машине разработки, переносится на удалённую машину (`scp`) и запускается там
готовым: удалённая машина ничего не собирает.

- **Машина сборки:** JDK 17, Docker + Docker Compose, openssl. Maven ставить не нужно: `./mvnw` скачает
  закреплённую версию сам.
- **Удалённая машина:** Docker + Docker Compose.

## Конфигурация

Секреты и параметры — в `.env` рядом с `docker-compose.yml` (в git не коммитится):

```bash
cp .env.example .env
```

| Переменная                                      | Назначение                                                                                 |
|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| `PIXPLAZE_DB`                                   | имя БД                                                                                     |
| `PIXPLAZE_DB_DRIVER`                            | не используется                                                                            |
| `PIXPLAZE_DB_URL`                               | JDBC-URL для `./mvnw flyway:*` с хоста (`localhost:5432`); в контейнере compose подставит `db` |
| `PIXPLAZE_DB_USERNAME` / `PIXPLAZE_DB_PASSWORD` | креды БД                                                                                   |
| `PIXPLAZE_WEB_API_URL`                          | гетевей API (этот BE)                                                                      |
| `PIXPLAZE_WEB_APP_URL`                          | гетевей веб-приложения                                                                     |
| `EC_PRIVATE_KEY` / `EC_PUBLIC_KEY`              | ES256-ключи подписи токенов, **Base64 DER одной строкой** (без них приложение не стартует) |
| `PIXPLAZE_TAG`                                  | тег образа для compose; не в `.env`, а в `version.env` рядом с архивом (см. этапы 2–3)     |

Ключи подписи генерирует `bash scripts/generate-keypair.sh`: создаёт `priv.pem`/`pub.pem` и печатает
готовые строки `EC_PRIVATE_KEY=…` / `EC_PUBLIC_KEY=…` для `.env`. Приложение принимает и PEM, и Base64 DER.

> macOS: если системный `openssl` — LibreSSL, раскомментируйте в скрипте строку с `brew --prefix openssl@3`.

Версия задаётся только в `<version>` в `pom.xml`: jar собирается без версии в имени
(`target/pixplaze-web-api.jar`), в `application.yml` она подставляется при сборке, тег образа берётся из pom.

## 1. Подготовка машины сборки

### 1.1 Подготовить Docker

Сборке нужен Docker: классы jOOQ генерируются по миграциям во временном `postgres:16` (Testcontainers).
Docker Desktop (macOS, Windows) и Docker Engine (Linux) работают без настройки. Для colima, OrbStack,
Rancher Desktop и Docker Desktop на Linux — один раз (после перезагрузки повторить):

```shell
scripts/link-docker-socket.sh    # симлинк живого сокета в /var/run/docker.sock
```

> Если скрипт не отработал — вручную (пример для colima, macOS):
> ```shell
> sudo ln -sf "$HOME/.colima/default/docker.sock" /var/run/docker.sock
> ```

### 1.2 Установить pixplaze-ext-api

Зависимость берётся из локального `~/.m2`:

```shell
cd ../pixplaze-ext-api && ./mvnw -DskipTests install && cd -
```

## 2. Сборка скриптом

```shell
scripts/build-image.sh           # PLATFORM=linux/arm64 — для другой архитектуры
```

Собирает jar и образ `pixplaze-web-api:<версия>` и кладёт в `target/dist` всё для переноса:
архив образа, `version.env` (`PIXPLAZE_TAG=<версия>`) и копию `docker-compose.yml`.

> Если сборка прошла успешно, этап 3 пропустите и переходите к этапу 4.

## 3. Ручная сборка

### 3.1 Узнать версию

```shell
VERSION=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
```

### 3.2 Собрать jar

```shell
./mvnw -DskipTests package          # → target/pixplaze-web-api.jar
```

Без Docker, по уже сгенерированным классам: `./mvnw -Djooq.codegen.skip=true -DskipTests package`.

### 3.3 Собрать образ

```shell
docker build --platform linux/amd64 -t "pixplaze-web-api:$VERSION" --load .
```

Если у машины сборки та же архитектура, что у удалённой, `--platform` можно не указывать.

### 3.4 Выгрузить образ и тег в `target/dist`

```shell
mkdir -p target/dist
docker save "pixplaze-web-api:$VERSION" | gzip > "target/dist/pixplaze-web-api-$VERSION.tar.gz"
echo "PIXPLAZE_TAG=$VERSION" > target/dist/version.env
cp docker-compose.yml target/dist/
```

## 4. Перенос на удалённую машину

```shell
scp target/dist/* .env user@remote:/opt/pixplaze/
```

`.env` — с боевыми кредами БД и ключами `EC_*`.

## 5. Запуск на удалённой машине

### 5.1 Загрузить образ

```shell
cd /opt/pixplaze
gunzip -c pixplaze-web-api-<версия>.tar.gz | docker load
```

### 5.2 Поднять контейнеры

```shell
docker compose --env-file .env --env-file version.env up -d
```

Поднимутся `db` (Postgres, том `pixplaze_db_data`) и `api` (образ `pixplaze-web-api:${PIXPLAZE_TAG}`).
`api` стартует после healthcheck БД и сам применяет Flyway-миграции. Без `version.env` compose возьмёт
тег `latest`.

Проверка и управление:

```bash
curl -s http://localhost:8080/auth/oauth/keys     # JWKS (публичные ключи) → 200
# Swagger UI:  http://<remote>:8080/swagger-ui.html

docker compose logs -f api     # логи
docker compose down            # остановить
docker compose down -v         # + удалить том БД (данные!)
```

## Локальный запуск

```shell
docker compose up -d db          # Postgres на localhost:5432
set -a; . ./.env; set +a         # креды БД и ключи в окружение
./mvnw -Pdev spring-boot:run        # миграции применятся при старте, с тестовыми seed'ами из db/dev
```

Ручные `./mvnw flyway:migrate` / `clean` / `info` используют `PIXPLAZE_DB_*` из окружения; `-Pdev` добавляет `db/dev`.

## Миграции и сид-данные

- **Версионные** миграции — `src/main/resources/db/migration/**`, Flyway применяет их при старте приложения.
  Новый файл миграции: `scripts/new-migration.sh`.
- **Справочные** repeatable-сиды (`db/migration/repeatable`, например роли) коммитятся и применяются всегда.
- **Тестовые** сиды (серверы, заявки, ваучеры) — `db/dev`, применяются только с профилем `dev` или
  `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/dev`. Часть из них приватна
  и не коммитится (`.gitignore`): в jar они попадают только при сборке из рабочей копии, где лежат.
- Изменили схему → пересоберите jar: jOOQ перегенерирует классы по миграциям.

## Эксплуатация

- **Порты:** API `8080`. Postgres в compose проброшен на `5432` всех интерфейсов — на удалённой машине
  строку `ports` у `db` стоит убрать.
- **Логи:** log4j2 (`src/main/resources/log4j2.yml`), пишутся в `./logs`.
- **JVM-флаги:** через `JAVA_TOOL_OPTIONS` в `environment` сервиса `api`.
- **Healthcheck API:** не настроен (actuator не подключён); при необходимости добавьте
  `spring-boot-starter-actuator` и проверку `/actuator/health`.
- **Прод-замечания:**
  - секреты — через секрет-менеджер, а не `.env`;
  - `app.url.app.gateway` должен быть **HTTPS** (иначе `user_code` ходит по незащищённому каналу);
  - ключи подписи (`EC_*`) — вне репозитория, ротация через `kid`;
  - тег образа фиксировать (`PIXPLAZE_TAG=<версия>`), не полагаться на `latest`.
