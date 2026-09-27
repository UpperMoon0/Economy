package com.nstut.economy.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TeamEconomyApiSurfaceTest {
    @Test
    void lifecycleBindingControlsAreNotPublicRegistryApi() {
        var names = Arrays.stream(TeamEconomyRegistry.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertFalse(names.contains("bindLifecycle"));
        assertFalse(names.contains("clearLifecycle"));
    }

    @Test
    void closurePersistenceTypeIsNotInStableTopLevelApiPackage() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.nstut.economy.api.TeamWalletState"));
        assertDoesNotThrow(() -> Class.forName("com.nstut.economy.api.internal.TeamWalletState"));
    }
}
