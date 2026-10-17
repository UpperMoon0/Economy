package com.nstut.economy.client;

import com.nstut.economy.config.StorageRoutingSettings;
import java.util.*;

/** Local draft: searching never changes the allowlist; only explicit row selections do. */
public final class StorageFilterPickerModel {
    public record Entry(String id, String name) { }
    private final List<Entry> catalog;
    private final Map<String, Entry> byId = new HashMap<>();
    private final LinkedHashSet<String> selected;

    public StorageFilterPickerModel(List<Entry> catalog, StorageRoutingSettings settings) {
        this.catalog = catalog.stream().distinct()
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
        for (Entry entry : this.catalog) byId.put(entry.id(), entry);
        selected = new LinkedHashSet<>(settings.allowedIds());
    }
    public boolean isSelected(String id) { return selected.contains(id); }
    public int size() { return selected.size(); }
    public boolean toggle(String id) {
        if (selected.remove(id)) return true;
        if (!byId.containsKey(id) || selected.size() >= StorageRoutingSettings.MAX_ENTRIES) return false;
        selected.add(id); return true;
    }
    public StorageRoutingSettings settings(int priority) {
        return new StorageRoutingSettings(selected, priority);
    }
    public List<Entry> search(String query) {
        if (query == null || query.isBlank()) return selected.stream()
                .map(id -> byId.getOrDefault(id, new Entry(id, id))).toList();
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        return catalog.stream().filter(entry -> {
            String text = (entry.name() + " " + entry.id()).toLowerCase(Locale.ROOT);
            for (String word : words) if (!text.contains(word)) return false;
            return true;
        }).limit(50).toList();
    }
}
