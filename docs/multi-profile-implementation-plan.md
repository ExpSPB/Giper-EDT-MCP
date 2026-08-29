# План реализации многопрофильных наборов MCP

Статус: утверждено пользователем 29 августа 2026 года. Исходный код не изменён, реализация не начата.

Основание: утверждённая спецификация
[`multi-profile-tool-surfaces.md`](multi-profile-tool-surfaces.md).

## 1. Цель плана

Реализовать несколько одновременно доступных профилей инструментов в одном EDT-MCP без копирования
каталога инструментов, без перезапуска сервера при изменении профиля и без возможности обойти
allowlist прямым вызовом, ресурсом, Workmate bridge или proxy.

План разбит на изолированные merge-gates. Каждый этап должен оставлять сборку и уже существующие
тесты зелёными. Переход к следующему этапу выполняется только после прохождения exit criteria
предыдущего.

## 2. Архитектурный gate для proxy

Перед началом этапа proxy нужно утвердить обнаруженное при детализации правило. Один proxy объединяет
несколько EDT, где профиль с одним ID может иметь разные allowlist, быть отключённым либо существовать
только на части backend'ов. Существующий выбор одного donor для `tools/list` может рекламировать
инструмент, который целевой EDT затем запретит.

Рекомендуемое fail-closed правило, принимаемое вместе с этим планом:

1. Для каждого запрошенного пути proxy получает с каждого live backend эффективный profile ID,
   fallback reason и канонический fingerprint опубликованных tool descriptors.
2. Локальные номера revision напрямую не сравниваются: одинаковая поверхность может иметь разные
   счётчики в разных EDT.
3. Детерминированный donor — совместимый backend с минимальным портом. В профильную routing group
   входят только backend'ы с теми же effective ID, fallback state и fingerprint.
4. Проект несовместимого backend не маршрутизируется через этот endpoint. Ошибка и `router_status`
   объясняют конкретное несовпадение.
5. Смешанное состояние «профиль существует на части backend'ов, на остальных сработал fallback» не
   считается общей успешной поверхностью.
6. `availableProfiles` через proxy содержит только профили, совместимые на всех текущих live
   backend'ах. Endpoint в ответе переписывается на URL proxy.
7. Если backend'ов нет, proxy публикует только `router_status` и `router_refresh`, а initialize
   сообщает, что существование профиля ещё не подтверждено.
8. Изменение состава или fingerprint группы атомарно инвалидирует кеш и создаёт
   `notifications/tools/list_changed` только для затронутого endpoint.

Это правило предпочтительнее пересечения allowlist: пересечение скрыто меняло бы роль при каждом
подключении EDT и не давало бы правдивого единственного `activeProfile`.

## 3. Целевая декомпозиция

| Область | Новые основные сущности | Ответственность |
|---|---|---|
| `profiles` | `ToolProfile`, `ToolProfileSnapshot`, `ToolProfileRepository`, `PreferenceToolProfileRepository`, `ToolProfileCodec`, `ToolProfileValidator`, `ToolProfileMigration` | Неизменяемая модель, JSON, миграция, атомарная публикация. |
| `profiles` | `ProfileResolution`, `ProfileResolver`, `ToolProfileChangeSet` | Requested/effective profile, fallback и diff snapshot'ов. |
| `protocol` | `McpRequestContext`, `ProfileToolPolicy` | Явный контекст запроса и единая авторизация списков, ресурсов и вызовов. |
| `transport` | `McpEndpointResolver`, `McpTransportSession`, `McpSessionRegistry` | Строгий разбор URL, path-bound sessions, TTL и DELETE. |
| transport/runtime | `ProfileNotificationService` | Адресные list-changed уведомления при изменении эффективной поверхности. |
| runtime | `ActiveToolCallRegistry` | Изоляция параллельных вызовов, сигналов прерывания и отображаемого статуса. |
| preferences | `ToolProfilesEditorModel`, `ToolProfileDialog` | Тестируемая без SWT модель CRUD и UI-диалоги. |
| proxy | `ProfileEndpoint`, `BackendProfileChannel`, профильный registry snapshot | Раздельные backend sessions, кеши и routing groups по профилю. |

Направление зависимостей:

