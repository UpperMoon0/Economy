package com.nstut.economy.trading;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import net.minecraft.server.MinecraftServer;

@ExtendWith(EphemeralTestServerProvider.class)
class OrderSettlement2612RegressionTest extends OrderSettlementRegressionCases {
    @BeforeEach void initializeItemComponents(MinecraftServer server) {
        // Requesting the ephemeral server binds item components before shared cases construct stacks.
        server.registryAccess();
    }
}
