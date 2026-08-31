# Хэндофф: multi-profile review → стенд EDT

Продолжение работы с ветки Cloud-агента. **P0–P2 уже в коде и зелёные на unit/proxy.** Осталось пройти live-контур на машине с EDT (P3 + `edt-mcp-ready-to-deploy`) и закрыть ревью.

Не править файл плана `/opt/cursor/artifacts/plans/multi_profile_review_fixes_99447ee7.plan.md`. Несвязанное из §3 ревью (версия продукта, fixtures 8.3.27, update checks) не трогать.

---

## 1. Где взять код

| | |
|---|---|
| Repo | `https://github.com/expspb/giper-edt-mcp` (канон GitHub: `ExpSPB/Giper-EDT-MCP`) |
| Base | `test` |
| Branch | `cursor/multi-profile-review-fixes-96c9` |
| HEAD на момент хэндоффа | `a8ce86a` |
| PR | https://github.com/expspb/giper-edt-mcp/pull/3 (draft) |

```powershell
git fetch origin cursor/multi-profile-review-fixes-96c9
git checkout cursor/multi-profile-review-fixes-96c9
git pull origin cursor/multi-profile-review-fixes-96c9
```

Четыре коммита поверх `test`:

1. `afc23a2` — registry без policy, `McpRequestContext.protocolVersion`, deny `isError:true`, `/mcp/` → 400, bridge только `IEdtMcpBridge`
2. `4819427` — Add из обязательного preset, unavailable tools, status bar / history
3. `044b172` — Apply = kick только затронутого URL; proxy: fallback из `activeProfile`, паритет status, `serverInfo.name`, SSE без 30s cutoff
4. `a8ce86a` — seed e2e-профилей, матрица conformance/e2e через proxy, README/spec

Локально не коммитить `docs/multi-profile-review-fixes-plan.md`, если он всплывёт untracked — это копия плана, не часть диффа.

---

## 2. Что уже доказано (не повторять как «не сделано»)

На Cloud без EDT:

- `bash source/compile.sh` → **BUILD SUCCESS**, 5938 tests, 0 fail / 0 error
- `mvn -f proxy/pom.xml clean verify` → **BUILD SUCCESS**, 186 tests

Пакеты только текущие: `fm.giper.edt.mcp.server`, `fm.giper.edt.mcp.proxy`. Старые `com.ditrix.edt.mcp.*` не трогать.

---

## 3. Утверждённые решения (не откатывать)

- Профиль берётся **только из URL**. Не из сессии, не из `ThreadLocal` как авторизация.
- Apply **не** рестартит плагин. Кикаются сессии/SSE **затронутого** пути. Правка `review` не роняет `/mcp`. Правка `default` кикает `/mcp` + fallback-клиентов; sessionless `/mcp` после этого → HTTP 400 «call initialize again».
- Live `tools/list_changed` **без разрыва не цель**. Клиент должен заново `initialize` на том URL.
- Denied `tools/call`: JSON-RPC **без** `error`, `result.isError = true`. Текст называет профиль и Preference page.
- `/mcp/` (trailing slash) — HTTP **400**, не alias `/mcp`.
- OSGi: только `IEdtMcpBridge`. Нет `BiFunction` / `Supplier` / `apply()`. `listTools()` / `callTool(name, args)` остаются `default` (спека §8.4) — это не обход HTTP-профиля.
- Add профиля: обязательный built-in preset (All / Analysis / Code Review / Development). CUSTOM/null нельзя.
- Proxy не подменяет контракт `get_server_status` (кроме фильтра несовместимых групп и переписи `endpoint` на URL proxy). `fallbackApplied` / `fallbackReason` читать из `activeProfile`; корень — только fallback для старых ответов.
- Explicit initialize: `serverInfo.name` = `SERVER_NAME + "/" + effectiveProfileId`. Fallback — имя `default` + warning в `instructions`.
- `legacyDefault()` (in-process / Tycho) = **allow-all по текущему каталогу**, не snapshot из PreferenceStore. Иначе headless-тесты видят пустой/узкий `default`. HTTP всегда резолвит snapshot из URL + repository.

Вне объёма: export/import профилей, PD на explicit endpoints, per-profile auth/PII/consent.

---

## 4. Стенд: порядок работ

Скилл: `.claude/skills/edt-mcp-ready-to-deploy/SKILL.md`. Идти сверху вниз, стоп на первом красном.

Типичный стенд из скиллов: не-elevated копия EDT, workspace `D:\WS\EDT`, MCP `:8765`, редеплой `pwsh D:\Soft\edt-redeploy.ps1`. Exit 1 + строка `MCP server UP on 8765` = успех. Не `Program Files`.

