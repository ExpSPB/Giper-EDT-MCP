# Аудит реализации multi-profile tool surfaces

**Дата проверки:** 30 августа 2026 года  
**Проверенный срез:** `HEAD d206930`  
**Основание для сравнения:**

- [`multi-profile-tool-surfaces.md`](multi-profile-tool-surfaces.md)
- [`multi-profile-implementation-plan.md`](multi-profile-implementation-plan.md)

Документы использовались только как критерии проверки. Замысел, отсутствующий в документах или коде, не предполагался. Проверялась фактическая реализация текущего `HEAD`.

## 1. Пункты плана и спецификации, которые не выполнены

### 1.1. Не завершено разделение runtime-политики и legacy-настроек

По спецификации реестр должен отвечать только за регистрацию и поиск инструментов, без неявного обращения к `ToolSettingsService` ([спецификация, строки 388–395](multi-profile-tool-surfaces.md#L388), [план, строки 597–609](multi-profile-implementation-plan.md#L597)).

`McpToolRegistry` продолжает предоставлять runtime-методы `getEnabledTools`, `getVisibleTools` и `isToolEnabled`, основанные на глобальных настройках:

- [`McpToolRegistry.java`, строка 137](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/McpToolRegistry.java#L137)

Эти методы по-прежнему используются runtime-кодом, включая `GetServerStatusTool`, `AskWorkmateTool`, legacy-ветки `McpProtocolHandler` и default-контекст `EdtMcpBridge`.

### 1.2. Не завершён контракт контекста запроса и сессии

`McpRequestContext` должен содержать requested/effective profile, fallback, revision, negotiated protocol version и capabilities ([спецификация, строки 365–370](multi-profile-tool-surfaces.md#L365)).

Версия протокола хранится в `McpTransportSession`, но при построении request context в него не переносится:

- [`McpTransportSession.java`, строка 13](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/transport/McpTransportSession.java#L13)
- [`McpHttpHandler.java`, строка 520](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/transport/McpHttpHandler.java#L520)

Одновременно в handler остался глобальный `lastClientCapabilities`, который спецификация требует удалить:

- [`McpProtocolHandler.java`, строка 87](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/protocol/McpProtocolHandler.java#L87)

### 1.3. Не выполнен profile-aware bridge без обходов

Спецификация требует, чтобы bridge был привязан к профилю и не позволял обходить профильную политику ([спецификация, строки 413–417](multi-profile-tool-surfaces.md#L413), [план, строки 362–369](multi-profile-implementation-plan.md#L362)).

Глобальный bridge сохранён, а запрет его прямого вызова обеспечивается текстовой инструкцией внешнему агенту, а не техническим ограничением.

### 1.4. Не завершена observability

История, логи и status bar должны отражать requested/effective profile, fallback и session ([спецификация, строки 419–424](multi-profile-tool-surfaces.md#L419)).

Метаданные истории привязываются через `ThreadLocal` только вокруг отдельных tool calls:

- [`McpCallHistory.java`, строка 127](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/history/McpCallHistory.java#L127)
- [`McpProtocolHandler.java`, строка 169](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/protocol/McpProtocolHandler.java#L169)

Для `initialize`, `tools/list`, resource-вызовов и других HTTP-запросов профильные метаданные в истории не обеспечены. Status bar показывает только имя текущего инструмента.

### 1.5. Не завершён UI профилей

Не реализованы:

- создание нового профиля из выбранного built-in preset;
- визуальная маркировка сохранённых, но отсутствующих инструментов как unavailable.

Требования описаны в [спецификации, строки 137–157](multi-profile-tool-surfaces.md#L137) и [плане, строки 385–435](multi-profile-implementation-plan.md#L385).

### 1.6. Не реализован полный proxy notification pipeline

План требует слушать backend SSE, инвалидировать профильные snapshots и пересылать уведомления proxy-клиентам ([план, строки 470–474](multi-profile-implementation-plan.md#L470)).

Production-кода, вызывающего `SseNotificationHub.onBackendListChanged`, нет. Этот вызов присутствует только в тестах.

### 1.7. Не выполнен Stage 11: live E2E, conformance matrix и финальный gate

Не выполнены следующие части плана:

- автоматическое создание fixture-профилей через production codec/preferences;
- полный сценарий hot update без перезапуска;
- отключение активного профиля с проверкой fallback и notification;
- повтор одной и той же live-матрицы через proxy;
- conformance-запуски для explicit profiles и proxy endpoints;
- подтверждённый финальный зелёный gate.

E2E harness пропускает тесты при отсутствии вручную подготовленных профилей:

- [`harness.py`, строка 486](../tests/e2e/harness.py#L486)

Workflow запускает conformance только для plugin `/mcp`:

- [`conformance.yml`, строка 133](../.github/workflows/conformance.yml#L133)

План требует пять conformance-вариантов ([план, строки 525–537](multi-profile-implementation-plan.md#L525)).

## 2. Места, где реализация расходится со спецификацией

### 2.1. Запрещённый `tools/call` возвращается как успешный результат

Спецификация требует успешный JSON-RPC envelope с `isError: true`, именем профиля и указанием места настройки ([спецификация, строка 502](multi-profile-tool-surfaces.md#L502)).

Реализация вызывает обычный `buildToolCallTextResponse`, который формирует успешный tool result без `isError: true`:

- [`McpProtocolHandler.java`, строка 394](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/protocol/McpProtocolHandler.java#L394)
- [`McpProtocolHandler.java`, строка 1177](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/protocol/McpProtocolHandler.java#L1177)

E2E-тест допускает такой ответ, если текст содержит `not allowed`:

- [`test_profiles.py`, строка 145](../tests/e2e/tools/test_profiles.py#L145)

### 2.2. Новый профиль создаётся пустым, а не из выбранного preset

`add()` задаёт `allowedTools(Set.of())`:

- [`ToolProfilesEditorModel.java`, строка 160](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/preferences/ToolProfilesEditorModel.java#L160)

Диалог создания не предлагает выбрать preset:

- [`ToolsTab.java`, строка 295](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/preferences/ToolsTab.java#L295)

Это расходится с требованием создавать профиль из выбранного built-in preset ([спецификация, строки 146–147](multi-profile-tool-surfaces.md#L146)).

### 2.3. Неизвестные tool names не помечаются в UI как unavailable

Модель сохраняет неизвестные имена и предоставляет `unknownAllowedTools()`:

- [`ToolProfilesEditorModel.java`, строка 142](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/preferences/ToolProfilesEditorModel.java#L142)

Однако `ToolsTab` этот API не использует. Требование о маркировке отсутствующих инструментов в UI не выполнено ([спецификация, строки 151–152](multi-profile-tool-surfaces.md#L151)).

### 2.4. Fallback не логируется при обычном запросе к unknown/disabled profile

Контекст получает fallback reason:

- [`McpHttpHandler.java`, строка 624](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/transport/McpHttpHandler.java#L624)

Но log entry при таком запросе не создаётся. Логирование существует только при последующем изменении repository для уже открытого пути:

- [`ProfileNotificationService.java`, строка 69](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/profiles/ProfileNotificationService.java#L69)

Это расходится с требованием отражать каждый fallback в status и log ([спецификация, строки 178–190](multi-profile-tool-surfaces.md#L178), [строки 623–624](multi-profile-tool-surfaces.md#L623)).

### 2.5. Proxy неверно разбирает production `get_server_status`

Backend помещает `fallbackApplied` и `fallbackReason` внутрь `activeProfile`:

- [`GetServerStatusTool.java`, строка 299](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetServerStatusTool.java#L299)

Proxy читает их из корня ответа:

- [`BackendProfileChannel.java`, строка 218](../proxy/src/main/java/com/ditrix/edt/mcp/proxy/BackendProfileChannel.java#L218)

В результате proxy теряет fallback reason и строит неправильный cache key и initialize warning. `FakeBackend` маскирует ошибку, возвращая отличающуюся от production структуру:

- [`FakeBackend.java`, строка 481](../proxy/src/test/java/com/ditrix/edt/mcp/proxy/FakeBackend.java#L481)

### 2.6. Proxy меняет контракт `get_server_status`

Proxy:

- игнорирует аргументы клиента;
- всегда запрашивает `includeProfiles=true`;
- заменяет `availableProfiles` собственным сокращённым списком.

Реализация:

- [`McpProxyHandler.java`, строка 605](../proxy/src/main/java/com/ditrix/edt/mcp/proxy/McpProxyHandler.java#L605)
- [`BackendRegistry.java`, строка 588](../proxy/src/main/java/com/ditrix/edt/mcp/proxy/BackendRegistry.java#L588)

В proxy-ответе остаются только `id` и `endpoint`, без `displayName`, `description`, `revision` и `allowedToolCount`. `includeProfileTools` и проверка несовместимых аргументов не поддерживаются. Это нарушает требование одинакового диагностического контракта direct и proxy ([спецификация, строка 589](multi-profile-tool-surfaces.md#L589)).

### 2.7. Explicit proxy endpoint не идентифицирует профиль в `serverInfo.name`

Proxy всегда возвращает имя `edt-mcp-proxy`:

- [`McpProxyHandler.java`, строка 329](../proxy/src/main/java/com/ditrix/edt/mcp/proxy/McpProxyHandler.java#L329)

Спецификация требует, чтобы logical server name explicit endpoint содержал идентификатор профиля ([спецификация, строки 166–168](multi-profile-tool-surfaces.md#L166)).

### 2.8. Backend profile changes не доходят до proxy-клиентов

`SseNotificationHub` реализован, но не подключён к backend SSE. Кроме того, клиентский SSE proxy принудительно закрывается примерно через 30 секунд:

- [`McpProxyHandler.java`, строка 665](../proxy/src/main/java/com/ditrix/edt/mcp/proxy/McpProxyHandler.java#L665)

Это не обеспечивает требуемое горячее обновление профилей без перезапуска и переподключения клиентов.

### 2.9. Рассылка plugin notifications не учитывает negotiated session state

`SseStreamRegistry` хранит только stream, session ID и requested path и не использует negotiated protocol version и capabilities:

- [`SseStreamRegistry.java`, строка 51](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/SseStreamRegistry.java#L51)

Таким образом, доставка уведомлений не реализует предусмотренную планом version-aware маршрутизацию.

### 2.10. Workmate может обойти профильную политику

`EdtMcpBridge` остаётся глобальным `BiFunction` с default-profile вызовом. `AskWorkmateTool` лишь просит внешний код не вызывать этот интерфейс напрямую:

- [`AskWorkmateTool.java`, строка 367](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/AskWorkmateTool.java#L367)
- [`EdtMcpBridge.java`, строка 227](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/bridge/EdtMcpBridge.java#L227)

Это текстовое соглашение, а не техническое ограничение capability boundary.

### 2.11. `/mcp/` принимается как legacy endpoint

Resolver разрешает trailing slash:

- [`McpEndpointResolver.java`, строка 31](../mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/transport/McpEndpointResolver.java#L31)
- [`ProfileEndpointResolver.java`, строка 31](../proxy/src/main/java/com/ditrix/edt/mcp/proxy/ProfileEndpointResolver.java#L31)

Спецификация перечисляет только `/mcp` и требует HTTP 400 для лишних сегментов ([спецификация, строки 192–194](multi-profile-tool-surfaces.md#L192)).

## 3. Решения, принятые без явного указания

Следующие решения присутствуют в реализации, но прямо не предусмотрены проверяемыми документами:

1. Создавать новый профиль с пустым allowlist вместо обязательного выбора preset.
2. Защищать Workmate bridge текстовым prompt-запретом, а не технически привязанным к профилю объектом.
3. В proxy принудительно включать `includeProfiles` и подменять backend-ответ сокращённой схемой.
4. Ограничить жизнь proxy SSE-соединения 30 секундами.
5. Сделать E2E-профили внешней ручной предпосылкой и использовать `skip`, хотя план требует создавать fixtures производственным способом.
6. Принимать дополнительный endpoint `/mcp/`.
7. Повысить версию продукта с `1.0.0` до `1.0.1` в рамках feature, хотя такой шаг в плане отсутствует.
8. Добавить в текущий результат после feature несвязанные с multi-profile изменения: подавление update checks, переход тестовых fixtures с `8.5.1` на `8.3.27`, удаление 8.5-specific form XML и версию `1.0.2`.
9. Оставить в спецификации устаревшую пометку `implementation not started` ([спецификация, строка 3](multi-profile-tool-surfaces.md#L3)).

Пункты этого раздела могут пересекаться с расхождениями из раздела 2: здесь они перечислены именно как выборы реализации, для которых не найдено явного основания в спецификации или плане.

## 4. Статус проверки тестами

Подтверждённого зелёного финального gate нет.

- Proxy-тесты были запущены, но часть тестов остановилась на невозможности установить loopback-соединения в текущем окружении.
- Tycho-проверка остановилась на lock-файле локального p2-репозитория до выполнения полного набора тестов.

Эти инфраструктурные ошибки не считаются доказанными дефектами реализации. Одновременно они не позволяют считать выполненным acceptance-критерий «все тесты проходят» ([спецификация, строки 570–591](multi-profile-tool-surfaces.md#L570)).
