package com.nstut.forge.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyClientRegistrationTest {
    @Test
    void screensUseTypedRegistrationWithoutReflectionBridge() throws IOException {
        Path repoRoot = Path.of(System.getProperty("economy.repoRoot"));
        String source = Files.readString(repoRoot.resolve(
                "forge-1.20.1/src/main/java/com/nstut/forge/EconomyClient.java"));

        assertTrue(source.contains("MenuScreens.register(BlockRegistries.MARKET_MENU.get(), MarketScreen::new)"));
        assertTrue(source.contains("MenuScreens.register(BlockRegistries.VAULT_MENU.get(), VaultScreen::new)"));
        assertTrue(source.contains("MenuScreens.register(BlockRegistries.TANK_MENU.get(), TankScreen::new)"));
        assertFalse(source.contains("Proxy.newProxyInstance"),
                "Typed registration must handle Forge's default fromPacket method directly");
    }
}
