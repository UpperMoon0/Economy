package com.nstut.economy.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenUI is a client-side UI framework. Economy classes that can load on a
 * dedicated server must never hard-reference com.nstut.openui classes, or a
 * server pack without the OpenUI jar would crash at startup. This guard scans
 * every compiled Economy class on the test classpath and fails when a
 * non-client class references OpenUI.
 */
class ServerDependencyGuardTest {

    private static final String OPENUI_MARKER = "com/nstut/openui";
    private static final String ECONOMY_PREFIX = "com/nstut/economy/";
    private static final String CLIENT_PREFIX = "com/nstut/economy/client/";
    private static final String SELF = "com/nstut/economy/server/ServerDependencyGuardTest";

    @Test
    @DisplayName("Server-loadable Economy classes never reference OpenUI classes")
    void serverClassesDoNotReferenceOpenUI() throws IOException {
        List<String> offenders = new ArrayList<>();
        String classPath = System.getProperty("java.class.path");
        assertNotNull(classPath, "java.class.path must be set");

        for (String entry : classPath.split(java.io.File.pathSeparator)) {
            if (entry.isEmpty()) continue;
            Path root = Path.of(entry);
            if (!Files.isDirectory(root)) continue; // OpenUI ships as a jar; Economy classes are class dirs
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".class"))
                        .forEach(file -> inspect(root, file, offenders));
            }
        }

        assertTrue(offenders.isEmpty(),
                "Non-client Economy classes must not reference OpenUI (server packs have no OpenUI jar). Offenders: "
                        + String.join(", ", offenders));
    }

    private static void inspect(Path root, Path file, List<String> offenders) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (!relative.startsWith(ECONOMY_PREFIX) || relative.startsWith(CLIENT_PREFIX)) {
            return;
        }
        if (relative.startsWith(SELF)) {
            return; // this class contains the marker string by definition
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (indexOf(bytes, OPENUI_MARKER.getBytes(java.nio.charset.StandardCharsets.UTF_8)) >= 0) {
                offenders.add(relative.substring(0, relative.length() - ".class".length()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    /** Naive byte-pattern search: class files reference types via UTF8 constant-pool entries. */
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
