/**
 * MCP Server for EDT
 * Copyright (C) 2026 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fm.giper.edt.mcp.server.profiles.PreferenceToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;

/** Exercises the real profile controls using an isolated preference store and SWT shell. */
public class ToolsTabProfilesTest
{
    private static final String PROFILE_ID = "qa-review"; //$NON-NLS-1$
    private static final String EDITABLE_TOOL = "list_projects"; //$NON-NLS-1$

    private PreferenceStore store;
    private PreferenceToolProfileRepository repository;
    private Display display;
    private boolean ownsDisplay;
    private Shell shell;
    private ToolsTab tab;

    @Before
    public void setUp() throws Exception
    {
        assumeTrue("SWT UI tests require Windows or an available display", //$NON-NLS-1$
            System.getProperty("os.name").startsWith("Windows") //$NON-NLS-1$
                || (System.getenv("DISPLAY") != null && !System.getenv("DISPLAY").isBlank())
                || (System.getenv("WAYLAND_DISPLAY") != null && !System.getenv("WAYLAND_DISPLAY").isBlank()));

        store = new PreferenceStore();
        repository = new PreferenceToolProfileRepository(store);
        ToolProfileSnapshot initial = ToolProfileSnapshot.of(0L, List.of(
            ToolProfile.builder()
                .id(ToolProfile.DEFAULT_ID)
                .displayName("Default") //$NON-NLS-1$
                .description("Compatibility endpoint") //$NON-NLS-1$
                .enabled(true)
                .allowedTools(Set.of("get_server_status")) //$NON-NLS-1$
                .build(),
            ToolProfile.builder()
                .id(PROFILE_ID)
                .displayName("QA review") //$NON-NLS-1$
                .description("Focused profile") //$NON-NLS-1$
                .enabled(true)
                .allowedTools(Set.of("get_server_status")) //$NON-NLS-1$
                .build()));
        assertTrue("fixture profile document must be accepted", repository.replaceAll(0L, initial).isAccepted()); //$NON-NLS-1$

        display = Display.getCurrent();
        ownsDisplay = display == null;
        if (ownsDisplay)
        {
            display = new Display();
        }
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
        tab = new ToolsTab(shell, repository, Set.of("get_server_status", EDITABLE_TOOL)); //$NON-NLS-1$
        shell.setSize(1200, 800);
        shell.open();
        drainEvents();
    }

    @After
    public void tearDown()
    {
        if (tab != null)
        {
            tab.dispose();
        }
        if (shell != null && !shell.isDisposed())
        {
            shell.dispose();
        }
        if (ownsDisplay && display != null && !display.isDisposed())
        {
            display.dispose();
        }
    }

    @Test
    public void profileSelectionUpdatesEndpointAndToolEditsWaitForSave() throws Exception
    {
        Combo profileCombo = findProfileCombo(shell, PROFILE_ID);
        selectProfile(profileCombo, PROFILE_ID);
        assertTrue("selected profile endpoint should be displayed", endpointText(shell).contains("/mcp/profiles/" + PROFILE_ID)); //$NON-NLS-1$

        ToolProfile publishedBefore = repository.getSnapshot().get(PROFILE_ID);
        CheckboxTreeViewer viewer = treeViewer();
        viewer.expandToLevel(ToolGroup.getGroupForTool(EDITABLE_TOOL), 1);
        drainEvents();
        Tree tree = findTree(shell);
        assertNotNull("profile tool tree should exist", tree); //$NON-NLS-1$
        TreeItem item = findItem(tree.getItems(), EDITABLE_TOOL);
        assertNotNull("configured tool should appear in the checkbox tree", item); //$NON-NLS-1$
        assertFalse("tool starts disabled in the selected profile", item.getChecked()); //$NON-NLS-1$

        item.setChecked(true);
        Event check = new Event();
        check.type = SWT.Selection;
        check.detail = SWT.CHECK;
        check.item = item;
        tree.notifyListeners(SWT.Selection, check);
        drainEvents();

        assertFalse("checkbox edit must remain a local draft until Save", //$NON-NLS-1$
            repository.getSnapshot().get(PROFILE_ID).getAllowedTools().contains(EDITABLE_TOOL));
        assertTrue("tab should report pending profile changes", tab.hasChanges()); //$NON-NLS-1$

        assertTrue("profile save should be accepted", tab.saveProfiles().isAccepted()); //$NON-NLS-1$
        assertTrue(repository.getSnapshot().get(PROFILE_ID).getAllowedTools().contains(EDITABLE_TOOL));
        assertNotEquals(publishedBefore.getRevision(), repository.getSnapshot().get(PROFILE_ID).getRevision());
    }

