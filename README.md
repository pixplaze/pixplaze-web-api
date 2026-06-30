# Pixplaze Web API

Бэкенд авторизации и интеграции с Minecraft-серверами Pixplaze: выдаёт и проверяет
ES256-JWT (логин/пароль, OAuth 2.0 Device Flow по RFC 8628, refresh), управляет связями
профиль <-> игрок <-> сервер.

## Стек

- **Java 17**, **Spring Boot 4**, Spring Security, Spring WebMVC
- **PostgreSQL 16**, **Flyway** (миграции), **jOOQ** (codegen из схемы БД)
- ES256-JWT (`jjwt`), MapStruct, Lombok, springdoc-openapi (Swagger UI)

## Модель развёртывания и системные требования

Образ собирается **на локальной машине** затем переносится **на удалённую машину** (напр. с помощью `scp`)
где удалённая машина запускает **готовый скопированный образ** — только тянет образ и поднимает.

### Машина сборки (разработка)
- **JDK 17** и **Maven 3.9+**
- **Docker** + **Docker Compose**
- **openssl** (генерация ключей)
- Живая Postgres для jOOQ codegen
- Зависимость **`com.pixplaze.api:pixplaze-ext-api:1.0.0`** в локальном `~/.m2` (см. ниже)

### Удалённая машина (деплой)
- **Docker** + **Docker Compose**

## Конфигурация

Секреты и параметры — в `.env` (рядом с `docker-compose.yml`; в git не коммитится). Создайте из шаблона:

```bash
cp .env.example .env
```

| Переменная                                      | Назначение                                                                                  |
|-------------------------------------------------|---------------------------------------------------------------------------------------------|
| `PIXPLAZE_DB`                                   | имя БД                                                                                      |
| `PIXPLAZE_DB_DRIVER`                            | `org.postgresql.Driver` (нужно для jOOQ codegen)                                            |
| `PIXPLAZE_DB_URL`                               | JDBC-URL **для хост-сборки** (`localhost:5432`); в рантайме compose сам подставит host `db` |
| `PIXPLAZE_DB_USERNAME` / `PIXPLAZE_DB_PASSWORD` | креды БД                                                                                    |
| `PIXPLAZE_WEB_API_URL`                          | гетевей API (этот BE)                                                                       |
| `PIXPLAZE_WEB_APP_URL`                          | гетевей веб-приложения                                                                      |
| `EC_PRIVATE_KEY` / `EC_PUBLIC_KEY`              | ES256-ключи подписи токенов, **Base64 DER одной строкой** (без них приложение не стартует)  |

### Генерация ключей подписи

```bash
bash scripts/generate-keypair.sh
```

