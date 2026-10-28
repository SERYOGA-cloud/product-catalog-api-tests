# Автотесты Product Catalog API

Проект автотестов для проверки REST API сервиса Product Catalog (Kotlin + JUnit 5 + REST Assured + Allure).

## Что нужно

- JDK 21
- URL стенда (`BASE_URL` / `-Dapi.base-url`)
- URL Swagger / OpenAPI спецификации (`SWAGGER_URL` / `-Dapi.swagger-url`)
- Токен авторизации при необходимости (`API_TOKEN` / `-Dapi.token`)

## Слои тестирования

- **API Contract**: REST API, HTTP-статусы, соответствие схеме OpenAPI 3.0.1 (`Product`, `Measure`), типы и обязательность полей, отсутствие незадокументированных полей в ответах.
- **Functional / Business**: бизнес-сценарии получения карточек товаров по `GTIN` и `NTIN`, статусы жизненного цикла (`ACTIVE`, `INACTIVE`, `DEACTIVATED_NTIN`), единицы измерения (`Measure`), идемпотентность повторных чтений.
- **Negative / Boundary / Security**: несуществующие и невалидные коды, граничные длины (`GTIN-8`, `GTIN-12`, `GTIN-13`, `GTIN-14`, длины `1..1024`), защита от `SQLi` / `XSS` / `JNDI`-инъекций, неподдерживаемые HTTP-методы (`405 Method Not Allowed`).
- **Maintenance Endpoints**: сервисные операции очистки времени и ссылок загрузки (`DELETE /api/product/download/time/`, `DELETE /api/product/download/link/`).

## Тест-сьюты и проверки качества

```bash
./gradlew clean check          # компиляция + статический анализ Detekt + stand-independent проверки
./gradlew detekt               # статический анализ Kotlin-кода (config/detekt/detekt.yml)
./gradlew smokeTest            # быстрые неразрушающие проверки (@ApiSmoke)
./gradlew regressionTest       # полный регрессионный набор (@ApiRegression)
./gradlew apiSmokeTest         # синоним smokeTest (api & smoke)
./gradlew apiRegressionTest    # синоним regressionTest (api & regression)
./gradlew test                 # запуск тестов (без стенда запускает только @Tag("offline") проверки)
```

Тесты используют составные аннотации `@ApiSmoke` и `@ApiRegression`.
Gradle-задачи используют пересечение тегов:
- `apiSmokeTest` / `smokeTest` — быстрые неразрушающие проверки (`api & smoke`, без `@Tag("destructive")`).
- `apiRegressionTest` / `regressionTest` — полный регрессионный набор (`api & regression`): позитивные, негативные, граничные, контрактные и сервисные проверки.
- `test` — все тесты при заданном `BASE_URL` / `USE_LOCAL_MOCK=true`, либо только автономные (`@Tag("offline")`) тесты при вызове `./gradlew clean test` без стенда.
- `detekt` — статический анализатор кода Kotlin (включён в `check`).

## Стек и зависимости

Все версии библиотек централизованы в Gradle Version Catalog (`gradle/libs.versions.toml`):
- **Язык и сборка**: Kotlin `2.1.10`, JDK `21`, Gradle `9.4.1`
- **Тестовый движок**: JUnit Jupiter `5.11.4`
- **HTTP-клиент**: REST Assured `5.5.1` + JSON Schema Validator (`json-schema-validator`)
- **Сериализация**: Jackson `2.18.2` (`jackson-module-kotlin`, `jackson-datatype-jsr310`, `jackson-dataformat-yaml`)
- **Проверки**: AssertJ `3.27.3` (`SoftAssertions`)
- **Отчётность**: Allure JUnit 5 `2.29.1` + Allure 3 Awesome Report (`3.14.3`, `singleFile: true`)
- **Контрактный стаб**: WireMock Standalone `3.10.0`
- **Статический анализ**: Detekt `1.23.8` (`config/detekt/detekt.yml`)

## Структура проекта

Проект автотестов написан на Kotlin и организован по слоям и эндпоинтам (по аналогии с `superkassa-tests`):