```text
preferences/storage -> profiles model
transport -> endpoint resolver + session registry + request context
protocol -> request context + profile policy
profile policy -> repository snapshot + McpToolRegistry
McpToolRegistry -> ни preferences, ни repository
proxy -> только публичный wire-контракт backend
```

## 4. Этап 0. Подготовка ветки и baseline

### Изменения

1. Перед первым изменением исходников создать ветку `codex/multi-profile-tool-surfaces` от текущего
   `master`; существующие пользовательские изменения не включать автоматически.
2. Зафиксировать утверждённую спецификацию и этот план отдельным documentation commit.
3. Запустить исходный baseline:
   - `bash source/compile.sh`;
   - `mvn -f proxy/pom.xml clean verify`;
   - текущий conformance для `/mcp`;
   - краткий live smoke при доступном тестовом EDT.
4. Добавить characterization-тесты текущего `/mcp`: initialize, tools/resources list, call,
   progressive disclosure, bridge, session header и SSE framing. Они должны отделить намеренное
   изменение контракта от регрессии.

### Критерии выхода

- известен исходный набор проходящих и ожидаемо падающих проверок;
- новые characterization-тесты не меняют production behavior;
- рабочая ветка содержит только документацию и тестовый baseline.

## 5. Этап 1. Модель, codec и repository профилей

### Новые классы

Пакет `com.ditrix.edt.mcp.server.profiles`:

- `ToolProfile` — immutable value object: `id`, `displayName`, `description`, `enabled`,
  `allowedTools`, `revision`;
- `ToolProfileSnapshot` — `schemaVersion`, `documentRevision`, отсортированная immutable map и
  обязательный `default`;
- `ToolProfileRepository` — чтение snapshot, optimistic `replaceAll(expectedRevision, ...)` и
  listeners;
- `PreferenceToolProfileRepository` — один JSON-ключ Eclipse Preferences и одна
  `AtomicReference<ToolProfileSnapshot>`;
- `ToolProfileCodec` — строгий parser и детерминированная сериализация;
- `ToolProfileValidator` — ID, уникальность, ограничения `default`;
- `DefaultToolProfileFactory` — единственное создание заводского safe-default;
- `ToolProfileChangeSet` и listener — изменённые, включённые, отключённые и удалённые ID.

### Контракт хранения

1. Профили и `allowedTools` сортируются, дубликаты удаляются, неизвестные tool names сохраняются.
2. `get_server_status` добавляется runtime-политикой и не навязывается пользовательской allowlist.
3. Сохранение: validate → canonical encode → один `setValue` → atomic publish → listener.
4. Семантический no-op не повышает revision и не отправляет событие.
5. Изменённый профиль получает новый `documentRevision`; неизменённый сохраняет свою revision.
6. Конфликт `expectedRevision` не перезаписывает чужие изменения.
7. Повреждённый JSON не перезаписывается автоматически: runtime получает safe `default`, исходная
   строка сохраняется до осознанного сохранения из UI и перед этим копируется в backup preference.
8. Неизвестная будущая schema version отличается от malformed document и диагностируется отдельно.

### Затрагиваемые файлы

- `preferences/PreferenceConstants.java`;
- `preferences/PreferenceInitializer.java`;
- `Activator.java` — жизненный цикл и getter repository;
- `META-INF/MANIFEST.MF` только если новые package imports действительно потребуются.

### Тесты

- `ToolProfileTest`;
- `ToolProfileValidatorTest`;
- `ToolProfileCodecTest`;
- `PreferenceToolProfileRepositoryTest`;
- конкурентное чтение при `replaceAll`, optimistic conflict, no-op, malformed и unsupported schema.

### Критерии выхода

- repository тестируется без SWT и поднятия HTTP;
- читатель видит только целый старый или целый новый snapshot;
- production runtime пока продолжает использовать старую настройку инструментов.

## 6. Этап 2. Миграция и временная совместимость старых настроек

### Изменения

1. Инициализировать repository только после `McpServer.registerTools()`, когда известен полный
   каталог.
2. Если нового документа нет:
   - выполнить существующие миграции `ToolSettingsService`;
   - прочитать итоговый `PREF_DISABLED_TOOLS`;
   - вычислить `allowed = registered - disabled`;
   - создать, проверить и одним действием сохранить `default`.
3. Валидный новый документ делает повторную миграцию невозможной.
4. На один совместимый релиз оставить `ToolSettingsService` фасадом над `default` и зеркально
   обновлять legacy disabled-set при сохранении `default`.
