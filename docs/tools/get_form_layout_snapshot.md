# get_form_layout_snapshot

Inspect the calculated visual layout of an EDT form. Parameters and examples: get_tool_guide('get_form_layout_snapshot').

## Parameters
| Parameter | Required | Type | Description |
| --- | --- | --- | --- |
| projectName | — | string | EDT project name. Required when formPath is specified. |
| formPath | — | string | Form FQN (e.g. 'Catalog.Products.Forms.ItemForm' or 'CommonForm.MyForm'); if omitted, uses the active form editor. |
| showElement | — | string | Name of a form element (e.g. an element on a non-default page) whose calculated bounds the snapshot must contain; returned as shownElement, or an error when it has none. |
| refresh | — | boolean | Force WYSIWYG refresh before snapshot (default: true) |
| mode | — | string (one of: compact, full) | Output mode: 'compact' (default, visible elements only) or 'full' (all nodes/properties) |

## Guide
Returns a YAML snapshot of a form's **calculated WYSIWYG layout**: per-element bounds (x/y/width/height), element types, and display-affecting properties, plus the overall form size. The response type is TEXT (the YAML itself).

## When to use
- Inspect or compare what a form actually lays out (positions, sizes, visibility) rather than its declarative `.form` definition.
- Verify a layout change took effect, or diff two states of the same form.
- For a rendered PNG instead of layout data, use `get_form_screenshot`.

## Render-mode flags (read this first)
No buffered-render JVM flag is required for this tool's element tree or form-level size. Per-element bounds are governed by EDT's **native form layout render mode**, exposed as `nativeFormLayoutRender`:
- Native render **on** (`-DnativeFormLayoutRender=true`, also EDT's default when the property is absent): EDT computes the layout in its C++ visualizer. It does not return per-element rectangles to the Java `modelProjection` / `layoutProjection` / `viewProjection` chain used by this tool, so `elementsWithBounds: 0` is structural rather than a rendering delay. Retrying or using `refresh: true` cannot populate those bounds.
- Native render **off** (`-DnativeFormLayoutRender=false`): EDT populates the Java projections. A zero-bounds result can then mean that the form has not finished rendering; retry or use `refresh: true`.

`-DnativeFormBufferedLayoutRender=true` is a different knob. It enables the offscreen buffered image used by `get_form_screenshot`; it does not make `get_form_layout_snapshot` produce per-element bounds. Use `get_server_status` → `formRenderFlags.nativeFormLayoutRender.atStartup` to see the native mode captured at EDT startup: `on`, `off`, or `unknown`. The layout-snapshot warning itself probes the current live mode, which is fixed by EDT's launch configuration and requires an EDT restart to change. Do not base the decision on `requested`, which is only the raw system-property value currently set and can be absent. If native rendering is on and you need the form's element hierarchy rather than pixel rectangles, call `get_metadata_details` with the form FQN to inspect the element tree and its nesting.

## Parameter details
- `projectName` - EDT project name. **Required when `formPath` is specified**; omitting it then returns an error. Ignored when targeting the active editor.
- `formPath` - metadata FQN of the form. If given, the tool opens and activates that form automatically. If omitted, the currently active form editor is used.
- `refresh` - re-render the form before capturing; default `true`. The bounds and the form size then come from one layout produced during the call; if the form cannot be re-rendered, a warning says the layout may predate the call. Set `false` to read the last-rendered state without re-laying-out.
- `mode` - `compact` (default) or `full`; an unknown value returns an error.
- `showElement` - programmatic name of a form element (case-insensitive; the name, not the title), typically one on a non-default page. Nothing is switched in the editor: with `-DnativeFormLayoutRender=false` EDT lays out every page of a Pages group, so the elements of every page already have bounds in the snapshot (pages share their Pages group's area, so their bounds overlap). `showElement` confirms the element has calculated bounds and returns them as `shownElement`; if it has none, the call fails and says why. In native render mode no element has bounds on any page, so a known name is refused and points at `get_form_screenshot` with `showElement` on EDT versions supporting page switching (to see the page), at `get_metadata_details` for the element tree, or at relaunching EDT with `-DnativeFormLayoutRender=false`. An unknown name fails in every render mode with an error that names the form and lists its pages (at most 20).

### EDT compatibility
On **EDT 2025.2.5**, the screenshot alternative cannot switch to another page:
`get_form_screenshot` refuses `showElement` inside Pages because this SDK lacks
safe selection-frame clearing and selection restoration. Ordinary current-page
screenshots and the element tree remain available under their usual render
requirements. Use `get_metadata_details` to inspect elements on every page; for
calculated per-element bounds, relaunch with `-DnativeFormLayoutRender=false`.

### formPath format
`MetadataType.ObjectName.Forms.FormName`, or `CommonForm.FormName` for a common form. Examples:
- `Catalog.Products.Forms.ItemForm`
- `Document.SalesOrder.Forms.DocumentForm`
- `CommonForm.MyForm`

## Modes
- `compact` (default) - only visual elements with positive bounds, and only selected display-affecting properties. Best for a readable overview.
- `full` - every layout node (including zero-bounds/structural ones) and all non-containment properties. Verbose; use when you need the complete tree.

## Examples
- Active editor, default compact: `{}`.
- Specific form: `{projectName: "MyProj", formPath: "Catalog.Products.Forms.ItemForm"}`.
- Full tree, no refresh: `{formPath: "CommonForm.MyForm", projectName: "MyProj", mode: "full", refresh: false}`.
- Bounds of an element on another page: `{projectName: "MyProj", formPath: "Document.SalesOrder.Forms.DocumentForm", showElement: "DeliveryAddress"}`.

## Notes & gotchas
- Blank result => the `-DnativeFormBufferedLayoutRender=true` flag is missing (see above), not a failure of this call.
- `formPath` without `projectName` is rejected: "projectName is required when formPath is specified".
- Needs a live workbench Display; runs on the UI thread.
- A "No calculated element bounds were found" warning is mode-specific. In native render mode EDT does not produce Java-side per-element bounds at all (structural, not transient) — retrying will not help; use `get_metadata_details` for the element tree, or relaunch with `-DnativeFormLayoutRender=false` (a trade-off: native render is what `get_form_screenshot`'s image path uses). When native render is off, the warning may mean the form had not finished rendering yet — retry or ensure `refresh` is `true`.

---
*Generated from the live MCP server (`get_tool_guide`) by `docs/generate_tool_docs.py`. Do not edit this file. Edit the tool's description/schema in its Java source and its guide body in `mcp/bundles/fm.giper.edt.mcp.server/guides/<tool>.md`.*
