# План будущей реализации, решения и риски

Версия: 1.0  
Текущий этап заканчивается документацией; код приложения в scope не входит.

## 1. Стратегия

Сначала подтверждается самый рискованный технический и продуктовый цикл:

> распознать Shorts → накопить время → показать gate → решить минимальную задачу или выйти → корректно вернуть доступ.

Нейросеть, новые приложения и сложная геймификация не начинаются, пока этот цикл не доказал надёжность и пользу.

## 2. Этапы после утверждения документации

### Этап A. Discovery детектора

Результат, не production app:

- accessibility snapshots актуального YouTube RU/EN;
- карта сильных/средних/слабых признаков;
- positive/negative surface catalogue;
- доказательство возможности показать touchable `TYPE_ACCESSIBILITY_OVERLAY`;
- измерение UsageStats на 2–3 устройствах;
- решение go/no-go по consumer Accessibility подходу.

Exit criteria:

- Shorts устойчиво отличимы от обычного видео на тестовой матрице;
- overlay не мешает Home/Settings;
- не требуется VPN или screen capture.

### Этап B. Кликабельный UX-прототип

- onboarding;
- permissions;
- dashboard;
- presets/custom limits;
- task gate;
- daily limit;
- Emergency Stop;
- accessibility review прототипа.

Exit criteria:

- 5 пользователей без подсказок понимают два лимита;
- каждый находит выход и Emergency;
- никто не считает NoScrol системным или YouTube-экраном.

### Этап C. Технический PoC

Минимум:

- только YouTube package;
- один фиксированный интервал в debug seconds;
- detector;
- одно арифметическое задание;
- accessibility overlay;
- без polished UI, history и Play release.

Exit criteria:

- end-to-end loop работает на Pixel/Samsung/Xiaomi;
- gate восстанавливается после ухода/возврата;
- нет false block на основной negative matrix.

### Этап D. Feature-complete MVP

- onboarding/disclosures;
- пресеты и custom values;
- Shorts cycle;
- daily YouTube + UsageStats reconciliation;
- Emergency Stop + reason + persistence;
- dashboard/local aggregates;
- data deletion/retention;
- release UX/accessibility.

### Этап E. Закрытый пилот

- подписанная sideload/internal testing сборка;
- 5–10 добровольных пользователей;
- 7 дней;
- локальные метрики и интервью;
- detector hotfix process;
- решение о public release.

### Этап F. Публикация

- Privacy Policy URL;
- Accessibility declaration;
- review video;
- Data Safety;
- актуальный target SDK;
- release QA;
- support channel и detector compatibility policy.

## 3. Рекомендуемый порядок backlog

1. Detector discovery.
2. Policy engine и fake event unit tests.
3. Overlay host proof.
4. Live Shorts timer.
5. Local arithmetic gate.
6. Settings/presets.
7. Emergency Stop.
8. Daily UsageStats.
9. Persistence/recovery.
10. Onboarding/disclosures.
11. Dashboard/history/deletion.
12. Full QA/pilot.

Такой порядок раньше выявляет невозможность надёжного детектора или overlay и не тратит время на второстепенный UI.

## 4. Architecture Decision Records

### ADR-001. Accessibility вместо VPN

**Статус:** принято.

**Решение:** использовать package-scoped AccessibilityService.

**Причины:** scroll — UI event; VPN видит IP packets, конфликтует с другим VPN и не различает Shorts/обычный YouTube через HTTPS/QUIC/cache.

**Последствия:** нужен sensitive access и Play declaration; detector зависит от UI YouTube.

### ADR-002. Accessibility overlay вместо application overlay

**Статус:** принято.

**Решение:** `TYPE_ACCESSIBILITY_OVERLAY`.

**Причины:** не требуется второй special permission `SYSTEM_ALERT_WINDOW`, окно связано с core Accessibility feature.

**Последствия:** overlay существует только при подключённом сервисе; требуется реальное тестирование IME/lifecycle.

### ADR-003. Основной лимит по времени, не по свайпам

**Статус:** принято.

**Решение:** активное время подтверждённого Shorts — источник gate.

**Причины:** `TYPE_VIEW_SCROLLED` приходит после события и поля заполняются неодинаково; autoplay и programmatic transitions усложняют точный count.

**Последствия:** UX говорит «через N минут», а scroll count может появиться позже как вспомогательная статистика.

