# Приватность, безопасность и Google Play

Версия: 1.0  
Актуальность внешних правил должна быть повторно проверена непосредственно перед публикацией.

## 1. Privacy posture MVP

NoScrol получает технически чувствительный системный доступ, поэтому продукт строится по принципу минимально достаточных данных:

- только нативный пакет YouTube;
- только события и дерево активного окна, необходимые для распознавания Shorts;
- обработка accessibility-данных в памяти;
- отсутствие скриншотов и записи экрана;
- отсутствие `INTERNET` permission;
- отсутствие аккаунта и сторонних SDK аналитики;
- локальное хранение только настроек, агрегатов времени, результатов задания и введённых самим пользователем причин Emergency Stop;
- понятное отключение и удаление данных.

## 2. Инвентаризация данных

| Данные | Источник | Цель | Хранение | Retention |
|---|---|---|---|---|
| Настройки лимитов | ввод пользователя | enforcement | DataStore | до сброса/удаления приложения |
| Статус Emergency Stop | ввод пользователя | bypass | DataStore | до ручного выключения |
| Причина Emergency Stop | ввод пользователя | рефлексия и локальная история | Room, app-private | 90 дней или ручное удаление |
| Время YouTube | Accessibility + UsageStats | дневной лимит/статистика | дневной агрегат Room | 90 дней |
| Время Shorts | detector heartbeat | gate/статистика | дневной агрегат Room | 90 дней |
| Task outcome | NoScrol UI | выдача интервала/агрегат | Room без введённых ответов | 90 дней |
| Версия YouTube | PackageManager | совместимость детектора | diagnostics | пока установлена/90 дней |
| UI tree и экранный текст | Accessibility | распознавание в моменте | **не сохраняется** | только время обработки |

Автоматическая очистка истории старше 90 дней запускается локально при открытии приложения или записи нового дня. Настройки и текущий незакрытый Emergency Event не удаляются до завершения их жизненного цикла.

## 3. Данные, которые запрещено собирать

- названия просмотренных видео;
- имена каналов;
- комментарии, сообщения и поисковые запросы;
- полный список установленных приложений;
- URL, DNS или сетевой трафик;
- screenshots, audio/video capture;
- содержимое clipboard;
- контакты, location, advertising ID и device identifiers;
- неправильные ответы пользователя на задачу;
- причины Emergency Stop в analytics/logs.

## 4. Разрешения и special access

### 4.1. Необходимые

| Доступ | Назначение | Обязательность |
|---|---|---|
| Accessibility Service | распознавание Shorts и показ accessibility overlay | обязателен для core feature |
| Usage Access / `PACKAGE_USAGE_STATS` | восстановление и сверка общего дневного времени YouTube | обязателен только для дневного лимита |
| Точечная package visibility YouTube | проверить установку/версию YouTube | обязателен |

Accessibility включается пользователем в системных настройках и может быть отозван в любой момент. Usage Access также выдаётся и отзывается отдельно.

### 4.2. Не запрашиваются

- `INTERNET`;
- `QUERY_ALL_PACKAGES`;
- `SYSTEM_ALERT_WINDOW`;
- `POST_NOTIFICATIONS` в MVP;
- `BIND_VPN_SERVICE`;
- storage/media permissions;
- location, microphone, camera;
- contacts, notifications listener;
- Device Admin.

## 5. Prominent disclosure Accessibility

NoScrol не является accessibility tool, предназначенным в первую очередь для людей с инвалидностью. В metadata нельзя указывать `isAccessibilityTool=true`.

До системного экрана Accessibility пользователь видит отдельное disclosure:

> Чтобы распознавать Shorts и показывать паузу, NoScrol получает события интерфейса только от приложения YouTube. Сервис может видеть элементы активного экрана YouTube и взаимодействовать с ним для показа блокирующего окна. Данные обрабатываются на устройстве: NoScrol не сохраняет содержимое экрана, названия видео, историю просмотров или введённый в YouTube текст и никуда их не отправляет.

Требования:

- disclosure находится внутри приложения;
- показывается в нормальном onboarding непосредственно перед Settings;
- требует отдельного affirmative action;
- отказ не трактуется как согласие;
- текст не спрятан в Privacy Policy;
- при существенном изменении использования API согласие и Play declaration пересматриваются.

Это соответствует текущим требованиям [Google Play AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

## 6. Disclosure Usage Access

Перед Usage Access:

> Для общего дневного лимита NoScrol читает длительность использования YouTube. Доступ не раскрывает содержимое видео или действия внутри YouTube. Без него задания в Shorts продолжат работать, но общий дневной лимит будет недоступен.

Пользователь может отказаться. В этом случае:

- дневной лимит выключается/помечается unavailable;
- UI не показывает фиктивно точное дневное значение;
- Shorts gate продолжает работать.

## 7. Детерминированная автоматизация

Accessibility automation ограничена понятными статическими правилами:

- порог достигнут → показать branded overlay;
- пользователь нажал «Выйти» → выполнить Back/Home;
- пользователь правильно ответил → убрать overlay;
- Emergency Stop подтверждён → убрать enforcement.

Нейросеть отсутствует в MVP. В будущих версиях AI может генерировать только содержимое урока. AI не может решать, какие accessibility actions выполнять, какие приложения контролировать или когда обходить правила.

## 8. Data Safety и privacy policy

При полностью локальной обработке и отсутствии передачи данных за пределы устройства эти данные обычно не считаются `collected` в смысле Google Play Data Safety. Однако форма должна быть заполнена по фактической release-сборке и всем включённым SDK. Официальное определение сверяется по [Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

Любое добавление следующих компонентов меняет оценку и требует повторной проверки:

- crash reporting;
- remote analytics;
- backend/AI;
- cloud backup;
- support upload;
- advertising/purchases SDK.

### 8.1. Краткий проект privacy policy

Полный юридический текст готовится перед публикацией, но должен отражать:

1. NoScrol обрабатывает события интерфейса только YouTube для определения Shorts.
2. Содержимое экрана не сохраняется и не отправляется.
3. Статистика времени и причины Emergency Stop хранятся локально.
4. Данные автоматически удаляются через 90 дней либо раньше по действию пользователя.
5. Пользователь может отозвать системные доступы в Android Settings.
6. Удаление приложения удаляет app-private данные.
7. MVP не содержит аккаунта, рекламы, внешней аналитики или передачи третьим сторонам.
8. Контакт разработчика и дата вступления документа в силу.

Privacy Policy должна быть доступна внутри приложения и по публичному URL для Google Play.

## 9. Google Play declaration

До production release необходимо:

- заполнить Accessibility declaration в Play Console;
- выбрать использование для `App functionality`, не выдавая приложение за accessibility tool;
- точно описать, что сервис ограничен YouTube и распознаёт Shorts;
- показать disclosure и affirmative consent;
- предоставить review video, включающее:
  1. запуск NoScrol;
  2. полный disclosure;
  3. сценарий отказа;
  4. переход в Settings и включение сервиса;
  5. вход в YouTube Shorts;
  6. срабатывание task gate;
  7. выход из YouTube и Emergency Stop;
- описать Accessibility use в store listing;
- заполнить Data Safety по фактической сборке;
- предоставить доступную Privacy Policy;
- убедиться, что все active tracks содержат одинаково корректные declarations.

Approval не гарантирован. Политика проверяется повторно перед каждым существенным изменением Accessibility поведения.

## 10. Package visibility

Для MVP известен единственный внешний пакет. Используется ограниченный manifest query для YouTube.

`QUERY_ALL_PACKAGES` не нужен и не должен добавляться. Текущие ограничения описаны в [Google Play package visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en).

## 11. Sideload и restricted settings

На Android 13+ sideloaded build может потребовать от тестировщика вручную разрешить restricted settings, прежде чем Accessibility станет доступен. Это системная защита, её нельзя обходить автоматически.

Инструкция пилота должна:

- объяснять, что доступ чувствительный;
- просить включать его только для подписанной тестовой сборки;
- показывать путь через App info → menu → Allow restricted settings, если система его предлагает;
- не использовать манипулятивные формулировки.

Официальное объяснение: [Android restricted settings](https://support.google.com/android/answer/12623953).

## 12. Threat model

### T-001. Утечка содержимого YouTube через логи

- Риск: разработчик случайно логирует `node.text`.
- Контроль: централизованный redaction, code review, lint/checklist, отсутствие raw node logging даже в debug по умолчанию.

### T-002. Утечка причин Emergency Stop

- Риск: причина может содержать личную информацию.
- Контроль: app-private Room, исключение из backup, отсутствие analytics/export по умолчанию, ручное удаление, retention 90 дней.

### T-003. Обманный overlay

- Риск: UI похож на системный/YouTube и может восприниматься как фишинг.
- Контроль: постоянный бренд NoScrol, отсутствие запросов паролей/аккаунтов, неприменение к другим пакетам, собственная визуальная система.

### T-004. Ложная блокировка обычного YouTube

- Риск: неверная сигнатура детектора.
- Контроль: multi-signal confidence, fail open, дневной блок отделён от Shorts detector, regression tests на Search/Home/Subscriptions/long video.

### T-005. Пользователь не может пользоваться телефоном

- Риск: полноэкранный overlay перехватывает все действия.
- Контроль: Home/системная навигация доступны, явная кнопка выхода, overlay только над активным YouTube, crash/revoke fail open.

### T-006. Неожиданно вечный Emergency Stop

- Риск: пользователь забывает вернуть блокировку.
- Контроль: явный баннер на главном экране, статус в настройках, время включения, UX-проверка перед активацией. Уведомление рассматривается после MVP, чтобы не добавлять permission.

### T-007. Подмена внешним intent

- Риск: другая программа активирует Emergency Stop или подставляет причину.
- Контроль: internal components not exported, никаких внешних deep links для enforcement, проверка caller там, где применимо.

### T-008. Несанкционированный cloud backup

- Риск: Android backup переносит причины Emergency Stop.
- Контроль: исключить Room/DataStore с чувствительными полями из backup или отключить backup MVP.

## 13. Пользовательский контроль

Пользователь может:

- видеть статус каждого special access;
- отключить Shorts gate;
- отключить дневной лимит;
- включить/выключить Emergency Stop;
- удалить локальную историю;
- отозвать Accessibility/Usage Access в Android;
- удалить приложение.

NoScrol не должен:

- скрывать иконку;
- мешать uninstall;
- автоматически возвращать отозванный доступ;
- блокировать Android Settings;
- обещать работу после отключения Accessibility.

## 14. Юридическое и продуктовое позиционирование

- Инструмент добровольного self-control, не monitoring/stalkerware.
- Не использовать маркетинг «контроль другого человека».
- Не объявлять медицинским приложением или лечением зависимости.
- Не гарантировать предотвращение любого использования YouTube.
- Для будущего parental/enterprise продукта потребуется отдельная policy- и архитектурная оценка, вероятно отдельный package/product.

## 15. Security/Privacy release checklist

- [ ] Production manifest не содержит `INTERNET`, broad package visibility или лишних permissions.
- [ ] Accessibility package filter ограничен YouTube.
- [ ] `isAccessibilityTool=false`.
- [ ] Нет raw accessibility text в logcat, crash dumps и БД.
- [ ] Причины Emergency исключены из backup.
- [ ] «Удалить историю» реально очищает все соответствующие таблицы.
- [ ] Retention job удаляет данные старше 90 дней.
- [ ] Disclosure совпадает с фактическим поведением.
- [ ] Play declaration и review video актуальны.
- [ ] Privacy Policy опубликована и доступна внутри приложения.
- [ ] Data Safety сверена со всеми зависимостями release build.
- [ ] Emergency нельзя активировать внешним intent.
- [ ] Системная навигация и отключение сервиса не заблокированы.

