# Независимые блоки будущей разработки

Цель документа — разбить MVP на задачи, каждая из которых помещается в один самостоятельный сеанс генерации кода. Реальная разработка этим документом не начинается.

## 1. Модель выполнения

Используется три уровня:

1. **WP-00 — фундамент:** один раз создаёт компилируемый проект и замораживает интерфейсы.
2. **Независимые WP-01…WP-15:** выполняются в любом порядке и используют fake/in-memory зависимости.
3. **Интеграционные IP-01…IP-06:** коротко соединяют уже готовые блоки; каждый integration package также рассчитан на один сеанс.

Единственная обязательная последовательность:

```text
WP-00 → любые WP-01…WP-15 → IP-01…IP-06
```

Функциональные WP не должны ожидать друг друга. Например, dashboard строится на fake repository, а detector — на synthetic snapshots.

## 2. Общие правила одного сеанса

Каждый сеанс обязан:

- работать только в своей области владения;
- не менять замороженные contracts без отдельного ADR;
- использовать fake вместо незавершённой соседней части;
- завершаться компилируемым состоянием;
- добавить unit/UI tests своей логики;
- не оставлять `TODO`, от которого зависит заявленная приёмка;
- не подключать backend, AI, VPN или новые target apps;
- сообщить изменённые файлы, выполненные проверки и известные ограничения.

Обычный Definition of Done блока:

- scope выполнен;
- acceptance cases блока проходят;
- существующие тесты не сломаны;
- публичный контракт документирован;
- нет изменений root navigation/DI/manifest, если блок ими не владеет.

## 3. Владение областями

| Область | Единственный владелец до интеграции |
|---|---|
| Core contracts и test fakes | WP-00 |
| Policy decisions | WP-01 |
| Shorts cycle | WP-02 |
| Daily usage reducer | WP-03 |
| Arithmetic tasks | WP-04 |
| Presets/settings domain | WP-05 |
| Emergency domain | WP-06 |
| DataStore/Room adapters | WP-07 |
| YouTube Shorts detector | WP-08 |
| Accessibility Android adapter | WP-09 |
| UsageStats Android adapter | WP-10 |
| Onboarding UI | WP-11 |
| Dashboard UI | WP-12 |
| Limits UI | WP-13 |
| Blocking overlay UI | WP-14 |
| Privacy/history maintenance UI | WP-15 |
| Root wiring, navigation и final manifest | только IP-пакеты |

## 4. Фундамент

<a id="wp-00"></a>

### WP-00 — Project shell и frozen contracts

**Цель:** создать пустое, запускаемое Android-приложение, на котором все остальные блоки смогут работать независимо.

**Сделать:**

- Kotlin/Compose/Material 3 project shell;
- единый version catalog и заранее согласованный набор Compose, Navigation, Coroutines, DataStore, Room и test dependencies, чтобы feature-блоки не редактировали build configuration;
- `core/contracts`, `core/model`, `core/testing`;
- интерфейсы detector, clocks, settings, usage, emergency, overlay и repositories;
- sealed policy inputs/outputs без логики;
- fake/in-memory реализации каждого порта;
- test runner и базовый CI/local test command;
- пустой root screen без продуктовых функций.

**Не делать:** Accessibility Service, Room schema, реальные экраны или policy logic.

**Приёмка:** debug build и unit tests проходят; любая feature может зависеть только от `core/contracts` и fake.

## 5. Независимые domain-блоки

<a id="wp-01"></a>

### WP-01 — Policy Engine

**Владеет:** `core/policy/**`.

**Сделать:** чистую Kotlin-функцию/класс, реализующий приоритет `Emergency → permissions → daily → task → allow`, плюс state transition tests.

**Fake inputs:** clocks, settings, counters, detector state.

**Приёмка:** покрыты все комбинации приоритета, особенно daily поверх task и выключение Emergency при превышенном daily.

**Не делать:** Android API, overlay и persistence.

<a id="wp-02"></a>

### WP-02 — Shorts Cycle Counter

**Владеет:** `core/usage/shorts/**`.

**Сделать:** накопление активных секунд, pause/resume, reset после задачи/полуночи, пересчёт после изменения интервала, защита от двойных tick.

**Fake inputs:** monotonic clock и detector heartbeat.

**Приёмка:** сценарии 3 мин → выход → 2 мин, screen off, process snapshot restore и изменение 5 → 2/10 минут.

**Не делать:** определение Shorts и UI.

<a id="wp-03"></a>

### WP-03 — Daily Usage Reducer

**Владеет:** `core/usage/daily/**`.

**Сделать:** реконструкцию YouTube foreground intervals из нормализованных events, объединение с live delta, local-date reset и monotonic non-decreasing reconciliation.