5. Изменение legacy-ключа после миграции не должно обратно перезаписывать профили.
6. `ToolPreset` получает операции над allowlist относительно переданного каталога. `CUSTOM` остаётся
   вычисляемым состоянием UI, а не сохраняемым типом.

### Тесты

- точное сохранение старой поверхности;
- идемпотентность и порядок legacy migrations v1–v4;
- явно включённые ранее инструменты не отключаются повторно;
- новый зарегистрированный инструмент не появляется в существующем профиле;
- downgrade mirror и изменение legacy-ключа после миграции;
- `ToolPresetTest` для применения и сопоставления allowlist.

### Критерии выхода

- обновление и downgrade на один релиз имеют документированное поведение;
- migration не может выполниться до полной регистрации каталога;
- новый repository становится источником истины, legacy key — только compatibility mirror.

## 7. Этап 3. Отделение каталога от профильной политики

### Изменения

1. Оставить в `McpToolRegistry` только registration, atomic `replaceAll`, lookup, all-tools и revision
   каталога. Убрать runtime-зависимость от `ToolSettingsService`.
2. Ввести `ProfileResolution` и `ProfileResolver`. Один запрос разрешается по одному snapshot:
   unknown/disabled → `default`; malformed path до resolver не доходит.
3. Ввести `ProfileToolPolicy` как единственную точку вычисления:
   - published tools;
   - callable tools;
   - доступных guide resources;
   - обязательного `get_server_status`;
   - запрета `enable_toolset` на явных endpoint'ах;
   - legacy progressive disclosure только для `/mcp`.
4. Перевести policy consumers в `McpProtocolHandler`, `GetToolGuideTool` и bridge. Старые registry
   методы временно оставить deprecated wrappers для `default`, затем удалить после перевода всех
   потребителей.
5. Порядок шлюзов: profile policy → destructive consent → tool execution → output/PII processing.

### Тесты

- `ProfileResolverTest` и `ProfileToolPolicyTest`;
- list/call/resource/guide не обходят профиль;
- `get_server_status` всегда доступен;
- explicit profile не видит и не вызывает `enable_toolset`;
- `/mcp` сохраняет старое progressive-disclosure поведение;
- одинаковый snapshot и catalog revision дают детерминированный sorted result.

### Критерии выхода

- в production path нет скрытого чтения `ToolSettingsService` из registry;
- один policy используется для публикации и прямого вызова;
- изменение профиля не требует перерегистрации инструментов.

## 8. Этап 4. Явный `McpRequestContext` и protocol dispatch

### Изменения

1. Добавить immutable `McpRequestContext`: resolution, requested endpoint, protocol version,
   capabilities, session ID и legacy flag.
2. Добавить `McpProtocolHandler.processRequest(String, McpRequestContext)`; старую сигнатуру временно
   оставить как wrapper с synthetic `default` для binary/test compatibility.
3. Передать один и тот же context в initialize, list, resources, call, logging и history.
4. Удалить глобальный `AtomicReference<ClientCapabilities>` из `McpProtocolHandler`.
5. Расширить `IMcpTool` default context-aware методом, делегирующим существующему `execute(Map)`,
   чтобы не ломать внешние реализации. Переопределять его только инструментам, которым нужен context.
6. Передать context в worker `InterruptibleToolExecutor`; `ThreadLocal` не использовать.
7. `InitializeResult` получает profile-aware `serverInfo.name` и fallback warning в `instructions`.

### Тесты

- два handler context с разными capabilities и профилями не влияют друг на друга;
- context доходит в interruptible worker;
- старый wrapper даёт `default`;
- binary-compatible default method работает для всех существующих tools;
- initialize корректно сообщает requested/effective/fallback.

### Критерии выхода

- protocol handler не хранит состояние последнего клиента;
- каждый authorization decision получает явный context;
- существующие tool implementations не требуют массовой правки.

## 9. Этап 5. URL resolver и полноценные транспортные сессии

### Изменения

1. `McpEndpointResolver` разбирает только raw path:
   - `/mcp`;
   - `/mcp/profiles/<id>`.
2. Пустой ID, лишний сегмент, upper-case/Unicode вне контракта, broken encoding, encoded slash,
   `.`/`..`, traversal и двойная неоднозначная кодировка получают HTTP 400 до JSON-RPC dispatch.
