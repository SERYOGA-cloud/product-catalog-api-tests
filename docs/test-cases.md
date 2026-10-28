# Каталог тест-кейсов (`test-cases.md`)

Все тест-кейсы спроектированы строго по контракту `docs/openapi.json` (`OpenAPI 3.0.1`).

---

## Сводная таблица тест-кейсов

| ID | Название | Endpoint | Приоритет | Тип проверки | Теги JUnit | Статус реализации |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| **TC-01** | Получение существующего активного продукта по GTIN | `GET /api/product/{code}` | P0 | Smoke / Positive | `api`, `smoke` | Implemented (`ProductByCodeSmokeTest`) |
| **TC-02** | Получение существующего продукта по национальному коду NTIN | `GET /api/product/{code}` | P0 | Smoke / Positive | `api`, `smoke` | Implemented (`ProductByCodeSmokeTest`) |
| **TC-03** | Запрос несуществующего продукта не вызывает серверную ошибку 5xx | `GET /api/product/{code}` | P0 | Smoke / Negative | `api`, `smoke` | Implemented (`ProductByCodeSmokeTest`) |
| **TC-04** | Проверка всех полей активного продукта и вложенного объекта `Measure` | `GET /api/product/{code}` | P0 | Positive | `api`, `regression` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-05** | Получение продукта со статусом `INACTIVE` | `GET /api/product/{code}` | P1 | Positive | `api`, `regression` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-06** | Получение социально значимого продукта (`is_social`) с маркировкой ЕАС (`is_markedeac`) | `GET /api/product/{code}` | P1 | Positive | `api`, `regression` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-07** | Получение продукта с деактивированным NTIN и причиной деактивации | `GET /api/product/{code}` | P1 | Positive / Business | `api`, `regression` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-08** | Консистентность данных при поиске одного продукта по `gtin` и по `ntin_code` | `GET /api/product/{code}` | P1 | Business Logic | `api`, `regression` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-09** | Идемпотентность последовательных `GET /api/product/{code}` запросов | `GET /api/product/{code}` | P1 | Business Logic | `api`, `regression` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-10** | Запрос несуществующего кода продукта возвращает ожидаемый код отсутствия ресурса (`404 Not Found`) | `GET /api/product/{code}` | P1 | Negative | `api`, `regression`, `negative` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-11** | Запрос продукта с нечисловыми и специальными символами в `code` | `GET /api/product/{code}` | P1 | Negative | `api`, `regression`, `negative` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-12** | Запрос продукта с пробельным `code` (`"   "`) | `GET /api/product/{code}` | P1 | Negative | `api`, `regression`, `negative` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-13** | Защита от инъекций (SQLi / XSS / JNDI) в параметре `code` | `GET /api/product/{code}` | P1 | Negative / Security | `api`, `regression`, `negative` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-14** | Запрос продукта без path-параметра `code` (`/api/product/`) | `GET /api/product/` | P1 | Negative | `api`, `regression`, `negative` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-15** | Вызов неподдерживаемых HTTP-методов (`POST`, `PUT`, `PATCH`, `DELETE`) на `/api/product/{code}` | `*/api/product/{code}` | P1 | Negative / Contract | `api`, `regression`, `negative` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-16** | Граничная длина `code`: минимальная строка (1 символ) | `GET /api/product/{code}` | P1 | Boundary | `api`, `regression`, `boundary` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-17** | Граничные длины стандартных кодов: 8 (GTIN-8), 12 (UPC), 13 (GTIN-13), 14 (GTIN-14) символов | `GET /api/product/{code}` | P1 | Boundary | `api`, `regression`, `boundary` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-18** | Нестандартные и сверхдлинные `code` (7, 15, 255 и 1024 символа) | `GET /api/product/{code}` | P2 | Boundary | `api`, `regression`, `boundary` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-19** | Десериализация граничных 64-битных значений (`int64` > `Int.MAX_VALUE`) в полях `id`, `modified`, `measure.id` | `GET /api/product/{code}` | P1 | Boundary / Contract | `api`, `regression`, `boundary` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-20** | Обработка `Product` с `null` / отсутствующими необязательными полями | `GET /api/product/{code}` | P2 | Boundary / Contract | `api`, `regression`, `boundary` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-21** | Валидация ответа `GET /api/product/{code}` по JSON Schema (`product-schema.json`) | `GET /api/product/{code}` | P0 | Contract | `api`, `regression`, `contract` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-22** | Проверка заголовка `Content-Type: application/json` при получении продукта | `GET /api/product/{code}` | P1 | Contract | `api`, `regression`, `contract` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-23** | Проверка допустимых значений enum `status` (`ACTIVE`, `INACTIVE`) | `GET /api/product/{code}` | P1 | Contract | `api`, `regression`, `contract` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-24** | Строгая проверка отсутствия незадокументированных полей в JSON-ответе `Product` | `GET /api/product/{code}` | P1 | Contract | `api`, `regression`, `contract` | Implemented (`ProductByCodeRegressionTest`) |
| **TC-25** | Очистка временной метки загрузки продукта (`DELETE /api/product/download/time/`) возвращает `200 OK` | `DELETE /api/product/download/time/` | P1 | Positive / Destructive | `api`, `smoke`, `regression`, `destructive` | Implemented (`DownloadTimeSmokeTest`) |
| **TC-26** | Очистка текущей ссылки на загрузку продукта (`DELETE /api/product/download/link/`) возвращает `200 OK` | `DELETE /api/product/download/link/` | P1 | Positive / Destructive | `api`, `smoke`, `regression`, `destructive` | Implemented (`DownloadLinkSmokeTest`) |
| **TC-27** | Идемпотентность повторного вызова `DELETE /api/product/download/time/` | `DELETE /api/product/download/time/` | P1 | Business / Destructive | `api`, `regression`, `destructive` | Implemented (`DownloadTimeRegressionTest`) |
| **TC-28** | Идемпотентность повторного вызова `DELETE /api/product/download/link/` | `DELETE /api/product/download/link/` | P1 | Business / Destructive | `api`, `regression`, `destructive` | Implemented (`DownloadLinkRegressionTest`) |
| **TC-29** | Вызов неподдерживаемых HTTP-методов (`GET`, `POST`, `PUT`, `PATCH`) на `/api/product/download/time/` | `*/api/product/download/time/` | P2 | Negative / Contract | `api`, `regression` | Implemented (`DownloadTimeRegressionTest`) |
| **TC-30** | Вызов неподдерживаемых HTTP-методов (`GET`, `POST`, `PUT`, `PATCH`) на `/api/product/download/link/` | `*/api/product/download/link/` | P2 | Negative / Contract | `api`, `regression` | Implemented (`DownloadLinkRegressionTest`) |
| **TC-31** | Проверка AuthProvider и отказа в доступе (`401`/`403`) при передаче невалидного токена (если авторизация включена) | `GET /api/product/{code}` | P1 | Negative / Auth | `api`, `regression`, `auth`, `offline` | Implemented (`ConfigAndAuthRegressionTest`) |
| **TC-32** | Проверка явной ошибки конфигурации при отсутствии `BASE_URL` или `SWAGGER_URL` | Конфигурация `TestConfig` | P0 | Configuration | `api`, `smoke`, `regression`, `offline` | Implemented (`ConfigAndAuthSmokeTest`) |

