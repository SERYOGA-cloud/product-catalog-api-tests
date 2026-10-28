# Product Catalog API Tests

Автотесты для проверки REST API сервиса Product Catalog (`Kotlin`, `JUnit 5`, `REST Assured`, `Jackson`, `AssertJ`, `Allure 3`).

## Требования

- **JDK 21**
- `BASE_URL` — адрес тестируемого сервиса
- `SWAGGER_URL` — URL спецификации OpenAPI / Swagger UI (или путь `docs/openapi.json`)
- `API_TOKEN` — токен авторизации (если на стенде включена авторизация)

## Конфигурация

Создайте локальный `.env` из шаблона (или передавайте параметры через `-D`):

```bash
cp .env.example .env
```

Основные параметры:
- `BASE_URL` — базовый URL API (обязателен при запуске на стенде)
- `SWAGGER_URL` — URL OpenAPI/Swagger (обязателен при запуске на стенде)
- `AUTH_TYPE` — `NONE` (по умолчанию), `BEARER`, `API_KEY`, `BASIC`
- `API_TOKEN` — токен доступа (обязателен при `AUTH_TYPE=BEARER` или `API_KEY`)
- `ALLOW_DESTRUCTIVE_TESTS=true` — разрешает выполнение `DELETE`-запросов на реальном стенде
- `USE_LOCAL_MOCK=true` — запуск всех тестов на встроенном локальном стабе WireMock без внешнего стенда

## Запуск тестов и Allure-отчёт

### 1. Smoke-тесты + отчёт

```bash
./gradlew --continue clean smokeTest allureReport \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

### 2. Regression-тесты + отчёт

```bash
./gradlew --continue clean regressionTest allureReport \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

### 3. Все тесты (`smoke` + `regression`) + отчёт

```bash
./gradlew --continue clean smokeTest regressionTest allureReport \
  -DBASE_URL=http://<HOST>:<PORT> \
  -DSWAGGER_URL=http://<HOST>:<PORT>/v3/api-docs
```

### 4. Автономный запуск без стенда (на локальном стабе)

```bash
./gradlew --continue clean smokeTest regressionTest allureReport -DUSE_LOCAL_MOCK=true
```

### 5. Просмотр отчёта Allure

Открыть сгенерированный однофайловый HTML-отчёт:

```bash
open build/reports/allure-report/allureReport/index.html
```

Или поднять локальный веб-сервер Allure:

```bash
./gradlew allureServe
```

### 6. Статический анализ кода

```bash
./gradlew clean check
```

## Структура проекта

- `src/test/kotlin/com/example/catalog/config` — конфигурация (`TestConfig`), загрузка OpenAPI (`OpenApiSpecLoader`), настройка REST Assured и шаги Allure
- `src/test/kotlin/com/example/catalog/auth` — авторизация (`AuthProvider`)
- `src/test/kotlin/com/example/catalog/clients` — HTTP-клиент (`ProductsApiClient`)
- `src/test/kotlin/com/example/catalog/models` — DTO-модели (`Product`, `Measure`, `ProductStatus`)
- `src/test/kotlin/com/example/catalog/assertions` — контрактные и бизнес-проверки (`ProductAssertions`)
- `src/test/kotlin/com/example/catalog/tests/api` — тестовые классы по эндпоинтам (`product/getbycode`, `product/download/time`, `product/download/link`, `config`)
- `src/test/resources/schemas` — JSON Schema контрактов
- `docs` — спецификация `openapi.json` и тестовая документация

## Документация

- [`docs/test-cases.md`](docs/test-cases.md) — каталог тест-кейсов (`TC-01`..`TC-32`)
- [`docs/test-strategy.md`](docs/test-strategy.md) — тестовая стратегия и матрица покрытия
- [`docs/api-inventory.md`](docs/api-inventory.md) — описание эндпоинтов и моделей API
- [`docs/open-questions.md`](docs/open-questions.md) — открытые вопросы к контракту и найденные расхождения
- [`docs/test-execution.md`](docs/test-execution.md) — подробная инструкция по запуску
- [`docs/openapi.json`](docs/openapi.json) — спецификация OpenAPI 3.0.1