3. `McpServer` сохраняет один prefix context `/mcp`; `McpHttpHandler` строго проверяет полный путь.
4. Канонические URL строятся из серверной конфигурации, не из недоверенного `Host`.
5. Добавить `McpSessionRegistry` с размерным лимитом, TTL и очисткой при shutdown.
6. Сессия создаётся только после успешного initialize и хранит requested path, protocol version,
   capabilities и last access. Effective profile пересчитывается на каждый запрос.
7. Последующий sessionful request требует ID и совпадение пути. Missing → 400; unknown/expired или
   другой путь → 404; DELETE освобождает session и streams.
8. Legacy sessionless clients разрешаются только на `/mcp` в пределах периода совместимости.

### Затрагиваемые классы

- `McpServer`;
- `transport/McpHttpHandler`;
- `transport/InterruptibleToolExecutor`;
- `protocol/InitializeResult` и transport constants.

### Тесты

- `McpEndpointResolverTest`;
- `McpSessionRegistryTest`;
- `McpHttpProfileIntegrationTest`;
- две session разных профилей, replay на другом пути, expiry, limit, DELETE и shutdown;
- unknown/disabled fallback и invalid-path no-fallback;
- JSON и SSE framing сохраняют профиль и session header.

### Критерии выхода

- session ID больше не является неотслеживаемым случайным заголовком;
- профиль невозможно выбрать телом, заголовком или session state;
- `/mcp` остаётся совместимым.

## 10. Этап 6. Адресные SSE-уведомления и горячее изменение профиля

### Изменения

1. `SseStreamRegistry` регистрирует stream с session и requested endpoint, а не в общем наборе.
2. Из нескольких GET streams одной session сообщение отправляется ровно в один; heartbeat и
   notification остаются сериализованными на stream lock.
3. `ProfileNotificationService` слушает repository и сравнивает старую/новую эффективную поверхность
   каждого активного requested path.
4. Уведомлять только при фактическом изменении tool descriptors:
   - изменение обычного профиля;
   - disable/delete → `default`;
   - create/enable ранее неизвестного профиля;
   - изменение `default`, включая подключённые fallback paths.
5. Авторизация нового snapshot действует до попытки доставки уведомления.
6. Fallback warning логируется на initialize и изменение resolution, а не на каждый poll.

### Тесты

- расширить `SseStreamRegistryTest`;
- одна доставка на session, изоляция endpoint'ов, очистка при DELETE/expiry;
- no-op profile update не уведомляет;
- disable/create/enable и изменение `default` уведомляют правильные пути;
- ошибка SSE не оставляет старое разрешение действующим.

### Критерии выхода

- изменение профиля не вызывает `McpServer.restart`;
- посторонние клиенты не получают уведомление;
- права меняются атомарно независимо от состояния SSE.

## 11. Этап 7. Самодиагностика и выбор профиля агентом

### Изменения `GetServerStatusTool`

1. Добавить параметры `includeProfiles` и `includeProfileTools` в input schema и реализацию.
2. Отклонять `includeProfileTools=true` без `includeProfiles=true` через `ToolResult.error`.
3. Формировать `activeProfile` из request context:
   `requestedProfileId`, effective `id`, display data, revision, effective endpoint/count,
   `fallbackApplied`, optional reason и warning.
4. `availableProfiles` включает только enabled profiles, сортируется по ID и по умолчанию не содержит
   полные allowlist.
5. При явном `includeProfileTools` отдавать отсортированные effective tools.
6. `allowedToolCount` всегда соответствует реально опубликованной поверхности, включая status tool.
7. Обновить output schema, guide/description и `serverInfo.instructions` fallback.

### Тесты

- расширить `GetServerStatusToolTest` и `McpProtocolHandlerTest`;
- existing, unknown и disabled profile;
- compact/full discovery, сортировка и invalid parameter combination;
- status не переключает context и не расширяет права;
- disabled profiles отсутствуют в `availableProfiles`.

### Критерии выхода

- агент однозначно отличает requested и effective profile;
- агент может выбрать URL другого enabled профиля, но текущая connection surface не меняется;
- fallback никогда не выдаётся за успешное подключение к запрошенному профилю.

## 12. Этап 8. Параллельные вызовы, история и in-process bridge