---

## Детальное описание ключевых тест-кейсов

### TC-01. Получение существующего активного продукта по GTIN
- **Endpoint**: `GET /api/product/{code}`
- **Предусловия**: Сервис доступен; в каталоге существует активный продукт с известным `gtin` (по умолчанию `4870207314266` или значение из `TEST_ACTIVE_GTIN`).
- **Тестовые данные**: `code = <activeGtin>`
- **Шаги**:
  1. Отправить запрос `GET /api/product/{code}`.
  2. Проверить HTTP-статус ответа.
  3. Десериализовать тело ответа в модель `Product`.
  4. Проверить значения ключевых полей (`id`, `gtin`, `status`).
- **Ожидаемый результат**: HTTP `200 OK`; тело ответа содержит валидный JSON объекта `Product`; `product.gtin == code`; `product.status == "ACTIVE"`; `product.id != null`.
- **Приоритет**: P0
- **Тип проверки**: Smoke / Positive
- **Статус реализации**: Реализован

### TC-02. Получение существующего продукта по национальному коду NTIN
- **Endpoint**: `GET /api/product/{code}`
- **Предусловия**: В каталоге существует продукт с известным `ntin_code` (`TEST_ACTIVE_NTIN`).
- **Тестовые данные**: `code = <activeNtin>`
- **Шаги**:
  1. Отправить запрос `GET /api/product/{code}` с кодом `ntin_code`.
  2. Проверить HTTP-статус и тело ответа.
