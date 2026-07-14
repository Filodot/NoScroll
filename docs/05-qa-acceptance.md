# QA, тестовая стратегия и критерии приёмки

Версия: 1.0

Связанные документы: [PRD](01-product-requirements.md), [UX/UI](02-ux-ui-specification.md), [архитектура](03-technical-architecture.md)

## 1. Цели тестирования

1. Подтвердить, что NoScrol блокирует именно Shorts, а не полезные разделы YouTube.
2. Подтвердить корректность двух независимых лимитов.
3. Исключить состояния, в которых пользователь не может покинуть YouTube или пользоваться телефоном.
4. Проверить устойчивость Emergency Stop и обязательность причины.
5. Проверить восстановление после process death, reboot и отзыва разрешений.
6. Подтвердить отсутствие сохранения или передачи содержимого YouTube.
7. Подготовить доказательства для Google Play review.

## 2. Уровни тестирования

### 2.1. Unit

- policy priority;
- detector feature scoring на обезличенных synthetic snapshots;
- Shorts cycle accumulator;
- daily usage interval reconstruction;
- midnight/timezone transitions;
- preset/custom settings;
- Emergency validation и persistence;
- arithmetic task generation/checking;
- retention 90 days;
- migrations DataStore/Room.

### 2.2. Integration/Robolectric

- repositories + Room/DataStore;
- process restoration;
- service-to-policy event pipeline;
- overlay command idempotency;
- UsageStats adapter на подготовленных event sequences;
- permission state repository.

### 2.3. Instrumented UI

- onboarding;
- настройка лимитов;
- task gate states;
- daily limit;
- emergency form;
- TalkBack semantics;
- font scaling;
- system Back/Home interaction.

### 2.4. Manual real-device

Обязательно для:

- фактического accessibility-дерева YouTube;
- overlay поверх чужого приложения;
- OEM background/lifecycle поведения;
- Usage Access точности;
- reboot;
- restricted settings sideload;
- Google Play review video.

## 3. Матрица окружений

### 3.1. Android API

Минимальная обязательная матрица:

| Класс | API/версия | Назначение |
|---|---|---|
| Minimum | API 26 / Android 8 | нижняя граница |
| Legacy representative | API 29 / Android 10 | background/activity changes |
| Mid | API 31 / Android 12 | overlay/touch behavior |
| Restricted settings | API 33 / Android 13 | sideload restrictions |
| Play baseline | API 35 / Android 15 | target requirement baseline |
| Current | latest stable Android | актуальное поведение |

Конкретная latest stable версия фиксируется при старте реализации и обновляется перед release.

### 3.2. Производители

Минимум:

- Google Pixel / AOSP-like;
- Samsung One UI;
- Xiaomi/Redmi HyperOS или MIUI;
- один дополнительный массовый OEM при доступности.

### 3.3. Навигация и форм-факторы

- gesture navigation;
- three-button navigation;
- portrait и landscape;
- phone compact/normal;
- large screen или emulator tablet;
- light/dark theme;
- font scale 100%, 130%, 200%;
- русский и английский UI YouTube.

### 3.4. YouTube

- актуальная stable версия;
- предыдущая поддерживаемая версия, если доступна тестовая сборка;
- пользователь вошёл/не вошёл в аккаунт;
- Premium/non-Premium при доступности;
- RU/EN locale;
- варианты UI с рекламой, live, comments, обычным fullscreen и Shorts, открытым по deep link.

## 4. Тестовые конфигурации лимитов

| Конфигурация | Shorts | Daily | Назначение |
|---|---:|---:|---|
| Boundary min | 1 мин | 10 мин | нижние границы |
| Recommended | 5 мин | 45 мин | основной сценарий |
| Boundary max | 30 мин | 240 мин | верхние границы |
| Daily off | 5 мин | выкл. | независимость gate |
| Shorts off | выкл. | 45 мин | независимость daily |
| Daily < Shorts | 20 мин | 10 мин | приоритет daily |
| Both off | выкл. | выкл. | пассивное состояние |

Для ускоренных automated/debug tests clock и thresholds подменяются тестовыми значениями в секундах. Release UI сохраняет продуктовые диапазоны в минутах.

## 5. Критерии детектора Shorts

### 5.1. Положительные поверхности

Детектор обязан распознать:

- вкладку Shorts;
- Short, открытый с Home;
- Short, открытый по deep link;
- переходы к следующему/предыдущему Short;
- открытие/закрытие comments sheet без полного прекращения сессии;
- возврат из channel/profile к Shorts, если текущий UI действительно Shorts.

### 5.2. Отрицательные поверхности

Детектор не должен считать Shorts:

- YouTube Home feed;
- Search и результаты;
- Subscriptions;
- Library/You;
- обычное длинное видео portrait;
- обычное видео fullscreen;
- вертикальное live-видео, если оно не в Shorts surface;
- comments обычного видео;
- settings/account;
- upload/create UI;
- системный share sheet;
- другой пакет с текстом `Shorts`.

### 5.3. Целевые показатели

- Gate не появляется на отрицательной поверхности во всех release-blocking сценариях.
- Не более 1 ложного блокирования на 10 часов расширенного exploratory-теста обычного YouTube.
- Не менее 95% подтверждённых входов в известные Shorts surfaces распознаются в течение 2 секунд.
- При неизвестном layout состояние `UNKNOWN`, время Shorts не идёт и gate не показывается.

## 6. Функциональные сценарии

### 6.1. Onboarding и доступы

| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| TC-001 | Первый запуск | Показ S-001, preset BALANCED выбран по умолчанию |
| TC-002 | Disclosure без checkbox | Переход в Settings недоступен |
| TC-003 | Отказ Accessibility | Onboarding можно покинуть, статус «Защита не работает» |
| TC-004 | Выдать Accessibility и вернуться | Статус обновляется без restart приложения |
| TC-005 | Пропустить Usage Access | Shorts gate доступен, daily unavailable |
| TC-006 | Выдать Usage Access позже | Daily progress появляется после reconciliation |
| TC-007 | Отозвать Accessibility во время работы | Overlay исчезает, dashboard показывает critical status |
| TC-008 | Отозвать Usage Access | Daily enforcement приостанавливается, Shorts gate остаётся |
| TC-009 | YouTube не установлен | Нет crash, показывается понятное состояние |

### 6.2. Пресеты и настройки

| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| TC-020 | Выбрать мягкий | 10/90 сохранено |
| TC-021 | Выбрать сбалансированный | 5/45 и recommendation badge |
| TC-022 | Выбрать строгий | 2/20 сохранено |
| TC-023 | Изменить одно значение | preset становится CUSTOM |
| TC-024 | Minimum/maximum значения | Сохраняются без overflow/rounding |
| TC-025 | Daily меньше Shorts | Сохранение разрешено, показано предупреждение |
| TC-026 | Уменьшить Shorts ниже used | Gate due на следующей активной проверке |
| TC-027 | Увеличить Shorts выше used | Remaining корректно увеличивается |
| TC-028 | Выключить daily при активном daily block | Overlay снимается, если нет task gate |
| TC-029 | Выключить Shorts при task gate | Task overlay снимается, daily продолжает действовать |

### 6.3. Shorts gate

| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| TC-040 | Смотреть Shorts до порога | Gate появляется ≤1 сек после threshold |
| TC-041 | Выйти и вернуться до порога | Used time сохраняется, не сбрасывается |
| TC-042 | Открыть обычное видео | Shorts cycle стоит, daily идёт |
| TC-043 | Выключить экран | Оба live counter приостановлены |
| TC-044 | Ввести правильный ответ | Новый полный interval, overlay исчезает ≤300 мс |
| TC-045 | Ввести неверный ответ | Доступ не выдан, спокойная inline error |
| TC-046 | Три неверных ответа | Появляется «Другой пример» |
| TC-047 | Нажать «Другой пример» | Новый валидный пример, grant не выдан |
| TC-048 | Нажать «Выйти из YouTube» | YouTube покинут, gate state сохраняется |
| TC-049 | Вернуться в Shorts с due gate | Overlay появляется снова |
| TC-050 | Нажать system Back на gate | Пользователь выходит из YouTube, не получает bypass |
| TC-051 | Нажать Home | Телефон доступен, при возврате gate снова действует |
| TC-052 | Process death на gate | Pending task/state восстановлены, время не выдано |

### 6.4. Дневной лимит

| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| TC-060 | Достичь daily в обычном видео | Показ O-002 поверх YouTube |
| TC-061 | Достичь daily в Shorts до cycle threshold | O-002, не task gate |
| TC-062 | Daily достигнут во время task gate | Task заменяется O-002 без раскрытия YouTube |
| TC-063 | Вернуться в YouTube после выхода | Daily overlay восстанавливается |
| TC-064 | Решить task прямо перед daily | Daily всё равно срабатывает по факту порога |
| TC-065 | Наступила полночь | Daily и Shorts cycle сбрасываются, Emergency сохраняется |
| TC-066 | Изменить timezone | Нет crash/negative time/двойного начисления |
| TC-067 | Reboot до daily threshold | Usage восстановлен с приемлемой погрешностью |
| TC-068 | Usage reconciliation меньше live | Показанный usage не уменьшается внутри дня без объяснения |
| TC-069 | Daily выключен | O-002 никогда не показывается |

### 6.5. Emergency Stop

| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| TC-080 | Нажать Emergency на dashboard | Открыта M-001, bypass ещё не включён |
| TC-081 | Пустая/пробельная причина | Confirm disabled |
| TC-082 | Причина 4 символа | Confirm disabled |
| TC-083 | Причина 5 символов | Confirm enabled |
| TC-084 | Ввод >300 символов | Значение ограничено или показана ошибка |
| TC-085 | Cancel формы | Предыдущее enforcement состояние сохранено |
| TC-086 | Confirm с dashboard | Все блоки отключены, banner активен |
| TC-087 | Confirm из task gate | Gate закрыт, Shorts доступны без задания |
| TC-088 | Confirm из daily block | Daily overlay закрыт, YouTube доступен |
| TC-089 | Смотреть YouTube в Emergency | Overlay не появляется, usage увеличивается отдельно |
| TC-090 | Перезапуск NoScrol | Emergency остаётся активен |
| TC-091 | Reboot | Emergency остаётся активен после восстановления |
| TC-092 | Полночь | Emergency остаётся активен, новый daily usage начинается |
| TC-093 | Выключить Emergency при неистёкших лимитах | Обычная работа продолжается |
| TC-094 | Выключить Emergency при истёкшем daily | O-002 появляется при активном YouTube |
| TC-095 | История | Причина/время локально сохранены, deactivatedAt заполнен |
| TC-096 | Удалить историю | Emergency history удалена, active state не повреждён |
| TC-097 | Shorts и daily выключены | Emergency disabled, форма причины не открывается |

### 6.6. Задачи

- Все выражения имеют целый ответ.
- Вычитание не даёт отрицательный результат.
- Умножение использует операнды 2–9.
- Последние пять выражений не повторяются.
- Цифровая клавиатура не закрывает критические действия.
- Unicode minus/multiplication читается и сравнивается корректно.
- Leading zero в ответе не вызывает ошибку (`056` == `56`) либо явно нормализуется.
- Пустой, слишком длинный и нечисловой ввод обрабатывается без crash.

## 7. Lifecycle и устойчивость

Обязательные destructive/recovery tests:

- kill process из Android Settings;
- force-stop и последующий ручной запуск;
- reboot;
- upgrade приложения с сохранением settings/data;
- downgrade не поддерживается и должен быть документирован;
- очистка app data;
- отключение/включение Accessibility;
- смена пользователя/guest не входит в MVP, но не должна раскрывать данные между профилями;
- low-memory recreation UI;
- rotation во всех modal/overlay состояниях;
- IME open/close;
- YouTube crash/update во время gate.

## 8. UX и accessibility QA

### 8.1. TalkBack

- Логичный focus order на всех экранах.
- Все switches объявляют состояние.
- Sliders объявляют минуты и диапазон.
- Progress объявляет использовано/лимит, а не процент без контекста.
- Задача читается словами и не как набор символов.
- Error live region объявляет неверный ответ один раз.
- Overlay имеет заголовок и не отправляет фокус в YouTube под ним.

### 8.2. Visual

- Контраст WCAG AA.
- 200% font scale не скрывает «Выйти» и Emergency Stop.
- Light/dark theme.
- Gesture insets и cutouts.
- Нет прозрачности, делающей текст зависимым от видео под overlay.
- Все состояния доступны без цветового различения.

### 8.3. Copy

- Нет обвинительных формулировок.
- Нет медицинских обещаний.
- Accessibility disclosure совпадает с Privacy Policy и реальным поведением.
- Daily и Shorts time не смешиваются.
- В Emergency явно сказано «до ручного включения».

## 9. Privacy/security tests

- Проверить release manifest на отсутствие запрещённых/лишних permissions.
- Проверить package filter Accessibility.
- Проверить logcat во время просмотра Shorts, поиска, комментариев и Emergency input: содержимого быть не должно.
- Проверить Room/DataStore dump: нет YouTube title/text и неправильных ответов.
- Проверить Android backup rules: причины Emergency не входят.
- Проверить exported components.
- Попытаться активировать Emergency внешним intent — должно быть невозможно.
- Проверить удаление 90-day history.
- Проверить «Удалить историю» после process restart.
- Проверить, что удаление приложения удаляет локальные данные.
- Проверить network inspector: release MVP не создаёт сетевых соединений.

## 10. Performance tests

### 10.1. Цели

- Overlay decision после threshold: ≤1 сек.
- Закрытие после correct: ≤300 мс.
- Tree scan: ≤2/сек.
- UsageStats query: вне main thread.
- Нет ANR при интенсивных content change events.
- Нет удержания `AccessibilityNodeInfo` после обработки.
- Вне YouTube отсутствует постоянный heartbeat.

### 10.2. Методика

