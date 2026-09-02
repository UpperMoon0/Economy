package com.nstut.economy.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Minecraft-version-neutral namespaced identifier used by Economy's public API.
 * This intentionally avoids exposing ResourceLocation/Identifier so addon source
 * remains stable across Minecraft's identifier renames.
 */
public record EconomyId(String namespace, String path) implements Comparable<EconomyId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public EconomyId {
        namespace = Objects.requireNonNull(namespace, "namespace");
        path = Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
    }

    public static EconomyId of(String namespace, String path) {
        return new EconomyId(namespace, path);
    }

    public static EconomyId parse(String value) {
        Objects.requireNonNull(value, "value");
        int split = value.indexOf(':');
        if (split < 0) {
            return new EconomyId("minecraft", value);
        }
        if (split == 0 || split == value.length() - 1 || value.indexOf(':', split + 1) >= 0) {
            throw new IllegalArgumentException("Invalid namespaced id: " + value);
        }
        return new EconomyId(value.substring(0, split), value.substring(split + 1));
    }

    @Override
    public int compareTo(EconomyId other) {
        int namespaceResult = namespace.compareTo(other.namespace);
        return namespaceResult != 0 ? namespaceResult : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }
}
