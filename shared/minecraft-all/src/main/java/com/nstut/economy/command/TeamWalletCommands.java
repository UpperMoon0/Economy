package com.nstut.economy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nstut.economy.api.*;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.server.MarketWalletSelection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.util.UUID;

/** Survival-accessible shared treasury controls; every mutation revalidates the actor on the server. */
public final class TeamWalletCommands {
    private TeamWalletCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        var node = Commands.literal("team").executes(ctx -> balance(ctx.getSource()));
        node.then(Commands.literal("balance").executes(ctx -> balance(ctx.getSource())));
        for (String action : new String[]{"deposit", "withdraw"}) {
            node.then(Commands.literal(action).then(Commands.argument("amount", StringArgumentType.word())
                    .executes(ctx -> move(ctx.getSource(), action, StringArgumentType.getString(ctx, "amount"), null))));
        }
        node.then(Commands.literal("pay").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.word()).executes(ctx ->
                        move(ctx.getSource(), "pay", StringArgumentType.getString(ctx, "amount"), EntityArgument.getPlayer(ctx, "player").getUUID())))));
        var use = Commands.literal("use");
        for (String selection : new String[]{"personal", "team", "default"}) {
            use.then(Commands.literal(selection).executes(ctx -> select(ctx.getSource(), selection)));
        }
        node.then(use);
        return node;
    }
    private static int balance(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return fail(source, "Only players can use team wallets.");
        var snapshot = EconomyApi.teamEconomy().walletSnapshot(EconomyApi.accounts(), player.getUUID());
        if (!snapshot.teamVisible()) return fail(source, "Team wallet unavailable. Join an FTB party; team economy must be enabled by the server.");
        source.sendSuccess(() -> Component.literal("Personal: " + snapshot.personalBalance().toPlainString()
                + " | Team " + snapshot.team().orElseThrow().displayName() + ": " + snapshot.teamBalance().toPlainString()
                + " | Role: " + snapshot.role() + " | Market uses " + MarketWalletSelection.label(player.getUUID())), false);
        return 1;
    }
    private static int select(CommandSourceStack source, String selection) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return fail(source, "Only players can select a wallet.");
        UUID actor = player.getUUID();
        if (selection.equals("team") && !MarketWalletSelection.selectTeam(actor))
            return fail(source, "Team wallet unavailable or requires " + EconomyApi.teamEconomy().spendRole() + ".");
        if (selection.equals("personal")) MarketWalletSelection.selectPersonal(actor);
        if (selection.equals("default")) MarketWalletSelection.reset(actor);
        source.sendSuccess(() -> Component.literal("Market now uses " + MarketWalletSelection.label(actor)
                + ". Existing orders keep their original wallet. Goods use the placing player's Vault/Tank."), false);
        com.nstut.economy.network.MarketNetwork.sendItemList(player);
        return 1;
    }
    private static int move(CommandSourceStack source, String action, String text, UUID recipient) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return fail(source, "Only players can use team wallets.");
        BigDecimal amount;
        try {
            if (text.length() > 32) throw new IllegalArgumentException();
            amount = new BigDecimal(text);
            if (amount.signum() <= 0 || amount.scale() > 4 || amount.precision() > 18 || amount.scale() < 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { return fail(source, "Enter a positive amount with at most four decimal places."); }
        var teams = EconomyApi.teamEconomy();
        UUID actor = player.getUUID();
        var team = teams.resolveTeam(actor);
        TeamRole required = action.equals("deposit") ? teams.depositRole() : teams.spendRole();
        if (team.isEmpty() || !(action.equals("deposit") ? teams.canDeposit(actor, team.get().id()) : teams.canSpend(actor, team.get().id())))
            return fail(source, "Team wallet unavailable or requires " + required + ".");
        var context = TransactionContext.transfer("Team " + action + " by " + actor, actor);
        boolean success = action.equals("deposit")
                ? teams.depositFromPlayer(EconomyApi.accounts(), actor, amount, context)
                : teams.spendFromTeam(EconomyApi.accounts(), actor, AccountRef.player(recipient == null ? actor : recipient), amount, context);
        if (!success) return fail(source, "Transfer rejected: check the balance and current team permissions.");
        source.sendSuccess(() -> Component.literal("Team " + action + ": " + amount.toPlainString() + " coins."), false);
        com.nstut.economy.network.MarketNetwork.sendItemList(player);
        return 1;
    }
    private static int fail(CommandSourceStack source, String message) { source.sendFailure(Component.literal(message)); return 0; }
}
