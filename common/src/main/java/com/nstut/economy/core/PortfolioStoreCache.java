package com.nstut.economy.core;

import com.nstut.economy.api.AccountRef;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;

/** Durable last-known per-store marks with a bounded owner enumeration cadence. */
public final class PortfolioStoreCache {
    public static final long REFRESH_MILLIS = 1000;
    public interface Source {
        boolean loaded();
        default boolean exists() { return true; }
        Map<String, Long> holdings();
    }
    public record Snapshot(Map<String, Long> holdings, int fresh, int stale, int unknown) {
        public String status() { return "fresh: " + fresh + "; stale: " + stale + "; unknown: " + unknown + " (1s cache)"; }
    }
    private record Mark(AccountRef owner, Map<String, Long> holdings, long timestamp, boolean loaded) { }
    private final Map<String, Mark> stores = new HashMap<>();
    private final Map<AccountRef, Map<String, Long>> totals = new HashMap<>();
    private final Map<AccountRef, Long> refreshed = new HashMap<>();
    private final Map<AccountRef, int[]> status = new HashMap<>();
    private final Map<AccountRef, Set<String>> ownerKeys = new HashMap<>();
    private final Runnable dirty;
    public PortfolioStoreCache(Runnable dirty) { this.dirty = dirty; }

    public Set<String> keys(AccountRef owner) {
        return Set.copyOf(ownerKeys.getOrDefault(owner, Set.of()));
    }

    public Snapshot snapshot(AccountRef owner, long now, Supplier<Map<String, Source>> sources) {
        Long last = refreshed.get(owner);
        if (last == null || now < last || now - last >= REFRESH_MILLIS) {
            for (var entry : sources.get().entrySet()) {
                String key = entry.getKey();
                Source source = entry.getValue();
                if (source.loaded()) {
                    if (source.exists()) update(owner, key, source.holdings(), now);
                    else remove(key, owner);
                }
                else {
                    Mark old = stores.get(key);
                    if (old == null) replace(key, new Mark(owner, Map.of(), 0, false));
                    else if (old.owner.equals(owner)) replace(key, new Mark(owner, old.holdings, old.timestamp, false));
                }
            }
            refreshed.put(owner, now);
        }
        int[] counts = status.getOrDefault(owner, new int[3]);
        return new Snapshot(Map.copyOf(totals.getOrDefault(owner, Map.of())), counts[0], counts[1], counts[2]);
    }

    public void update(AccountRef owner, String key, Map<String, Long> holdings, long now) {
        Map<String, Long> positive = new HashMap<>();
        holdings.forEach((id, count) -> { if (id != null && count != null && count > 0) positive.put(id, count); });
        Mark next = new Mark(owner, Map.copyOf(positive), Math.max(1, now), true);
        Mark old = replace(key, next);
        if (old != null) add(old.owner, old.holdings, -1);
        add(owner, next.holdings, 1);
        if (old == null || !old.owner.equals(owner) || !old.holdings.equals(next.holdings)
                || old.timestamp != next.timestamp) dirty.run();
    }

    public void reassign(String key, AccountRef owner) {
        Mark old = stores.get(key);
        if (old == null || old.owner.equals(owner)) return;
        add(old.owner, old.holdings, -1);
        add(owner, old.holdings, 1);
        replace(key, new Mark(owner, old.holdings, old.timestamp, false));
        invalidate(old.owner); invalidate(owner); dirty.run();
    }
    public void invalidate(AccountRef owner) { refreshed.remove(owner); }
    private void remove(String key, AccountRef owner) {
        Mark old = stores.get(key);
        if (old == null || !old.owner.equals(owner)) return;
        stores.remove(key);
        add(old.owner, old.holdings, -1);
        status.computeIfAbsent(old.owner, ignored -> new int[3])[index(old)]--;
        ownerKeys.computeIfAbsent(old.owner, ignored -> new HashSet<>()).remove(key);
        dirty.run();
    }
    private Mark replace(String key, Mark next) {
        Mark old = stores.put(key, next);
        if (old != null) {
            status.computeIfAbsent(old.owner, ignored -> new int[3])[index(old)]--;
            ownerKeys.computeIfAbsent(old.owner, ignored -> new HashSet<>()).remove(key);
        }
        status.computeIfAbsent(next.owner, ignored -> new int[3])[index(next)]++;
        ownerKeys.computeIfAbsent(next.owner, ignored -> new HashSet<>()).add(key);
        return old;
    }
    private static int index(Mark mark) { return mark.timestamp == 0 ? 2 : mark.loaded ? 0 : 1; }
    private void add(AccountRef owner, Map<String, Long> holdings, int sign) {
        Map<String, Long> aggregate = totals.computeIfAbsent(owner, ignored -> new HashMap<>());
        holdings.forEach((id, count) -> {
            long next = Math.addExact(aggregate.getOrDefault(id, 0L), Math.multiplyExact(count, sign));
            if (next == 0) aggregate.remove(id); else aggregate.put(id, next);
        });
    }

    /** Opaque versioned binary string keeps version-specific NBT adapters tiny. */
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); out.writeInt(stores.size());
            for (String key : new TreeSet<>(stores.keySet())) {
                Mark mark = stores.get(key);
                out.writeUTF(key); out.writeUTF(mark.owner.toString()); out.writeLong(mark.timestamp);
                out.writeInt(mark.holdings.size());
                for (String id : new TreeSet<>(mark.holdings.keySet())) { out.writeUTF(id); out.writeLong(mark.holdings.get(id)); }
            }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Cannot encode portfolio storage marks", failure); }
    }
    public void decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) return;
        Map<String, Mark> loaded = new HashMap<>();
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            if (in.readInt() != 1) throw new IOException("Unknown storage mark version");
            int count = bounded(in.readInt());
            for (int i = 0; i < count; i++) {
                String key = in.readUTF(); AccountRef owner = AccountRef.parse(in.readUTF()); long time = in.readLong();
                int quantities = bounded(in.readInt()); Map<String, Long> values = new HashMap<>();
                for (int j = 0; j < quantities; j++) { String id = in.readUTF(); long amount = in.readLong(); if (amount > 0) values.put(id, amount); }
                loaded.put(key, new Mark(owner, Map.copyOf(values), Math.max(0, time), false));
            }
            stores.clear(); totals.clear(); refreshed.clear(); status.clear(); ownerKeys.clear();
            loaded.forEach((key, mark) -> { replace(key, mark); add(mark.owner, mark.holdings, 1); });
        } catch (IOException | IllegalArgumentException invalid) {
            System.getLogger(PortfolioStoreCache.class.getName()).log(System.Logger.Level.WARNING,
                    "Ignoring invalid portfolio storage marks; unobserved stores will be explicit unknown", invalid);
        }
    }
    private static int bounded(int count) throws IOException {
        if (count < 0 || count > 1_000_000) throw new IOException("Invalid storage mark count");
        return count;
    }
}
