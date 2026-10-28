# Тестовая стратегия (`test-strategy.md`)

## 1. Цели и область тестирования

Настоящая тестовая стратегия определяет подход к автоматизированному тестированию REST API микросервиса **Product Catalog** (`catalog-controller`) на основе контракта OpenAPI `3.0.1` ([docs/openapi.json](openapi.json)).

### Реально поддерживаемые операции API:
1. `GET /api/product/{code}` (`operationId: getProduct`) — получение продукта по `gtin` или `ntinCode`.
2. `DELETE /api/product/download/time/` (`operationId: downloadTime`) — очистка временной метки загрузки продукта.
3. `DELETE /api/product/download/link/` (`operationId: downloadLink`) — очистка текущей ссылки на загрузку продукта.

### Неприменимые категории операций (отсутствуют в контракте API):
В соответствии с правилом *«Включай только те операции, которые реально поддерживает API. Не придумывай поля, методы, статусы и бизнес-правила»*:
- **Создание продукта (`POST /api/product`)** — не поддерживается API.
- **Обновление продукта (`PUT` / `PATCH /api/product/{id}`)** — не поддерживается API.
- **Удаление конкретного продукта (`DELETE /api/product/{code}`)** — не поддерживается API (кроме негативной проверки `405 Method Not Allowed`).
- **Получение списка продуктов с пагинацией, фильтрацией и сортировкой (`GET /api/product`)** — не поддерживается API.

---

## 2. Группы проверок и уровни тестирования

### 2.1. Smoke-тесты (`@Tag("smoke")`, Приоритет P0)
Минимальный, быстрый и полностью безопасный (read-only) набор тестов, проверяющий доступность API и работоспособность ключевого сценария:
- Успешное получение существующего активного продукта по коду `gtin` (`GET /api/product/{code}` -> `200 OK`, непустой `Product` с корректным `gtin` и `status = ACTIVE`).
- Успешное получение существующего продукта по национальному коду `ntin_code` (`GET /api/product/{code}` -> `200 OK`).
- Базовая проверка корректной обработки запроса несуществующего кода продукта (отсутствие `500 Internal Server Error`).

> [!IMPORTANT]
> Операции `DELETE /api/product/download/time/` и `DELETE /api/product/download/link/` **не включаются** в `smoke`, так как они изменяют глобальное состояние сервиса.

### 2.2. Позитивные тесты (`@Tag("positive")`, `@Tag("regression")`, Приоритет P0 / P1)
Проверка штатных сценариев работы всех существующих endpoints:
- Поиск продукта по стандартному `GTIN-13` и `GTIN-8`.
- Поиск продукта по `ntin_code`.
- Проверка чтения продукта со статусом `ACTIVE` и со статусом `INACTIVE`.
- Проверка чтения социально значимого товара (`is_social = true`) и обычного товара (`is_social = false`), а также признака маркировки ЕАС (`is_markedeac`).
- Проверка корректного чтения связанной сущности `Measure` (`id`, `name`, `short_name`, `code`).
- Проверка чтения продукта с деактивированным NTIN (`ntin_isdeactivated = true`, `ntin_deactivationreason`, `ntin_duplicateofproduct`).
- Успешная очистка временной метки загрузки (`DELETE /api/product/download/time/` -> `200 OK`).
- Успешная очистка текущей ссылки на загрузку (`DELETE /api/product/download/link/` -> `200 OK`).

### 2.3. Негативные тесты (`@Tag("negative")`, `@Tag("regression")`, Приоритет P1)
Проверка устойчивости API к некорректным входным данным и неподдерживаемым операциям:
- Запрос несуществующего продукта по валидному формату GTIN/NTIN (`GET /api/product/{unknownCode}`).
- Запрос с пустым сегментом пути (`GET /api/product/`) или пробелами (`GET /api/product/%20%20`).
- Запрос с нечисловыми/невалидными символами в `code` (буквы, спецсимволы, кириллица, SQL-injection / XSS векторы, path traversal).
- Вызов неподдерживаемых HTTP-методов (`POST`, `PUT`, `PATCH`, `DELETE`) для `/api/product/{code}` -> ожидается `405 Method Not Allowed`.
- Вызов неподдерживаемых HTTP-методов (`GET`, `POST`, `PUT`, `PATCH`) для `/api/product/download/time/` и `/api/product/download/link/` -> ожидается `405 Method Not Allowed`.
- Повторный вызов (идемпотентность) `DELETE /api/product/download/time/` и `DELETE /api/product/download/link/`.
- Проверка поведения при передаче невалидных учётных данных / отсутствии токена (для окружений, где включена авторизация).

### 2.4. Граничные тесты (`@Tag("boundary")`, `@Tag("regression")`, Приоритет P1 / P2)
Проверка граничных значений параметра `code` и числовых полей схемы `Product`:
- Минимальная длина `code` (1 символ, например `"0"`).
- Границы стандартных длин штрихкодов: 7 символов (меньше GTIN-8), 8 символов (GTIN-8), 12 символов (GTIN-12/UPC), 13 символов (GTIN-13/NTIN), 14 символов (GTIN-14), 15 символов (превышение GTIN-14).
- Сверхдлинная строка `code` (255 и 1024 символа).
- Граничные значения `int64` (`Long`) для полей `id`, `modified` и `measure.id` (корректная десериализация 64-битных целых чисел свыше `Int.MAX_VALUE`).