Скрипт создаёт `priv.pem`/`pub.pem` и печатает готовые строки `EC_PRIVATE_KEY=…` /
`EC_PUBLIC_KEY=…` (Base64 DER) — впишите их в `.env`. Приложение принимает и PEM, и
Base64 DER (P-256, PKCS#8/SEC1 для приватного, X.509 для публичного).

> macOS: если системный `openssl` — это LibreSSL, раскомментируйте в скрипте строку с
> `brew --prefix openssl@3` (см. комментарий в файле).

## Шаг 1. Сборка образа (на машине разработки)

Нужна живая Postgres для jOOQ codegen — поднимем её через тот же compose.

### 1.0 Установить локальную зависимость pixplaze-ext-api
```shell
mvn install:install-file \
  -Dfile=.m2/repository/com/pixplaze/api/pixplaze-ext-api/1.0.0/pixplaze-ext-api-1.0.0.jar \
  -DgroupId=com.pixplaze.api -DartifactId=pixplaze-ext-api -Dversion=1.0.0 -Dpackaging=jar
```

#### 1.1 Поднять Postgres (для codegen; слушает 127.0.0.1:5432)
```shell
docker compose up -d db
```
> Шаг можно пропустить, если есть удалённая БД для разработки

#### 1.2 Загрузить переменные сборки (креды БД + ключи) в окружение
```shell
set -a; . ./.env; set +a
```

#### 1.3 Применить миграции, затем собрать jar (codegen читает мигрированную схему)
```shell
mvn flyway:migrate
mvn -DskipTests package          # → target/pixplaze-web-api-1.0.0.jar
```

#### 1.4 Собрать runtime-образ из jar
```shell
docker build --platform linux/arm64 -t pixplaze-web-api:latest -- load .
```

> Если целевая машина той же ОС, что и локальная, можно:

```shell
docker build -t pixplaze-web-api:1.0.0 .
```

#### 1.5 Выгрузить образ в архив
```shell
docker save pixplaze-web-api:latest | gzip > pixplaze-web-api-1.0.0.tar.gz
```

## Шаг 2. Перенос образа на удалённую машину

На удалённую машину переносятся три файла:
- **архив образа** (напр. `pixplaze-web-api-1.0.0.tar.gz`)
- **`docker-compose.yml`**
- **`.env`** (с боевыми кредами БД и ключами `EC_*`)

#### 2.1 Перенести архив + файлы запуска на удалённую машину
```shell
scp pixplaze-web-api-1.0.0.tar.gz docker-compose.yml .env user@remote:/opt/pixplaze/
```

## Шаг 3. Запуск на удалённой машине
### 3.1 Перейти в директорию образа (куда скопировали)
```shell
cd /opt/pixplaze
```

### 3.2 Загрузить архив образа в репозиторий удалённой машины
```shell
docker load < pixplaze-web-api-1.0.0.tar.gz   # или: gunzip -c ...tar.gz | docker load
```

### 3.3 Поднять контейнер
```shell
docker compose up -d
```

Поднимутся `db` (Postgres, том `pixplaze_db_data`) и `api` (загруженный образ). `api` стартует
после healthcheck БД и сам прогоняет Flyway-миграции. Compose ничего не собирает — берёт
готовый образ `pixplaze-web-api:1.0.0` (тег можно переопределить переменной `PIXPLAZE_TAG`).

Проверка и управление:

```bash
curl -s http://localhost:8080/auth/oauth/keys     # JWKS (публичные ключи) → 200
# Swagger UI:  http://<remote>:8080/swagger-ui.html

docker compose logs -f api     # логи
docker compose down            # остановить
docker compose down -v         # + удалить том БД (данные!)
```

## Миграции и сид-данные

- **Версионные** миграции — `src/main/resources/db/migration/**`, применяются Flyway
  автоматически при старте приложения (и вручную `mvn flyway:migrate` при сборке).
- **Repeatable** сиды (`db/migration/repeatable/R__insert_values_*.sql` — роли, ваучеры)
  помечены приватными и **не коммитятся** (`.gitignore`). Для рабочего окружения положите их
  в эту папку до миграции, иначе справочные данные не зальются.
- Изменили схему → пересоберите jar (jOOQ codegen перечитает схему). Хелпер для новой
  миграции: `scripts/new-migration.sh`.

## Эксплуатация

- **Порты:** API `8080` (наружу). Postgres проброшен только на `127.0.0.1:5432` (для локального
  codegen); наружу не публикуется. На удалённой машине эту строку `ports` у `db` можно убрать.
- **Логи:** log4j2, пишутся в `./logs` (см. `logback`/`log4j2`-конфиг проекта).
- **JVM-флаги:** через `JAVA_TOOL_OPTIONS` в `environment` сервиса `api`.
- **Healthcheck API:** не настроен (actuator не подключён); при необходимости добавьте
  `spring-boot-starter-actuator` и проверку `/actuator/health`.
- **Прод-замечания:**
  - на удалённой машине убрать `ports` у `db`; секреты — через секрет-менеджер, а не `.env` в образе;
  - `app.url.app.gateway` должен быть **HTTPS** (иначе `user_code` ходит по незащищённому каналу);
  - ключи подписи (`EC_*`) — хранить вне репозитория, ротация через `kid`;
  - тег образа фиксировать (`docker save pixplaze-web-api:<версия>`), не полагаться на `latest`.

> **На будущее (CI без живой БД).** Чтобы `mvn package` собирался без ручной Postgres, codegen
> можно отвязать от живой БД: jOOQ `DDLDatabase` (генерация из SQL-миграций, in-memory) или
> эфемерная Postgres через Testcontainers (нужен Docker на машине сборки). Сейчас выбран простой
> путь: локальная сборка с живой БД + перенос готового образа.