### Active calls

Текущие одиночные `activeToolCall`, `currentToolName` и `UserSignal` в `McpServer` заменить
`ActiveToolCallRegistry`, индексированным внутренним call ID/session. Сигнал прерывания принадлежит
конкретному вызову и не может быть потреблён соседним агентом. Status UI может показывать выбранный
или последний вызов, но runtime state остаётся раздельным.

### История и наблюдаемость

Расширить `McpCallRecord`, `McpCallHistory`, file-log serializer и History View полями:

- requested profile;
- effective profile;
- fallback reason;
- session ID.

На переходе сохранить старые overloads с `default`/`null` для тестов и внешних callers.

### Bridge и Workmate

1. `IEdtMcpBridge.listTools()` и `callTool()` используют synthetic `default` context.
2. Добавить profile-aware overload без удаления старого API.
3. Bridge повторно применяет policy на каждом вызове, а не доверяет ранее выданному списку.
4. `AskWorkmateTool` не получает глобальный `BiFunction`: использовать profile-bound bridge/context,
   иначе узкий профиль сможет вызвать инструменты `default`.
5. `GetToolGuideTool` не раскрывает guide запрещённого инструмента.

### Тесты

- `ActiveToolCallRegistryTest` и конкурентные call/signal tests;
- history/file-log/UI model с обоими profile ID;
- `EdtMcpBridgeTest`: default и explicit profile;
- попытка обхода через Workmate, direct call, guide и stale list;
- завершение одного вызова не снимает/не прерывает другой.

### Критерии выхода

- два одновременных агента не разделяют capabilities, active call или signal;
- все in-process пути соблюдают ту же allowlist, что HTTP;
- логи позволяют восстановить requested/effective resolution без сохранения секретов.

## 13. Этап 9. Preferences UI без рестарта сервера

### Модель UI

Добавить тестируемый без SWT `ToolProfilesEditorModel`, который хранит исходный revision, локальные
drafts, selected ID и dirty state. Операции: Add, Duplicate, Rename display name, Enable/Disable,
Delete, Apply Preset и Restore Selected.

Правила:

- ID задаётся при создании и неизменяем;
- `default` нельзя удалить или отключить;
- Duplicate копирует allowlist под новым ID;
- неизвестные tool names не теряются при редактировании;
- смена selected profile не сохраняет документ;
- Apply выполняет один optimistic `replaceAll` всех drafts.

### UI

Перестроить `preferences/ToolsTab.java`:

- profile selector и Add/Duplicate/Rename/Enable/Delete;
- display name, description, read-only endpoint и Copy URL;
- предупреждение о консервативном fallback-наборе `default`;
- существующие preset/tree/count редактируют selected draft;
- `get_server_status` показывается обязательным и неотключаемым;
- tool parameters и destructive consent явно остаются глобальными.

Изменить `McpServerPreferencePage.performOk()`:

- сохранять repository snapshot и глобальные настройки отдельно;
- при revision conflict не закрывать страницу, предложить reload;
- убрать restart при tools/profile changes;
- оставить restart только для transport settings, действительно требующих его.

Обновить `Messages.java`, `messages.properties`, `messages_ru.properties`. Клиентский MCP surface,
errors и README остаются английскими согласно правилам репозитория; русский используется только в
локализованном UI.

### Тесты и ручная проверка

- `ToolProfilesEditorModelTest`: весь CRUD, dirty state, preset, unknown tools, conflict/reload;
- `MessagesParityTest`;
- существующие `ToolSettingsServiceTest` и page tests;
- ручной UI smoke: CRUD, Copy URL, Apply без disconnect, Restore Selected и restart только для port.

### Критерии выхода

- вся бизнес-логика редактора покрыта headless tests;
- Apply профиля не разрывает MCP connections;
- два окна Preferences не могут молча перезаписать изменения друг друга.

## 14. Этап 10. Profile-aware proxy

### Path и client sessions

1. Добавить immutable `ProfileEndpoint` и строгий parser с теми же правилами, что plugin.
2. `SessionManager` хранит `SessionContext`: endpoint, protocol version, capabilities и last access.
3. Session replay на другом path → 404; DELETE закрывает только эту client session.

### Backend channels

1. Разделить `Backend` (EDT instance/health) и `BackendProfileChannel` для пары
   `(backend, requestedProfileId)`.
