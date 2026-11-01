"""Raw HTTP session and Origin contract of the installed Giper plugin.

Adapted from upstream transport_session (#564/#561). The normal harness parses
HTTP errors into JSON-RPC bodies, so these tests inspect the status themselves.
Legacy /mcp retains sessionless compatibility; explicit profiles require a session.
Plugin DELETE rejects unknown, deleted and cross-path sessions with 404. The
proxy's idempotent unknown DELETE is a separate contract: MCP_PORT must point to
the plugin, not the proxy. No named-profile fixture is needed for reserved default.

Every initialized session belongs to this module and is closed on its original
path in finally, even on assertion failure. The harness's session is never read,
replaced or deleted. No model or preference mutation is sent. Run on an owned
stand whose default profile has not been hot-applied since startup (a hot Apply
deliberately revokes legacy sessionless access until clients initialize again).
"""

from contextlib import contextmanager
import json
import urllib.error
import urllib.parse
import urllib.request

import harness
from harness import assert_no_diff, e2e_test, _fail


PROTOCOL_VERSION = harness.PROTOCOL_VERSION
SESSION_HEADER = "Mcp-Session-Id"
LEGACY_PATH = "/mcp"
EXPLICIT_PATH = "/mcp/profiles/default"
# Server-minted ids are UUIDs; this value cannot name another client's session.
UNKNOWN_SESSION = "e2e-transport-never-issued"


class _Headers(dict):
    """JDK HttpServer may change header casing; HTTP names remain case-insensitive."""

    def __init__(self, message):
        super().__init__(message.items())
        self._lower = {key.lower(): value for key, value in message.items()}

    def get(self, name, default=None):
        return self._lower.get(name.lower(), default)


