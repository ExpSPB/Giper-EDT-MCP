/*
 * MCP Server for EDT
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import fm.giper.edt.mcp.server.protocol.GsonProvider;

/** One replaceable diagnostic snapshot per project in the plugin's workspace state directory. */
public final class DiagnosticBaselineStore
{
    private static final int VERSION = 1;
    // The workspace belongs to a single EDT process. Windows can deny a replacement while another request
    // holds the destination open or is deleting it. Share the guard across independently created stores.
    private static final Object FILE_ACCESS = new Object();
    private final Path directory;

    public DiagnosticBaselineStore(Path directory)
    {
        this.directory = directory;
    }

    /** Fields used by baseline identity or by filters that must also be applied to saved rows. */
    public record Finding(String location, String modulePath, String checkId, String shortUid,
        String contributorId, String message, String severity)
    {
        public Finding
        {
            Objects.requireNonNull(location);
            Objects.requireNonNull(message);
            if (severity == null || severity.isBlank())
            {
                throw new IllegalArgumentException("Missing diagnostic severity");
            }
        }

        private Identity identity()
        {
            // EDT assigns short UIDs per project. Prefer the stable contributor/check pair.
            if (checkId != null && !checkId.isEmpty())
            {
                return new Identity(location, modulePath, contributorId, checkId, null, message);
            }
            return new Identity(location, modulePath, contributorId, null, shortUid, message);
        }
    }

    private record Identity(String location, String modulePath, String contributorId,
        String checkId, String shortUid, String message) {}

    /** The exact selectors and the spellings they resolved to at save time. Empty lists cover the whole project. */
    public record Snapshot(Instant savedAt, List<String> scope, List<String> scopeVariants, List<Finding> findings)
    {
        public Snapshot
        {
            Objects.requireNonNull(savedAt);
            scope = List.copyOf(scope);
            scopeVariants = List.copyOf(scopeVariants);
            findings = List.copyOf(findings);
            if (scope.isEmpty() != scopeVariants.isEmpty() || scope.stream().anyMatch(String::isBlank)
                || scopeVariants.stream().anyMatch(s -> s.isBlank() || !s.equals(s.toLowerCase(Locale.ROOT))))
            {
                throw new IllegalArgumentException("Invalid diagnostic baseline scope");
            }
        }
    }

    private record StoredSnapshot(int version, String projectName, String savedAt,
        List<String> scope, List<String> scopeVariants, List<Finding> findings) {}

    public record Diff<T>(List<T> added, int resolved) {}

    /** Returns the unmatched current rows in their input order and the count of unmatched saved occurrences. */
    public static <T> Diff<T> compare(List<Finding> baseline, List<T> current, Function<T, Finding> toFinding)
    {
        Map<Identity, Integer> remaining = new HashMap<>();
        for (Finding finding : baseline)
        {
            remaining.merge(finding.identity(), 1, Integer::sum);
        }
        List<T> added = new ArrayList<>();
        int resolved = baseline.size();
        for (T row : current)
        {
            Identity identity = toFinding.apply(row).identity();
            Integer count = remaining.get(identity);
            if (count == null)
            {
                added.add(row);
            }
            else
            {
                if (count == 1)
                {
                    remaining.remove(identity);
                }
                else
                {
                    remaining.put(identity, count - 1);
                }
                resolved--;
            }
        }
        return new Diff<>(List.copyOf(added), resolved);
    }

    /** Returns null for an absent file. Malformed, unsupported and unreadable files are reported as errors. */
    public Snapshot load(String projectName) throws IOException
    {
        Path path = file(projectName);
        String json;
        synchronized (FILE_ACCESS)
        {
            try
            {
                json = Files.readString(path);
            }
            catch (NoSuchFileException e)
            {
                return null;
            }
        }
        try (JsonReader reader = new JsonReader(new StringReader(json)))
        {
            // Reading the adapter directly preserves the reader's strictness (including its trailing-data check).
            JsonObject document = GsonProvider.get().getAdapter(JsonElement.class).read(reader).getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || !document.getAsJsonPrimitive("version").isNumber()
                || !Integer.toString(VERSION).equals(document.get("version").getAsString())
                || !projectName.equals(string(document, "projectName", false)))
            {
                throw new IOException("Unexpected diagnostic baseline format or project in " + path);
            }
            List<Finding> findings = new ArrayList<>();
            for (JsonElement element : document.getAsJsonArray("findings"))
            {
                JsonObject row = element.getAsJsonObject();
                findings.add(new Finding(string(row, "location", false), string(row, "modulePath", true),
                    string(row, "checkId", true), string(row, "shortUid", true), string(row, "contributorId", true),
                    string(row, "message", false), string(row, "severity", false)));
            }
            return new Snapshot(Instant.parse(string(document, "savedAt", false)), strings(document, "scope"),
                strings(document, "scopeVariants"), findings);
        }
        catch (RuntimeException e)
        {
            throw new IOException("Invalid diagnostic baseline in " + path, e);
        }
    }

    public Snapshot save(String projectName, List<String> scope, List<String> scopeVariants,
        List<Finding> findings) throws IOException
    {
        Path destination = file(projectName);
        Snapshot snapshot = new Snapshot(Instant.now(), scope, scopeVariants, findings);
        StoredSnapshot document = new StoredSnapshot(VERSION, projectName, snapshot.savedAt().toString(),
            snapshot.scope(), snapshot.scopeVariants(), snapshot.findings());
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "baseline-", ".tmp");
        try
        {
            // Each writer owns its temporary file. Clear touches only the published file, so publication
            // and removal each take effect in a single filesystem operation, even across store instances.
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(temporary)))
            {
                GsonProvider.get().getAdapter(StoredSnapshot.class).write(writer, document);
            }
            synchronized (FILE_ACCESS)
            {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
        return snapshot;
    }

    public boolean clear(String projectName) throws IOException
    {
        synchronized (FILE_ACCESS)
        {
            return Files.deleteIfExists(file(projectName));
        }
    }

    private Path file(String projectName)
    {
        if (projectName == null || projectName.isBlank())
        {
            throw new IllegalArgumentException("Missing diagnostic baseline project name");
        }
        // A fixed-length, case-sensitive key also works on case-insensitive filesystems and for reserved names.
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(projectName.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> strings(JsonObject document, String field) throws IOException
    {
        List<String> result = new ArrayList<>();
        for (JsonElement element : document.getAsJsonArray(field))
        {
            result.add(stringValue(element, field));
        }
        return result;
    }

    private static String string(JsonObject object, String field, boolean nullable) throws IOException
    {
        JsonElement element = object.get(field);
        if (nullable && element != null && element.isJsonNull())
        {
            return null;
        }
        return stringValue(element, field);
    }

    private static String stringValue(JsonElement element, String field) throws IOException
    {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
        {
            throw new IOException("Invalid diagnostic baseline field " + field);
        }
        return element.getAsString();
    }
}