2. Каждый channel имеет свой URI, handshake/session, request IDs, stale retry и resolution metadata.
3. `BackendRegistry` discovery `list_projects` явно использует `/mcp`; routed/fan-out calls используют
   client endpoint.
4. HTTP 404 инвалидирует только session соответствующего channel и повторяет initialize на том же
   profile URL.

### Profile groups и cache

1. После handshake получить `get_server_status` и `tools/list`, канонизировать descriptors и вычислить
   fingerprint без JSON-RPC IDs/framing.
2. Создать atomic profile snapshot registry: donor, compatible/incompatible backends, причины,
   fingerprint и cached list.
3. Cache key включает requested ID, effective ID, fallback reason и group fingerprint. Fallback path
   никогда не использует кеш explicit `/mcp` или `/mcp/profiles/default`.
4. `McpProxyHandler` передаёт endpoint через initialize, list, routed calls, fan-out, hotplug refresh и
   retry.
5. Proxy initialize берёт resolution donor, переносит fallback warning в instructions и выдаёт свою
   path-bound client session.
6. `router_status` показывает несовместимые backend'ы; `availableProfiles` строится по правилу
   раздела 2.

### SSE

Добавить client GET/SSE registry по endpoint и backend notification channel по
`(backend, requestedProfileId)`. Backend list-changed инвалидирует только профильный snapshot,
пересчитывает compatibility group, дедуплицируется и доставляется затронутым clients.

### Тесты

Расширить `FakeBackend` профильными paths, session IDs, status, allowlist/fallback и notifications.
Добавить `ProfileRoutingIT` и расширить:

- `SessionManagerTest`;
- `BackendRegistryTest`;
- `ProxyRoutingIT`;
- `HotplugFailoverIT`;
- `ZeroBackendAndDupIT`;
- `RouterToolsTest` и `FanOutTest`.

Проверить разные profile URLs, scoped/unscoped/fan-out routing, concurrent sessions, stale retry,
cache isolation, unknown/disabled fallback, invalid path, mixed backend state, deterministic donor,
zero-backend mode и точечную invalidation.

### Критерии выхода

- `mvn -f proxy/pom.xml clean verify` проходит;
- proxy никогда не рекламирует surface, которую может направить на несовместимый backend;
- direct и proxy contracts отличаются только обязательными router tools.

## 15. Этап 11. E2E, conformance, документация и release gate

### E2E harness

1. В `tests/e2e/harness.py` добавить `McpClient` с собственными URL, session ID и request ID.
2. Старые `call()`/initialize оставить wrappers над default client, чтобы не переписывать весь suite.
3. Исполнение существующего fixture suite остаётся последовательным. Параллельны только новые
   read-only profile clients; нельзя параллельно мутировать один `TestConfiguration`.
4. Заранее подготовить в чистом test workspace профили `e2e-review`, `e2e-development` и
   `e2e-disabled` через production codec/preferences, без production test-only MCP tool.

### Новый profile e2e suite

- два клиента и разные `activeProfile`/`tools/list`;
- `includeProfiles` и `includeProfileTools`;
- параллельный разрешённый read;
- write запрещён до валидации аргументов в review и проходит к обычной валидации в development;
- `enable_toolset` не меняет explicit profile;
- unknown/disabled fallback и invalid path;
- hot update без restart;
- тот же набор через proxy;
- итоговое состояние fixtures чистое.

Для полной автоматизации hot update выбрать test-only PDE/OSGi fixture или CI-only control bundle,
не попадающий в production feature. Если это неоправданно дорого, unit/integration покрывают hot
update автоматически, а UI live acceptance остаётся ручным и явно отмечается в отчёте.

### Conformance

Запустить официальный suite 2025-11-25 без расширения baseline:

- plugin `/mcp`;
- plugin `/mcp/profiles/default`;
- один ограниченный explicit profile;
- proxy `/mcp`;
- proxy `/mcp/profiles/default`.

Custom transport tests отдельно проверяют path-bound sessions, fallback, invalid paths, notification
isolation и стабильность list независимо от порядка запросов. Stateless draft остаётся контрактным
тестом, а не отдельной реализацией протокола.

### Документация

