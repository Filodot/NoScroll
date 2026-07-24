# Умные ИИ-уроки — независимые блоки разработки

Каждый блок завершается unit-тестами, сборкой, отдельным коммитом и публикацией прогресса.

## EDU-01 — Domain foundation

- модели курса, источника, плана, урока, упражнения, попытки и усвоения;
- расширяемые типы упражнений;
- deterministic quality validator;
- mastery policy и scheduler;
- статический офлайн-каталог.

Не меняет Android/Room/UI. Приёмка: все unit-тесты и существующая сборка проходят.

## EDU-02 — Persistence

- Room entities/DAO/repositories для курсов, плана, понятий, пакетов и попыток;
- миграция без destructive fallback;
- атомарное потребление lesson package;
- удаление всех данных курса;
- in-memory fake для Compose и domain-тестов.

## EDU-03 — Learning UI shell

- раздел «Обучение»;
- список курсов, детали курса и экран статического урока;
- отображение прогресса и количества офлайн-уроков;
- объективная проверка choice/order/fill/numeric/code-output;
- замена подозрительного задания.

## EDU-04 — Material import

- SAF picker;
- PDF, DOCX, TXT и Markdown;
- безопасное потоковое чтение и ограничения размера;
- chunking с page/section metadata и SHA-256;
- экран обработки, отмены и ошибок.

## EDU-05 — Secrets and provider router

- ввод ключей Gemini, Groq и Cloudflare;
- Android Keystore encryption;
- provider adapters и единый typed contract;
- timeout, rate limit, backoff, circuit breaker и remote-like local config;
- redacted diagnostics.

## EDU-06 — Curriculum generation

- hierarchical analysis документов;
- построение плана по материалу;
- построение плана по теме;
- редактор дерева: rename/reorder/add/delete/merge/split;
- plan versioning и предупреждение AI-only.

## EDU-07 — Lesson generation and quality

- JSON schemas для всех поддержанных payload;
- deterministic validation;
- critic/repair/regenerate/provider failover;
- карантин;
- предварительный кэш трёх–пяти уроков.

## EDU-08 — Progress and repetition

- сохранение попыток;
- mastery projection;
- spaced review queue;
- новые/повтор/weak prerequisite;
- recalculation после редактирования плана.

## EDU-09 — NoScroll integration

- `TaskType.LEARNING`;
- выбор курсов в настройках;
- выдача только локально готового упражнения;
- один правильный ответ для grant;
- suspicious replacement без штрафа;
- arithmetic/custom fallback при пустом кэше.

## EDU-10 — Python and SQL

- code-output без исполнения;
- SQL в изолированной in-memory SQLite с read-only ограничением;
- Python executor с лимитом времени и памяти;
- открытые/скрытые тесты;
- запрет файловой системы, сети и Android API.

## EDU-11 — Hardening and release

- process death, reboot, offline и exhausted quota;
- большие/повреждённые документы;
- provider outage matrix;
- accessibility/font scale;
- privacy/data deletion;
- lint, unit/instrumentation tests и release APK.
