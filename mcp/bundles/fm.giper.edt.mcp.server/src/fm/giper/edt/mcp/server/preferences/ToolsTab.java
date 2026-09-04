/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.preferences.ToolParameterSettings.ParameterDef;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.FileOpResult;
import fm.giper.edt.mcp.server.profiles.ToolProfileFileService;
import fm.giper.edt.mcp.server.profiles.ReplaceResult;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.utils.DestructiveConsentGate;

/**
 * Tools management tab for MCP Server preferences.
 * Profile drafts are edited through {@link ToolProfilesEditorModel}; the tree
 * and presets mutate the selected draft. Parameter values and destructive
 * consent stay global.
 */
public class ToolsTab
{
    private final Composite composite;
    private final ToolProfilesEditorModel model;
    private CheckboxTreeViewer treeViewer;
    private Combo profileCombo;
    private Combo presetCombo;
    private Label countLabel;
    private Label fallbackWarning;
    private Text displayNameText;
    private Text descriptionText;
    private Text endpointText;
    private Button enableButton;
    private Button deleteButton;
    private Composite detailPanel;

    /** Local copy of destructive-allowed tools for editing (committed on performOk) */
    private final Set<String> destructiveAllowedTools;

    /** Flag to avoid recursion when updating check states (SWT is single-threaded) */
    private boolean updatingChecks = false;
    private boolean updatingProfileFields = false;

    /** Track created images for disposal */
    private final List<Image> managedImages = new ArrayList<>();

    // Parameter management (merged from ToolSettingsTab)
    private final ToolParameterSettings paramSettings = ToolParameterSettings.getInstance();
    /** Pending values: tool.param -> value */
    private final Map<String, Integer> pendingValues = new LinkedHashMap<>();
    /** Currently displayed spinners for the selected tool */
    private final List<Spinner> currentSpinners = new ArrayList<>();
    private String selectedTool;

    /**
     * Sibling General tab, used to read the PENDING (not-yet-committed) consent level so the
     * per-tool allow checkbox reflects the user's current combo choice, not the persisted store.
     * May be {@code null} (then the persisted level is used).
     */
    private GeneralTab generalTab;

    public ToolsTab(Composite parent)
    {
        model = ToolProfilesEditorModel.load(publishedSnapshot(), ToolProfilesEditorModel.catalogFromGroups());
        destructiveAllowedTools = new HashSet<>(ConsentSettingsService.getInstance().getAllowedTools());
        loadAllValues();

        composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        composite.setLayout(layout);

        createProfileBar(composite);

        SashForm sash = new SashForm(composite, SWT.HORIZONTAL);
        GridData sashGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        sashGd.widthHint = 550;
        sashGd.heightHint = 350;
        sash.setLayoutData(sashGd);

        // Left side: preset bar + checkbox tree + count label
        Composite left = new Composite(sash, SWT.NONE);
        GridLayout leftLayout = new GridLayout(1, false);
        leftLayout.marginWidth = 5;
        leftLayout.marginHeight = 5;
        left.setLayout(leftLayout);
        createPresetBar(left);
        createToolTree(left);
        createCountLabel(left);

        // Right side: detail panel (description + settings)
        createDetailPanel(sash);

        sash.setWeights(new int[]{40, 60});

        // Full refresh only after tree / preset / count widgets exist.
        // createProfileBar() fills the profile fields earlier; calling refreshProfileUi()
        // there NPEs and Eclipse shows "The current page contains invalid values".
        refreshProfileUi();
    }

    public Composite getControl()
    {
        return composite;
    }

    /**
     * Links the sibling General tab so the per-tool destructive-consent checkbox can reflect the
     * PENDING consent level selected on that tab (before it is committed on {@code performOk}),
     * instead of the persisted store value.
     *
     * @param generalTab the sibling General tab, or {@code null} to fall back to the persisted level
     */
    public void setGeneralTab(GeneralTab generalTab)
    {
        this.generalTab = generalTab;
    }