- **Ожидаемый результат**: HTTP `200 OK`; `product.ntinCode == code`; `product.id != null`.
- **Приоритет**: P0
- **Тип проверки**: Smoke / Positive
- **Статус реализации**: Реализован

### TC-04. Проверка всех полей активного продукта и вложенного объекта `Measure`
- **Endpoint**: `GET /api/product/{code}`
- **Предусловия**: В каталоге существует продукт с заполненными реквизитами и единицей измерения `Measure`.
- **Тестовые данные**: `code = <activeGtin>`
- **Шаги**:
  1. Выполнить `GET /api/product/{code}`.
  2. Проверить заполненность полей `id`, `name_ru`, `name_kk`, `gtin`, `ntin_code`, `is_social`, `modified`, `status`, `is_markedeac`, `measure`.
  3. Проверить поля вложенного объекта `measure` (`id`, `name`, `short_name`, `code`).
- **Ожидаемый результат**: HTTP `200 OK`; все поля десериализуются без ошибок; `measure` не равен `null` и содержит корректный код и наименование единицы измерения.
- **Приоритет**: P0
- **Тип проверки**: Positive
- **Статус реализации**: Реализован

### TC-08. Консистентность данных при поиске одного продукта по `gtin` и по `ntin_code`
- **Endpoint**: `GET /api/product/{code}`
- **Предусловия**: Продукт имеет одновременно `gtin` и `ntin_code`.
- **Тестовые данные**: `gtin` и `ntin_code` одного продукта.
- **Шаги**:
  1. Выполнить `GET /api/product/{gtin}` и сохранить ответ `productByGtin`.
  2. Извлечь `ntinCode = productByGtin.ntinCode`.
  3. Выполнить `GET /api/product/{ntinCode}` и сохранить ответ `productByNtin`.
  4. Сравнить оба объекта `Product`.
- **Ожидаемый результат**: Оба запроса возвращают `200 OK` и идентичный объект `Product` (`id`, `name_ru`, `name_kk`, `status`, `measure` полностью совпадают).
- **Приоритет**: P1
- **Тип проверки**: Business Logic
- **Статус реализации**: Реализован

### TC-10. Запрос несуществующего кода продукта возвращает ожидаемый код отсутствия ресурса
- **Endpoint**: `GET /api/product/{code}`
- **Предусловия**: Код `0000000000000` гарантированно отсутствует в каталоге.
- **Тестовые данные**: `code = "0000000000000"`
- **Шаги**:
  1. Выполнить `GET /api/product/0000000000000`.
  2. Проверить HTTP-статус ответа.
- **Ожидаемый результат**: Сервер возвращает `404 Not Found` (не возвращает `500 Internal Server Error` или валидный продукт с чужим `gtin`).
- **Приоритет**: P1
- **Тип проверки**: Negative
- **Статус реализации**: Реализован

### TC-21. Валидация ответа `GET /api/product/{code}` по JSON Schema
- **Endpoint**: `GET /api/product/{code}`
- **Предусловия**: Доступна схема `schemas/product-schema.json`, построенная строго по `#/components/schemas/Product` и `#/components/schemas/Measure`.
- **Тестовые данные**: `code = <activeGtin>`
- **Шаги**:
  1. Выполнить `GET /api/product/{code}`.
  2. Применить валидатор `JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/product-schema.json")`.
- **Ожидаемый результат**: Ответ имеет статус `200 OK` и полностью соответствует JSON-схеме контракта OpenAPI.
- **Приоритет**: P0
- **Тип проверки**: Contract
- **Статус реализации**: Реализован

### TC-25 / TC-26. Очистка временной метки и ссылки на загрузку продукта
- **Endpoint**: `DELETE /api/product/download/time/`, `DELETE /api/product/download/link/`
- **Предусловия**: Разрешён запуск изменяющих состояние тестов (`ALLOW_DESTRUCTIVE_TESTS=true`).
- **Тестовые данные**: Без параметров.
- **Шаги**:
  1. Выполнить `DELETE /api/product/download/time/` (для TC-25) или `DELETE /api/product/download/link/` (для TC-26).
  2. Проверить HTTP-статус ответа.
- **Ожидаемый результат**: HTTP `200 OK` в соответствии с контрактом OpenAPI.
- **Приоритет**: P1
- **Тип проверки**: Positive / Destructive
- **Статус реализации**: Реализован