def _raw(method, params=None, session=None, origin=None, http_method="POST",
         request_id=1, path=LEGACY_PATH, rpc_version="2.0"):
    """One bounded request returning (HTTP status, headers, decoded body).

    Preserve the configured plugin authority while choosing the path explicitly;
    no cached harness client/session or retry can conceal an expected refusal.
    """
    headers = {
        "Content-Type": "application/json; charset=utf-8",
        "Accept": "application/json, text/event-stream",
        "MCP-Protocol-Version": PROTOCOL_VERSION,
    }
    if session:
        headers[SESSION_HEADER] = session
    if origin is not None:
        headers["Origin"] = origin
    data = None
    if http_method == "POST":
        data = json.dumps({
            "jsonrpc": rpc_version, "id": request_id,
            "method": method, "params": params or {},
        }).encode("utf-8")
    configured = urllib.parse.urlsplit(harness.MCP_URL)
    url = urllib.parse.urlunsplit((configured.scheme, configured.netloc, path, "", ""))
    request = urllib.request.Request(url, data=data, headers=headers, method=http_method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, _Headers(response.headers), _body(response.read())
    except urllib.error.HTTPError as error:
        with error:
            return error.code, _Headers(error.headers), _body(error.read())


def _body(raw):
    text = raw.decode("utf-8", "replace").strip()
    if not text:
        return None
    if text.startswith("event:") or text.startswith("data:"):
        for line in text.splitlines():
            if line.startswith("data:"):
                text = line[len("data:"):].strip()
                break
    try:
        return json.loads(text)
    except ValueError:
        return {"_unparsed": text}


def _close_owned(session, path):
    status, _, body = _raw(None, session=session, http_method="DELETE", path=path)
    # Tests may already have deleted this exact session. No other status confirms
    # cleanup; in particular this must not silently accept a proxy's wrong path.
    if status not in (200, 404):
        _fail("cleanup of own session on %s failed: HTTP %s, %r" % (path, status, body))


@contextmanager
def _session(path=EXPLICIT_PATH):
    status, headers, body = _raw("initialize", {
        "protocolVersion": PROTOCOL_VERSION,
        "capabilities": {},
        "clientInfo": {"name": "e2e-transport-session", "version": "1.0"},
    }, path=path)
    session = headers.get(SESSION_HEADER)
    try:
        if status != 200 or "result" not in (body or {}):
            _fail("initialize on %s must succeed, got HTTP %s: %r" % (path, status, body))
        if not session:
            _fail("initialize on %s must issue %s" % (path, SESSION_HEADER))
        yield session
    finally:
        if session:
            _close_owned(session, path)


def _expect_tools(status, body, context):
    if status != 200 or not (body or {}).get("result", {}).get("tools"):
        _fail("%s must return tools with HTTP 200, got %s: %r" % (context, status, body))


@e2e_test(tool="_transport_session", kind="read")
def test_initialize_issues_a_session_that_then_works():
    for path in (LEGACY_PATH, EXPLICIT_PATH):
        with _session(path) as session:
            status, _, body = _raw("tools/list", session=session, request_id=2, path=path)
            _expect_tools(status, body, "issued session on " + path)
    assert_no_diff("session handshakes must not mutate the project")


@e2e_test(tool="_transport_session", kind="read")
def test_a_failed_initialize_issues_no_session():
    for path in (LEGACY_PATH, EXPLICIT_PATH):
        status, headers, body = _raw("initialize", {
            "protocolVersion": PROTOCOL_VERSION, "capabilities": {},
        }, path=path, rpc_version="1.0")
        issued = headers.get(SESSION_HEADER)
        try:
            if status != 200 or "error" not in (body or {}):
                _fail("invalid JSON-RPC initialize must return a protocol error, got %s: %r"
                      % (status, body))
            if issued:
                _fail("a failed initialize must issue no %s, got %r" % (SESSION_HEADER, issued))
        finally:
            if issued:
                _close_owned(issued, path)


@e2e_test(tool="_transport_session", kind="read")
def test_legacy_mcp_retains_sessionless_compatibility():
    status, headers, body = _raw("tools/list", path=LEGACY_PATH)
    _expect_tools(status, body, "legacy /mcp without a session")
    if headers.get(SESSION_HEADER):
        _fail("a sessionless tools/list must not mint an initialize session")


@e2e_test(tool="_transport_session", kind="read")
def test_an_explicit_profile_without_a_session_is_refused_with_400():
    status, _, body = _raw("tools/list", request_id=3, path=EXPLICIT_PATH)
    if status != 400:
        _fail("explicit profile with no session must answer 400, got %s: %r" % (status, body))
    message = json.dumps(body or {})
    if SESSION_HEADER.lower() not in message.lower():
        _fail("missing-session refusal must name the missing header: %s" % message)


@e2e_test(tool="_transport_session", kind="read")
def test_an_unknown_session_is_refused_with_404():
    for path in (LEGACY_PATH, EXPLICIT_PATH):
        status, _, body = _raw("tools/list", session=UNKNOWN_SESSION, path=path)
        if status != 404 or not isinstance(body, dict) or "session" not in str(body.get("error", "")).lower():
            _fail("unknown session on %s must answer 404 with a session error, got %s: %r"
                  % (path, status, body))


@e2e_test(tool="_transport_session", kind="read")
def test_delete_terminates_the_session_and_repeated_delete_is_404():
    for path in (LEGACY_PATH, EXPLICIT_PATH):
        with _session(path) as session:
            status, _, body = _raw(None, session=session, http_method="DELETE", path=path)
            if status != 200:
                _fail("DELETE of an owned session on %s must answer 200, got %s: %r"
                      % (path, status, body))
            status, _, body = _raw("tools/list", session=session, request_id=5, path=path)
            if status != 404:
                _fail("a terminated session on %s must answer 404, got %s: %r"
                      % (path, status, body))
            status, _, body = _raw(None, session=session, http_method="DELETE", path=path)
            if status != 404:
                _fail("plugin repeated DELETE must answer 404, got %s: %r" % (status, body))


@e2e_test(tool="_transport_session", kind="read")
def test_delete_of_an_unknown_session_is_404():
    for path in (LEGACY_PATH, EXPLICIT_PATH):
        status, _, body = _raw(None, session=UNKNOWN_SESSION, http_method="DELETE", path=path)
        if status != 404:
            _fail("plugin unknown DELETE on %s must answer 404, got %s: %r" % (path, status, body))


@e2e_test(tool="_transport_session", kind="read")
def test_cross_path_post_and_delete_are_404_without_closing_the_owner():
    pairs = ((LEGACY_PATH, EXPLICIT_PATH), (EXPLICIT_PATH, LEGACY_PATH),
             (EXPLICIT_PATH, "/mcp/profiles/zz-e2e-transport-other"))
    for owner_path, other_path in pairs:
        with _session(owner_path) as session:
            for http_method in ("POST", "DELETE"):
                status, _, body = _raw("tools/list", session=session,
                                       http_method=http_method, path=other_path)
                if status != 404:
                    _fail("%s replay from %s to %s must answer 404, got %s: %r"
                          % (http_method, owner_path, other_path, status, body))
            status, _, body = _raw("tools/list", session=session, path=owner_path)
            _expect_tools(status, body, "original session after cross-path refusals")
    assert_no_diff("cross-path refusals must not mutate the project")


@e2e_test(tool="_transport_session", kind="read")
def test_two_sessions_are_independent():
    with _session() as first, _session() as second:
        if first == second:
            _fail("each initialize must mint its own session id")
        status, _, body = _raw(None, session=first, http_method="DELETE", path=EXPLICIT_PATH)
        if status != 200:
            _fail("first session deletion failed: %s: %r" % (status, body))
        status, _, body = _raw("tools/list", session=second, request_id=6, path=EXPLICIT_PATH)
        _expect_tools(status, body, "second session after first session DELETE")


@e2e_test(tool="_transport_session", kind="read")
def test_loopback_origins_are_served_with_matching_cors_grants():
    with _session() as session:
        for origin in ("http://localhost:3000", "http://127.0.0.1:8765", "http://[::1]:3000"):
            status, headers, body = _raw("tools/list", session=session, origin=origin,
                                        request_id=7, path=EXPLICIT_PATH)
            _expect_tools(status, body, "loopback Origin " + origin)
            if headers.get("Access-Control-Allow-Origin") != origin:
                _fail("served loopback Origin must receive its matching CORS grant: %r" % headers)


@e2e_test(tool="_transport_session", kind="read")
def test_attacker_reachable_origins_are_403_without_cors_grants():
    with _session() as session:
        for origin in ("null", "file:///C:/page.html", "vscode-webview://abc123",
                       "https://evil.example.com", "http://localhost.attacker.com"):
            status, headers, body = _raw("tools/list", session=session, origin=origin,
                                        request_id=8, path=EXPLICIT_PATH)
            if status != 403:
                _fail("Origin %r must be refused with 403, got %s: %r" % (origin, status, body))
            if headers.get("Access-Control-Allow-Origin"):
                _fail("a refused Origin must receive no CORS grant: %r" % headers)


@e2e_test(tool="_transport_session", kind="read")
def test_a_refused_origin_gets_no_cors_grant_even_without_a_session():
    status, headers, body = _raw("tools/list", origin="null", request_id=9, path=EXPLICIT_PATH)
    if status != 403 or headers.get("Access-Control-Allow-Origin"):
        _fail("Origin refusal must precede missing-session400 and grant no CORS: %s, %r, %r"
              % (status, headers, body))
    assert_no_diff("a refused request must not mutate the project")
