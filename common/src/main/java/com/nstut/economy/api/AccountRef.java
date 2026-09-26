package com.nstut.economy.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identity for an Economy account. The account kind participates in
 * identity, so a player and team may safely share the same raw UUID.
 */
public record AccountRef(AccountKind kind, UUID id) implements Comparable<AccountRef> {
    public AccountRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static AccountRef player(UUID id) { return new AccountRef(AccountKind.PLAYER, id); }
    public static AccountRef team(UUID id) { return new AccountRef(AccountKind.TEAM, id); }
    public static AccountRef server(UUID id) { return new AccountRef(AccountKind.SERVER, id); }
    public static AccountRef tax(UUID id) { return new AccountRef(AccountKind.TAX, id); }

    /** Strict persisted form: KIND:uuid. Never reinterpret malformed typed identities as players. */
    public static AccountRef parse(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0) throw new IllegalArgumentException("Invalid account identity: " + value);
        return new AccountRef(AccountKind.valueOf(value.substring(0, separator)), UUID.fromString(value.substring(separator + 1)));
    }

    @Override
    public int compareTo(AccountRef other) {
        int kindOrder = Integer.compare(kind.ordinal(), other.kind.ordinal());
        return kindOrder != 0 ? kindOrder : id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return kind.name() + ":" + id;
    }
}
