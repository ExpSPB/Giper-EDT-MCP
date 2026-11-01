"""Exercise the preset entry point and its actual client routing without network I/O."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest import mock


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location('preset_route_harness', E2E_DIR / 'harness.py')
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location(
    'preset_route_entrypoint', E2E_DIR / 'tools/test_preset_launch.py')
LAUNCH = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict('sys.modules', {'harness': HARNESS}):
    TOOL_SPEC.loader.exec_module(LAUNCH)


class PresetStandaloneRouteTest(unittest.TestCase):
    def _run_entrypoint(self, profile_id):
        client = HARNESS.McpClient(host='127.0.0.1', port=9891)
        observed = []

        def exchange(body, method=None, params=None):
            request = json.loads(body)
            observed.append((request['method'], client.path, client.url, HARNESS.MCP_URL))
            if request['method'] == 'notifications/initialized':
                return 202, {}, ''
            if request['method'] == 'initialize':
                result = {'protocolVersion': HARNESS.PROTOCOL_VERSION, 'capabilities': {},
                          'serverInfo': {'name': 'offline-route-check', 'version': '1'}}
                headers = {'Mcp-Session-Id': 'owned-offline-session'}
            elif request['method'] == 'tools/list':
                result = {'tools': [
                    {'name': name, 'annotations': {'readOnlyHint': True}}
                    for name in ('get_server_status', 'list_projects')
                ]}
                headers = {}
            else:
                raise AssertionError('Unexpected entry-point request: ' + request['method'])
            return 200, headers, json.dumps({'jsonrpc': '2.0', 'id': request['id'], 'result': result})

        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.dict(os.environ, {
                'EDT_MCP_TOOL_PRESET': 'analysis-only',
                'EDT_MCP_PRESET_PROFILE_ALLOWLIST': 'get_server_status,list_projects',
                'EDT_MCP_PRESET_PROFILE_ID': profile_id,
            }))
            stack.enter_context(mock.patch.object(HARNESS, '_DEFAULT_CLIENT', client))
            stack.enter_context(mock.patch.object(HARNESS, 'MCP_URL', 'http://127.0.0.1:9891/mcp'))
            stack.enter_context(mock.patch.object(HARNESS, 'MCP_HOST', '127.0.0.1'))
            stack.enter_context(mock.patch.object(HARNESS, 'MCP_PORT', '9891'))
            stack.enter_context(mock.patch.object(LAUNCH, 'all_fixtures_status', return_value=''))
            stack.enter_context(mock.patch.object(LAUNCH, 'assert_no_diff'))
            stack.enter_context(mock.patch.object(LAUNCH, 'wait_for_server'))
            stack.enter_context(mock.patch.object(client, '_http_post', side_effect=exchange))
            stack.enter_context(mock.patch.object(HARNESS.urllib.request, 'urlopen',
                                                 side_effect=AssertionError('No network allowed')))
            output = stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            # Keep main, initialize, notify, both preset assertions and _post real.
            LAUNCH.main()

        path = '/mcp/profiles/' + profile_id if profile_id else '/mcp'
        url = 'http://127.0.0.1:9891' + path
        self.assertEqual(observed, [
            (method, path, url, url)
            for method in ('initialize', 'notifications/initialized', 'tools/list', 'tools/list')
        ])
        self.assertEqual(client.session_id, 'owned-offline-session')
        self.assertEqual(output.getvalue().count('[PASS]'), 2)
        self.assertNotIn('[SKIP]', output.getvalue())

    def test_legacy_route_initializes_and_lists_on_the_actual_default_client(self):
        self._run_entrypoint('')

    def test_explicit_profile_routes_handshake_and_both_lists_to_the_selected_client(self):
        self._run_entrypoint('e2e-review')


if __name__ == '__main__':
    unittest.main()
