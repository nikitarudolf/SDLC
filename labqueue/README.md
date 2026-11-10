# LabQueue — приложение

Электронная очередь на защиту лабораторных работ. Требования — `srs/srs-labqueue.md`,
дизайн — `sdd/sdd-labqueue.md`, план итераций — `sdd/plan-labqueue.md`.

## Что уже сделано (итерация I1, 09.11–22.11.2026)

| Функция плана | Содержание | Требования |
|---------------|------------|------------|
| F01 Project Setup & CI | Spring Boot 3.5, Java 21, Flyway, Docker Compose, GitHub Actions | REQ-INST-001, REQ-MAINT-001 |
| F02 Accounts & Authentication | регистрация студента, подтверждение e-mail, вход, роли, блокировка после 5 попыток, приглашение преподавателя | REQ-FUNC-001, REQ-SEC-001…004 |
| F03 Disciplines & Groups | группы, импорт списка группы из .csv, дисциплины с числом ЛР и группами | REQ-FUNC-002 |
| F04 Slot Management | создание слота с расчётом вместимости, проверка пересечений, изменение, отмена с уведомлением | REQ-FUNC-003, REQ-FUNC-005 |
| ядро `queue` | статусы записи и переходы по диаграмме состояний, правила сроков, продвижение из листа ожидания, расчётное время | REQ-FUNC-007…011, 020 |

Также сделан список слотов студента на неделю (базовая часть F06). Запись на защиту, лист ожидания и живая
очередь — итерации I2 и I3. Письма в I1 записываются в лог приложения; SMTP и outbox подключаются в F13.

## Структура

```
src/main/java/by/bsuir/labqueue/
  queue/         ядро очереди без Spring (BookingStatus, BookingRules, SlotRules, QueuePolicy, EtaCalculator)
  security/      учётные записи, вход, роли, блокировка
  catalog/       группы, дисциплины, импорт .csv
  slot/          слоты преподавателя
  booking/       сущность записи (сервис записи — I2)
  journal/       журнал действий
  notification/  отправка писем
  web/           контроллеры MVC, шаблоны — src/main/resources/templates
src/main/resources/db/migration   миграции Flyway (PostgreSQL и H2)
src/main/resources/db/postgres    миграции только для PostgreSQL (частичный уникальный индекс)
```

## Запуск

Быстрая проверка без базы данных (H2 в памяти и демонстрационные данные):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Учётные записи демо-данных: `teacher@labqueue.local`, `student@labqueue.local`,
`admin@labqueue.local`; пароли указаны в `src/main/resources/application-local.yml`.
Ссылки из писем (подтверждение e-mail, приглашение) выводятся в лог.

С PostgreSQL в Docker:

```bash
cp .env.example .env   # задать пароли
docker compose up -d --build
```

## Тесты

```bash
./mvnw test
```

47 тестов: модульные тесты ядра очереди и правил учётной записи, интеграционные тесты сервисов и
HTTP-сценариев на H2 со схемой из миграций Flyway, тест зависимостей модулей (ArchUnit).
В CI дополнительно выполняется запуск с PostgreSQL 16 и проверка схемы.
