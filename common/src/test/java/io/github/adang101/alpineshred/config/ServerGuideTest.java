package io.github.adang101.alpineshred.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code docs/server-guide.md} holds two config tables between marker comments. They are generated from
 * {@link AlpineConfig}; this test fails with the expected text, so a change is a copy and paste.
 */
class ServerGuideTest {
    @Test
    void everyKeyHasANoteAndEveryNoteHasAKey() {
        Set<String> keys = new HashSet<>();
        AlpineConfig.COMMON.entries().forEach(e -> keys.add(e.path));
        AlpineConfig.CLIENT.entries().forEach(e -> keys.add(e.path));
        Set<String> missing = new HashSet<>(keys);
        missing.removeAll(ServerGuideTable.NOTES.keySet());
        Set<String> stale = new HashSet<>(ServerGuideTable.NOTES.keySet());
        stale.removeAll(keys);
        assertTrue(missing.isEmpty(), "config keys with no note in ServerGuideTable: " + missing);
        assertTrue(stale.isEmpty(), "notes in ServerGuideTable for keys that do not exist: " + stale);
    }

    @Test
    void commonTableMatchesTheGuide() throws IOException {
        check(ServerGuideTable.COMMON_START, ServerGuideTable.COMMON_END, ServerGuideTable.table(AlpineConfig.COMMON));
    }

    @Test
    void clientTableMatchesTheGuide() throws IOException {
        check(ServerGuideTable.CLIENT_START, ServerGuideTable.CLIENT_END, ServerGuideTable.table(AlpineConfig.CLIENT));
    }

    private static void check(String start, String end, String expected) throws IOException {
        Path file = Path.of("docs/server-guide.md");
        if (!Files.isRegularFile(file)) file = Path.of("../docs/server-guide.md");
        assertTrue(Files.isRegularFile(file), "docs/server-guide.md not found from " + Path.of("").toAbsolutePath());
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int from = text.indexOf(start), to = text.indexOf(end);
        assertTrue(from >= 0 && to > from, "docs/server-guide.md needs " + start + " ... " + end);
        String actual = text.substring(from + start.length(), to).strip();
        assertEquals(expected.strip(), actual, "docs/server-guide.md between " + start + " and " + end
                + " is stale. Replace it with this text:\n" + expected);
    }
}
