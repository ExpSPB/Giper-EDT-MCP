/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.ArrayList;
import java.util.List;

import fm.giper.edt.mcp.server.protocol.GsonProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Strict parser and deterministic serializer for the profile document.
 * Profiles and allowlists are always written sorted; unknown tool names are preserved.
 */
public final class ToolProfileCodec
{
    static final String KEY_SCHEMA_VERSION = "schemaVersion"; //$NON-NLS-1$
    static final String KEY_DOCUMENT_REVISION = "documentRevision"; //$NON-NLS-1$
    static final String KEY_PROFILES = "profiles"; //$NON-NLS-1$
    static final String KEY_ID = "id"; //$NON-NLS-1$
    static final String KEY_DISPLAY_NAME = "displayName"; //$NON-NLS-1$
    static final String KEY_DESCRIPTION = "description"; //$NON-NLS-1$
    static final String KEY_ENABLED = "enabled"; //$NON-NLS-1$
    static final String KEY_ALLOWED_TOOLS = "allowedTools"; //$NON-NLS-1$
    static final String KEY_REVISION = "revision"; //$NON-NLS-1$

    private ToolProfileCodec()
    {
        // Utility class
    }

    /**
     * Encodes a snapshot to canonical JSON. Callers must validate first.
     */
    public static String encode(ToolProfileSnapshot snapshot)
    {
        if (snapshot == null)
        {
            throw new IllegalArgumentException("snapshot is required"); //$NON-NLS-1$
        }
        JsonObject root = new JsonObject();
        root.addProperty(KEY_SCHEMA_VERSION, snapshot.getSchemaVersion());
        root.addProperty(KEY_DOCUMENT_REVISION, snapshot.getDocumentRevision());
        JsonArray profiles = new JsonArray();
        for (ToolProfile profile : snapshot.asList())
        {
            profiles.add(encodeProfile(profile));
        }
        root.add(KEY_PROFILES, profiles);
        return GsonProvider.get().toJson(root);
    }

    /**
     * Strict decode. Distinguishes malformed documents from an unsupported future schema.
     */
    public static ToolProfileSnapshot decode(String json) throws ToolProfileDocumentException
    {
        if (json == null || json.isBlank())
        {
            throw new MalformedToolProfileDocumentException("Profile document is empty"); //$NON-NLS-1$
        }
        JsonElement root;
        try
        {
            root = JsonParser.parseString(json);
        }
        catch (JsonSyntaxException e)
        {
            throw new MalformedToolProfileDocumentException("Profile document is not valid JSON", e); //$NON-NLS-1$
        }
        if (root == null || !root.isJsonObject())
        {
            throw new MalformedToolProfileDocumentException("Profile document must be a JSON object"); //$NON-NLS-1$
        }
        JsonObject object = root.getAsJsonObject();
        int schemaVersion = requireInt(object, KEY_SCHEMA_VERSION);
        if (schemaVersion > ToolProfileSnapshot.CURRENT_SCHEMA_VERSION)
        {
            throw new UnsupportedToolProfileSchemaException(schemaVersion);
        }
        if (schemaVersion < 1)
        {
            throw new MalformedToolProfileDocumentException("schemaVersion must be >= 1"); //$NON-NLS-1$
        }
        long documentRevision = requireLong(object, KEY_DOCUMENT_REVISION);
        JsonArray array = requireArray(object, KEY_PROFILES);
        List<ToolProfile> profiles = new ArrayList<>(array.size());
        for (JsonElement element : array)
        {
            if (element == null || !element.isJsonObject())
            {
                throw new MalformedToolProfileDocumentException("Each profile must be a JSON object"); //$NON-NLS-1$
            }
            profiles.add(decodeProfile(element.getAsJsonObject()));
        }
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(schemaVersion, documentRevision, profiles);
        ToolProfileValidator.ValidationResult validation = ToolProfileValidator.validate(snapshot);
        if (!validation.isValid())
        {
            throw new MalformedToolProfileDocumentException(validation.firstError());
        }
        return snapshot;
    }

    private static JsonObject encodeProfile(ToolProfile profile)
    {
        JsonObject object = new JsonObject();
        object.addProperty(KEY_ID, profile.getId());
        object.addProperty(KEY_DISPLAY_NAME, profile.getDisplayName());
        object.addProperty(KEY_DESCRIPTION, profile.getDescription());
        object.addProperty(KEY_ENABLED, profile.isEnabled());
        JsonArray tools = new JsonArray();
        for (String tool : profile.getAllowedTools())
        {
            tools.add(tool);
        }
        object.add(KEY_ALLOWED_TOOLS, tools);
        object.addProperty(KEY_REVISION, profile.getRevision());
        return object;
    }

    private static ToolProfile decodeProfile(JsonObject object) throws MalformedToolProfileDocumentException
    {
        String id = requireString(object, KEY_ID);
        String displayName = requireString(object, KEY_DISPLAY_NAME);
        String description = optionalString(object, KEY_DESCRIPTION);
        if (!object.has(KEY_ENABLED) || !object.get(KEY_ENABLED).isJsonPrimitive()
            || !object.get(KEY_ENABLED).getAsJsonPrimitive().isBoolean())
        {
            throw new MalformedToolProfileDocumentException("Profile '" + id + "' is missing boolean enabled"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        boolean enabled = object.get(KEY_ENABLED).getAsBoolean();
        JsonArray tools = requireArray(object, KEY_ALLOWED_TOOLS);
        List<String> allowed = new ArrayList<>(tools.size());
        for (JsonElement element : tools)
        {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            {
                throw new MalformedToolProfileDocumentException(
                    "allowedTools of '" + id + "' must be an array of strings"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            allowed.add(element.getAsString());
        }
        long revision = requireLong(object, KEY_REVISION);
        return ToolProfile.builder()
            .id(id)
            .displayName(displayName)
            .description(description)
            .enabled(enabled)
            .allowedTools(allowed)
            .revision(revision)
            .build();
    }

    private static String requireString(JsonObject object, String key)
        throws MalformedToolProfileDocumentException
    {
        if (!object.has(key) || object.get(key).isJsonNull())
        {
            throw new MalformedToolProfileDocumentException("Missing string field '" + key + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            throw new MalformedToolProfileDocumentException("Field '" + key + "' must be a string"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String key)
        throws MalformedToolProfileDocumentException
    {
        if (!object.has(key) || object.get(key).isJsonNull())
        {
            return ""; //$NON-NLS-1$
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            throw new MalformedToolProfileDocumentException("Field '" + key + "' must be a string"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject object, String key) throws MalformedToolProfileDocumentException
    {
        try
        {
            return Math.toIntExact(requireLong(object, key));
        }
        catch (ArithmeticException e)
        {
            throw new MalformedToolProfileDocumentException(
                "Field '" + key + "' is outside the int range", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static long requireLong(JsonObject object, String key) throws MalformedToolProfileDocumentException
    {
        if (!object.has(key) || object.get(key).isJsonNull())
        {
            throw new MalformedToolProfileDocumentException("Missing numeric field '" + key + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            throw new MalformedToolProfileDocumentException("Field '" + key + "' must be a number"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return value.getAsLong();
    }

    private static JsonArray requireArray(JsonObject object, String key)
        throws MalformedToolProfileDocumentException
    {
        if (!object.has(key) || object.get(key).isJsonNull())
        {
            throw new MalformedToolProfileDocumentException("Missing array field '" + key + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        JsonElement value = object.get(key);
        if (!value.isJsonArray())
        {
            throw new MalformedToolProfileDocumentException("Field '" + key + "' must be an array"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return value.getAsJsonArray();
    }
}