    private void createProfileBar(Composite parent)
    {
        Group group = new Group(parent, SWT.NONE);
        group.setText(Messages.ToolsTab_Profile);
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        profileCombo = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
        profileCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        profileCombo.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (updatingProfileFields)
                {
                    return;
                }
                flushProfileFields();
                int idx = profileCombo.getSelectionIndex();
                if (idx >= 0 && idx < model.getDrafts().size())
                {
                    model.select(model.getDrafts().get(idx).getId());
                    refreshProfileUi();
                }
            }
        });

        Composite buttons = new Composite(group, SWT.NONE);
        GridLayout buttonsLayout = new GridLayout(8, false);
        buttonsLayout.marginWidth = 0;
        buttonsLayout.marginHeight = 0;
        buttons.setLayout(buttonsLayout);
        addActionButton(buttons, Messages.ToolsTab_Add, this::addProfile);
        addActionButton(buttons, Messages.ToolsTab_Duplicate, this::duplicateProfile);
        addActionButton(buttons, Messages.ToolsTab_Rename, this::renameProfile);
        enableButton = addActionButton(buttons, Messages.ToolsTab_Disable, this::toggleEnabled);
        deleteButton = addActionButton(buttons, Messages.ToolsTab_Delete, this::deleteProfile);
        addActionButton(buttons, Messages.ToolsTab_RestoreSelected, this::restoreSelected);
        addActionButton(buttons, Messages.ToolsTab_ExportProfile, this::exportProfile);
        addActionButton(buttons, Messages.ToolsTab_ImportProfile, this::importProfile);

        Label nameLabel = new Label(group, SWT.NONE);
        nameLabel.setText(Messages.ToolsTab_DisplayName);
        displayNameText = new Text(group, SWT.BORDER);
        displayNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        displayNameText.addModifyListener(profileFieldListener(true));

        Label descLabel = new Label(group, SWT.NONE);
        descLabel.setText(Messages.ToolsTab_Description);
        descriptionText = new Text(group, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData descGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        descGd.heightHint = 40;
        descriptionText.setLayoutData(descGd);
        descriptionText.setToolTipText(Messages.ToolsTab_DescriptionHint);
        descriptionText.addModifyListener(profileFieldListener(false));

        Label hint = new Label(group, SWT.WRAP);
        hint.setText(Messages.ToolsTab_DescriptionHint);
        GridData hintGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        hintGd.horizontalSpan = 2;
        hint.setLayoutData(hintGd);

        Label endpointLabel = new Label(group, SWT.NONE);
        endpointLabel.setText(Messages.ToolsTab_Endpoint);
        Composite endpointRow = new Composite(group, SWT.NONE);
        endpointRow.setLayout(new GridLayout(2, false));
        endpointRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        endpointText = new Text(endpointRow, SWT.BORDER | SWT.READ_ONLY);
        endpointText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button copy = new Button(endpointRow, SWT.PUSH);
        copy.setText(Messages.ToolsTab_CopyUrl);
        copy.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                copyEndpointUrl();
            }
        });

        fallbackWarning = new Label(group, SWT.WRAP);
        fallbackWarning.setText(Messages.ToolsTab_DefaultFallbackWarning);
        GridData warnGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        warnGd.horizontalSpan = 2;
        fallbackWarning.setLayoutData(warnGd);

        refreshProfileFields();
    }

    private Button addActionButton(Composite parent, String text, Runnable action)
    {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                action.run();
            }
        });
        return button;
    }

    private ModifyListener profileFieldListener(boolean displayName)
    {
        return new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent e)
            {
                if (updatingProfileFields)
                {
                    return;
                }
                if (displayName)
                {
                    model.renameDisplayName(displayNameText.getText());
                }
                else
                {
                    model.setDescription(descriptionText.getText());
                }
            }
        };
    }

    private void addProfile()
    {
        flushProfileFields();
        ToolProfileDialog dialog = new ToolProfileDialog(composite.getShell(),
            ToolProfileDialog.Mode.ADD, "", "", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (dialog.open() == ToolProfileDialog.OK)
        {
            showIfFailed(model.add(dialog.getProfileId(), dialog.getDisplayName(),
                dialog.getDescription(), dialog.getPreset()));
            refreshProfileUi();
        }
    }

    private void duplicateProfile()
    {
        flushProfileFields();
        ToolProfile selected = model.getSelected();
        if (selected == null)
        {
            return;
        }
        ToolProfileDialog dialog = new ToolProfileDialog(composite.getShell(),
            ToolProfileDialog.Mode.DUPLICATE, selected.getId() + "-copy", //$NON-NLS-1$
            selected.getDisplayName() + " copy", selected.getDescription()); //$NON-NLS-1$
        if (dialog.open() == ToolProfileDialog.OK)
        {
            showIfFailed(model.duplicate(dialog.getProfileId(), dialog.getDisplayName()));
            if (dialog.getDescription() != null)
            {
                model.setDescription(dialog.getDescription());
            }
            refreshProfileUi();
        }
    }

    private void renameProfile()
    {
        flushProfileFields();
        ToolProfile selected = model.getSelected();
        if (selected == null)
        {
            return;
        }
        ToolProfileDialog dialog = new ToolProfileDialog(composite.getShell(),
            ToolProfileDialog.Mode.RENAME, selected.getId(), selected.getDisplayName(),
            selected.getDescription());
        if (dialog.open() == ToolProfileDialog.OK)
        {
            showIfFailed(model.renameDisplayName(dialog.getDisplayName()));
            refreshProfileUi();
        }
    }

    private void toggleEnabled()
    {
        flushProfileFields();
        ToolProfile selected = model.getSelected();
        if (selected == null)
        {
            return;
        }
        showIfFailed(model.setEnabled(!selected.isEnabled()));
        refreshProfileUi();
    }

    private void deleteProfile()
    {
        flushProfileFields();
        showIfFailed(model.deleteSelected());
        refreshProfileUi();
    }

    private void restoreSelected()
    {
        showIfFailed(model.restoreSelected());
        refreshProfileUi();
    }

    private void exportProfile()
    {
        flushProfileFields();
        ToolProfile selected = model.getSelected();
        if (selected == null)
        {
            return;
        }
        FileDialog dialog = new FileDialog(composite.getShell(), SWT.SAVE);
        dialog.setText(Messages.ToolsTab_ExportDialogTitle);
        dialog.setFilterNames(new String[]{Messages.ToolsTab_JsonFilter});
        dialog.setFilterExtensions(new String[]{"*.json"}); //$NON-NLS-1$
        dialog.setFileName("edt-mcp-profile-" + selected.getId() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        dialog.setFilterPath(new File(System.getProperty("user.home")).getAbsolutePath()); //$NON-NLS-1$
        dialog.setOverwrite(true);
        String chosenPath = dialog.open();
        if (chosenPath == null)
        {
            return;
        }
        Path exportPath = Paths.get(chosenPath);
        if (!chosenPath.toLowerCase().endsWith(".json")) //$NON-NLS-1$
        {
            exportPath = exportPath.resolveSibling(exportPath.getFileName().toString() + ".json"); //$NON-NLS-1$
        }
        FileOpResult result = ToolProfileFileService.exportProfile(exportPath, selected);
        if (!result.isOk())
        {
            String detail = result.getMessage() != null ? result.getMessage() : result.getStatus().name();
            Activator.logError("Profile export failed: " + detail, null); //$NON-NLS-1$
            MessageDialog.openError(composite.getShell(), Messages.ToolsTab_ExportFailedTitle,
                NLS.bind(Messages.ToolsTab_ExportFailedMessage, detail));
        }
    }

    private void importProfile()
    {
        flushProfileFields();
        ToolProfile current = model.getSelected();
        if (current == null)
        {
            return;
        }
        FileDialog dialog = new FileDialog(composite.getShell(), SWT.OPEN);
        dialog.setText(Messages.ToolsTab_ImportDialogTitle);
        dialog.setFilterNames(new String[]{Messages.ToolsTab_JsonFilter});
        dialog.setFilterExtensions(new String[]{"*.json"}); //$NON-NLS-1$
        dialog.setFilterPath(new File(System.getProperty("user.home")).getAbsolutePath()); //$NON-NLS-1$
        String chosenPath = dialog.open();
        if (chosenPath == null)
        {
            return;
        }
        FileOpResult fileResult = ToolProfileFileService.importProfile(Paths.get(chosenPath));
        if (!fileResult.isOk())
        {
            String detail = fileResult.getMessage() != null ? fileResult.getMessage() : fileResult.getStatus().name();
            Activator.logError("Profile import failed: " + detail, null); //$NON-NLS-1$
            MessageDialog.openError(composite.getShell(), Messages.ToolsTab_ImportFailedTitle,
                NLS.bind(Messages.ToolsTab_ImportFailedMessage, detail));
            return;
        }
        ToolProfile imported = fileResult.getProfile();
        if (!confirmImport(current, imported))
        {
            return;
        }
        ToolProfilesEditorModel.OperationResult applyResult = model.applyImportedProfileToSelected(imported);
        if (!applyResult.isOk())
        {
            MessageDialog.openError(composite.getShell(), Messages.ToolsTab_ImportFailedTitle,
                NLS.bind(Messages.ToolsTab_ImportFailedMessage, applyResult.getMessage()));
            return;
        }
        refreshProfileUi();
        showUnknownToolsIfAny();
    }

    private boolean confirmImport(ToolProfile current, ToolProfile imported)
    {
        String message = NLS.bind(Messages.ToolsTab_ImportConfirm, new Object[]{
            current.getId(),
            current.getDisplayName(),
            imported.getDisplayName(),
            enabledStateLabel(current.isEnabled()),
            enabledStateLabel(imported.isEnabled()),
            Integer.toString(current.getAllowedTools().size()),
            Integer.toString(imported.getAllowedTools().size())
        });
        if (!current.getId().equals(imported.getId()))
        {
            message += System.lineSeparator() + System.lineSeparator()
                + NLS.bind(Messages.ToolsTab_ImportConfirmFileId, imported.getId());
        }
        return MessageDialog.openQuestion(composite.getShell(), Messages.ToolsTab_ImportConfirmTitle, message);
    }

    private static String enabledStateLabel(boolean enabled)
    {
        return enabled ? Messages.ToolsTab_Enable : Messages.ToolsTab_Disable;
    }

    private void showUnknownToolsIfAny()
    {
        Set<String> unknown = model.unknownAllowedTools();
        if (unknown.isEmpty())
        {
            return;
        }
        String names = unknown.stream().sorted().collect(Collectors.joining(", ")); //$NON-NLS-1$
        MessageDialog.openInformation(composite.getShell(), Messages.ToolsTab_UnknownToolsTitle,
            NLS.bind(Messages.ToolsTab_UnknownToolsMessage, names));
    }

    private void showIfFailed(ToolProfilesEditorModel.OperationResult result)
    {
        if (!result.isOk())
        {
            MessageDialog.openError(composite.getShell(), Messages.ToolsTab_Profile, result.getMessage());
        }
    }

    private void copyEndpointUrl()
    {
        Clipboard clipboard = new Clipboard(composite.getDisplay());
        try
        {
            clipboard.setContents(new Object[]{endpointText.getText()},
                new Transfer[]{TextTransfer.getInstance()});
        }
        finally
        {
            clipboard.dispose();
        }
    }

    private void flushProfileFields()
    {
        if (displayNameText == null || displayNameText.isDisposed())
        {
            return;
        }
        model.renameDisplayName(displayNameText.getText());
        model.setDescription(descriptionText.getText());
    }

    private void refreshProfileUi()
    {
        refreshProfileFields();
        if (treeViewer != null)
        {
            treeViewer.refresh();
        }
        refreshCheckStates();
        selectMatchingPreset();
        updateCountLabel();
    }

    private void refreshProfileFields()
    {
        if (profileCombo == null || displayNameText == null)
        {
            return;
        }
        updatingProfileFields = true;
        try
        {
            List<ToolProfile> drafts = model.getDrafts();
            profileCombo.removeAll();
            int selected = 0;
            for (int i = 0; i < drafts.size(); i++)
            {
                ToolProfile profile = drafts.get(i);
                String label = profile.getDisplayName() + " [" + profile.getId() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
                if (profile.isDefault())
                {
                    label += Messages.ToolsTab_ProfileDefaultMarker;
                }
                if (!profile.isEnabled())
                {
                    label += Messages.ToolsTab_ProfileDisabledMarker;
                }
                profileCombo.add(label);
                if (profile.getId().equals(model.getSelectedId()))
                {
                    selected = i;
                }
            }
            if (!drafts.isEmpty())
            {
                profileCombo.select(selected);
            }
            ToolProfile current = model.getSelected();
            displayNameText.setText(current == null || current.getDisplayName() == null
                ? "" : current.getDisplayName()); //$NON-NLS-1$
            descriptionText.setText(current == null || current.getDescription() == null
                ? "" : current.getDescription()); //$NON-NLS-1$
            endpointText.setText(profileUrl(current == null ? ToolProfile.DEFAULT_ID : current.getId()));
            fallbackWarning.setVisible(current != null && current.isDefault());
            boolean canMutate = model.canDeleteSelected();
            enableButton.setEnabled(canMutate);
            deleteButton.setEnabled(canMutate);
            enableButton.setText(current != null && !current.isEnabled()
                ? Messages.ToolsTab_Enable : Messages.ToolsTab_Disable);
        }
        finally
        {
            updatingProfileFields = false;
        }
    }

    private String profileUrl(String profileId)
    {
        int port = PreferenceConstants.DEFAULT_PORT;
        if (generalTab != null)
        {
            port = generalTab.getPort();
        }
        else
        {
            Activator activator = Activator.getDefault();
            if (activator != null)
            {
                IPreferenceStore store = activator.getPreferenceStore();
                if (store != null)
                {
                    port = store.getInt(PreferenceConstants.PREF_PORT);
                }
            }
        }
        return "http://127.0.0.1:" + port + model.profileEndpointPath(profileId); //$NON-NLS-1$
    }

    private static ToolProfileSnapshot publishedSnapshot()
    {
        Activator activator = Activator.getDefault();
        if (activator != null && activator.getToolProfileRepository() != null)
        {
            return activator.getToolProfileRepository().getSnapshot();
        }
        return DefaultToolProfileFactory.createSafeSnapshot();
    }

    private void createPresetBar(Composite parent)
    {
        Composite bar = new Composite(parent, SWT.NONE);
        // Layout: [✓] [✗] [Preset: label] [Preset combo]
        GridLayout barLayout = new GridLayout(4, false);
        barLayout.marginWidth = 0;
        barLayout.marginHeight = 0;
        bar.setLayout(barLayout);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Check All button (leftmost)
        Button checkAllButton = new Button(bar, SWT.PUSH);
        checkAllButton.setToolTipText(Messages.ToolsTab_EnableAllTools);
        ImageDescriptor checkAllIcon = AbstractUIPlugin.imageDescriptorFromPlugin(
            Activator.PLUGIN_ID, "icons/check_all.png"); //$NON-NLS-1$
        if (checkAllIcon != null)
        {
            Image img = checkAllIcon.createImage();
            checkAllButton.setImage(img);
            managedImages.add(img);
        }
        else
        {
            checkAllButton.setText(Messages.ToolsTab_AllFallback);
        }
        checkAllButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                for (ToolGroup group : ToolGroup.values())
                {
                    model.setGroupEnabled(group.getToolNames(), true);
                }
                refreshCheckStates();
                selectMatchingPreset();
                updateCountLabel();
            }
        });

        // Uncheck All button
        Button uncheckAllButton = new Button(bar, SWT.PUSH);
        uncheckAllButton.setToolTipText(Messages.ToolsTab_DisableAllTools);
        ImageDescriptor uncheckAllIcon = AbstractUIPlugin.imageDescriptorFromPlugin(
            Activator.PLUGIN_ID, "icons/uncheck_all.png"); //$NON-NLS-1$
        if (uncheckAllIcon != null)
        {
            Image img = uncheckAllIcon.createImage();
            uncheckAllButton.setImage(img);
            managedImages.add(img);
        }
        else
        {
            uncheckAllButton.setText(Messages.ToolsTab_NoneFallback);
        }
        uncheckAllButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                for (ToolGroup group : ToolGroup.values())
                {
                    model.setGroupEnabled(group.getToolNames(), false);
                }
                refreshCheckStates();
                selectMatchingPreset();
                updateCountLabel();
            }
        });

        // Preset label + combo (after buttons)
        Label presetLabel = new Label(bar, SWT.NONE);
        presetLabel.setText(Messages.ToolsTab_Preset);

        presetCombo = new Combo(bar, SWT.DROP_DOWN | SWT.READ_ONLY);
        for (ToolPreset preset : ToolPreset.values())
        {
            presetCombo.add(preset.getDisplayName());
        }
        presetCombo.setToolTipText(Messages.ToolsTab_PresetTooltip);
        GridData comboGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        presetCombo.setLayoutData(comboGd);
        selectMatchingPreset();

        presetCombo.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                int idx = presetCombo.getSelectionIndex();
                if (idx >= 0)
                {
                    ToolPreset preset = ToolPreset.values()[idx];
                    if (preset != ToolPreset.CUSTOM && preset.getDisabledTools() != null)
                    {
                        model.applyPreset(preset);
                        refreshCheckStates();
                        updateCountLabel();
                    }
                }
            }
        });
    }

    private void createToolTree(Composite parent)
    {
        treeViewer = new CheckboxTreeViewer(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        treeViewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        treeViewer.setContentProvider(new ToolTreeContentProvider());
        treeViewer.setLabelProvider(new ToolTreeLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                String text = super.getText(element);
                if (element instanceof String toolName && model.unknownAllowedTools().contains(toolName))
                {
                    return toolName + Messages.ToolsTab_UnavailableTool;
                }
                return text;
            }
        });
        treeViewer.setInput(ToolGroup.values());

        refreshCheckStates();

        treeViewer.addCheckStateListener(new ICheckStateListener()
        {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event)
            {
                if (updatingChecks)
                {
                    return;
                }
                applyCheckChange(event.getElement(), event.getChecked());

                refreshCheckStates();
                selectMatchingPreset();
                updateCountLabel();
            }
        });

        // Set tool check states lazily when a group is expanded (avoids forcing expansion at startup)
        treeViewer.addTreeListener(new ITreeViewerListener()
        {
            @Override
            public void treeCollapsed(TreeExpansionEvent event)
            {
                // No action needed on collapse; check states are only set lazily on expand.
            }

            @Override
            public void treeExpanded(TreeExpansionEvent event)
            {
                if (event.getElement() instanceof ToolGroup group)
                {
                    updatingChecks = true;
                    try
                    {
                        for (String toolName : group.getToolNames())
                        {
                            treeViewer.setChecked(toolName, model.isToolChecked(toolName));
                        }
                    }
                    finally
                    {
                        updatingChecks = false;
                    }
                }
            }
        });

        treeViewer.addSelectionChangedListener(new ISelectionChangedListener()
        {
            @Override
            public void selectionChanged(SelectionChangedEvent event)
            {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                showDetailsForElement(selection.getFirstElement());
            }
        });
    }

    /**
     * Applies a single checkbox change to the selected profile draft. A checked
     * element enables the tool; an unchecked element removes it from the allowlist.
     * {@code get_server_status} stays checked. A {@link ToolGroup} fans the change
     * out; a {@link String} is a single tool.
     *
     * @param element the changed tree element (a {@link ToolGroup} or tool-name {@link String})
     * @param checked {@code true} when the element was checked (enabled)
     */
    private void applyCheckChange(Object element, boolean checked)
    {
        if (element instanceof ToolGroup group)
        {
            model.setGroupEnabled(group.getToolNames(), checked);
        }
        else if (element instanceof String toolName)
        {
            if (model.unknownAllowedTools().contains(toolName))
            {
                return;
            }
            model.setToolEnabled(toolName, checked);
        }
    }

    private void createCountLabel(Composite parent)
    {
        countLabel = new Label(parent, SWT.NONE);
        countLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateCountLabel();
    }

    private void createDetailPanel(Composite parent)
    {
        detailPanel = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 5;
        layout.marginHeight = 5;
        detailPanel.setLayout(layout);

        Label hint = new Label(detailPanel, SWT.WRAP);
        hint.setText(Messages.ToolsTab_SelectHint);
        hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    }

    private void showDetailsForElement(Object element)
    {
        savePendingSpinnerValues();
        currentSpinners.clear();
        selectedTool = (element instanceof String s) ? s : null;

        for (Control child : detailPanel.getChildren())
        {
            child.dispose();
        }

        if (element instanceof ToolGroup group)
        {
            showGroupDetails(group);
        }
        else if (element instanceof String toolName)
        {
            showToolDetails(toolName);
        }
        else
        {
            Label hint = new Label(detailPanel, SWT.WRAP);
            hint.setText(Messages.ToolsTab_SelectHint);
            hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        }

        detailPanel.layout(true, true);
    }

    private void showGroupDetails(ToolGroup group)
    {
        Label nameLabel = new Label(detailPanel, SWT.NONE);
        nameLabel.setText(group.getDisplayName());
        nameLabel.setFont(JFaceResources.getBannerFont());
        nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Text descText = new Text(detailPanel, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        descText.setText(group.getDescription());
        descText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    private void showToolDetails(String toolName)
    {
        IMcpTool tool = McpToolRegistry.getInstance().getTool(toolName);

        Label nameLabel = new Label(detailPanel, SWT.NONE);
        nameLabel.setText(toolName);
        nameLabel.setFont(JFaceResources.getBannerFont());
        nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Text descText = new Text(detailPanel, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        descText.setText(tool != null ? tool.getDescription() : ""); //$NON-NLS-1$
        descText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createDestructiveConsentControl(toolName);

        List<ParameterDef> params = paramSettings.getParametersForTool(toolName);
        if (params.isEmpty())
        {
            return;
        }

        Group settingsGroup = new Group(detailPanel, SWT.NONE);
        settingsGroup.setText(Messages.ToolsTab_SettingsGlobal);
        GridLayout groupLayout = new GridLayout(2, false);
        groupLayout.marginWidth = 8;
        groupLayout.marginHeight = 8;
        settingsGroup.setLayout(groupLayout);
        GridData groupGd = new GridData(SWT.FILL, SWT.TOP, true, false);
        groupGd.verticalIndent = 8;
        settingsGroup.setLayoutData(groupGd);

        for (ParameterDef param : params)
        {
            Label paramLabel = new Label(settingsGroup, SWT.NONE);
            paramLabel.setText(param.getDisplayName() + ":"); //$NON-NLS-1$
            paramLabel.setToolTipText(param.getDescription());

            Spinner spinner = new Spinner(settingsGroup, SWT.BORDER);
            spinner.setMinimum(param.getMinValue());
            spinner.setMaximum(param.getMaxValue());

            String key = ToolParameterSettings.buildKey(toolName, param.getName());
            Integer pending = pendingValues.get(key);
            spinner.setSelection(pending != null ? pending
                : paramSettings.getParameterValue(toolName, param.getName(), param.getDefaultValue()));
            spinner.setToolTipText(NLS.bind(Messages.ToolsTab_ParamTooltip, new Object[]{
                param.getDescription(), param.getDefaultValue(), param.getMinValue(), param.getMaxValue()}));
            spinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
            spinner.setData("key", key); //$NON-NLS-1$
            currentSpinners.add(spinner);
        }

        Button restoreButton = new Button(settingsGroup, SWT.PUSH);
        restoreButton.setText(Messages.ToolsTab_RestoreDefaults);
        GridData btnGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        btnGd.horizontalSpan = 2;
        btnGd.verticalIndent = 5;
        restoreButton.setLayoutData(btnGd);
        restoreButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                restoreDefaultsForTool(toolName);
            }
        });
    }

    /**
     * Renders the per-tool "allow destructive without consent" checkbox, but only for a
     * {@link DestructiveConsentGate#GATED_TOOLS} tool. The checkbox is enabled only at the
     * {@code PER_TOOL} consent level (it is the per-tool allow-set for that level); at any
     * other level it is shown disabled with an explanatory tooltip. The selection is held
     * in {@link #destructiveAllowedTools} and committed on {@link #performOk()}.
     *
     * @param toolName the selected tool name
     */
    private void createDestructiveConsentControl(String toolName)
    {
        if (!DestructiveConsentGate.GATED_TOOLS.contains(toolName))
        {
            return;
        }

        boolean perToolLevel = currentConsentLevel() == ConsentSettingsService.Level.PER_TOOL;

        Button allowCheck = new Button(detailPanel, SWT.CHECK);
        allowCheck.setText(Messages.ToolsTab_AllowDestructiveGlobal);
        allowCheck.setSelection(destructiveAllowedTools.contains(toolName));
        allowCheck.setEnabled(perToolLevel);
        allowCheck.setToolTipText(perToolLevel
            ? Messages.ToolsTab_AllowDestructive_Tooltip_Enabled
            : Messages.ToolsTab_AllowDestructive_Tooltip_Disabled);
        GridData checkGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        checkGd.verticalIndent = 8;
        allowCheck.setLayoutData(checkGd);
        allowCheck.addSelectionListener(destructiveAllowSelectionListener(allowCheck, toolName));
    }

    /**
     * Resolves the consent level that governs whether the per-tool allow checkbox is editable.
     * Prefers the PENDING selection on the sibling {@link GeneralTab} (so a level change made on
     * that tab takes effect without pressing Apply first); falls back to the persisted store level
     * when no General tab is linked (e.g. a headless/standalone construction).
     */
    private ConsentSettingsService.Level currentConsentLevel()
    {
        if (generalTab != null)
        {
            return generalTab.getPendingConsentLevel();
        }
        return ConsentSettingsService.getInstance().getLevel();
    }

    private SelectionAdapter destructiveAllowSelectionListener(Button allowCheck, String toolName)
    {
        return new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (allowCheck.getSelection())
                {
                    destructiveAllowedTools.add(toolName);
                }
                else
                {
                    destructiveAllowedTools.remove(toolName);
                }
            }
        };
    }

    private void refreshCheckStates()
    {
        if (treeViewer == null)
        {
            return;
        }
        updatingChecks = true;
        try
        {
            for (ToolGroup group : ToolGroup.values())
            {
                boolean allEnabled = true;
                boolean anyEnabled = false;

                for (String toolName : group.getToolNames())
                {
                    boolean enabled = model.isToolChecked(toolName);
                    // Only update tool widget if group is already expanded (avoids forcing expansion)
                    if (treeViewer.getExpandedState(group))
                    {
                        treeViewer.setChecked(toolName, enabled);
                    }
                    if (enabled)
                    {
                        anyEnabled = true;
                    }
                    else
                    {
                        allEnabled = false;
                    }
                }

                treeViewer.setChecked(group, allEnabled || anyEnabled);
                treeViewer.setGrayed(group, anyEnabled && !allEnabled);
            }
            for (String unknown : model.unknownAllowedTools())
            {
                treeViewer.setChecked(unknown, true);
                treeViewer.setGrayed(unknown, true);
            }
        }
        finally
        {
            updatingChecks = false;
        }
    }

    private void selectMatchingPreset()
    {
        if (presetCombo == null)
        {
            return;
        }
        ToolPreset matched = model.matchPreset();
        ToolPreset[] presets = ToolPreset.values();
        for (int i = 0; i < presets.length; i++)
        {
            if (presets[i] == matched)
            {
                presetCombo.select(i);
                break;
            }
        }
    }

    private void updateCountLabel()
    {
        if (countLabel == null)
        {
            return;
        }
        int total = 0;
        int enabled = 0;
        for (ToolGroup group : ToolGroup.values())
        {
            for (String toolName : group.getToolNames())
            {
                total++;
                if (model.isToolChecked(toolName))
                {
                    enabled++;
                }
            }
        }
        countLabel.setText(NLS.bind(Messages.ToolsTab_CountLabel, enabled, total));
    }

    private void savePendingSpinnerValues()
    {
        for (Spinner spinner : currentSpinners)
        {
            if (!spinner.isDisposed())
            {
                String key = (String) spinner.getData("key"); //$NON-NLS-1$
                if (key != null)
                {
                    pendingValues.put(key, spinner.getSelection());
                }
            }
        }
    }

    private void loadAllValues()
    {
        for (Map.Entry<String, List<ParameterDef>> entry : paramSettings.getAllParameters().entrySet())
        {
            for (ParameterDef param : entry.getValue())
            {
                String key = ToolParameterSettings.buildKey(entry.getKey(), param.getName());
                pendingValues.put(key, paramSettings.getParameterValue(
                    entry.getKey(), param.getName(), param.getDefaultValue()));
            }
        }
    }

    private void restoreDefaultsForTool(String toolName)
    {
        List<ParameterDef> params = paramSettings.getParametersForTool(toolName);
        int spinnerIdx = 0;
        for (ParameterDef param : params)
        {
            String key = ToolParameterSettings.buildKey(toolName, param.getName());
            pendingValues.put(key, param.getDefaultValue());
            if (spinnerIdx < currentSpinners.size())
            {
                currentSpinners.get(spinnerIdx).setSelection(param.getDefaultValue());
            }
            spinnerIdx++;
        }
    }

    /**
     * Publishes every profile draft with one optimistic replace. Does not write
     * global parameter or consent settings.
     */
    public ReplaceResult saveProfiles()
    {
        flushProfileFields();
        ToolProfileRepository repository = profileRepository();
        if (repository == null)
        {
            return ReplaceResult.rejected(publishedSnapshot(), "Profile repository is not available"); //$NON-NLS-1$
        }
        return model.apply(repository);
    }

    /**
     * Reloads drafts from the published repository snapshot after a revision conflict.
     */
    public void reloadProfiles()
    {
        ToolProfileRepository repository = profileRepository();
        model.reload(repository == null ? publishedSnapshot() : repository.getSnapshot());
        refreshProfileUi();
    }

    /**
     * Saves global parameter values and destructive-consent allow-set only.
     */
    public void performOk()
    {
        savePendingSpinnerValues();
        ConsentSettingsService.getInstance().setAllowedTools(destructiveAllowedTools);
        for (Map.Entry<String, Integer> entry : pendingValues.entrySet())
        {
            String key = entry.getKey();
            String[] parts = key.split("\\.", 3); //$NON-NLS-1$
            if (parts.length == 3)
            {
                paramSettings.setParameterValue(parts[1], parts[2], entry.getValue());
            }
        }
    }

    /**
     * Resets only the selected profile allowlist after confirmation. Other profiles
     * and global consent/parameter settings stay unchanged.
     */
    public void performDefaults()
    {
        if (!MessageDialog.openQuestion(composite.getShell(),
            Messages.ToolsTab_RestoreDefaultsConfirmTitle, Messages.ToolsTab_RestoreDefaultsConfirm))
        {
            return;
        }
        model.resetSelectedToShippedDefaults();
        refreshProfileUi();
        if (selectedTool != null)
        {
            currentSpinners.clear();
            showDetailsForElement(selectedTool);
        }
    }

    /**
     * Returns true when profile drafts differ from the last loaded snapshot.
     */
    public boolean hasChanges()
    {
        return model.isDirty();
    }

    private static ToolProfileRepository profileRepository()
    {
        Activator activator = Activator.getDefault();
        return activator == null ? null : activator.getToolProfileRepository();
    }

    /**
     * Disposes all managed SWT images. Must be called when the tab is disposed.
     */
    public void dispose()
    {
        for (Image image : managedImages)
        {
            if (image != null && !image.isDisposed())
            {
                image.dispose();
            }
        }
        managedImages.clear();
    }

    // === Tree content provider ===

    private class ToolTreeContentProvider implements ITreeContentProvider
    {
        @Override
        public Object[] getElements(Object inputElement)
        {
            java.util.List<Object> roots = new java.util.ArrayList<>();
            if (inputElement instanceof ToolGroup[])
            {
                for (ToolGroup group : (ToolGroup[]) inputElement)
                {
                    roots.add(group);
                }
            }
            if (model != null)
            {
                roots.addAll(model.unknownAllowedTools());
            }
            return roots.toArray();
        }

        @Override
        public Object[] getChildren(Object parentElement)
        {
            if (parentElement instanceof ToolGroup group)
            {
                return group.getToolNames().toArray();
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element)
        {
            if (element instanceof String toolName)
            {
                return ToolGroup.getGroupForTool(toolName);
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element)
        {
            return element instanceof ToolGroup;
        }
    }

    // === Tree label provider ===

    private static class ToolTreeLabelProvider extends LabelProvider
    {
        private Image groupImage;

        @Override
        public String getText(Object element)
        {
            if (element instanceof ToolGroup group)
            {
                return group.getDisplayName() + " (" + group.getToolNames().size() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (element instanceof String toolName)
            {
                if (ToolProfilesEditorModel.MANDATORY_TOOL.equals(toolName))
                {
                    return toolName + Messages.ToolsTab_MandatoryTool;
                }
                return toolName;
            }
            return super.getText(element);
        }

        @Override
        public Image getImage(Object element)
        {
            if (element instanceof ToolGroup)
            {
                if (groupImage == null)
                {
                    ImageDescriptor desc = AbstractUIPlugin.imageDescriptorFromPlugin(
                        Activator.PLUGIN_ID, "icons/group.png"); //$NON-NLS-1$
                    if (desc != null)
                    {
                        groupImage = desc.createImage();
                    }
                }
                return groupImage;
            }
            return null;
        }

        @Override
        public void dispose()
        {
            if (groupImage != null)
            {
                groupImage.dispose();
                groupImage = null;
            }
            super.dispose();
        }

    }
}