### 2.5. Проверки бизнес-логики (`@Tag("business")`, `@Tag("regression")`, Приоритет P1)
- Соответствие возвращённого продукта запрошенному коду: если запрос выполнен по `code`, то в ответе `product.gtin == code` ИЛИ `product.ntinCode == code`.
- Эквивалентность карточки товара при поиске одного и того же продукта по его `gtin` и по его `ntin_code` (возвращается один и тот же `id`, `name_ru`, `name_kk`, `measure`).
- Консистентность признаков деактивации: для деактивированного кода (`ntin_isdeactivated == true`) проверяется наличие причины `ntin_deactivationreason` и/или кода дубликата `ntin_duplicateofproduct`.
- Идемпотентность чтения `GET /api/product/{code}` при последовательных запросах и после сброса кеша загрузки (`DELETE /api/product/download/*`).

### 2.6. Контрактные тесты (`@Tag("contract")`, `@Tag("regression")`, Приоритет P1)
- Строгая валидация JSON-ответа `GET /api/product/{code}` по JSON Schema, автоматически/напрямую соответствующей `#/components/schemas/Product` и `#/components/schemas/Measure` из `docs/openapi.json`.
- Проверка типов всех 13 полей `Product` (`int64`, `string`, `boolean`, `enum`, nested `object`) и 4 полей `Measure`.
- Проверка допустимых значений `status` строго из множества `["ACTIVE", "INACTIVE"]`.
- Проверка заголовка `Content-Type` (`application/json`).
- Обнаружение незадокументированных полей (strict deserialization check) для выявления несовместимых изменений контракта.

---

## 3. Матрица покрытия Endpoints

| Endpoint | HTTP-метод | Smoke (P0) | Positive (P0/P1) | Negative (P1) | Boundary (P1/P2) | Business Logic (P1) | Contract / Schema (P1) | Тестовые классы |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| `/api/product/{code}` | `GET` | ✅ TC-01, TC-02, TC-03 | ✅ TC-04..TC-08 | ✅ TC-09..TC-15 | ✅ TC-16..TC-20 | ✅ TC-21..TC-23 | ✅ TC-24..TC-27 | `ProductByCodeSmokeTest`, `ProductByCodeRegressionTest` |
| `/api/product/download/time/` | `DELETE` | ❌ (State-mutating) | ✅ TC-25 | ✅ TC-29 | — | ✅ TC-27 (Идемпотентность) | ✅ TC-29 | `DownloadTimeSmokeTest`, `DownloadTimeRegressionTest` |
| `/api/product/download/link/` | `DELETE` | ❌ (State-mutating) | ✅ TC-26 | ✅ TC-30 | — | ✅ TC-28 (Идемпотентность) | ✅ TC-30 | `DownloadLinkSmokeTest`, `DownloadLinkRegressionTest` |

---

## 4. Приоритизация тестов

- **P0 (Critical / Blocker)**: Базовая работоспособность `GET /api/product/{code}` по `gtin` и `ntin_code`, корректная десериализация `Product` и `Measure`, отсутствие `5xx` ошибок.
- **P1 (High / Major)**: Полные позитивные сценарии (`ACTIVE`/`INACTIVE`, `is_social`, `ntin_isdeactivated`), контрактная валидация JSON Schema, операции `DELETE /api/product/download/*`, негативные проверки несуществующих кодов и неподдерживаемых HTTP-методов.
- **P2 (Medium / Minor)**: Экстремальные граничные длины `code` (255–1024 символа), спецсимволы и редкие комбинации опциональных/nullable полей.

---

## 5. Управление тестовыми данными и изоляция

1. **Read-Only безопасность**: Основная часть тестов работает с `GET /api/product/{code}` и не изменяет состояние БД.
2. **Конфигурируемые фикстуры**: Поскольку API не имеет `POST /api/product`, эталонные коды товаров (`ACTIVE` GTIN, `ACTIVE` NTIN, `INACTIVE` GTIN, несуществующий код) вынесены в конфигурацию (`ProductTestData` + переменные окружения `TEST_ACTIVE_GTIN`, `TEST_ACTIVE_NTIN`, `TEST_INACTIVE_GTIN`).
3. **Изоляция изменяющих состояние операций (`DELETE`)**:
   - Тесты `DELETE /api/product/download/time/` и `DELETE /api/product/download/link/` помечены тегом `@Tag("destructive")`.
   - Выполнение разрушительных тестов на внешнем стенде контролируется флагом `ALLOW_DESTRUCTIVE_TESTS=true` (по умолчанию при запуске против внешнего сервера без этого флага тесты безопасно помечаются как skipped/blocked via JUnit `Assumptions`, чтобы не сбросить состояние на промышленном или разделяемом контуре).
4. **Двойной режим запуска (Live API vs Local Contract Stub)**:
   - При заданных обязательных параметрах `BASE_URL` и `SWAGGER_URL` тесты обращаются к целевому серверу.
   - Если `BASE_URL` или `SWAGGER_URL` не заданы ни в аргументах командной строки, ни в переменных окружения, ни в локальном файле `.env` — `TestConfig` немедленно выдаёт понятную ошибку конфигурации (`ConfigurationException`), указывающую, какую именно переменную необходимо задать. Тесты никогда не обращаются к случайному или захардкоженному стенду.
   - Дополнительно предусмотрен явно включаемый режим локального контрактного тестирования (`-DUSE_LOCAL_MOCK=true` / WireMock), позволяющий проверить все тесты, сериализацию, негативные сценарии, таймауты и генерацию Allure-отчётов в изолированном окружении.
