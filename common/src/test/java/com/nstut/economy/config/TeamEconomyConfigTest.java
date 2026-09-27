package com.nstut.economy.config;

import com.nstut.economy.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TeamEconomyConfigTest {
    @TempDir Path directory;
    private final EconomyConfig config = EconomyConfig.getInstance();

    @AfterEach void restoreDefaults() throws Exception {
        config.loadTeamConfig(directory.resolve("defaults.properties"));
    }

    @Test void freshConfigEnablesOptionalSupportWithoutRequiringAProvider() throws Exception {
        Path file = directory.resolve("economy-team.properties");
        config.loadTeamConfig(file);
        assertTrue(config.isTeamEconomyEnabled());
        assertEquals(TeamEconomyMode.HYBRID, config.getTeamEconomyMode());
        assertTrue(Files.readString(file).contains("enabled=true"));
        TeamEconomyRegistry registry = new TeamEconomyRegistry();
        registry.setMode(config.getTeamEconomyMode());
        UUID player = UUID.randomUUID();
        assertTrue(registry.resolveTeam(player).isEmpty());
        assertEquals(AccountRef.player(player), registry.defaultPrincipal(player));
    }

    @Test void explicitDisableSurvivesReloadAndOverridesLegacyMode() throws Exception {
        Path file = directory.resolve("economy-team.properties");
        Files.writeString(file, "enabled=false\nmode=HYBRID\n");
        config.loadTeamConfig(file);
        assertFalse(config.isTeamEconomyEnabled());
        assertEquals(TeamEconomyMode.PERSONAL_ONLY, config.getTeamEconomyMode());
        assertFalse(Files.readString(file).contains("mode="));
        config.loadTeamConfig(file);
        assertFalse(config.isTeamEconomyEnabled());
    }

    @Test void legacyDefaultMigratesToEnabledAndPreservesRoles() throws Exception {
        Path file = directory.resolve("economy-team.properties");
        Files.writeString(file, "mode=PERSONAL_ONLY\nspendRole=OWNER\n");
        config.loadTeamConfig(file);
        assertTrue(config.isTeamEconomyEnabled());
        assertEquals(TeamRole.OWNER, config.getTeamSpendRole());
        assertEquals(TeamRole.OWNER, config.getTeamWithdrawRole());
        assertTrue(Files.readString(file).contains("enabled=true"));
        assertFalse(Files.readString(file).contains("mode="));
    }

    @Test void invalidToggleIsRejected() throws Exception {
        Path file = directory.resolve("economy-team.properties");
        Files.writeString(file, "enabled=typo\n");
        assertThrows(IllegalArgumentException.class, () -> config.loadTeamConfig(file));
    }
}
