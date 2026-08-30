"""
e2e tests for multi-profile MCP surfaces (kind: read / action).

These are protocol-and-policy tests, not a new tool. They use extra McpClient
instances so /mcp and /mcp/profiles/<id> stay isolated. Named profiles
e2e-review / e2e-development / e2e-disabled must be seeded from
tests/e2e/fixtures/e2e_tool_profiles.json (or added from a built-in preset).
Missing fixtures fail the test; they do not skip.

Apply / hot allowlist updates kick only the affected profile's sessions
(covered by ProfileNotificationServiceTest). Live Preferences UI stays manual.
"""

from concurrent.futures import ThreadPoolExecutor, as_completed

from harness import (
    E2E_DEV_PROFILE,
    E2E_DISABLED_PROFILE,
    E2E_REVIEW_PROFILE,
    MCP_PROXY_HOST,
    MCP_PROXY_PORT,
    McpClient,
    PROJECT,
    assert_no_diff,
    assert_ok,
    default_client,
    e2e_test,
    http_post_status,
    profile_client,
    proxy_client,
    proxy_profile_client,
    require_enabled_profile,
    require_proxy,
)


def _tool_names(list_raw):
    result = list_raw.get("result") if isinstance(list_raw, dict) else {}
    tools = result.get("tools") or []
    return [t.get("name") for t in tools if isinstance(t, dict) and t.get("name")]


def _init(client):
    raw = client.initialize()
    if not isinstance(raw, dict) or "result" not in raw:
        raise AssertionError("initialize on %s failed: %r" % (client.path, raw))
    return raw["result"]


@e2e_test(tool="get_server_status", kind="read")
def test_invalid_profile_path_is_http_400_without_fallback():
    status, body = http_post_status("/mcp/not-a-profile")
    if status != 400:
        raise AssertionError(
            "expected HTTP 400 for a syntactically invalid path, got %s body=%r"
            % (status, body[:300]))
    slash = http_post_status("/mcp/")
    if slash[0] != 400:
        raise AssertionError(
            "expected HTTP 400 for /mcp/ trailing slash, got %s body=%r"
            % (slash[0], slash[1][:300]))
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_unknown_profile_falls_back_to_default():
    client = profile_client("zz-e2e-missing-profile")
    result = _init(client)
    name = ((result.get("serverInfo") or {}).get("name") or "")
    instructions = result.get("instructions") or ""
    if "default" not in name:
        raise AssertionError("fallback initialize must name default, got serverInfo.name=%r" % name)
    if "UNKNOWN_PROFILE" not in instructions:
        raise AssertionError(
            "fallback initialize must name UNKNOWN_PROFILE in instructions, got %r" % instructions)
    status = client.call("get_server_status", {"includeProfiles": True})
    assert_ok(status, "status after unknown-profile fallback")
    active = (status.structured or {}).get("activeProfile") or {}
    if active.get("id") != "default" or active.get("requestedProfileId") != "zz-e2e-missing-profile":
        raise AssertionError("fallback status mismatch: %r" % active)
    if active.get("fallbackApplied") is not True:
        raise AssertionError("expected fallbackApplied=true, got %r" % active)
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_include_profiles_compact_and_full():
    compact = default_client().call("get_server_status", {"includeProfiles": True})
    assert_ok(compact, "compact includeProfiles")
    compact_list = compact.structured.get("availableProfiles") or []
    if not compact_list:
        raise AssertionError("availableProfiles must list at least default: %r" % compact.structured)
    if any("allowedTools" in p for p in compact_list if isinstance(p, dict)):
        raise AssertionError("compact discovery must omit allowedTools: %r" % compact_list)

    denied = default_client().call(
        "get_server_status", {"includeProfileTools": True})
    if not denied.is_error:
        raise AssertionError(
            "includeProfileTools without includeProfiles must fail, got %r" % denied.structured)

    full = default_client().call(
        "get_server_status", {"includeProfiles": True, "includeProfileTools": True})
    assert_ok(full, "full includeProfiles")
    full_list = full.structured.get("availableProfiles") or []
    default_entry = next((p for p in full_list if p.get("id") == "default"), None)
    if default_entry is None or "allowedTools" not in default_entry:
        raise AssertionError("full discovery must include default.allowedTools: %r" % full_list)
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_two_clients_see_distinct_surfaces_when_review_exists():
    require_enabled_profile(E2E_REVIEW_PROFILE)
    review = profile_client(E2E_REVIEW_PROFILE)
    _init(review)
    default_names = set(_tool_names(default_client().tools_list()))
    review_names = set(_tool_names(review.tools_list()))
    if default_names == review_names:
        raise AssertionError(
            "review profile tools/list must differ from /mcp; both were %r" % sorted(review_names))
    status = review.call("get_server_status", {"includeProfiles": True})
    assert_ok(status, "review status")
    active = (status.structured or {}).get("activeProfile") or {}
    if active.get("id") != E2E_REVIEW_PROFILE:
        raise AssertionError("review client activeProfile.id=%r" % active.get("id"))
    if active.get("fallbackApplied") is True:
        raise AssertionError("enabled review must not fall back: %r" % active)
    assert_no_diff()