- README: конфигурация URL, создание/выбор профиля, fallback и proxy consistency;
- `tests/conformance/README.md` и CI matrix;
- migration/downgrade note;
- пример конфигурации нескольких агентов без утверждения, что profile ID является секретом.

### Финальный gate

Выполнить `edt-mcp-ready-to-deploy` по порядку:

1. hygiene и review diff;
2. все новые/изменённые unit tests;
3. Tycho build;
4. README и golden `tools/list`, если surface изменился;
5. свежий live redeploy с anti-stale jar check;
6. полный e2e и чистые fixtures;
7. conformance без новых failures;
8. анализ EDT `.metadata/.log`;
9. чистый git status и локальные commits без push/merge.

## 16. Порядок коммитов и точки отката

Рекомендуемые независимые commits:

1. docs + characterization baseline;
2. profile model/codec/repository;
3. migration + legacy facade;
4. registry/policy separation;
5. request context + protocol;
6. endpoint/session transport;
7. notifications;
8. status discovery;
9. active calls/history/bridge;
10. Preferences UI;
11. proxy path/session/channel;
12. proxy compatibility/cache/SSE;
13. e2e/conformance/docs.

Каждый commit должен компилироваться и иметь тесты своего слоя. Временные compatibility wrappers
удаляются только после перевода последнего потребителя, отдельным легко откатываемым изменением.

## 17. Допустимое распараллеливание реализации

После этапа 2 можно параллельно готовить:

- pure unit tests profile policy;
- `ToolProfilesEditorModel` без подключения к SWT;
- proxy `ProfileEndpoint` и FakeBackend;
- объектный `McpClient` в e2e harness с сохранением старого API.

Нельзя параллельно без общего контракта менять:

- `McpProtocolHandler`, `McpHttpHandler` и `InterruptibleToolExecutor`;
- repository migration и `ToolSettingsService` facade;
- proxy compatibility group и server status schema;
- выполнять e2e shards против одного EDT workspace/checkout.

## 18. Общие критерии завершения

Работа считается законченной, когда одновременно выполнены критерии утверждённой спецификации и:

- нет runtime-чтений профильной политики из legacy singleton;
- profile authorization применяется ко всем list/read/call/guide/bridge путям;
- capabilities, sessions, SSE streams, active calls и signals изолированы;
- unknown/disabled fallback наблюдаем и не маскирует malformed path;
- UI сохраняет snapshot без restart и без lost update;
- proxy работает только с совместимой profile routing group;
- существующий `/mcp`, migration и downgrade mirror проверены;
- Tycho, proxy tests, live e2e, conformance и EDT log review пройдены;
- исходный код, клиентские тексты и README соответствуют English-only правилу репозитория.

## 19. Оценка риска по этапам

| Этап | Риск | Основная причина |
|---|---|---|
| Model/repository | Средний | Миграция и сохранность повреждённых preferences. |
| Registry/policy | Высокий | Единый шлюз должен закрыть все обходные пути. |
| Context/session/SSE | Очень высокий | Протокол, конкурентность и совместимость клиентов. |
| Active calls/bridge | Высокий | Скрытое глобальное состояние и Workmate bypass. |
| UI | Средний | Dirty-state, optimistic conflict и SWT lifecycle. |
| Proxy | Очень высокий | Несколько backend'ов, разные surfaces, cache и retry. |
| E2E/conformance | Высокий | Нужны несколько реальных clients и live EDT. |

## 20. Отложенные доработки (не в текущем объёме)

Зафиксировано 29 августа 2026: обмен профилями между воркспейсами EDT **не делается** в этой ветке.
Сейчас профили создаются вручную на вкладке Tools. Следующая доработка — файл обмена.

Рекомендуемая форма (утвердить при старте работы, если не передумают):

- **Где:** кнопки Export / Import на вкладке Tools. MCP-инструментов для этого нет.
- **Формат:** JSON того же документа, что уже пишет `ToolProfileCodec` (не второй XML-codec).
- **Import:** заменить весь snapshot, включая `default`; подтверждение перед записью.
- **Когда применять:** сразу в repository (`replaceAll`), без отдельного черновика.
- **Не входит:** per-profile токены/PII/consent, merge-по-id, секретность profile ID.

Кодек и `replaceAll` уже есть; доработка — FileDialog + валидация + конфликт revision + unit-тест
модели без SWT. После выгрузки один JSON переносится в другой воркспейс Import'ом.
