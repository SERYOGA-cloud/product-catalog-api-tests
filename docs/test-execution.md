# Инструкция по запуску тестов и интерпретации результатов (`test-execution.md`)

Данный документ содержит проверенные команды для сборки проекта, запуска различных групп тестов (`smoke`, `regression`, отдельных классов), переключения между целевым окружением и локальным контрактным стабом, а также генерации отчётов JUnit и Allure.

---

## 1. Настройка JDK

Проект использует **JDK 21** (OpenJDK 21 / JetBrains Runtime 21 / Eclipse Temurin 21):

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
```

Проверка версии:
```bash
java -version
```

---

## 2. Параметры конфигурации (`TestConfig`)

Проект **не содержит захардкоженных адресов** серверов или Swagger/OpenAPI.

Приоритет чтения параметров:
1. Системные свойства JVM (`-DKEY=VALUE`).
2. Переменные окружения ОС (`export KEY=VALUE`).
3. Локальный файл `.env` в корне проекта (игнорируется Git; создаётся из шаблона `.env.example`).
4. Независимые от окружения настройки в `src/test/resources/config/local.properties` или `src/test/resources/config/ci.properties`.

| Переменная / Свойство | Обязательность | По умолчанию | Описание |
| :--- | :---: | :--- | :--- |
| `BASE_URL` | **Обязателен** (при `USE_LOCAL_MOCK=false`) | *(не задан)* | Базовый URL тестируемого сервиса Product Catalog. При отсутствии выбрасывается `ConfigurationException`. |
| `SWAGGER_URL` | **Обязателен** (при `USE_LOCAL_MOCK=false`) | *(не задан)* | URL Swagger UI, URL OpenAPI JSON/YAML спецификации или локальный путь к файлу (`docs/openapi.json`). |
| `AUTH_TYPE` | Опционально | `NONE` | Тип авторизации: `NONE`, `BEARER`, `API_KEY`, `BASIC`. |
| `API_TOKEN` | При `BEARER` / `API_KEY` | *(не задан)* | Токен авторизации. Передавать через переменные окружения, `.env` или CI secrets. |
| `API_HEADER_NAME` | Опционально | `X-API-Key` | Имя заголовка для `AUTH_TYPE=API_KEY`. |
| `API_USERNAME` / `API_PASSWORD` | При `BASIC` | *(не заданы)* | Учётные данные для `AUTH_TYPE=BASIC`. |
| `CONNECT_TIMEOUT_MS` | Опционально | `5000` | Таймаут установления TCP/HTTP-соединения в мс. |
| `READ_TIMEOUT_MS` | Опционально | `15000` | Таймаут ожидания чтения ответа сокета в мс. |
| `ALLOW_DESTRUCTIVE_TESTS` | Опционально | `false` | Разрешает выполнение изменяющих состояние операций `DELETE /api/product/download/time/` и `DELETE /api/product/download/link/` на целевом стенде. |
| `USE_LOCAL_MOCK` | Опционально | `false` | При `true` запускает встроенный локальный контрактный сервер WireMock по спецификации `docs/openapi.json`. |
| `TEST_ACTIVE_GTIN` | Опционально | `1234567890123` | Эталонный GTIN существующего активного продукта. |
| `TEST_ACTIVE_NTIN` | Опционально | `0200208079406` | Эталонный NTIN существующего продукта. |
| `TEST_INACTIVE_GTIN` | Опционально | `4870207999999` | Эталонный GTIN неактивного продукта (`status = INACTIVE`). |
| `TEST_DEACTIVATED_NTIN` | Опционально | `0290000099999` | Эталонный деактивированный NTIN (`ntin_isdeactivated = true`). |

---

## 3. Команды запуска тестов

### 3.1. Подготовка локального `.env` (рекомендуемый способ для локального запуска)
```bash
cp .env.example .env
# Заполните BASE_URL и SWAGGER_URL в файле .env (файл .env добавлен в .gitignore)
```

### 3.2. Проверка компиляции проекта
```bash
./gradlew testClasses
```

### 3.3. Запуск Smoke-тестов (P0, без изменяющих состояние операций)
Против целевого стенда (через переменные окружения или `-D`):
```bash
./gradlew smokeTest -DBASE_URL="$BASE_URL" -DSWAGGER_URL="$SWAGGER_URL"
```

Против локального контрактного стаба (без внешнего стенда):
```bash
./gradlew smokeTest -DUSE_LOCAL_MOCK=true
```

### 3.4. Запуск полного набора Regression-тестов
Против целевого стенда (только read-only и безопасные негативные проверки; `DELETE`-тесты будут помечены как `SKIPPED/BLOCKED`, пока не передан `ALLOW_DESTRUCTIVE_TESTS=true`):
```bash
./gradlew regressionTest -DBASE_URL="$BASE_URL" -DSWAGGER_URL="$SWAGGER_URL"
```

Против целевого стенда **включая** изменяющие состояние операции `DELETE /api/product/download/*`:
```bash
./gradlew regressionTest -DBASE_URL="$BASE_URL" -DSWAGGER_URL="$SWAGGER_URL" -DALLOW_DESTRUCTIVE_TESTS=true
```

Против локального контрактного стаба (все 32 тест-кейса / 54 тестовых запуска):
```bash
./gradlew regressionTest -DUSE_LOCAL_MOCK=true
```

### 3.5. Запуск всех тестов стандартной задачей `test`
```bash
./gradlew test -DBASE_URL="$BASE_URL" -DSWAGGER_URL="$SWAGGER_URL"
```

### 3.6. Запуск одного тестового класса или вложенной группы (`@Nested`)
```bash
# Только регрессионные проверки GET /api/product/{code}
./gradlew test --tests "com.example.catalog.tests.api.product.getbycode.ProductByCodeRegressionTest*" -DUSE_LOCAL_MOCK=true

# Только контрактные тесты OpenAPI / JSON Schema
./gradlew test --tests "com.example.catalog.tests.api.product.getbycode.ProductByCodeRegressionTest\$ContractRegressionTests" -DUSE_LOCAL_MOCK=true

# Один конкретный smoke-метод
./gradlew test --tests "com.example.catalog.tests.api.product.getbycode.ProductByCodeSmokeTest.getProductByValidActiveGtinReturnsOkAndActiveProduct" -DUSE_LOCAL_MOCK=true
```

### 3.7. Фильтрация по произвольным JUnit-тегам (`smoke`, `positive`, `negative`, `boundary`, `business`, `contract`, `destructive`, `auth`)
```bash
./gradlew test -DincludeTags=contract -DexcludeTags=destructive -DUSE_LOCAL_MOCK=true
```

---

## 4. Формирование и просмотр отчётов

### 4.1. JUnit XML и стандартный HTML-отчёт Gradle
После любого запуска автоматически создаются:
- **JUnit XML**: `build/test-results/test/*.xml` (или `build/test-results/smokeTest/*.xml`, `build/test-results/regressionTest/*.xml`)
- **HTML-отчёт Gradle**: `build/reports/tests/test/index.html`

Открыть HTML-отчёт на macOS:
```bash
open build/reports/tests/smokeTest/index.html
```

### 4.2. Отчёт Allure
Сырые результаты Allure (включая безопасно очищенные от секретов HTTP-запросы и ответы) сохраняются в:
- `build/allure-results/`

Сгенерировать статический HTML-отчёт Allure:
```bash
./gradlew allureReport
```
Готовый отчёт будет находиться в `build/reports/allure-report/index.html`.

---

## 5. Интерпретация результатов и статусов

| Статус теста | Значение и действия инженера |
| :--- | :--- |
| **PASSED** | HTTP-статус, заголовки, JSON Schema и бизнес-инварианты полностью соответствуют контракту OpenAPI и ожиданиям теста. |
| **FAILED (`ConfigurationException: Required parameter BASE_URL is not set` / `SWAGGER_URL is not set`)** | Обязательный входной параметр `BASE_URL` или `SWAGGER_URL` не был передан. Укажите их в `.env`, переменных окружения или через `-DBASE_URL=... -DSWAGGER_URL=...` (либо используйте `-DUSE_LOCAL_MOCK=true`). |
| **SKIPPED (Assumption failed: `BLOCKED: Target API server ... is unreachable`)** | Указанный `BASE_URL` недоступен с текущей машины. Проверьте сетевой доступ / VPN или запустите с `-DUSE_LOCAL_MOCK=true`. |
| **SKIPPED (`State-modifying DELETE operation requires ALLOW_DESTRUCTIVE_TESTS=true`)** | Защита от случайного сброса метки времени и ссылки загрузки на разделяемом стенде. Если вы уверены, что стенд тестовый, добавьте `-DALLOW_DESTRUCTIVE_TESTS=true`. |
| **FAILED (`JsonSchemaValidationException` / `UnrecognizedPropertyException`)** | Обнаружено несовместимое изменение контракта API (новое незадокументированное поле, изменившийся тип поля или невалидный `status`). Сравните фактический ответ во вложении Allure со спецификацией `SWAGGER_URL` / `docs/openapi.json`. |
