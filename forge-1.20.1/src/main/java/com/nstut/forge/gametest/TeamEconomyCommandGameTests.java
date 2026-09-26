package com.nstut.forge.gametest;

import com.mojang.authlib.GameProfile;
import com.nstut.Economy;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.EconomyApi;
import com.nstut.economy.api.TeamEconomyMode;
import com.nstut.economy.api.TeamEconomyProvider;
import com.nstut.economy.api.TeamRef;
import com.nstut.economy.api.TeamRole;
import com.nstut.economy.compat.FtbTeamsLifecycleHooks;
import com.nstut.economy.server.MarketWalletSelection;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Economy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamEconomyCommandGameTests {
    private TeamEconomyCommandGameTests() {
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 100)
    public static void teamWalletCommandsExerciseRegistrationArgumentsPermissionsAndSelection(GameTestHelper helper) {
        helper.assertTrue(EconomyApi.isReady(), "Economy API must be ready for team command coverage");
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-player"));
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        UUID actor = player.getUUID();
        UUID teamId = UUID.randomUUID();
        TeamRef team = new TeamRef(teamId, "Command Test Team", actor);
        TeamRole[] role = {TeamRole.OFFICER};

        TeamEconomyProvider fake = new TeamEconomyProvider() {
            @Override public Optional<TeamRef> resolveTeam(UUID playerId) {
                return actor.equals(playerId) && role[0] != TeamRole.NONE ? Optional.of(team) : Optional.empty();
            }
            @Override public Optional<TeamRef> getTeam(UUID id) {
                return teamId.equals(id) ? Optional.of(team) : Optional.empty();
            }
            @Override public TeamRole getRole(UUID playerId, UUID id) {
                return actor.equals(playerId) && teamId.equals(id) ? role[0] : TeamRole.NONE;
            }
        };

        var teams = EconomyApi.teamEconomy();
        TeamEconomyProvider previousProvider = teams.provider().orElse(null);
        TeamEconomyMode previousMode = teams.mode();
        TeamRole previousView = teams.viewRole();
        TeamRole previousDeposit = teams.depositRole();
        TeamRole previousSpend = teams.spendRole();
        TeamRole previousAdmin = teams.adminRole();

        if (previousProvider != null) teams.unregisterProvider(previousProvider);
        teams.registerProvider(fake);
        teams.setMode(TeamEconomyMode.HYBRID);
        teams.setViewRole(TeamRole.MEMBER);
        teams.setDepositRole(TeamRole.MEMBER);
        teams.setSpendRole(TeamRole.OFFICER);
        teams.setAdminRole(TeamRole.OWNER);
        MarketWalletSelection.reset(actor);

        try {
            var accounts = EconomyApi.accounts();
            var personal = accounts.getOrCreatePlayerAccount(actor);
            var teamAccount = accounts.getOrCreateTeamAccount(teamId);
            personal.credit(new BigDecimal("20"), null);
            BigDecimal personalStart = personal.getBalance();
            BigDecimal teamStart = teamAccount.getBalance();

            var commands = helper.getLevel().getServer().getCommands();
            var source = player.createCommandSourceStack();

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team balance") == 1,
                    "/economy team balance must be registered for players");
            helper.assertTrue(commands.performPrefixedCommand(source, "eco team balance") == 1,
                    "/eco team balance alias must share the team command tree");

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team deposit nope") == 0,
                    "invalid team deposit amount must be rejected by the real command path");
            helper.assertTrue(personal.getBalance().compareTo(personalStart) == 0
                            && teamAccount.getBalance().compareTo(teamStart) == 0,
                    "invalid amount must not mutate either wallet");

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team deposit 10") == 1,
                    "member-capable deposit must succeed");
            helper.assertTrue(personal.getBalance().compareTo(personalStart.subtract(new BigDecimal("10"))) == 0,
                    "deposit must debit the actor personal wallet");
            helper.assertTrue(teamAccount.getBalance().compareTo(teamStart.add(new BigDecimal("10"))) == 0,
                    "deposit must credit the team wallet");

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team use team") == 1,
                    "officer must be able to select the team market wallet");
            helper.assertTrue(MarketWalletSelection.selected(actor).equals(AccountRef.team(teamId)),
                    "team selection must persist server-side");

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team withdraw 3") == 1,
                    "officer withdrawal must succeed");
            helper.assertTrue(commands.performPrefixedCommand(source, "economy team pay test-mock-player 2") == 1,
                    "team pay must resolve the Brigadier player argument and succeed");
            helper.assertTrue(personal.getBalance().compareTo(personalStart.subtract(new BigDecimal("5"))) == 0,
                    "withdraw plus pay-to-self must return five coins to the actor personal wallet");
            helper.assertTrue(teamAccount.getBalance().compareTo(teamStart.add(new BigDecimal("5"))) == 0,
                    "withdraw plus pay must debit five coins from the team wallet");

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team use personal") == 1,
                    "personal selection must be executable");
            helper.assertTrue(MarketWalletSelection.selected(actor).equals(AccountRef.player(actor)),
                    "personal selection must replace the team selection");
            helper.assertTrue(commands.performPrefixedCommand(source, "economy team use default") == 1,
                    "default selection reset must be executable");

            role[0] = TeamRole.MEMBER;
            BigDecimal personalBeforeDenied = personal.getBalance();
            BigDecimal teamBeforeDenied = teamAccount.getBalance();
            helper.assertTrue(commands.performPrefixedCommand(source, "economy team withdraw 1") == 0,
                    "member below spendRole must not withdraw");
            helper.assertTrue(commands.performPrefixedCommand(source, "economy team use team") == 0,
                    "member below spendRole must not select the team market wallet");
            helper.assertTrue(personal.getBalance().compareTo(personalBeforeDenied) == 0
                            && teamAccount.getBalance().compareTo(teamBeforeDenied) == 0,
                    "denied spend commands must not mutate balances");
            helper.assertTrue(MarketWalletSelection.selected(actor).equals(AccountRef.player(actor)),
                    "denied team selection must leave the personal wallet selected");

            helper.assertTrue(commands.performPrefixedCommand(source, "economy team deposit 1") == 1,
                    "depositRole must remain independently usable by a MEMBER");

            role[0] = TeamRole.NONE;
            BigDecimal personalBeforeUnavailable = personal.getBalance();
            BigDecimal teamBeforeUnavailable = teamAccount.getBalance();
            helper.assertTrue(commands.performPrefixedCommand(source, "economy team balance") == 0,
                    "team balance must fail cleanly when the actor has no usable team");
            helper.assertTrue(commands.performPrefixedCommand(source, "economy team deposit 1") == 0,
                    "team deposit must fail cleanly when the actor has no usable team");
            helper.assertTrue(personal.getBalance().compareTo(personalBeforeUnavailable) == 0
                            && teamAccount.getBalance().compareTo(teamBeforeUnavailable) == 0,
                    "unavailable-team command failures must not mutate balances");
            helper.succeed();
        } finally {
            MarketWalletSelection.reset(actor);
            teams.unregisterProvider(fake);
            if (previousProvider != null) teams.registerProvider(previousProvider);
            teams.setMode(previousMode);
            teams.setViewRole(previousView);
            teams.setDepositRole(previousDeposit);
            teams.setSpendRole(previousSpend);
            teams.setAdminRole(previousAdmin);
            helper.getLevel().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }

    @GameTest(template = "economy_gametest_empty", timeoutTicks = 40)
    public static void ftbTeamsLifecycleHooksRegisterAgainstRealRuntime(GameTestHelper helper) {
        try {
            Class.forName("dev.ftb.mods.ftbteams.api.Team");
            Field installed = FtbTeamsLifecycleHooks.class.getDeclaredField("installed");
            installed.setAccessible(true);
            helper.assertTrue(installed.getBoolean(null),
                    "FTB Teams lifecycle hook must have registered successfully during normal mod startup");
            helper.succeed();
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new AssertionError("FTB Teams runtime lifecycle integration must be loadable and registered", failure);
        }
    }
}