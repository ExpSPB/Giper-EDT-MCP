# Ревью реализации multi-profile tool surfaces

**Дата:** 30 августа 2026  
**Дополнение:** 31 августа 2026 — тесты на швы, коммит `2da4380` на локальной `test`  
**Проверенный срез:** `origin/test` = `29d8bf2` (merge PR #3 `cursor/multi-profile-review-fixes-96c9`)
**Критерии:** [`multi-profile-implementation-plan.md`](multi-profile-implementation-plan.md), [`multi-profile-tool-surfaces.md`](multi-profile-tool-surfaces.md)

## 0. О срезе и методике

Локальная ветка `test` (`233475b`) **отстаёт на 7 коммитов**: в `origin/test` влит раунд исправлений по
предыдущему аудиту ([`multi-profile-implementation-review.md`](multi-profile-implementation-review.md)).
Ревьюился более полный срез — `origin/test`. Если вы работаете на локальном `test`, часть замечаний
прошлого аудита там ещё актуальна.

Что сделано в рамках этого ревью:

- прочитаны спецификация и план целиком, каждый пункт сопоставлен с кодом;
- прочитан код четырёх слоёв (profiles, transport/protocol, status/bridge/UI/observability, proxy);
- **самостоятельно запущены оба офлайн-гейта** (результаты в §1);
- каждое утверждение из отчётов подчинённых агентов, попавшее в разделы «Блокеры» и «Существенное»,
  перепроверено чтением кода лично. Пометка «(проверено)» означает именно это; «(со слов ревьюера)» —
  находка правдоподобна по конструкции, но я не открывал файл сам.

Что **не** проверялось: живой контур (редеплой в EDT, e2e против рабочего стенда, conformance,
разбор `.metadata/.log`) — на этой машине нет установленного EDT. Также не проверялись комментарии
и треды PR #3 — в окружении нет `gh`.

## 1. Фактическое состояние гейтов

| Гейт | Статус | Чем подтверждено |
|---|---|---|
| Tier 1: Tycho build + unit | **зелёный** | `mvn clean verify` в `mcp/` → `BUILD SUCCESS`, `Tests run: 5938, Failures: 0, Errors: 0, Skipped: 2` — запущено мной |
| Proxy: `mvn -f proxy/pom.xml verify` | **зелёный** | `BUILD SUCCESS`, `Tests run: 199, Failures: 0, Errors: 0` — запущено мной |
| Tier 2: живой редеплой + anti-stale | **не пройден** | нет стенда; в [`handoff-multi-profile-edt-stand.md`](handoff-multi-profile-edt-stand.md) прямо помечен как оставшийся |
| Tier 3: e2e против живого EDT (+ proxy) | **не пройден** | там же |
| Tier 4: conformance по 5 URL | **не пройден** | матрица в CI подготовлена (см. §5), но прогон не выполнен |
| Разбор EDT `.metadata/.log` | **не пройден** | — |
| Ручной UI smoke (п. 4.3 хэндоффа) | **не пройден** | — |

Итог: **работа не проходит `edt-mcp-ready-to-deploy`** — закрыты только две нижние ступени из семи.
При этом PR #3 уже смержен в `test`, то есть слияние опередило live-гейт, который сам же хэндофф
объявляет обязательным.

## 2. Оценка полноты по этапам плана

| Этап | Область | Полнота | Комментарий |
|---|---|---|---|
| 0 | Ветка, baseline, characterization | **частично** | Ветка и документация есть. Отдельного characterization-набора «до изменений» не создавалось; регрессии отделялись правкой существующих тестов. |
| 1 | Модель, codec, repository | **выполнено** | Все девять классов на месте, инварианты неизменяемости/атомарности выдержаны. Дефекты: B5, M8. |
| 2 | Миграция и legacy-совместимость | **выполнено с дефектом** | Механика корректна и идемпотентна, обе стороны зеркала работают. Но миграция порождает **блокер B1** и молчит при отказе (M7). |
| 3 | Отделение каталога от политики | **выполнено** | `McpToolRegistry` больше не читает `ToolSettingsService` (проверено: импорта нет, `getEnabledTools`/`getVisibleTools`/`isToolEnabled` удалены). `ProfileToolPolicy` — единственная точка авторизации. Сделано чище плана: deprecated-обёрток не осталось. |
| 4 | `McpRequestContext` и dispatch | **выполнено** | Контекст неизменяем, несёт версию протокола и capabilities, глобальный `lastClientCapabilities` удалён, в worker передаётся явно, без `ThreadLocal`. |
| 5 | URL resolver и сессии | **выполнено** | Resolver строгий и корректный по всем кейсам спецификации (см. §4). Сессии path-bound, 400/404/DELETE/TTL/кап реализованы. |
| 6 | Адресные SSE-уведомления | **не выполнено** | Уведомления заменены разрывом сессий — **блокер B3**. |
| 7 | Самодиагностика профиля | **выполнено** | Контракт `activeProfile`/`availableProfiles` соблюдён. Дефект схемы — M5. |
| 8 | Параллельные вызовы, история, bridge | **частично** | `ActiveToolCallRegistry` сделан хорошо и изолирует сигналы. Но история профиля не работает (**блокер B2**), status bar путает профиль (M2), граница Workmate — текстовая (M4). |
| 9 | Preferences UI без рестарта | **выполнено** | Рестарта на изменение профилей нет, Add только из пресета, unknown-инструменты помечены, конфликт ревизии предлагает reload. Замечания — §7. |
| 10 | Profile-aware proxy | **частично** | Каркас по плану, прошлый дефект парсинга `activeProfile` исправлен. Но правило §2 держится только на happy path — **блокеры B4, B6**. |
| 11 | E2E, conformance, документация | **частично** | Фикстуры, сид, матрица и README сделаны. Прогонов нет (§1). |

## 3. Блокеры

### B1. Первая же миграция навсегда ломает sessionless `/mcp` (проверено)

`ProfileNotificationService` регистрируется слушателем в `Activator.java:105` — **до** вызова
`mcpServer.registerTools()` в `Activator.java:116`, внутри которого и выполняется миграция
(`McpServer.java:189`). Поэтому переход `ABSENT → VALID` (пустой safe-`default` → мигрированный
allowlist) считается изменением поверхности `default`, и цепочка
`ProfileNotificationService.java:120-123` → `McpServer.setLegacySessionRequired(true)` срабатывает
**при каждом первом запуске после обновления**.

Флаг односторонний: единственное присвоение `true` — в этой строке, `false` в production не ставит
никто, `McpServer.start()` его не сбрасывает. После этого `McpHttpHandler.java:524-533` требует
`MCP-Session-Id` на legacy-пути, и клиент, который его не присылает, получает HTTP 400 с текстом
«Session required after the default profile changed» — хотя пользователь ничего не менял.

Нарушены спецификация §6.4 («старый `/mcp` в течение периода совместимости продолжает принимать
клиентов без поддержки сессий») и §10 («существующим клиентам `/mcp` не требуется менять
конфигурацию»). Срабатывает на 100% обновлений и чистых установок.

### B2. Профиль и сессия никогда не попадают в историю (проверено)

`McpProtocolHandler.processRequest` в блоке `finally` вызывает `McpCallHistory.clearRequestMeta()`
(`:138`) **раньше**, чем `recordToHistory(...)` (`:151`). А `McpCallHistory.record(...)` пятиаргументной
перегрузки читает ровно этот `ThreadLocal` (`McpCallHistory.java:174`) и при `null` пишет запись с
четырьмя `null` (`:177`).

Следствие: `requestedProfileId`, `effectiveProfileId`, `fallbackReason` и `sessionId` пусты **во всех**
записях — в UI-истории, в JSONL-логе, для всех методов и для обоих путей (HTTP и bridge). Требование
спецификации §8.5 и плана §12 фактически не реализовано, хотя вся проводка написана.

Тесты этого не ловят, потому что все три релевантных теста конструируют запись руками или сами
привязывают `ThreadLocal`, минуя choke point. Правка — на одну строку (перенести `clearRequestMeta()`
после блока записи или передавать контекст явно в девятиаргументную перегрузку, которая сейчас в
production мертва).

### B3. `notifications/tools/list_changed` не отправляется — ни плагином, ни proxy (проверено)

Класс называется `ProfileNotificationService`, но не уведомляет: при изменении поверхности он вызывает
только `kick(path, registry)` (`ProfileNotificationService.java:114`), то есть
`McpSessionRegistry.closeByRequestedPath` + `SseStreamRegistry.closeByRequestedPath`. Единственный
production-вызов `notifyToolsListChanged()` остался в `EnableToolsetTool.java:142` — это старый
progressive disclosure.

В proxy — зеркально: `SseNotificationHub.onBackendListChanged` (`:79-101`) инвалидирует кеш, закрывает
клиентские сессии и рвёт SSE-потоки, а `publishListChanged` (`:130`) — **мёртвый код**, вызовов нет ни
в main, ни в тестах.

Расхождение затрагивает спецификацию §6.1.9, §8.3, §13 (unit-требование и e2e-сценарий 12), §14 и
весь этап 6 плана, а также пункт 8 fail-closed правила §2. Дополнительно сервер объявляет
`tools.listChanged = true` (`InitializeResult.java:86`), то есть обещает клиенту механизм, который для
профильных изменений не используется никогда.

**Отдельно отмечу способ, которым это расхождение было закрыто.** Строка 3 спецификации переписана на
«Apply сбрасывает только сессии затронутого URL; live `tools/list_changed` без reconnect не
используется», при этом тело спецификации (§6.1.9, §8.3, §13, §14) и план оставлены без изменений.
То есть утверждённый документ приведён в соответствие с кодом задним числом и только в шапке,
а требования внутри него теперь противоречат его же статусу. Это решение стоит либо провести
явно и целиком (с правкой §8.3, §13, §14, этапа 6 плана и снятием `listChanged: true`), либо
откатить и реализовать уведомления.

### B4. Proxy: маршрутизация fail-**open** при недоступной резолюции (проверено)

`ProjectRouter.routeUnscoped` (`:241-252`): если `group.getDonor() == null`, но живые backend'ы есть,
запрос уходит на `live.get(0)` в обход группы совместимости. То же в fan-out
(`McpProxyHandler.java:593`: `group.getCompatible().isEmpty() ? registry.live() : ...`), и проверка
«проект несовместимого backend не маршрутизируется» тоже отключается при пустом donor
(`ProjectRouter.java:222-228`).

Состояние достижимо любым таймаутом или `IOException` при `refreshResolution`. Нарушен критерий
выхода §14 плана: «proxy никогда не рекламирует surface, которую может направить на несовместимый
backend».

### B5. Proxy: `plainTextMode` на backend превращает резолюцию в fail-open (проверено)

`BackendProfileChannel.structuredOrResult` (`:432-442`) при отсутствии `structuredContent` возвращает
сам `result`. При включённом на backend `plainTextMode` (штатная настройка EDT) там лежит только
`content`, поэтому в `parseResolution` (`:313-353`) ветка `status != null` отрабатывает, но
`activeProfile` не находится: `effective` остаётся равным запрошенному id, `fallback` — `null`.

Proxy решает, что запрошенный профиль существует и fallback не применялся. Последствия:
`serverInfo.name` называет несуществующий профиль, предупреждение в `instructions` не добавляется,
fallback-поверхность кешируется как легитимная, а `availableProfiles` подставляет один `default`.
Это молчаливый fallback, прямо запрещённый спецификацией §6.2.

Рядом — `McpProxyHandler.java:657-670`: при `structured == null` весь payload donor'а уходит клиенту
как есть, **без** перезаписи endpoint'ов на URL proxy и **без** фильтрации `availableProfiles`, то есть
наружу утекают backend-URL'ы и профили, не согласованные между backend'ами (нарушен пункт 6 правила §2).

### B6. `Math.toIntExact` в кодеке может сорвать активацию бандла (проверено)

`ToolProfileCodec.requireInt` (`:196`) бросает непроверяемое `ArithmeticException`, а
`PreferenceToolProfileRepository.reloadFromStore` ловит только `UnsupportedToolProfileSchemaException`
(`:66`) и `ToolProfileDocumentException` (`:73`). Исключение уходит из конструктора, вызываемого в
`Activator.java:94`, — бандл не стартует, MCP-сервера нет вообще. Ровно тот класс порчи, который весь
MALFORMED-механизм обязан переживать.

Сегодня для этого нужен `schemaVersion` вне диапазона int, то есть ручная правка. Но следующая же
запланированная доработка (§20 плана — Import профилей из файла) заведёт в этот кодек произвольный
пользовательский JSON, и тогда путь становится штатным. Достаточно ловить `RuntimeException` и
переводить в MALFORMED.

## 4. Что реализовано хорошо

Чтобы оценка была сбалансированной — вот что проверено и держит нагрузку:

- **URL resolver строгий и корректный.** `McpEndpointResolver` даёт HTTP 400 без fallback на всех
  кейсах спецификации: пустой id, лишний сегмент, uppercase, Unicode, `%2F`, `.`/`..`, `%2e%2e`,
  двойное кодирование, `/mcp/`, `//`, `\`. Одинарное percent-кодирование принимается и канонизируется,
  так что сессии и потоки не расщепляются по алиасам. Канонический URL строится из конфигурации
  сервера, недоверенный `Host` не читается нигде.
- **Обхода авторизации нет.** Профиль определяется только путём URL, пересчитывается из актуального
  snapshot на каждый запрос, в сессии не кешируется. Заголовок, тело, аргумент инструмента и состояние
  сессии на него не влияют. `tools/list`, `resources/list`, `resources/read`, `tools/call` и bridge
  проходят через единый `ProfileToolPolicy`.
- **Запрещённый `tools/call` возвращает правильный конверт** — успешный JSON-RPC с `isError: true`,
  текст называет профиль и место настройки (`ProfileToolPolicy.java:109-120`). Прошлое замечание
  закрыто и покрыто тестом.
- **Хранилище корректно.** Snapshot действительно неизменяем и публикуется одной `AtomicReference`;
  оптимистичный `replaceAll` защищён общим write-lock, TOCTOU нет; семантический no-op не поднимает
  ревизию и не будит слушателей; повреждённый JSON сохраняется и бэкапится, не перезаписываясь;
  будущая schemaVersion отличается от malformed.
- **Миграция выполняется строго после полной регистрации каталога** (`McpServer.java:188-189`) и
  идемпотентна; пустой каталог отклоняется. Урезанного `default` не будет.
- **Изоляция параллельных вызовов сделана честно.** `ActiveToolCallRegistry` индексирован по callId,
  сигнал прерывания принадлежит своему вызову и не потребляется соседним — покрыто конкурентным тестом.
- **`ProfileToolPolicy` — маленький, читаемый, без Eclipse-зависимостей**, с обязательным
  `get_server_status` и снятием `enable_toolset` на явных endpoint'ах.
- **Прошлый дефект proxy с `fallbackApplied` исправлен** и закрыт тремя unit-тестами
  (`BackendProfileChannelTest`), включая совместимость со старой корневой формой.
- **Контракт `get_server_status` через proxy больше не подменяется**: аргументы клиента проходят
  насквозь, `displayName`/`description`/`revision`/`allowedToolCount` сохраняются, переписывается
  только `endpoint`.

## 5. Существенные замечания

| # | Замечание | Где | Статус проверки |
|---|---|---|---|
| M1 | **Status bar показывает чужой профиль.** Имя инструмента берётся из конкретного `ActiveToolCall`, а профиль — из глобальных `volatile`-полей, которые перезаписывает любой следующий запрос. При двух агентах ярлык может назвать профиль соседа. Это ровно тот сценарий, ради которого делалась фича. | `McpServer.java:350-359` | проверено |
| M2 | **`get_tool_guide` проверяет профиль только на явных endpoint'ах.** На `/mcp` guide инструмента вне allowlist выдаётся полностью, тогда как `resources/read` закрыт безусловно. План §12.5 исключений не предусматривает. | `GetToolGuideTool.java:103-112` | проверено |
| M3 | **Граница Workmate закрыта текстом промпта, а не технически.** Профиль передаётся строкой «Always pass profile '<id>' as the third argument» агенту, исполняющему произвольный JShell; ничто не мешает вызвать двухаргументную перегрузку. Узкий профиль дотягивается до поверхности `default`. Спецификация §8.4 такое поведение разрешает, план §12.4 прямо называет его тем, что надо предотвратить — документы противоречат друг другу, **нужно ваше решение**. | `AskWorkmateTool.java:381-394`, `EdtMcpBridge.java:115-119` | проверено |
| M4 | **Схема `availableProfiles` объявлена объектом, а отдаётся массивом** — и неверная схема уже вморожена в golden (`{"type": "object"}`) последним коммитом ветки. Ратчет теперь защищает дефект вместо того, чтобы его ловить. Готовый билдер `objectArrayProperty` существует. | `GetServerStatusTool.java:150`, `tests/e2e/tools_list.golden.json` | проверено |
| M5 | **Proxy разбирает декодированный путь, плагин — raw.** `getPath()` против `getRawPath()`. `/mcp/profiles/%2570rod` плагин отвергает как двойное кодирование, proxy принимает. Требование «те же правила, что plugin» не выполнено. Ни один IT это не поймает: `FakeBackend` использует тот же резолвер и тот же `getPath()`. | `McpProxyHandler.java:167` против `McpHttpHandler.java:632` | проверено |
| M6 | **Legacy-фасад молча теряет изменения при конфликте ревизии**: `CONFLICT`/`REJECTED` не обрабатываются и не логируются, переключение чекбокса просто ничего не делает. | `ToolSettingsService.java:594-599` | со слов ревьюера |
| M7 | **Отказ миграции полностью нем.** `ReplaceResult` отбрасывается; при MALFORMED-документе или конфликте сервер стартует с пустым `default` (только `get_server_status`) без единой строки в логе. | `ToolProfileMigration.java:33-43`, `McpServer.java:189` | со слов ревьюера |
| M8 | **Дубли profile ID молча схлопываются.** `ToolProfileSnapshot.of` складывает профили в `TreeMap`, а валидатор проверяет уникальность уже по этой карте — проверка недостижима, мёртвый код. Важно именно для будущего Import (§20). | `ToolProfileValidator.java:76-82` | со слов ревьюера, структурно достоверно |
| M9 | **Downgrade-зеркало считается по статическому `ToolGroup`, а не по живому реестру**, тогда как миграция использует реестр. Два разных определения «каталога» в одном инварианте. | `ToolSettingsService.java:602-605` | со слов ревьюера |
| M10 | **Утечки в proxy:** `stopNotificationListener()` не вызывается нигде, слушатели переживают остановку и реконнектятся каждые 2 с; каналы, карты и порты ушедших backend'ов не вычищаются; у клиентских сессий нет TTL (только кап 10 000, после исчерпания `initialize` отвечает ошибкой навсегда). | `BackendProfileChannel.java:153-161`, `SessionManager.java` | со слов ревьюера |
| M11 | **Фикстура e2e-профилей дублирует каталог вручную и ничем не заратчена.** `default` в `e2e_tool_profiles.json` — это 88 имён, выписанных руками; соответствие golden держится только на комментарии в хэндоффе («сейчас 88 = 88»). Добавление нового инструмента тихо сузит `default` в CI, и упадёт golden-тест с непонятным сообщением, а e2e нового инструмента — с «profile deny». | `tests/e2e/fixtures/e2e_tool_profiles.json` | проверено |

## 6. Качество кода

Общее впечатление — **выше среднего по репозиторию**. Новые классы (`ProfileResolver`,
`ProfileToolPolicy`, `McpEndpointResolver`, `McpSessionRegistry`, `ToolProfilesEditorModel`,
`ProfileEndpoint`) маленькие, с одной ответственностью, тестируемые headless, без Eclipse- и
SWT-зависимостей там, где это обещано. Ошибки везде идут через `ToolResult.error(...).toJson()` и
actionable: называют конкретное нарушение, профиль и место настройки. Правило English-only на
клиентской поверхности соблюдено, русский — только в `messages_ru.properties`, паритет бандлов
заратчен. God-классы (`McpProtocolHandler`, `McpHttpHandler`) новый долг не набрали, но и не
уменьшились.

Замечания:

- **Класс `ProfileNotificationService` называется не тем, что делает** — он не уведомляет, а разрывает
  соединения. Имя вводит в заблуждение при чтении.
- **Цикл пакетов `profiles ↔ preferences`** вопреки плану §3: `ToolProfileMigration` импортирует
  `preferences.ToolSettingsService`, а тот — пять типов из `profiles`. Плюс
  `PreferenceToolProfileRepository` знает ключ Eclipse Preferences напрямую, а
  `ProfileNotificationService` тянет `McpServer`, `SseStreamRegistry` и четыре класса `transport`.
  Слой `profiles` не самодостаточен.
- **`effectiveToolNames()` теряет заявленную детерминированность**: `Set.copyOf(TreeSet)` даёт
  `ImmutableCollections.SetN` с рандомизированным порядком обхода. Спасает только то, что оба
  потребителя пересортировывают.
- **Стоимость авторизации**: `policyOf` пересобирает `TreeMap` всего каталога на каждый
  `isCallable`/`deniedMessage`, а `isCallable` заново считает `effectiveToolNames()`. На один
  `tools/call` — несколько полных сортировок каталога.
- **Мёртвый код и compatibility-обёртки**, которые план §16 требует убирать отдельным коммитом:
  `allowlistFromDisabled`, `hasPersistedDocument`, `withDocumentRevision`, пять геттеров
  `ToolProfileChangeSet`, `SseStreamRegistry.broadcast` (опасен — рассылает во все endpoint'ы),
  `SseNotificationHub.publishListChanged`, `BackendProfileChannel.stopNotificationListener`,
  `Backend.forward`, тестовые перегрузки `register(...)`, `McpServer.setCurrentToolName` (пустой
  метод, который всё ещё вызывается).
- **Гонки и неатомарность в proxy**: `groupFor` использует `get`+`put` вместо `computeIfAbsent`
  (N параллельных запросов на холодном кеше делают N полных резолюций, каждая — handshake на все
  backend'ы с таймаутом 10 с прямо на HTTP-потоке); `ensureNotificationListener` — проверка-и-действие
  без синхронизации; запись в клиентский SSE-поток идёт без того `synchronized (out)`, которым защищён
  heartbeat.
- **Устаревшие javadoc** в `Activator.java:69,250` («Runtime still uses `ToolSettingsService` until
  migration wires this in») и `PreferenceConstants.java:116-118`.
- **Мелочи UI**: неудачный Duplicate молча применяет описание к исходному профилю
  (`ToolsTab.java:320-328`); Restore Defaults вдобавок к выбранному профилю сбрасывает глобальные
  значения параметров и allow-set деструктивных операций, хотя диалог обещает «only the selected
  profile»; настройки progressive disclosure в UI нет вообще, при том что `enable_toolset` и
  `list_toolsets` отправляют агента «enable it in EDT Preferences» — к чекбоксу, которого нет.
- **`docs/tools/get_server_status.md` устарел** — всё ещё «No parameters», без `activeProfile` и
  `availableProfiles`. Это генерируемый артефакт, нужен перегон `docs/generate_tool_docs.py` на стенде.
  Bundled guide и README обновлены корректно.

## 7. Качество тестов

Тесты в основном настоящие, а не декоративные: `EdtMcpBridgeTest` сравнивает ответ bridge байт-в-байт
с ответом обычного handler'а и доказывает, что устаревший список не даёт вызвать запрещённый
инструмент; `McpHttpProfileIntegrationTest` поднимает реальный HTTP и проверяет 400/404/DELETE/SSE;
`McpSessionRegistryTest` управляет часами через инжектированный `LongSupplier`; `ActiveToolCallRegistryTest`
включает конкурентный сценарий; `MessagesParityTest` ратчетит байтовый контракт русского бандла.

Системная слабость одна и она объясняет большинство блокеров: **тестируются компоненты, а не швы**.
Конкретные пробелы:

1. **`InterruptibleToolExecutor` не покрыт ни одним тестом**, хотя план §8 прямо требует «context
   доходит в interruptible worker».
2. **~~Нет теста на проводку метаданных через choke point~~** — закрыто `processRequestRecordsProfileAndSessionFromContext` (см. §11). До этого все три теста истории конструировали запись руками.
3. **Нет ни одного HTTP-теста с реально настроенным профилем**: в `McpHttpProfileIntegrationTest` нет
   `Activator`, поэтому все кейсы — это fallback `UNKNOWN_PROFILE`. По проводу не проверены разные
   `tools/list` у двух профилей, `DISABLED_PROFILE` и `/mcp/profiles/default` против `/mcp`.
4. **Вся ветка progressive disclosure не покрыта**: `publishedTools(true)` не вызывается ни разу.
5. **`FakeBackend` не воспроизводит production-JSON целиком**: нет `warning`, нет `allowedTools`, нет
   корневых диагностических полей, `availableProfiles` возвращается всегда (а не только при
   `includeProfiles`). Режим `plainTextMode` добавлен (`setPlainTextMode`) — B5 больше не невидим.
   Критерий «direct и proxy contracts отличаются только router tools» по-прежнему не проверяется.
6. **Круговая зависимость теста от проверяемого кода**: `FakeBackend` парсит путь тем же
   `ProfileEndpointResolver` и тем же `getPath()`, поэтому расхождение M5 принципиально не ловится.
7. **`ProfileRoutingIT.testListChangedInvalidatesOnlyThatProfileAndDedupes` не проверяет то, что
   заявляет**: инвалидатор подменён счётчиком, поэтому реальная инвалидация не выполняется;
   `assertEquals(2, delivered.get())` фиксирует **отсутствие** дедупликации при названии «dedupes».
8. **Конкурентный тест репозитория вакуумный**: проверяет `getDefault() != null && !asMap().isEmpty()`,
   что при `AtomicReference` не может провалиться; писатель всего один, поэтому lost update не ловится.
9. **Legacy-миграции v1–v4 не выполняются** ни в одном тесте миграции — все три заранее выставляют
   версию миграции в текущую. Требование плана §6 не проверено.
10. **Нет `ToolProfileChangeSetTest`**; в proxy закрыт `donor == null` (два теста в
    `ProjectRouterTest`, шов `putGroupForTest`). По-прежнему не покрыты `DISABLED_PROFILE`,
    hotplug на профильном endpoint'е, TTL сессий; UI (`ToolsTab`, `ToolProfileDialog`) не покрыт вовсе.
11. **Скрытая зависимость от порядка тестов**: `ToolContractConsistencyTest` и `GuideCoverageTest`
    вызывают `new McpServer().registerTools()`, который теперь запускает реальную миграцию против
    живого репозитория и пишет профильный документ в preference store тестового воркспейса.

## 8. Гигиена и посторонние изменения

- **В git закоммичены 1101 артефакт сборки.** Каталог
  `mcp/bundles/com.ditrix.edt.mcp.server/` целиком состоит из `target/**` — скомпилированные `.class`
  старого пакета плюс `MANIFEST.MF`. Модулем он не является (в `mcp/bundles/pom.xml` только
  `fm.giper.edt.mcp.server`). Попали коммитом `2c54f23` «Cursor: Apply local changes for cloud agent»,
  потому что `.gitignore` перечисляет `target` жёсткими путями под новым именем пакета. Это прямое
  нарушение гейта «чистое дерево» и его надо снести отдельным коммитом, добавив в `.gitignore` общий
  паттерн.
- **В ветку смешаны изменения, не относящиеся к multi-profile**: ребрендинг форка (DitriX → ExpSPB,
  NOTICE, LICENSE, badges, update-site URL), подъём версии `1.0.0 → 1.0.3`, перевод тестовых фикстур с
  платформы 8.5.1 на 8.3.27 с вырезанием 8.5-специфичного form XML, и `UpdateChecker.SUPPRESS_UPDATE_CHECKS = true`
  (`:58`) — временный локальный хак, который в таком виде уедет в релиз.
- **`multi-profile-implementation-plan.md` всё ещё утверждает «Исходный код не изменён, реализация не
  начата»** (строка 3), хотя реализация закончена. Спецификация обновлена, план — нет.
- **PR #3 смержен раньше live-гейта**, который хэндофф объявляет обязательным условием снятия статуса
  draft. Комментарии и треды PR я проверить не смог — в окружении нет `gh`.

## 9. Рекомендуемый порядок работ

**До возврата к стенду** (всё локально, каждая правка мелкая):

1. **B2** — перенести `clearRequestMeta()` после блока записи (или писать из `McpRequestContext`).
   Красный тест уже есть: `processRequestRecordsProfileAndSessionFromContext`.
2. **B1** — либо не считать первичную миграцию изменением поверхности, либо сбрасывать
   `legacySessionRequired` в `McpServer.start()`. На локальной `test` (`3221855`) защёлка уже
   снята; стопорные тесты зелёные (см. §11).
3. **B6** — ловить `RuntimeException` в `reloadFromStore` / не выпускать `ArithmeticException` из
   `decode`. Красные тесты: `schemaVersionOutsideIntRangeIsMalformedNotUnchecked` и
   `schemaVersionOutsideIntRangeIsMalformedAndDoesNotAbortLoad`.
4. **B4** — при `donor == null` и непустом `live` отказывать, а не брать `live.get(0)`. Красные
   тесты: `testUnscopedCallFailsClosedWhenLiveBackendsHaveNoDonor`,
   `testScopedCallFailsClosedWhenOwnerIsOutsideUnresolvedGroup`.
5. **B5** — считать резолюцию без `activeProfile` неуспешной; переписывать endpoint'ы и в
   текстовом канале. Красные тесты: `parseResolutionDoesNotConfirmRequestedIdWhenStatusHasNoActiveProfile`,
   `testPlainTextStatusDoesNotConfirmMissingProfileOrLeakBackendUrls`; `FakeBackend.setPlainTextMode` есть.
6. **M4** — исправить `objectProperty` → `objectArrayProperty` и **перегенерировать golden** (сейчас в
   нём зафиксирована ошибка).
7. **M5** — передавать `getRawPath()` в proxy; завести общий набор тест-векторов для обоих резолверов.
8. **M1, M2** — профиль в `ActiveToolCall`; снять условие `isExplicitEndpoint()` в `GetToolGuideTool`.
9. **M6, M7** — не глотать `CONFLICT` и отказ миграции молча.
10. **Гигиена** — удалить `mcp/bundles/com.ditrix.edt.mcp.server/`, поправить `.gitignore`, обновить
    статус плана, решить судьбу `SUPPRESS_UPDATE_CHECKS`.

**Требуют вашего решения, а не правки:**

- **B3** — уведомления против разрыва сессий. Либо реализовать `list_changed`, либо провести правку
  спецификации целиком и явно (§8.3, §13, §14, этап 6 плана, `listChanged: true`), а не одной строкой
  в шапке.
- **M3** — граница Workmate. Варианты: выдавать per-call объект, привязанный к профилю вызывающего;
  либо признать риск и задокументировать, что `ask_workmate` эквивалентен выдаче поверхности `default`
  (тогда узкие профили его не включают), плюс тест, фиксирующий это поведение.

**Затем** — весь live-контур по `edt-mcp-ready-to-deploy` и §4 хэндоффа: сид профилей, редеплой,
anti-stale, ручной smoke, полный e2e с поднятым proxy, пять URL conformance, разбор `.metadata/.log`.

## 10. Итоговая оценка

**Полнота:** архитектурно фича реализована целиком — все одиннадцать этапов имеют код, и ключевые
решения плана (профиль только из URL, единая точка авторизации, неизменяемый snapshot, path-bound
сессии, fail-closed группы в proxy) воплощены именно так, как были утверждены. Не реализован один
этап целиком (адресные уведомления, этап 6) и не пройдено пять из семи ступеней финального гейта.

**Качество кода:** хорошее. Новые классы аккуратные и тестируемые, разделение каталога и политики
выполнено чище, чем требовал план. Основной долг — цикл пакетов `profiles ↔ preferences`, накопленные
compatibility-обёртки и мёртвый код, а также несколько гонок и утечек в proxy.

**Главный риск:** не отдельные дефекты, а то, что **все шесть блокеров лежат в швах между
компонентами** — порядок вызовов в `finally`, порядок регистрации слушателя относительно миграции,
поведение при нештатном ответе backend'а, `getPath()` против `getRawPath()`. Компонентные тесты
зелёные (5938 + 199), и именно поэтому они ничего из этого не показали. Пока не пройден живой контур,
зелёная сборка **не является** свидетельством работоспособности фичи, а один из блокеров (B1)
срабатывает у каждого пользователя при первом же обновлении.

## 11. Дополнение: тесты на швы (31 августа 2026)

После ревью на локальной `test` добавлен коммит `2da4380` — тесты **до правок**, чтобы фикс
нельзя было закрыть зелёной компонентной сборкой. B3 и M3 не покрывались: это решения продукта,
а не один правильный assert.

На этой ветке картина кода уже разошлась с срезом ревью `29d8bf2`: защёлка
`legacySessionRequired` (B1) снята, `clearRequestMeta()` из `processRequest` убран, но история
всё ещё читает `ThreadLocal`, который HTTP-путь не биндит. Поэтому часть тестов — **красные
контракты**, часть — **зелёные стопоры** против возврата B1.

| Шов | Класс / метод | Контракт | Ожидание на `2da4380` |
|---|---|---|---|
| **B2** | `McpProtocolHandlerTest.processRequestRecordsProfileAndSessionFromContext` | `processRequest(body, context)` без предварительного `bindRequestMeta` пишет в singleton-историю `requested=review`, `effective=review`, `sessionId=sess-review` | **красный** — `recordToHistory` берёт пустой ThreadLocal |
| **B4** | `ProjectRouterTest.testUnscopedCallFailsClosedWhenLiveBackendsHaveNoDonor` | live backend есть, `donor == null` → `RouteResult.ERROR`, не `live.get(0)` | **красный** |
| **B4** | `ProjectRouterTest.testScopedCallFailsClosedWhenOwnerIsOutsideUnresolvedGroup` | проект на live backend вне нерешённой группы → `ERROR`, не маршрут к owner | **красный** |
| **B5** | `BackendProfileChannelTest.parseResolutionDoesNotConfirmRequestedIdWhenStatusHasNoActiveProfile` | text-only `result` без `activeProfile` не выглядит как точное попадание в запрошенный id | **красный** |
| **B5** | `BackendProfileChannelTest.parseResolutionReadsFallbackFromActiveProfile` | `fallbackApplied` / `fallbackReason` читаются из `activeProfile`, не только с корня | **красный** на этой ветке (на `origin/test` уже зелёный) |
| **B5** | `BackendProfileChannelTest.parseResolutionIgnoresRootFallbackWhenActiveProfileSaysNone` | корневой `fallbackApplied=true` не перебивает `activeProfile.fallbackApplied=false` | **красный** на этой ветке |
| **B5** | `BackendProfileChannelTest.parseResolutionFallsBackToRootForOldBackends` | старый корневой формат без вложенного флага всё ещё читается | зелёный (корневой путь и так работает) |
| **B5** | `ProfileRoutingIT.testPlainTextStatusDoesNotConfirmMissingProfileOrLeakBackendUrls` | unknown-профиль + `plainTextMode`: в `initialize` есть `UNKNOWN_PROFILE`; в `get_server_status` нет `http://127.0.0.1:<backendPort>` | **красный** (пустые `instructions`, URL не переписываются) |
| **B6** | `ToolProfileCodecTest.schemaVersionOutsideIntRangeIsMalformedNotUnchecked` | `schemaVersion = Long.MAX_VALUE` → `MalformedToolProfileDocumentException`, не `ArithmeticException` | **красный** |
| **B6** | `PreferenceToolProfileRepositoryTest.schemaVersionOutsideIntRangeIsMalformedAndDoesNotAbortLoad` | тот же JSON в store: конструктор жив, `MALFORMED`, документ не затёрт | **красный** (сейчас исключение срывает конструктор) |
| **B1** (стопор) | `McpHttpProfileIntegrationTest.legacyMcpStaysSessionless…` — повторный sessionless ping | `/mcp` без `MCP-Session-Id` остаётся 200 после «первого persist» default | **зелёный** на этой ветке |
| **B1** (стопор) | `ProfileNotificationServiceTest.firstDefaultPersistDoesNotDropLegacyStream` | `ABSENT/safe → мигрированный default` не снимает SSE на `/mcp` | **зелёный** на этой ветке |

Что пришлось добавить, чтобы красные тесты вообще могли существовать (не фикс блокеров):

- `BackendRegistry.putGroupForTest` — иначе `installStateForTest` сам ставит `donor = live.get(0)` и B4 не воспроизводится;
- `FakeBackend.setPlainTextMode` и полные `http://127.0.0.1:<port>…` в `availableProfiles[].endpoint` — иначе B5 не отличить от happy path.

Прокси прогнан после добавления: 6 падений, 0 ошибок компиляции (`BackendProfileChannelTest` 3, `ProjectRouterTest` 2, `ProfileRoutingIT` 1). Плагинный Tycho на этих новых методах в том же прогоне не поднимался (нет target platform в урезанном reactor).

Не писались сознательно: B3 (`list_changed` vs kick), M3 (граница Workmate), M5 (`getRawPath` — круговая зависимость фейка от резолвера).
