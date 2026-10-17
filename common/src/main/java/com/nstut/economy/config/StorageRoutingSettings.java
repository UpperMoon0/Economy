package com.nstut.economy.config;

import java.util.LinkedHashSet;
import java.util.Set;

/** Durable purchase filter. Empty means unrestricted in either mode; priority is higher-first. */
public record StorageRoutingSettings(Set<String> allowedIds, int priority, boolean blacklist) {
    public static final int MAX_TEXT_LENGTH = 4096;
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_PRIORITY = 9999;
    public static final StorageRoutingSettings DEFAULT = new StorageRoutingSettings(Set.of(), 0);

    public StorageRoutingSettings(Set<String> allowedIds, int priority) { this(allowedIds, priority, false); }

    public StorageRoutingSettings {
        allowedIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(allowedIds));
        if (allowedIds.size() > MAX_ENTRIES || Math.abs((long) priority) > MAX_PRIORITY)
            throw new IllegalArgumentException("Too many filters or priority out of range");
        for (String id : allowedIds)
            if (id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new IllegalArgumentException("Expected namespaced item or fluid IDs");
        if (String.join(",", allowedIds).length() > MAX_TEXT_LENGTH)
            throw new IllegalArgumentException("Filter too long");
    }

    public static StorageRoutingSettings parse(String text, int priority) {
        return parse(text, priority, false);
    }

    public static StorageRoutingSettings parse(String text, int priority, boolean blacklist) {
        if (text == null || text.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("Filter too long");
        Set<String> ids = new LinkedHashSet<>();
        if (!text.isBlank()) for (String part : text.split(",", -1)) ids.add(part.trim());
        return new StorageRoutingSettings(ids, priority, blacklist);
    }

    public String filterText() { return String.join(",", allowedIds); }
    public StorageRoutingSettings withBlacklist(boolean blacklist) {
        return new StorageRoutingSettings(allowedIds, priority, blacklist);
    }
    public boolean accepts(String id) { return allowedIds.isEmpty() || (allowedIds.contains(id) != blacklist); }
}