### 4.1 Сид профилей (до boot EDT)

Фикстура `tests/e2e/fixtures/e2e_tool_profiles.json` должна совпадать с golden `tools/list` по именам default (сейчас 88 = 88). `git` и `ask_workmate` в default нет (default-off).

```powershell
# EDT должен быть ОСТАНОВЛЕН. Пишет instance prefs.
python tests/e2e/fixtures/seed_tool_profiles.py D:\WS\EDT
```

Пишет `.metadata/.plugins/org.eclipse.core.runtime.settings/fm.giper.edt.mcp.server.prefs` ключ `mcpToolProfiles`.

Профили: `e2e-review` (read, без `write_module_source`), `e2e-development` (есть write), `e2e-disabled` (enabled=false → fallback). `require_enabled_profile` больше **не skip** — без сида тесты **падают**.

Альтернатива: Tools tab → Add только из preset, руками завести те же id.

### 4.2 Сборка + редеплой + anti-stale

```powershell
bash source/compile.sh
# toolchain не на PATH:
# bash source/compile.sh --java-home <JDK17> --maven-home <maven>
mvn -f proxy/pom.xml clean verify
pwsh D:\Soft\edt-redeploy.ps1   # или с -Build, если скрипт сам собирает
```

Anti-stale (подставить путь к **задеплоенному** jar, не к target Tycho):

```powershell
# deny envelope
jar tf <deployed.jar> | findstr McpProtocolHandler
# ожидаемые литералы в class:
#   buildDeniedToolCallResponse
#   isError
unzip -p <deployed.jar> fm/giper/edt/mcp/server/protocol/McpProtocolHandler.class | findstr /C:"not allowed" /C:"Preference"

# /mcp/ больше не alias
unzip -p <deployed.jar> fm/giper/edt/mcp/server/transport/McpEndpointResolver.class | findstr /C:"/mcp/"
# не должно резолвить trailing slash как LEGACY
```

Дождаться `ready` у проектов. Если EDT клинит — kill + `-clean`.

### 4.3 Ручной smoke (5 минут)

1. Preferences → Tools: Add без preset не закрывается OK. С preset — allowlist заполнен.
2. Имя инструмента, которого нет в каталоге, серое `(unavailable)`, checkbox не подменяет другим именем. Apply сохраняет имя.
3. Status bar во время вызова: `MCP[e2e-review]: list_projects` или `MCP[missing -> default]: get_server_status`.
4. Apply на `e2e-review` **не** роняет клиент `/mcp`. Клиент review теряет сессию; после reconnect видит новый набор.
5. Apply на `default` кикает `/mcp` и fallback. Следующий sessionless запрос на `/mcp` → 400, нужен `initialize`.
6. `POST http://127.0.0.1:8765/mcp/` → HTTP 400.
7. `POST /mcp/profiles/zz-missing` → initialize с `serverInfo.name` содержащим `default`, в `instructions` есть `UNKNOWN_PROFILE`.
8. Review `write_module_source` → `isError: true`, не JSON-RPC `error`.

### 4.4 Proxy на стенде

```powershell
java -jar proxy/target/edt-mcp-proxy-*.jar serve --port 8764 --scan 8765-8765
```

Health: `http://127.0.0.1:8764/health`.

Для e2e:

```powershell
$env:MCP_PROXY_PORT = "8764"
# MCP_PROXY_HOST по умолчанию = MCP_HOST (127.0.0.1)
```

Без `MCP_PROXY_PORT` proxy-тесты в `test_profiles.py` — `E2ESkip`. С портом, но мёртвым proxy — **fail**.

### 4.5 E2E

Сначала профильный фильтр, потом полный прогон:

```powershell
python tests/e2e/run_all.py --project TestConfiguration --filter test_profiles
python tests/e2e/run_all.py --project TestConfiguration
```

Ожидание: все pass, `fixture clean: True`. Env-gated skips (live infobase) нормальны. Skip из-за отсутствия `e2e-review` — **баг стенда** (не засидили prefs).

Golden `tools/list` не регенерировать, если поверхность инструментов не менялась. Если сид default сузит каталог — golden разъедется (сейчас список совпадает).

Покрыто в `tests/e2e/tools/test_profiles.py`:

- `/mcp/` и мусорный path → 400
- unknown / disabled → fallback + `fallbackApplied`
- review ≠ default surface; deny write с `isError`
- development доходит до валидации аргументов write
- path-bound session не принимается на чужом URL
- два профиля живы без рестарта сервера (обычный трафик)
- повтор матрицы через proxy + паритет status