@e2e_test(tool="list_projects", kind="read")
def test_parallel_read_on_two_clients():
    require_enabled_profile(E2E_REVIEW_PROFILE)
    review = profile_client(E2E_REVIEW_PROFILE)
    _init(review)

    def _read(client):
        r = client.call("list_projects", {})
        assert_ok(r, "list_projects on %s" % client.path)
        return r.text or ""

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [
            pool.submit(_read, default_client()),
            pool.submit(_read, review),
        ]
        texts = [f.result() for f in as_completed(futures)]
    if any(PROJECT not in text for text in texts):
        raise AssertionError("both parallel reads must mention %s, got %r" % (PROJECT, texts))
    assert_no_diff()


@e2e_test(tool="write_module_source", kind="read")
def test_review_denies_write_before_argument_validation():
    require_enabled_profile(E2E_REVIEW_PROFILE)
    review = profile_client(E2E_REVIEW_PROFILE)
    _init(review)
    names = _tool_names(review.tools_list())
    if "write_module_source" in names:
        raise AssertionError("e2e-review must not publish write_module_source: %r" % names)
    r = review.call("write_module_source", {})
    err = r.error_text()
    if not r.is_error:
        raise AssertionError(
            "review deny must set isError:true, got %r" % err)
    if "not allowed" not in err.lower() and "profile" not in err.lower():
        raise AssertionError(
            "review must refuse write_module_source before validating args, got %r" % err)
    assert_no_diff()


@e2e_test(tool="write_module_source", kind="read")
def test_development_validates_write_arguments():
    require_enabled_profile(E2E_DEV_PROFILE)
    dev = profile_client(E2E_DEV_PROFILE)
    _init(dev)
    names = _tool_names(dev.tools_list())
    if "write_module_source" not in names:
        raise AssertionError("e2e-development must publish write_module_source: %r" % names)
    r = dev.call("write_module_source", {})
    err = r.error_text()
    if "not allowed" in err.lower():
        raise AssertionError(
            "development must reach argument validation, not a profile deny: %r" % err)
    if not r.is_error:
        raise AssertionError("empty write_module_source must still fail validation")
    assert_no_diff()