- `src/test/kotlin/com/example/catalog/config` — централизованная runtime-конфигурация (`TestConfig`), загрузчик OpenAPI-контракта (`OpenApiSpecLoader`), настройка REST Assured (`RestAssuredConfig`) и Allure-шаги (`AllureReportSteps`).
- `src/test/kotlin/com/example/catalog/auth` — провайдер авторизации (`NONE`, `BEARER`, `API_KEY`, `BASIC`).
- `src/test/kotlin/com/example/catalog/clients` — HTTP-клиент API (`ProductsApiClient`).
- `src/test/kotlin/com/example/catalog/models` — DTO-модели (`Product`, `Measure`, `ProductStatus`).
- `src/test/kotlin/com/example/catalog/assertions` — контрактные и бизнес-проверки (`ProductAssertions`).
- `src/test/kotlin/com/example/catalog/tags` — составные теги-аннотации (`@ApiSmoke`, `@ApiRegression`, `TestLayer`, `TestSuite`).
- `src/test/kotlin/com/example/catalog/tests/api` — тесты, сгруппированные по эндпоинтам (`*SmokeTest.kt` и `*RegressionTest.kt` с вложенной иерархией `@Nested`):
  - `product/getbycode` — `ProductByCodeSmokeTest`, `ProductByCodeRegressionTest` (`GET /api/product/{code}`)
  - `product/download/time` — `DownloadTimeSmokeTest`, `DownloadTimeRegressionTest` (`DELETE /api/product/download/time/`)
  - `product/download/link` — `DownloadLinkSmokeTest`, `DownloadLinkRegressionTest` (`DELETE /api/product/download/link/`)
  - `config` — `ConfigAndAuthSmokeTest`, `ConfigAndAuthRegressionTest`
- `config/allurerc.json` — конфигурация Allure 3 Awesome UI (`singleFile: true`).
- `docs` — спецификация `openapi.json`, инвентаризация API, тестовая стратегия и каталог тест-кейсов.

## Запуск тестов и Allure 3 отчет

Команды ниже повторяют привычный сценарий: сначала запускаем нужный сьют, затем строим современный **Allure 3 Awesome отчет** (`singleFile: true`) или поднимаем локальный web-сервер из полученных результатов.

### 1. API smoke + HTML-отчет

**macOS / Linux:**

```bash
./gradlew --continue clean smokeTest allureReport \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

Готовый однофайловый отчет Allure 3 будет в:

```text
build/reports/allure-report/allureReport/index.html
build/reports/allure-report/index.html
```

Открыть отчет на macOS (работает прямо из файла без сервера благодаря `singleFile: true`):

```bash
open build/reports/allure-report/allureReport/index.html
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat --continue clean smokeTest allureReport "-DBASE_URL=http://<HOST>:<PORT>" "-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs"
Start-Process ".\build\reports\allure-report\allureReport\index.html"
```

### 2. API smoke + web-сервер Allure

**macOS / Linux:**

```bash
./gradlew --continue clean smokeTest allureServe \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat --continue clean smokeTest allureServe "-DBASE_URL=http://<HOST>:<PORT>" "-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs"
```

`allureServe` поднимает локальный web-сервер и открывает отчет в браузере, удерживая процесс активным до остановки (`Ctrl+C`).

### 3. API regression + HTML-отчет

**macOS / Linux:**

```bash
./gradlew --continue clean regressionTest allureReport \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

Открыть отчет на macOS:

```bash
open build/reports/allure-report/index.html
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat --continue clean regressionTest allureReport "-DBASE_URL=http://<HOST>:<PORT>" "-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs"
Start-Process ".\build\reports\allure-report\index.html"
```

### 4. API regression + web-сервер Allure

**macOS / Linux:**

```bash
./gradlew --continue clean regressionTest allureServe \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat --continue clean regressionTest allureServe "-DBASE_URL=http://<HOST>:<PORT>" "-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs"
```

### 5. API smoke и regression (все тесты) + HTML-отчет

**macOS / Linux:**