- Android Studio Profiler во время 30 минут Shorts;
- memory heap comparison до/после 100 входов/выходов;
- StrictMode debug build;
- macrobenchmark открытия dashboard и overlay render host;
- battery exploratory test 2 часа обычного телефона без YouTube и 1 час YouTube.

Числовой battery budget фиксируется после первого PoC, поскольку до реализации измерить его корректно невозможно.

## 11. Accuracy tests для времени

Сценарии сравниваются с независимым секундомером:

- 5 минут Shorts непрерывно;
- 3 мин Shorts → 4 мин другое приложение → 2 мин Shorts;
- 10 мин обычное видео → 5 мин Shorts;
- screen off внутри Shorts;
- Home/Recent apps;
- process death и восстановление;
- сессия через полночь.

Цели:

- Shorts gate error: не более ±2 секунд на 5-минутной непрерывной сессии после подтверждения детектора;
- daily enforcement error: не более ±60 секунд на 60 мин ручного сценария после reconciliation;
- ни один счётчик не становится отрицательным;
- короткие повторные events не создают двойное начисление.

## 12. Совместимость с исключёнными режимами

Режимы не обязаны учитываться, но должны вести себя безопасно:

- PiP не вызывает полноэкранный overlay над другим приложением;
- Cast не начисляет фоновое время;
- split screen не блокирует второе приложение;
- браузерный YouTube игнорируется;
- YouTube Music игнорируется;
- уведомления YouTube не анализируются.

## 13. Приёмочные критерии MVP

MVP готов к закрытому пилоту, когда:

- [ ] Все Must-требования PRD имеют пройденный acceptance test.
- [ ] Детектор проходит positive/negative matrix на Pixel, Samsung и Xiaomi.
- [ ] Ни одного blocker/critical defect.
- [ ] Нет известного пути закрыть task overlay и продолжить Shorts без correct/Emergency/выхода.
- [ ] Home и системные Settings всегда доступны.
- [ ] Emergency persistence пройдена на process death и reboot.
- [ ] Дневной лимит имеет приоритет над task gate.
- [ ] Permission revoke приводит к честному unavailable state.
- [ ] Privacy log/storage/network audit пройден.
- [ ] TalkBack и 200% font scale пройдены.
- [ ] Review disclosure и видео подготовлены для выбранного канала распространения.

MVP готов к публичной публикации, когда дополнительно:

- [ ] Закрытый пилот не выявил неприемлемых false positives.
- [ ] Play policy повторно проверена по актуальной версии.
- [ ] Privacy Policy опубликована.
- [ ] Data Safety и Accessibility declaration заполнены.
- [ ] Release подписан, versioning и update path проверены.
- [ ] Есть процесс быстрого выпуска detector fix после обновления YouTube.

## 14. Severity

| Severity | Пример | Release rule |
|---|---|---|
| Blocker | Телефон/Settings недоступны из-за overlay; данные экрана утекли | Релиз запрещён |
| Critical | Обычный YouTube постоянно блокируется; Emergency не выключается | Релиз запрещён |
| Major | Daily timer существенно неточен; state теряется после reboot | Пилот запрещён до исправления |
| Minor | Неверный отступ, редкая анимационная ошибка | Может войти с зафиксированным issue |
| Cosmetic | Незначительное визуальное расхождение | По решению release owner |

## 15. Трассировка требований

| Требования | Основные тесты |
|---|---|
| FR-001–004 | TC-001–009 |
| FR-010–022 | detector positive/negative matrix, TC-040–043 |
| FR-030–033 | TC-020–029, TC-040–052 |
| FR-040–043 | TC-060–069, accuracy tests |
| FR-050–052 | TC-020–029 |
| FR-060–064 | TC-080–096 |
| FR-070–072 | dashboard UI, permission tests, TalkBack |
| FR-080–081 | privacy/security tests |
| NFR-001–004 | performance tests |
| NFR-005–008 | privacy audit, failure tests |
| NFR-009 | UX/accessibility QA |
| NFR-010 | environment matrix/build validation |

## 16. Протокол закрытого пилота

Рекомендуемый порядок после технической приёмки:

1. 5–10 добровольных тестировщиков на 7 дней.
2. До установки зафиксировать субъективное обычное время Shorts.
3. Первые 2 дня использовать сбалансированный профиль 5/45.
4. Разрешить кастомизацию после понимания механики.
5. На 3-й и 7-й день собрать интервью:
   - был ли gate понятен;
   - продолжал ли пользователь осознанно;
   - были ли ложные блокировки;
   - зачем включался Emergency;
   - не мешал ли общий daily полезному YouTube.
6. Не собирать причины Emergency удалённо автоматически; пользователь делится ими только добровольно.
7. По итогам принять решение о развитии задач/AI, а не добавлять их до проверки core loop.
