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
        config.setTankCapacity(128000);
        config.setAllowExternalAutomation(false);
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
        assertEquals(TeamRole.OWNER, config.getTeamPayoutRole());
        assertTrue(Files.readString(file).contains("enabled=true"));
        assertFalse(Files.readString(file).contains("mode="));
    }

    @Test void legacyWithdrawRoleMigratesToPayoutRole() throws Exception {
        Path file = directory.resolve("economy-team.properties");
        Files.writeString(file, "enabled=true\nwithdrawRole=OFFICER\n");
        config.loadTeamConfig(file);
        assertEquals(TeamRole.OFFICER, config.getTeamPayoutRole());
        String migrated = Files.readString(file);
        assertTrue(migrated.contains("payoutRole=OFFICER"));
        assertFalse(migrated.contains("withdrawRole="));
    }

    @Test void invalidToggleIsRejected() throws Exception {
        Path file = directory.resolve("economy-team.properties");
        Files.writeString(file, "enabled=typo\n");
        assertThrows(IllegalArgumentException.class, () -> config.loadTeamConfig(file));
    }
    @Test void storageConfigControlsTankCapacityAndAutomation() throws Exception {
        Path file = directory.resolve("economy-storage.properties");
        Files.writeString(file, "tankCapacity=256000\nallowExternalAutomation=true\n");
        config.loadStorageConfig(file);
        assertEquals(256000, config.getTankCapacity());
        assertTrue(config.isExternalAutomationAllowed());
    }

    @Test void storageConfigWritesDefaultsAndRejectsInvalidCapacity() throws Exception {
        Path file = directory.resolve("economy-storage-defaults.properties");
        config.setTankCapacity(128000);
        config.setAllowExternalAutomation(false);
        config.loadStorageConfig(file);
        String written = Files.readString(file);
        assertTrue(written.contains("tankCapacity=128000"));
        assertTrue(written.contains("allowExternalAutomation=false"));
        Files.writeString(file, "tankCapacity=oops\nallowExternalAutomation=false\n");
        assertThrows(IllegalArgumentException.class, () -> config.loadStorageConfig(file));
    }

}