**Fake inputs:** prepared UsageEvents и wall/monotonic clocks.

**Приёмка:** midnight, timezone, незакрытый foreground interval, process death и погрешность без отрицательных значений.

**Не делать:** вызовы `UsageStatsManager`.

<a id="wp-04"></a>

### WP-04 — Local Arithmetic Task Engine

**Владеет:** `core/tasks/**`.

**Сделать:** генерацию `+`, `−`, `×`, проверку integer answer, three-fail replacement, anti-repeat последних пяти задач и pending-task state.

**Fake inputs:** seeded random и fake clock.

**Приёмка:** нет отрицательного вычитания/дробей, leading zero нормализуется, генератор детерминирован в тестах.

**Не делать:** Compose UI и AI.

<a id="wp-05"></a>

### WP-05 — Presets и Settings Domain

**Владеет:** `core/settings/**`.

**Сделать:** модели `Gentle 10/90`, `Balanced 5/45`, `Strict 2/20`, `Custom`; диапазоны, validation, summary text model и переход в Custom.

**Fake inputs:** in-memory settings repository.

**Приёмка:** границы 1–30 и 10–240, daily off, daily < Shorts warning, immediate recalculation command.

**Не делать:** DataStore и экран настроек.

<a id="wp-06"></a>

### WP-06 — Emergency Stop Domain

**Владеет:** `core/emergency/**`.

**Сделать:** validation причины 5–300 символов, activation/deactivation use cases, persistence contract, duration/usage aggregate и запрет activation при обоих выключенных лимитах.

**Fake inputs:** in-memory emergency repository и clocks.

**Приёмка:** process/reboot-shaped restore, activation sources, cancel без изменения state, деактивация без удаления истории.

**Не делать:** modal UI и Room.

## 6. Независимые data/Android-блоки

<a id="wp-07"></a>

### WP-07 — Persistence adapters

**Владеет:** `data/local/**`, Room schema/migrations, DataStore adapters.

**Сделать:** реализации frozen repository contracts для settings, DailyUsage, GateCycle, PendingTask и EmergencyEvent; retention 90 дней; backup exclusions.

**Fake callers:** repository contract tests, без реального UI/service.

**Приёмка:** CRUD, transaction task grant, migration test, active Emergency не удаляется retention job.

**Не делать:** domain decisions и navigation.

<a id="wp-08"></a>

### WP-08 — YouTube Shorts Detector

**Владеет:** `monitoring/detector/**` и обезличенные fixtures.

**Сделать:** feature extraction, multi-signal scoring, debounce и `UNKNOWN/NOT_SHORTS/SHORTS_CONFIRMED` на frozen `WindowSnapshot`.

**Fake inputs:** synthetic/redacted snapshots, не живой Accessibility Service.

**Приёмка:** positive/negative matrix; один слабый текстовый признак не блокирует; unknown fail open.

**Не делать:** Android service, timer и overlay.

<a id="wp-09"></a>

### WP-09 — Accessibility Service Adapter

**Владеет:** `monitoring/accessibility/**`, service metadata XML и debug-only manifest registration.

**Сделать:** package-scoped service, event coalescing, safe `WindowSnapshot` mapper, foreground/screen signals, освобождение nodes и debug-only регистрацию для изолированной проверки. Финальную запись в main manifest выполняет IP-04.

**Fake output consumer:** recording sink из `core/testing`.

**Приёмка:** события только YouTube, raw text не логируется, scan throttling ≤2/сек, revoke/reconnect без crash.

**Не делать:** detector logic, counters, policy и overlay.

<a id="wp-10"></a>

### WP-10 — UsageStats Adapter

**Владеет:** `monitoring/usagestats/**`.

**Сделать:** проверку special access, запрос events от local midnight, нормализацию `RESUMED/PAUSED`, version-safe error mapping и background dispatcher.

**Fake output consumer:** WP-03 contract/recording sink.

**Приёмка:** permission denied, empty events, open interval и API-level fixtures; main thread не блокируется.

**Не делать:** daily reduction, UI и enforcement.

## 7. Независимые UI-блоки

Каждый UI-блок использует state holder с fake repository и Compose previews. Он не добавляет себя в root navigation до интеграции.

<a id="wp-11"></a>

### WP-11 — Onboarding и permissions UI

**Владеет:** `feature/onboarding/**`.

**Сделать:** S-001…S-005, Accessibility disclosure, Usage Access explanation, checklist и fake permission states.

**Приёмка:** отказ/возврат/skip daily, checkbox consent, TalkBack и 200% font scale UI tests.

**Не делать:** реальные Settings intents — их подключает IP-05.

<a id="wp-12"></a>

### WP-12 — Dashboard UI

