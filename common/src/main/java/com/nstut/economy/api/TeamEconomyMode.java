package com.nstut.economy.api;

/** User-facing account-selection policy when a team provider is available. */
public enum TeamEconomyMode {
    /** Preserve legacy behavior and expose no team wallet. */
    PERSONAL_ONLY,
    /** Keep personal accounts primary while also exposing the shared team wallet. */
    HYBRID,
    /** Use the shared team wallet as the default principal when permitted. */
    TEAM_PRIMARY
}
