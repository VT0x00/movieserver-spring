## Задание: Movie Server

REST-сервис для хранения и каталогизации фильмов: загружаешь видеофайл, описываешь его, ищишь и скачиваешь обратно.

### Сущность
`Movie`: `id` (UUID), `title`, `year`, `genre`, `durationSeconds`, `fileName`, `filePath`, `fileSize`, `createdAt`.

### Эндпоинты
| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/movies` | Загрузка: `MultipartFile` + метаданные (title, year, genre) в multipart-запросе |
| `GET` | `/api/movies` | Список с пагинацией (`page`, `size`) и фильтрами: `title` (частичное), `genre`, `year` |
| `GET` | `/api/movies/{id}` | Карточка фильма |
| `GET` | `/api/movies/{id}/download` | Скачивание файла (`Content-Disposition: attachment`) |
| `PUT` | `/api/movies/{id}` | Обновление метаданных |
| `DELETE` | `/api/movies/{id}` | Удаление записи + файла с диска |
| `GET` | `/api/status` | Health-check: версия, количество фильмов, размер хранилища |

### Требования
- Ошибки: 404 для несуществующего фильма (своё исключение + `@RestControllerAdvice`), 400 на валидацию (пустой title, year вне 1900–2030, файл не видео).
- Файлы хранятся в каталоге `movies/` (как `books/` в bookserver), путь — в `application.properties`.
- Схема БД — только через Flyway-миграции (`V1__create_movies_table.sql`).
- Swagger UI доступен на `/swagger-ui.html`.
- Тесты: unit-тесты сервиса (Mockito) + интеграционные тесты контроллера (MockMvc, H2 in-memory). Покрыть: успешную загрузку, 404, валидацию, пагинацию/фильтры.
- Структура пакетов как в bookserver: `domain` / `repository` / `service` / `web` (`dto`, `controller`, exception handler).

## Стек (зависимости)

**Ядро**
- Java 21, Maven
- `spring-boot-starter-parent` **4.1.x** (генерация через start.spring.io)

**Веб-слой**
- `spring-boot-starter-webmvc` — REST, `MultipartFile`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` — Swagger UI

**Слой данных**
- `spring-boot-starter-data-jpa` (+ Hibernate)
- `org.hibernate.orm:hibernate-community-dialects` — SQLite-диалект
- `org.xerial:sqlite-jdbc` (runtime) — основная БД
- `spring-boot-starter-flyway` — миграции
- `com.h2database:h2` (runtime/test) — для тестов

**Тесты**
- `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`
- JUnit 5 + Mockito (идут в составе starter'ов)
- Опционально: `spring-boot-testcontainers` + `testcontainers-junit-jupiter` (если захочешь Postgres)

**Инструменты**
- `org.projectlombok:lombok` (optional) + `annotationProcessorPaths` в `maven-compiler-plugin`
- Опционально: `jacoco-maven-plugin` (аналог `go test -cover`)
- Опционально: `spring-boot-starter-grpc-server` / `grpc-client` — если хочешь повторить gRPC-часть из bookserver (в Spring Boot 4 это уже встроенные starter'ы)
- Docker + docker-compose для упаковки

**Критерий готовности:** `mvn spring-boot:run` поднимает сервис, `mvn test` зелёный, Swagger показывает все эндпоинты, curl-ом можно загрузить/найти/скачать фильм.