### ADR-004. Два независимых лимита

**Статус:** принято.

**Решение:** Shorts cycle и daily whole-YouTube.

**Причины:** пользователь хочет сохранить полезный YouTube, но также иметь общий дневной бюджет.

**Последствия:** нужны Accessibility live tracking и отдельный Usage Access; UI обязан ясно различать показатели.

### ADR-005. Daily limit выше task gate

**Статус:** принято.

**Решение:** после daily limit задача не продлевает доступ.

**Причины:** иначе дневной предел превращается в ещё один бесконечный gate.

**Последствия:** единственный добровольный bypass — Emergency Stop.

### ADR-006. Emergency Stop бессрочный и с причиной

**Статус:** принято по требованию продукта.

**Решение:** logical bypass сохраняется до ручного выключения, причина обязательна.

**Причины:** реальные ситуации нельзя ограничивать таймером; причина добавляет осознанность.

**Последствия:** риск забыть режим включённым; компенсируется заметным banner и историей. Постоянное notification исключено из MVP ради permission minimization.

### ADR-007. Учёт продолжается при Emergency Stop

**Статус:** принято.

**Причины:** сохраняется честная статистика и после выключения можно корректно переоценить daily limit.

**Последствия:** usage во время bypass помечается отдельно.

### ADR-008. Только локальные арифметические задачи

**Статус:** принято для MVP.

**Причины:** проверяется механизм паузы, а не качество AI-контента; отсутствуют backend, latency, стоимость и privacy-риск.

**Последствия:** задачи могут быстро стать предсказуемыми; это допустимо для пилота.

### ADR-009. Fail open

**Статус:** принято.

**Решение:** `UNKNOWN` detector state не блокирует.

**Причины:** ложная блокировка полезного YouTube и системная ловушка опаснее пропущенного Shorts.

**Последствия:** обновление YouTube может временно снизить эффективность до релиза detector fix.

### ADR-010. Один Gradle module для MVP

**Статус:** предварительно принято.

**Причины:** меньше build/DI сложности до product validation.

**Последствия:** строгие package boundaries и interfaces обязательны; модульное выделение возможно при добавлении второго target app.

## 5. Реестр рисков

| ID | Риск | Вероятность | Влияние | Митигация |
|---|---|---:|---:|---|
| R-001 | YouTube меняет accessibility tree | Высокая | Высокое | multi-signal detector, fail open, regression matrix, быстрые релизы |
| R-002 | Google Play отклоняет Accessibility use | Средняя | Высокое | disclosure, narrow deterministic use, review video, early policy pre-review |
| R-003 | OEM убивает/ломает service pipeline | Средняя | Высокое | real-device matrix, lifecycle recovery, честный status |
| R-004 | UsageStats неточен | Средняя | Среднее | live accumulator + reconciliation, tolerance, clear copy |
| R-005 | Overlay мешает системной навигации | Низкая после QA | Критическое | accessibility QA, Home/Back tests, release blocker |
| R-006 | Emergency становится постоянным bypass | Средняя | Среднее | reason friction, banner, local duration, pilot interview |
| R-007 | Пользователь не понимает два лимита | Средняя | Высокое | presets, summary copy, usability test до кода |
| R-008 | Arithmetic слишком раздражает | Средняя | Среднее | короткое задание, выход всегда доступен, настройка интервала |
| R-009 | Sensitive data попадает в logs | Низкая при контроле | Критическое | запрет raw logging, audits, no Internet/SDKs |
| R-010 | Sideload restricted settings затрудняет пилот | Высокая | Среднее | инструкция, trusted builds, Play internal testing при возможности |
| R-011 | Scope разрастается до Instagram/AI | Высокая | Среднее | жёсткие non-goals и exit criteria MVP |
| R-012 | GPL-код случайно копируется в закрытый продукт | Средняя | Высокое | license review, использовать permissive samples или выпускать GPL-compatible |

## 6. Open-source references

### Curbox