```bash
./gradlew --continue clean smokeTest regressionTest allureReport \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat --continue clean smokeTest regressionTest allureReport "-DBASE_URL=http://<HOST>:<PORT>" "-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs"
Start-Process ".\build\reports\allure-report\index.html"
```

### 6. API smoke и regression (все тесты) + web-сервер Allure

**macOS / Linux:**

```bash
./gradlew --continue clean smokeTest regressionTest allureServe \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat --continue clean smokeTest regressionTest allureServe "-DBASE_URL=http://<HOST>:<PORT>" "-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs"
```

### 7. Автономный запуск на локальном контрактном стабе (без внешнего стенда)

```bash
./gradlew --continue clean smokeTest regressionTest allureReport -DUSE_LOCAL_MOCK=true
```

Для быстрого запуска без очистки результатов прошлого прогона достаточно убрать `clean`.

## Конфигурация

Runtime-конфигурация загружается с приоритетом:
1. Системные свойства JVM (`-D...`)
2. Переменные окружения (`ENV`)
3. Локальный файл `.env` (создается из шаблона `.env.example`, игнорируется Git)
4. Профиль `src/test/resources/config/local.properties` (или `ci.properties`)

Основные параметры:
- `-DBASE_URL=http://<HOST>:<PORT>` (или `-Dapi.base-url=...`, или переменная окружения `BASE_URL`) — **обязательный** базовый URL тестируемого API.
- `-DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs` (или `-Dapi.swagger-url=...`, или переменная окружения `SWAGGER_URL`, или локальный путь `docs/openapi.json`) — **обязательный** источник контракта OpenAPI/Swagger.
- `-DAUTH_TYPE=NONE` (`NONE` | `BEARER` | `API_KEY` | `BASIC`) — режим авторизации.
- `-DAPI_TOKEN=<TOKEN>` (или `-Dapi.token=...`, `-Dapi.x-api-key=...`, или переменная окружения `API_TOKEN`) — токен/ключ доступа (обязателен при `AUTH_TYPE != NONE`).
- `-DALLOW_DESTRUCTIVE_TESTS=true` — включает выполнение мутирующих `DELETE`-тестов на реальном стенде.

Секреты нельзя коммитить в репозиторий. Токены, пароли и доступы к стендам должны передаваться через переменные окружения локально или через secret-переменные CI/CD.

## Если тесты не запускаются

- Проверь, что переданы оба обязательных параметра `BASE_URL` и `SWAGGER_URL` (с `http://` или `https://`).
- Проверь, что тестовый стенд доступен с рабочей машины.
- Если на стенде включена авторизация, проверь `AUTH_TYPE` и `API_TOKEN`.

## Документация и тест-кейсы

Вся аналитика, тестовая документация и каталог тест-кейсов находятся в папке [`docs/`](docs/):

- [`docs/test-cases.md`](docs/test-cases.md) — **каталог всех 32 тест-кейсов (`TC-01`..`TC-32`)** с приоритетами, предусловиями, входными данными и ожидаемыми результатами.
- [`docs/test-strategy.md`](docs/test-strategy.md) — **тестовая стратегия и матрица покрытия** (позитивные, негативные, граничные, контрактные, сервисные и security-проверки).
- [`docs/api-inventory.md`](docs/api-inventory.md) — **инвентаризация API** (описание всех endpoints, параметров, кодов ответов и DTO-схем `Product` и `Measure`).
- [`docs/open-questions.md`](docs/open-questions.md) — **открытые вопросы к контракту (`OQ-01`..`OQ-09`) и найденные баги/расхождения (`BUG-01`, `BUG-02`, `GAP-01`, `GAP-02`)**.
- [`docs/test-execution.md`](docs/test-execution.md) — **подробное руководство по запуску тестов** и работе с отчетами.
- [`docs/openapi.json`](docs/openapi.json) — **спецификация OpenAPI 3.0.1** тестируемого сервиса.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — **правила разработки и добавления новых автотестов** (стандарты кода, слоёв, шагов Allure и тегирования).