@e2e_test(tool="enable_toolset", kind="read")
def test_enable_toolset_does_not_change_explicit_profile_list():
    require_enabled_profile(E2E_REVIEW_PROFILE)
    review = profile_client(E2E_REVIEW_PROFILE)
    _init(review)
    before = _tool_names(review.tools_list())
    r = review.call("enable_toolset", {"toolset": "debug"})
    after = _tool_names(review.tools_list())
    if before != after:
        raise AssertionError(
            "enable_toolset must not change an explicit profile tools/list: %r -> %r"
            % (before, after))
    # Explicit endpoints reject enable_toolset as unsupported (PD stays on /mcp).
    # A missing-allowlist deny or a no-op success is also fine; the list must not change.
    if r.is_error:
        err = r.error_text().lower()
        if ("not allowed" not in err and "disabled" not in err and "unknown" not in err
                and "not supported" not in err):
            raise AssertionError("unexpected enable_toolset error on review: %r" % r.error_text())
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_disabled_profile_falls_back_and_is_omitted():
    client = profile_client(E2E_DISABLED_PROFILE)
    result = _init(client)
    instructions = result.get("instructions") or ""
    status = client.call("get_server_status", {"includeProfiles": True})
    assert_ok(status, "status after disabled-profile fallback")
    available = status.structured.get("availableProfiles") or []
    ids = [p.get("id") for p in available if isinstance(p, dict)]
    if E2E_DISABLED_PROFILE in ids:
        raise AssertionError("disabled profile must be omitted from availableProfiles: %r" % ids)
    active = status.structured.get("activeProfile") or {}
    if active.get("id") != "default":
        raise AssertionError("disabled profile must fall back to default: %r" % active)
    if E2E_DISABLED_PROFILE not in (active.get("requestedProfileId") or ""):
        # Workspace may not have the disabled profile at all — then this is unknown, not disabled.
        if "UNKNOWN_PROFILE" not in instructions and "DISABLED_PROFILE" not in instructions:
            raise AssertionError(
                "expected fallback for %s, instructions=%r active=%r"
                % (E2E_DISABLED_PROFILE, instructions, active))
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_path_bound_session_is_rejected_on_another_profile():
    require_enabled_profile(E2E_REVIEW_PROFILE)
    review = profile_client(E2E_REVIEW_PROFILE)
    _init(review)
    if not review.session_id:
        raise AssertionError("review initialize must mint a session")
    other = McpClient("/mcp")
    other.session_id = review.session_id
    raw = other.tools_list()
    if isinstance(raw, dict) and "result" in raw:
        raise AssertionError(
            "replaying a review session on /mcp must not list tools, got %r" % raw)
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_two_profiles_stay_alive_without_server_restart():
    """Ordinary traffic on one profile must not kick the other (Apply-only kick)."""
    require_enabled_profile(E2E_REVIEW_PROFILE)
    review = profile_client(E2E_REVIEW_PROFILE)
    _init(review)
    default = default_client()
    status_default = default.call("get_server_status", {})
    assert_ok(status_default, "default still answers after review initialize")
    status_review = review.call("get_server_status", {})
    assert_ok(status_review, "review still answers after default status")
    if (status_review.structured or {}).get("activeProfile", {}).get("id") != E2E_REVIEW_PROFILE:
        raise AssertionError("review surface changed without Apply: %r" % status_review.structured)
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_proxy_slash_and_unknown_path_are_http_400():
    require_proxy()
    slash = http_post_status("/mcp/", host=MCP_PROXY_HOST, port=MCP_PROXY_PORT)
    if slash[0] != 400:
        raise AssertionError(
            "proxy /mcp/ must be HTTP 400, got %s body=%r" % (slash[0], slash[1][:300]))
    unknown = http_post_status("/mcp/not-a-profile", host=MCP_PROXY_HOST, port=MCP_PROXY_PORT)
    if unknown[0] != 400:
        raise AssertionError(
            "proxy invalid path must be HTTP 400, got %s body=%r" % (unknown[0], unknown[1][:300]))
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_proxy_repeats_review_isolation_and_status_contract():
    require_proxy()
    require_enabled_profile(E2E_REVIEW_PROFILE)
    default = proxy_client("/mcp")
    _init(default)
    review = proxy_profile_client(E2E_REVIEW_PROFILE)
    result = _init(review)
    name = ((result.get("serverInfo") or {}).get("name") or "")
    if E2E_REVIEW_PROFILE not in name:
        raise AssertionError(
            "proxy explicit initialize must name the profile, got serverInfo.name=%r" % name)
    default_names = set(_tool_names(default.tools_list()))
    review_names = set(_tool_names(review.tools_list()))
    if default_names == review_names:
        raise AssertionError("proxy review tools/list must differ from /mcp")
    denied = review.call("write_module_source", {})
    if not denied.is_error:
        raise AssertionError("proxy review deny must set isError:true")
    status = review.call("get_server_status", {"includeProfiles": True})
    assert_ok(status, "proxy review status")
    profiles = status.structured.get("availableProfiles") or []
    if not profiles:
        raise AssertionError("proxy must pass through availableProfiles")
    first = profiles[0]
    for key in ("id", "endpoint"):
        if key not in first:
            raise AssertionError("proxy profile entry missing %s: %r" % (key, first))
    if "displayName" not in first and "revision" not in first and "allowedToolCount" not in first:
        raise AssertionError(
            "proxy must not strip diagnostic profile fields: %r" % first)
    active = status.structured.get("activeProfile") or {}
    if active.get("id") != E2E_REVIEW_PROFILE:
        raise AssertionError("proxy review activeProfile.id=%r" % active.get("id"))
    assert_no_diff()


@e2e_test(tool="get_server_status", kind="read")
def test_proxy_include_profile_tools_requires_include_profiles():
    require_proxy()
    client = proxy_client("/mcp")
    _init(client)
    denied = client.call("get_server_status", {"includeProfileTools": True})
    if not denied.is_error:
        raise AssertionError(
            "proxy includeProfileTools without includeProfiles must fail")
    assert_no_diff()