Live Apply из UI e2e не автоматизирован (нет HTTP control API). Kick — `ProfileNotificationServiceTest`. UI Apply проверить руками (п. 4.3.4–4.3.5).

### 4.6 Conformance (5 URL, тот же `baseline.yml`)

Не расширять `tests/conformance/baseline.yml`. Новый fail = регрессия протокола.

```powershell
$urls = @(
  "http://127.0.0.1:8765/mcp",
  "http://127.0.0.1:8765/mcp/profiles/default",
  "http://127.0.0.1:8765/mcp/profiles/e2e-review",
  "http://127.0.0.1:8764/mcp",
  "http://127.0.0.1:8764/mcp/profiles/default"
)
foreach ($u in $urls) {
  Write-Host "[conformance] $u"
  npx --yes @modelcontextprotocol/conformance@latest server `
    --url $u `
    --spec-version 2025-11-25 `
    --expected-failures tests/conformance/baseline.yml
}
```

Node 22+ (`fs.globSync`). Proxy должен уже слушать `:8764`.

### 4.7 Журнал EDT

После прогона:

```
<workspace>/.metadata/.log
```

плюс ротация `.bak_*.log`. Агрегат:

```powershell
Get-Content .bak_*.log, .log -ErrorAction SilentlyContinue |
  Select-String -Pattern "^!ENTRY .* 4 " -Context 0,1 |
  Select-String "^!MESSAGE" |
  Group-Object | Sort-Object Count -Descending
```

Триаж: OURS-real (чинить) / OURS-noise (валидный отказ не должен быть ERROR) / PLATFORM (записать и идти дальше). Fallback на initialize должен быть **WARNING**, не ERROR (`Activator.logWarning` в `McpProtocolHandler.logFallbackIfNeeded`).

---

## 5. Карта файлов (если чинить по живому)

| Тема | Где |
|---|---|
| Policy / context | `McpRequestContext`, `ProfileToolPolicy`, `McpToolRegistry` (только каталог) |
| Deny `isError` | `McpProtocolHandler.buildDeniedToolCallResponse` |
| `/mcp/` 400 | `McpEndpointResolver`, `ProfileEndpointResolver` |
| Bridge | `IEdtMcpBridge`, `EdtMcpBridge`, `Activator.publishBridge`, `AskWorkmateTool` |
| Add / unavailable | `ToolProfilesEditorModel`, `ToolProfileDialog`, `ToolsTab` |
| Kick | `ProfileNotificationService`, `McpSessionRegistry.closeByRequestedPath`, `SseStreamRegistry.closeByRequestedPath` |
| Status bar | `McpServer.formatStatus` |
| Proxy status / name / SSE | `McpProxyHandler`, `BackendProfileChannel.parseResolution`, `SseNotificationHub` |
| Сид | `tests/e2e/fixtures/*`, `.github/actions/setup-edt`, `.github/workflows/e2e-tests.yml` |
| Conformance matrix | `.github/workflows/conformance.yml`, `tests/conformance/README.md` |

Спека/статус: `docs/multi-profile-tool-surfaces.md` (пометка «реализация не начата» снята).

---

## 6. После зелёного стенда

1. Если чинили код — коммит на **этой** ветке, не force-push, не amend чужих коммитов.
2. Пройти оставшиеся пункты ready-to-deploy: hygiene, чистый `git status` (e2e не должен оставить fixture churn).
3. Снять draft у PR, когда live gate зелёный.
4. Каждый комментарий ревью — **ответ** (что сделано / почему отклонено). Резолвить тред только после ответа и реального фикса. Не резолвить молча.
5. После пуша/фиксов: один комментарий `@codex review` + одно предложение, куда бить (не эссе). Уже был запрос на `buildDeniedToolCallResponse` и proxy `handleGetServerStatus`.
6. **Не мержить** PR без явной просьбы.

---

## 7. Подсказка агенту на стенде

Загрузить скиллы **до** правок: `edt-mcp-ready-to-deploy`, `edt-mcp-e2e-testing` (`tests/e2e/SKILL.md`), `edt-mcp-testing`, `edt-mcp-build-test`. Поверхность репо — English; кириллица в коде только как реальные 1C-токены. Ответы пользователю — по его правилу.

Первый шаг на машине: `git pull` этой ветки → сид prefs → compile → redeploy → anti-stale → `--filter test_profiles` с поднятым proxy → полный e2e → пять URL conformance → разбор `.log`. Не начинать с «ещё одного рефакторинга реестра».