**Владеет:** `feature/dashboard/**`.

**Сделать:** нормальное, Emergency и permission-error состояния; Shorts/daily progress; remaining time; fake state previews.

**Приёмка:** daily disabled/unavailable, Emergency banner, текстовые progress values, light/dark/200%.

**Не делать:** реальные repositories и navigation.

<a id="wp-13"></a>

### WP-13 — Limits UI

**Владеет:** `feature/limits/**`.

**Сделать:** preset cards, два master switches, sliders/steppers, custom transition, warning daily < Shorts и summary.

**Приёмка:** все границы, unsaved state, save/cancel, TalkBack announcements.

**Не делать:** DataStore implementation.

<a id="wp-14"></a>

### WP-14 — Blocking Overlay UI

**Владеет:** `feature/overlay/**`, без реального WindowManager host.

**Сделать:** O-001 task gate, answer/error/success, O-002 daily limit, Emergency form overlay variant и standalone Compose host screen.

**Приёмка:** task → daily replacement state, keyboard/insets, exit always visible, Back intent не dismiss bypass.

**Не делать:** `TYPE_ACCESSIBILITY_OVERLAY`, task generation и policy.

<a id="wp-15"></a>

### WP-15 — Settings, history и data controls UI

**Владеет:** `feature/settings/**` и `feature/history/**`.

**Сделать:** permission statuses, privacy summary, Emergency history, diagnostics-redacted view, delete-history confirmation.

**Приёмка:** empty/error states, active event display, deletion confirmation, ни одного raw YouTube field.

**Не делать:** retention/persistence и реальные system intents.

## 8. Интеграционные пакеты

Интеграционные пакеты выполняются последовательно после нужных WP. Они не добавляют новую доменную логику — только заменяют fake на реальные adapters.

<a id="ip-01"></a>

### IP-01 — Root navigation и DI

Соединить WP-11…WP-15 в приложение, настроить navigation и dependency graph с fake repositories. Приёмка: полный UI flow работает без Android monitoring.

<a id="ip-02"></a>

### IP-02 — Settings, persistence и Emergency

Подключить WP-05/06/07 к UI. Приёмка: настройки и Emergency переживают restart; причина/история/delete работают.

<a id="ip-03"></a>

### IP-03 — Monitoring pipeline

Соединить WP-02/08/09 с redacted diagnostics screen. Приёмка: реальный Shorts меняет detector state и cycle counter, но пока не блокируется.

<a id="ip-04"></a>

### IP-04 — Task gate pipeline

Соединить WP-01/04/14 с monitoring pipeline и создать реальный `TYPE_ACCESSIBILITY_OVERLAY` host. Приёмка: threshold → task → correct grant/exit/Emergency.

<a id="ip-05"></a>

### IP-05 — Daily limit и system access

Соединить WP-03/10 с policy/dashboard/daily overlay; добавить Settings intents onboarding. Приёмка: daily имеет приоритет, permission revoke честно отключает функцию.

<a id="ip-06"></a>

### IP-06 — End-to-end hardening

Process death, reboot, midnight, OEM matrix, accessibility/privacy audit, release manifest, Play review flow. Приёмка: checklist из `05-qa-acceptance.md` выполнен для закрытого пилота.

## 9. Что можно делать параллельно

После WP-00 одновременно допустимы группы:

- Domain: WP-01…WP-06;
- Android/data: WP-07…WP-10;
- UI: WP-11…WP-15.

Чтобы merge оставался простым:

- ни один WP не редактирует root navigation/DI;
- ни один WP после WP-00 не меняет общий build configuration без отдельного ADR;
- UI не импортирует Android adapters;
- Android adapters не импортируют feature UI;
- domain не зависит от Android SDK;
- все взаимодействия идут через `core/contracts`.

## 10. Как формулировать будущий запрос на один сеанс

Достаточно указать ID, например:

> Выполни WP-04 из `docs/07-implementation-work-packages.md`. Не выходи за scope, используй frozen contracts и fake dependencies. Заверши тестами и отчётом по Definition of Done.

Перед стартом сеанса агент должен прочитать:

1. этот WP;
2. соответствующий раздел [архитектуры](03-technical-architecture.md);
3. связанные требования [PRD](01-product-requirements.md);
4. связанные тесты [QA](05-qa-acceptance.md).

## 11. Отчёт после каждого сеанса

Краткий обязательный формат:

- выполненный WP/IP;
- результат;
- изменённые файлы;
- тесты/проверки и их результат;
- отклонения от frozen contract;
- известные ограничения;
- готовность к merge.

Если contract оказался недостаточен, сеанс не должен молча менять его. Нужно остановить только затронутую часть, оформить предложение ADR и продолжить всё, что не зависит от изменения.
