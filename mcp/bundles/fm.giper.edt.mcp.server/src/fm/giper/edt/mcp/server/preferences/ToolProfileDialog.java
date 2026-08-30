/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import fm.giper.edt.mcp.server.profiles.ToolProfile;

/**
 * Thin SWT dialog for add / duplicate / rename. Validation lives in
 * {@link ToolProfilesEditorModel}; this class only collects fields.
 */
public class ToolProfileDialog extends Dialog
{
    public enum Mode
    {
        ADD,
        DUPLICATE,
        RENAME
    }

    private final Mode mode;
    private final String initialId;
    private final String initialDisplayName;
    private final String initialDescription;

    private Text idText;
    private Text nameText;
    private Text descriptionText;
    private Combo presetCombo;

    private String profileId;
    private String displayName;
    private String description;
    private ToolPreset preset;

    public ToolProfileDialog(Shell parentShell, Mode mode, String initialId,
        String initialDisplayName, String initialDescription)
    {
        super(parentShell);
        this.mode = mode;
        this.initialId = initialId;
        this.initialDisplayName = initialDisplayName;
        this.initialDescription = initialDescription;
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        super.configureShell(newShell);
        if (mode == Mode.ADD)
        {
            newShell.setText(Messages.ToolProfileDialog_TitleAdd);
        }
        else if (mode == Mode.DUPLICATE)
        {
            newShell.setText(Messages.ToolProfileDialog_TitleDuplicate);
        }
        else
        {
            newShell.setText(Messages.ToolProfileDialog_TitleRename);
        }
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayoutFactory.fillDefaults().margins(10, 10).numColumns(2).applyTo(container);

        if (mode != Mode.RENAME)
        {
            Label idLabel = new Label(container, SWT.NONE);
            idLabel.setText(Messages.ToolProfileDialog_Id);
            idText = new Text(container, SWT.BORDER);
            idText.setText(initialId == null ? "" : initialId); //$NON-NLS-1$
            GridDataFactory.fillDefaults().grab(true, false).hint(280, SWT.DEFAULT).applyTo(idText);

            Label hint = new Label(container, SWT.WRAP);
            hint.setText(Messages.ToolProfileDialog_IdHint);
            GridDataFactory.fillDefaults().span(2, 1).hint(360, SWT.DEFAULT).applyTo(hint);

            if (mode == Mode.ADD)
            {
                Label presetLabel = new Label(container, SWT.NONE);
                presetLabel.setText(Messages.ToolProfileDialog_Preset);
                presetCombo = new Combo(container, SWT.DROP_DOWN | SWT.READ_ONLY);
                for (ToolPreset value : ToolPreset.values())
                {
                    if (value != ToolPreset.CUSTOM)
                    {
                        presetCombo.add(value.getDisplayName());
                    }
                }
                presetCombo.select(-1);
                GridDataFactory.fillDefaults().grab(true, false).applyTo(presetCombo);
            }
        }

        Label nameLabel = new Label(container, SWT.NONE);
        nameLabel.setText(Messages.ToolProfileDialog_DisplayName);
        nameText = new Text(container, SWT.BORDER);
        nameText.setText(initialDisplayName == null ? "" : initialDisplayName); //$NON-NLS-1$
        GridDataFactory.fillDefaults().grab(true, false).applyTo(nameText);

        if (mode != Mode.RENAME)
        {
            Label descLabel = new Label(container, SWT.NONE);
            descLabel.setText(Messages.ToolProfileDialog_Description);
            descriptionText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
            descriptionText.setText(initialDescription == null ? "" : initialDescription); //$NON-NLS-1$
            GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 60).applyTo(descriptionText);
        }

        return container;
    }

    @Override
    protected void okPressed()
    {
        displayName = nameText.getText();
        if (displayName == null || displayName.isBlank())
        {
            MessageDialog.openError(getShell(), getShell().getText(),
                Messages.ToolProfileDialog_EmptyName);
            return;
        }
        if (mode != Mode.RENAME)
        {
            profileId = idText.getText() == null ? "" : idText.getText().trim(); //$NON-NLS-1$
            if (!ToolProfile.ID_PATTERN.matcher(profileId).matches())
            {
                MessageDialog.openError(getShell(), getShell().getText(),
                    Messages.ToolProfileDialog_InvalidId);
                return;
            }
            description = descriptionText == null ? "" : descriptionText.getText(); //$NON-NLS-1$
            if (mode == Mode.ADD)
            {
                preset = selectedPreset();
                if (preset == null)
                {
                    MessageDialog.openError(getShell(), getShell().getText(),
                        Messages.ToolProfileDialog_PresetRequired);
                    return;
                }
            }
        }
        else
        {
            profileId = initialId;
            description = initialDescription;
        }
        super.okPressed();
    }

    public String getProfileId()
    {
        return profileId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getDescription()
    {
        return description;
    }

    public ToolPreset getPreset()
    {
        return preset;
    }

    private ToolPreset selectedPreset()
    {
        if (presetCombo == null)
        {
            return null;
        }
        int index = presetCombo.getSelectionIndex();
        if (index < 0)
        {
            return null;
        }
        int cursor = 0;
        for (ToolPreset value : ToolPreset.values())
        {
            if (value == ToolPreset.CUSTOM)
            {
                continue;
            }
            if (cursor == index)
            {
                return value;
            }
            cursor++;
        }
        return null;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
}
