package com.nstut.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.trading.FluidCommodity;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import com.nstut.economy.util.CoinText;
import com.nstut.economy.util.CommodityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EconomyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildEconomyNode("economy"));
        dispatcher.register(buildEconomyNode("eco"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildEconomyNode(String rootName) {
        return Commands.literal(rootName)
            .then(TeamWalletCommands.node())
            .then(Commands.literal("balance")
                .then(Commands.literal("personal")
                    .executes(context -> showBalancePrincipal(context, null, false))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                        .executes(context -> showBalancePrincipal(context, EntityArgument.getPlayer(context, "player"), false))))
                .then(Commands.literal("team")
                    .executes(context -> showBalancePrincipal(context, null, true))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                        .executes(context -> showBalancePrincipal(context, EntityArgument.getPlayer(context, "player"), true))))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        return showBalance(context, player);
                    }
                    context.getSource().sendFailure(Component.literal("This command can only be used by players"));
                    return 0;
                })
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        return showBalance(context, target);
                    })
                )
            )
            .then(Commands.literal("pay")
                .then(Commands.literal("personal")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> payFromPrincipal(context, false)))))
                .then(Commands.literal("team")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> payFromPrincipal(context, true)))))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
                            if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                                context.getSource().sendFailure(Component.literal("Only players can send money"));
                                return 0;
                            }
                            ServerPlayer receiver = EntityArgument.getPlayer(context, "player");
                            BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
                            IAccountManager accounts = IAccountManager.getInstance();
                            IBankAccount senderAccount = accounts.getOrCreatePlayerAccount(sender.getUUID());
                            IBankAccount receiverAccount = accounts.getOrCreatePlayerAccount(receiver.getUUID());

                            boolean isSelf = sender.getUUID().equals(receiver.getUUID());
                            if (isSelf) {
                                context.getSource().sendFailure(Component.literal("You cannot pay yourself."));
                                return 0;
                            }
                            if (senderAccount.transferTo(receiverAccount, amount,
                                TransactionContext.transfer("Payment from " + sender.getName().getString(), sender.getUUID()))) {
                                String amtStr = com.nstut.economy.util.EconomyFormatUtil.formatMoney(amount);
                                if (isSelf) {
                                    context.getSource().sendSuccess(() ->
                                        marketMessage("Transferred ", amtStr, " to yourself."), false);
                                    playMoneySound(sender);
                                } else {
                                    context.getSource().sendSuccess(() ->
                                        marketMessage("Paid ", amtStr, " to ")
                                                .append(Component.literal(receiver.getName().getString())
                                                        .withStyle(ChatFormatting.YELLOW)), false);
                                    receiver.sendSystemMessage(marketMessage("Received ", amtStr, " from ")
                                            .append(Component.literal(sender.getName().getString())
                                                    .withStyle(ChatFormatting.YELLOW)));
                                    playMoneySound(sender);
                                    playMoneySound(receiver);
                                }
                                com.nstut.economy.data.EconomyAccountData.recordSnapshot(sender.getUUID(), sender.level());
                                if (!isSelf) com.nstut.economy.data.EconomyAccountData.recordSnapshot(receiver.getUUID(), receiver.level());
                                return 1;
                            } else {
                                context.getSource().sendFailure(Component.literal("Insufficient funds"));
                                return 0;
                            }
                        })
                    )
                )
            )
            .then(Commands.literal("serverorder")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("list")
                    .executes(EconomyCommands::listServerOrders)
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("orderId", StringArgumentType.word())
                        .suggests((context, builder) -> suggestServerOrderIds(builder))
                        .executes(EconomyCommands::removeServerOrder)
                    )
                )
                .then(Commands.literal("buy")
                    .then(Commands.literal("perbucket")
                        .then(Commands.argument("commodity", IdentifierArgument.id())
                            .suggests((context, builder) -> suggestCommodityIds(builder))
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                    .executes(context -> createServerOrder(context, true, true))
                                )
                            )
                        )
                    )
                    .then(Commands.argument("commodity", IdentifierArgument.id())
                        .suggests((context, builder) -> suggestCommodityIds(builder))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> createServerOrder(context, true, false))
                            )
                        )
                    )
                )
                .then(Commands.literal("sell")
                    .then(Commands.literal("perbucket")
                        .then(Commands.argument("commodity", IdentifierArgument.id())
                            .suggests((context, builder) -> suggestCommodityIds(builder))
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                    .executes(context -> createServerOrder(context, false, true))
                                )
                            )
                        )
                    )
                    .then(Commands.argument("commodity", IdentifierArgument.id())
                        .suggests((context, builder) -> suggestCommodityIds(builder))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> createServerOrder(context, false, false))
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("give")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("personal")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> adminGivePrincipal(context, false)))))
                .then(Commands.literal("team")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> adminGivePrincipal(context, true)))))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adminGive(context))
                    )
                )
            )
            .then(Commands.literal("take")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("personal")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> adminTakePrincipal(context, false)))))
                .then(Commands.literal("team")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> adminTakePrincipal(context, true)))))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adminTake(context))
                    )
                )
            )
            .then(Commands.literal("set")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("personal")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                            .executes(context -> adminSetPrincipal(context, false)))))
                .then(Commands.literal("team")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                            .executes(context -> adminSetPrincipal(context, true)))))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(context -> adminSet(context))
                    )
                )
            );
    }

    private static int showBalancePrincipal(CommandContext<CommandSourceStack> context, ServerPlayer explicitTarget, boolean team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = explicitTarget;
        if (target == null) {
            if (!(context.getSource().getEntity() instanceof ServerPlayer self)) {
                context.getSource().sendFailure(Component.literal("Choose a player when running this principal balance command from the console."));
                return 0;
            }
            target = self;
        }
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount account;
        String label;
        if (team) {
            var ref = com.nstut.economy.api.EconomyApi.teamEconomy().resolveTeam(target.getUUID());
            if (ref.isEmpty()) { context.getSource().sendFailure(Component.literal(target.getName().getString() + " has no available Team account.")); return 0; }
            account = accounts.getOrCreateTeamAccount(ref.get().id());
            label = ref.get().displayName() + " Team";
        } else {
            account = accounts.getOrCreatePlayerAccount(target.getUUID());
            label = target.getName().getString() + " Personal";
        }
        IBankAccount resolved = account; String resolvedLabel = label;
        context.getSource().sendSuccess(() -> Component.literal(resolvedLabel + " balance: ")
                .append(CoinText.amount(resolved.getBalance())), false);
        return 1;
    }

    private static int payFromPrincipal(CommandContext<CommandSourceStack> context, boolean team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
            context.getSource().sendFailure(Component.literal("Only players can send money.")); return 0;
        }
        ServerPlayer receiver = EntityArgument.getPlayer(context, "player");
        if (sender.getUUID().equals(receiver.getUUID())) {
            context.getSource().sendFailure(Component.literal(team ? "Team funds cannot be paid to your own Personal account." : "You cannot pay yourself.")); return 0;
        }
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        com.nstut.economy.api.AccountRef sourcePrincipal;
        boolean success;
        if (team) {
            sourcePrincipal = com.nstut.economy.api.EconomyApi.teamEconomy().teamPrincipal(sender.getUUID()).orElse(null);
            if (sourcePrincipal == null) {
                context.getSource().sendFailure(Component.literal("No current Team wallet is available."));
                return 0;
            }
            success = com.nstut.economy.api.EconomyApi.teamEconomy().spendFromTeam(
                    IAccountManager.getInstance(), sender.getUUID(), com.nstut.economy.api.AccountRef.player(receiver.getUUID()), amount,
                    TransactionContext.transfer("Team command payment from " + sender.getName().getString(), sender.getUUID()));
        } else {
            sourcePrincipal = com.nstut.economy.api.AccountRef.player(sender.getUUID());
            success = IAccountManager.getInstance().transfer(
                    sourcePrincipal, com.nstut.economy.api.AccountRef.player(receiver.getUUID()), amount,
                    TransactionContext.transfer("Personal command payment from " + sender.getName().getString(), sender.getUUID()));
        }
        if (!success) { context.getSource().sendFailure(Component.literal(team ? "Team payment rejected: check Team balance and payout permission." : "Personal payment rejected: insufficient balance or transaction rule.")); return 0; }
        String sourceLabel = team ? "Team" : "Personal";
        context.getSource().sendSuccess(() -> Component.literal(sourceLabel + " payment: ")
                .append(CoinText.amount(amount)).append(Component.literal(" to " + receiver.getName().getString())), false);
        com.nstut.economy.data.EconomyAccountData.recordSnapshot(sourcePrincipal, sender.level());
        com.nstut.economy.data.EconomyAccountData.recordSnapshot(
                com.nstut.economy.api.AccountRef.player(receiver.getUUID()), receiver.level());
        return 1;
    }

    private static IBankAccount adminPrincipalAccount(CommandContext<CommandSourceStack> context, boolean team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!team) return IAccountManager.getInstance().getOrCreatePlayerAccount(target.getUUID());
        var ref = com.nstut.economy.api.EconomyApi.teamEconomy().resolveTeam(target.getUUID());
        if (ref.isEmpty()) return null;
        return IAccountManager.getInstance().getOrCreateTeamAccount(ref.get().id());
    }

    private static int adminGivePrincipal(CommandContext<CommandSourceStack> context, boolean team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        IBankAccount account = adminPrincipalAccount(context, team);
        if (account == null) { context.getSource().sendFailure(Component.literal("Target has no available Team account.")); return 0; }
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        if (!account.credit(amount, TransactionContext.adminGive("Admin principal command"))) return 0;
        context.getSource().sendSuccess(() -> Component.literal("Added ").append(CoinText.amount(amount)).append(Component.literal(team ? " to Team account." : " to Personal account.")), true);
        return 1;
    }

    private static int adminTakePrincipal(CommandContext<CommandSourceStack> context, boolean team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        IBankAccount account = adminPrincipalAccount(context, team);
        if (account == null) { context.getSource().sendFailure(Component.literal("Target has no available Team account.")); return 0; }
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        if (!account.debit(amount, TransactionContext.adminTake("Admin principal command"))) { context.getSource().sendFailure(Component.literal("Selected principal has insufficient funds.")); return 0; }
        context.getSource().sendSuccess(() -> Component.literal("Removed ").append(CoinText.amount(amount)).append(Component.literal(team ? " from Team account." : " from Personal account.")), true);
        return 1;
    }

    private static int adminSetPrincipal(CommandContext<CommandSourceStack> context, boolean team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        IBankAccount account = adminPrincipalAccount(context, team);
        if (account == null) { context.getSource().sendFailure(Component.literal("Target has no available Team account.")); return 0; }
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        if (!(account instanceof com.nstut.economy.core.BankAccount bank)) { context.getSource().sendFailure(Component.literal("Selected principal does not support direct administrative balance assignment.")); return 0; }
        bank.setBalance(amount);
        context.getSource().sendSuccess(() -> Component.literal("Set ").append(Component.literal(team ? "Team" : "Personal")).append(Component.literal(" balance to ")).append(CoinText.amount(amount)), true);
        return 1;
    }

    private static int showBalance(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount account = accounts.getOrCreatePlayerAccount(player.getUUID());
        EconomyConfig config = EconomyConfig.getInstance();
        context.getSource().sendSuccess(() ->
            Component.literal(player.getName().getString() + "'s balance: ")
                .append(CoinText.amount(account.getBalance()))
                .append(Component.literal(" " + config.getCurrencyName())),
            false
        );
        return 1;
    }

    private static int adminGive(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount account = accounts.getOrCreatePlayerAccount(target.getUUID());
        account.credit(amount, TransactionContext.adminGive("Admin command"));
        context.getSource().sendSuccess(() ->
            Component.literal("Gave ")
                .append(CoinText.amount(amount))
                .append(Component.literal(" to " + target.getName().getString())),
            true
        );
        return 1;
    }

    private static int adminTake(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount account = accounts.getOrCreatePlayerAccount(target.getUUID());
        if (account.debit(amount, TransactionContext.adminTake("Admin command"))) {
            context.getSource().sendSuccess(() ->
                Component.literal("Took ")
                    .append(CoinText.amount(amount))
                    .append(Component.literal(" from " + target.getName().getString())),
                true
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Player has insufficient funds"));
            return 0;
        }
    }

    private static int adminSet(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
        IAccountManager accounts = IAccountManager.getInstance();
        com.nstut.economy.core.BankAccount account =
            (com.nstut.economy.core.BankAccount) accounts.getOrCreatePlayerAccount(target.getUUID());
        account.setBalance(amount);
        context.getSource().sendSuccess(() ->
            Component.literal("Set " + target.getName().getString() + "'s balance to ")
                .append(CoinText.amount(amount)),
            true
        );
        return 1;
    }

    private static int listServerOrders(CommandContext<CommandSourceStack> context) {
        List<Order> serverOrders = Economy.getOrderManager().getAllOrders().stream()
                .filter(Order::isServerOrder)
                .toList();
        if (serverOrders.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No active server orders."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("Active server orders (" + serverOrders.size() + "):"), false);
        for (Order order : serverOrders) {
            String side = order.getType().name().toLowerCase(java.util.Locale.ROOT);
            String quantity = order.isInfinite() ? "∞" : Integer.toString(order.getQuantity());
            String price = order.getPricePerUnit().stripTrailingZeros().toPlainString();
            context.getSource().sendSuccess(() -> Component.literal(
                    order.getOrderId() + " | " + side + " | " + quantity + " x "
                            + order.getCommodity().getId() + " @ " + price), false);
        }
        return serverOrders.size();
    }

    private static int removeServerOrder(CommandContext<CommandSourceStack> context) {
        String rawId = StringArgumentType.getString(context, "orderId");
        final UUID orderId;
        try {
            orderId = UUID.fromString(rawId);
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.literal("Invalid order ID: " + rawId));
            return 0;
        }

        OrderManager orderManager = Economy.getOrderManager();
        java.util.Optional<Order> candidate = orderManager.getOrder(orderId);
        if (candidate.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No active order with ID " + orderId));
            return 0;
        }
        if (!candidate.get().isServerOrder()) {
            context.getSource().sendFailure(Component.literal("Order " + orderId + " is not a server order."));
            return 0;
        }
        if (!orderManager.cancelOrder(orderId, OrderManager.SERVER_ID)) {
            context.getSource().sendFailure(Component.literal("Server order " + orderId + " could not be removed."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Removed server order " + orderId), true);
        return 1;
    }

    private static int createServerOrder(CommandContext<CommandSourceStack> context, boolean isBuy,
                                         boolean bucketQuote) {
        Identifier id = IdentifierArgument.getId(context, "commodity");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal quotedPrice = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));

        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        boolean isFluid = fluid != Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid);
        ICommodity commodity;
        if (isFluid) {
            if (!CommodityUtil.isCanonicalFluid(fluid)) {
                Fluid source = CommodityUtil.getCanonicalFluid(fluid);
                Identifier sourceId = BuiltInRegistries.FLUID.getKey(source);
                context.getSource().sendFailure(Component.literal(
                        "Fluid variants cannot be traded; use the source fluid " + sourceId));
                return 0;
            }
            commodity = new FluidCommodity(id, fluid, BigDecimal.ZERO);
        } else {
            if (bucketQuote) {
                context.getSource().sendFailure(Component.literal(
                        "The bucket price form can only be used with fluids."));
                return 0;
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == net.minecraft.world.item.Items.AIR) {
                context.getSource().sendFailure(Component.literal("Unknown commodity: " + id));
                return 0;
            }
            commodity = new ItemCommodity(id, item, BigDecimal.ZERO);
        }

        BigDecimal pricePerUnit = isFluid && bucketQuote
                ? FluidCommodity.pricePerMb(quotedPrice)
                : quotedPrice;

        OrderManager orderManager = Economy.getOrderManager();
        Order order = isBuy
                ? orderManager.createServerBuyOrder(commodity, quantity, pricePerUnit)
                : orderManager.createServerSellOrder(commodity, quantity, pricePerUnit);
        if (order == null) {
            context.getSource().sendFailure(Component.literal("Server order violates the configured quantity or price limits."));
            return 0;
        }

        String priceStr = order.getTotalPrice().stripTrailingZeros().toPlainString();
        String amount = com.nstut.economy.util.EconomyFormatUtil
                .formatCommodityQuantityDetailed(quantity, isFluid);
        String side = isBuy ? "buy" : "sell";
        BigDecimal displayedPrice = isFluid && bucketQuote ? quotedPrice : pricePerUnit;
        String priceUnit = isFluid ? (bucketQuote ? "per bucket" : "per mB (legacy)") : "each";
        context.getSource().sendSuccess(() ->
            Component.literal("Server " + side + " order created: " + amount + " of "
                + commodity.getDisplayName().getString() + " @ ")
                .append(CoinText.amount(displayedPrice))
                .append(Component.literal(" " + priceUnit + " (total: "))
                .append(CoinText.amount(priceStr))
                .append(Component.literal(", ID: " + order.getOrderId() + ")")),
            true
        );
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestCommodityIds(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Set<Identifier> ids = new LinkedHashSet<>(BuiltInRegistries.ITEM.keySet());
        BuiltInRegistries.FLUID.keySet().stream()
                .filter(id -> CommodityUtil.isCanonicalFluid(BuiltInRegistries.FLUID.getValue(id)))
                .forEach(ids::add);
        return SharedSuggestionProvider.suggestResource(ids, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestServerOrderIds(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        List<String> ids = Economy.getOrderManager().getAllOrders().stream()
                .filter(Order::isServerOrder)
                .map(order -> order.getOrderId().toString())
                .toList();
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static void playMoneySound(ServerPlayer player) {
        if (player != null && player.level() != null) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                com.nstut.economy.sound.SoundRegistries.MONEY.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private static MutableComponent marketMessage(String action, String amount, String suffix) {
        return Component.literal("[Market] ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal(action).withStyle(ChatFormatting.GREEN))
                .append(CoinText.amount(amount))
                .append(Component.literal(suffix).withStyle(ChatFormatting.WHITE));
    }
}
