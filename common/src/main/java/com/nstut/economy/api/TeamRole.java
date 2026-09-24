package com.nstut.economy.api;

/** Loader-neutral team authorization level used by Economy. */
public enum TeamRole {
    NONE,
    MEMBER,
    OFFICER,
    OWNER;

    public boolean atLeast(TeamRole required) {
        if (required == null) return true;
        return ordinal() >= required.ordinal();
    }
}