    @Test
    public void reloadProfilesDiscardsUnsavedCheckboxEdits() throws Exception
    {
        Combo profileCombo = findProfileCombo(shell, PROFILE_ID);
        selectProfile(profileCombo, PROFILE_ID);
        CheckboxTreeViewer viewer = treeViewer();
        viewer.expandToLevel(ToolGroup.getGroupForTool(EDITABLE_TOOL), 1);
        drainEvents();
        Tree tree = findTree(shell);
        assertNotNull("profile tool tree should exist", tree); //$NON-NLS-1$
        TreeItem item = findItem(tree.getItems(), EDITABLE_TOOL);
        assertNotNull(item);

        item.setChecked(true);
        Event check = new Event();
        check.type = SWT.Selection;
        check.detail = SWT.CHECK;
        check.item = item;
        tree.notifyListeners(SWT.Selection, check);
        drainEvents();
        assertTrue(tab.hasChanges());

        tab.reloadProfiles();
        viewer.expandToLevel(ToolGroup.getGroupForTool(EDITABLE_TOOL), 1);
        drainEvents();
        Tree reloadedTree = findTree(shell);
        assertNotNull("profile tool tree should exist after reload", reloadedTree); //$NON-NLS-1$
        TreeItem reloadedItem = findItem(reloadedTree.getItems(), EDITABLE_TOOL);
        assertNotNull(reloadedItem);
        assertFalse("reload should restore the published checkbox value", reloadedItem.getChecked()); //$NON-NLS-1$
        assertFalse(tab.hasChanges());
        assertFalse(repository.getSnapshot().get(PROFILE_ID).getAllowedTools().contains(EDITABLE_TOOL));
    }

    private static void selectProfile(Combo combo, String profileId)
    {
        int index = -1;
        String[] items = combo.getItems();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i].contains("[" + profileId + "]")) //$NON-NLS-1$
            {
                index = i;
                break;
            }
        }
        assertTrue("profile combo should contain id " + profileId, index >= 0); //$NON-NLS-1$
        combo.select(index);
        Event selection = new Event();
        selection.type = SWT.Selection;
        combo.notifyListeners(SWT.Selection, selection);
    }

    private static Combo findProfileCombo(Composite root, String profileId)
    {
        for (Control child : root.getChildren())
        {
            if (child instanceof Combo combo)
            {
                for (String item : combo.getItems())
                {
                    if (item.contains("[" + profileId + "]")) //$NON-NLS-1$
                    {
                        return combo;
                    }
                }
            }
            if (child instanceof Composite composite)
            {
                Combo found = findProfileCombo(composite, profileId);
                if (found != null)
                {
                    return found;
                }
            }
        }
        fail("could not find profile combo for " + profileId); //$NON-NLS-1$
        return null;
    }

    private static String endpointText(Composite root)
    {
        for (Control child : root.getChildren())
        {
            if (child instanceof Text text && text.getText().contains("/mcp")) //$NON-NLS-1$
            {
                return text.getText();
            }
            if (child instanceof Label label && label.getText().contains("/mcp")) //$NON-NLS-1$
            {
                return label.getText();
            }
            if (child instanceof Composite composite)
            {
                String found = endpointTextOrNull(composite);
                if (found != null)
                {
                    return found;
                }
            }
        }
        fail("profile endpoint path was not displayed"); //$NON-NLS-1$
        return ""; //$NON-NLS-1$
    }

    private static String endpointTextOrNull(Composite root)
    {
        for (Control child : root.getChildren())
        {
            if (child instanceof Text text && text.getText().contains("/mcp")) //$NON-NLS-1$
            {
                return text.getText();
            }
            if (child instanceof Label label && label.getText().contains("/mcp")) //$NON-NLS-1$
            {
                return label.getText();
            }
            if (child instanceof Composite composite)
            {
                String found = endpointTextOrNull(composite);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static Tree findTree(Composite root)
    {
        for (Control child : root.getChildren())
        {
            if (child instanceof Tree tree)
            {
                return tree;
            }
            if (child instanceof Composite composite)
            {
                Tree found = findTreeOrNull(composite);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static Tree findTreeOrNull(Composite root)
    {
        for (Control child : root.getChildren())
        {
            if (child instanceof Tree tree)
            {
                return tree;
            }
            if (child instanceof Composite composite)
            {
                Tree found = findTreeOrNull(composite);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static TreeItem findItem(TreeItem[] items, String name)
    {
        for (TreeItem item : items)
        {
            if (name.equals(item.getText()))
            {
                return item;
            }
            TreeItem found = findItem(item.getItems(), name);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    private CheckboxTreeViewer treeViewer() throws Exception
    {
        Field field = ToolsTab.class.getDeclaredField("treeViewer"); //$NON-NLS-1$
        field.setAccessible(true);
        return (CheckboxTreeViewer) field.get(tab);
    }

    private void drainEvents()
    {
        while (display.readAndDispatch())
        {
            // Drain queued widget updates after selection or checkbox notification.
        }
    }
}