- Репозиторий: [curbox-app/curbox-android](https://github.com/curbox-app/curbox-android).
- Полезно изучить: Accessibility архитектура, блокировка Reels/Shorts, friction mechanisms, usage UI.
- Лицензия: GPL-3.0-or-later.
- Ограничение: копирование в распространяемый производный продукт потребует GPL-compatible стратегии и публикации соответствующего исходного кода.

### Scrolless

- Репозиторий: [DuarteBarbosaDev/Scrolless](https://github.com/DuarteBarbosaDev/Scrolless).
- Полезно изучить: узкий Shorts/Reels detector, timers, overlay, Compose structure.
- Лицензия: GPL-3.0.
- Ограничение: те же copyleft требования; сигнатуры UI нельзя считать долговечными.

### QuestPhone

- Репозиторий: [QuestPhone/questphone](https://github.com/QuestPhone/questphone).
- Полезно изучить: продуктовую механику «задание → доступ».
- Лицензия: GPL-3.0.
- Ограничение: experimental архитектура и более широкий scope; не использовать как production foundation без аудита.

### Official Android samples

- [AppUsageStatistics](https://github.com/googlesamples/android-AppUsageStatistics) — UsageStats, Apache-2.0, архивирован и требует модернизации.
- [Accessibility codelab / GlobalActionBarService](https://github.com/android/codelab-android-accessibility/tree/master/GlobalActionBarService) — service/overlay concepts, Apache-2.0, старый build stack.
- [Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service) — актуальная официальная документация.

### Правило использования

- Если продукт остаётся закрытым, прямое копирование GPL-кода запрещается без отдельного юридического решения.
- Алгоритмы изучаются, а реализация создаётся самостоятельно по Android API и permissive samples.
- Все зависимости проходят license inventory до первого public build.

## 7. Definition of Ready к разработке

Разработка может начаться, когда:

- [ ] PRD, UX/UI, architecture, privacy и QA документы согласованы.
- [ ] Подтверждены рекомендуемые 5/45 и диапазоны custom settings.
- [ ] Подтверждено поведение daily limit без task bypass.
- [ ] Подтвержден бессрочный Emergency Stop и retention причины.
- [ ] Выбрана минимальная Android версия.
- [ ] Проведено detector discovery хотя бы на Pixel и Samsung.
- [ ] Решена стратегия лицензирования: open source GPL-compatible или clean implementation.
- [ ] Подготовлены low-fidelity прототипы ключевых потоков.
- [ ] Назначены owner продукта, Android разработки, UX и QA, даже если роли совмещены.

## 8. Definition of Done документационной фазы

- Все Must-требования имеют ID.
- Для каждого Must существует тест или acceptance rule.
- Все экраны и основные состояния описаны.
- Приоритет policy state однозначен.
- Emergency Stop полностью описан от ввода причины до выключения.
- Данные и permissions перечислены.
- Известны non-goals и риски.
- Нет требования, которое подразумевает VPN/traffic monitoring.
- Документы не обещают абсолютную блокировку.

## 9. Post-MVP backlog

Добавлять только после результатов пилота:

- более разнообразные локальные задачи;
- микротемы на 5 минут;
- backend и AI с кэшированием/офлайн fallback;
- отдельная настройка длительности grant после задачи;
- count scrolls как вспомогательная метрика;
- optional reminder при долгом Emergency Stop;
- export локальной статистики;
- английская локализация NoScrol UI;
- Instagram Reels detector adapter;
- remote detector config с отдельной privacy review;
- widget/quick status;
- experimentation framework без raw accessibility data.

Не добавлять автоматически даже после MVP:

- parental monitoring;
- скрытый режим;
- VPN/MITM;
- запрет uninstall/Settings;
- AI, управляющий Accessibility actions.

## 10. Изменение требований

Любое изменение следующих областей требует обновления PRD, UX, architecture, QA и Play declaration assessment:

- новый target app;
- новые accessibility event types/flags;
- передача данных по сети;
- AI/backend;
- remote analytics/crash reporting;
- родительский или enterprise режим;
- изменение Emergency semantics;
- новый способ обхода daily limit;
- использование `SYSTEM_ALERT_WINDOW`, VPN или broad package visibility.

Изменения фиксируются новой версией документов и коротким ADR с причиной, альтернативами и последствиями.

## 11. Вопросы, не блокирующие PoC

Эти решения можно принять после detector discovery, не меняя core contract:

- финальное название и бренд;
- Hilt или manual DI;
- точная цветовая палитра;
- включать ли optional haptics;
- хранить ли агрегаты ровно 90 дней или дать настройку retention после пилота;
- Play internal testing или подписанный sideload для первого пилота.
