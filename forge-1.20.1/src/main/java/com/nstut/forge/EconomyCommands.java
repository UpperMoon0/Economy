package com.nstut.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mod.EventBusSubscriber(modid = Economy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EconomyCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(buildEconomyNode("economy", buildContext));
        dispatcher.register(buildEconomyNode("eco", buildContext));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildEconomyNode(String rootName, CommandBuildContext buildContext) {
        return Commands.literal(rootName)
            .then(Commands.literal("balance")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        return showBalance(context, player);
                    }
                    context.getSource().sendFailure(Component.literal("This command can only be used by players"));
                    return 0;
                })
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        return showBalance(context, target);
                    })
                )
            )
            .then(Commands.literal("pay")
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

                            EconomyConfig config = EconomyConfig.getInstance();
                            boolean isSelf = sender.getUUID().equals(receiver.getUUID());
                            if (senderAccount.transferTo(receiverAccount, amount,
                                TransactionContext.transfer("Payment from " + sender.getName().getString(), receiver.getUUID()))) {
                                String amtStr = config.getCurrencySymbol() + amount.toPlainString();
                                if (isSelf) {
                                    context.getSource().sendSuccess(() ->
                                        Component.literal("Self-payment test: Transferred " + amtStr + " to yourself."), false);
                                    playMoneySound(sender);
                                } else {
                                    context.getSource().sendSuccess(() ->
                                        Component.literal("Paid " + amtStr + " to " + receiver.getName().getString()), false);
                                    receiver.sendSystemMessage(Component.literal("Received " + amtStr + " from " + sender.getName().getString()));
                                    playMoneySound(sender);
                                    playMoneySound(receiver);
                                }
                                return 1;
                            } else {
                                context.getSource().sendFailure(Component.literal("Insufficient funds"));
                                return 0;
                            }
                        })
                    )
                )
            )
            .then(Commands.literal("vault")
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("This command can only be used by players"));
                        return 0;
                    }
                    var record = VaultManager.getVaultRecord(player.getUUID());
                    if (record == null) {
                        context.getSource().sendFailure(Component.literal("You do not have a Vault block. Place one to store trade items."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() ->
                        Component.literal("Your vault is at " + record.pos.toShortString() +
                            " in dimension " + record.dimension),
                        false
                    );
                    return 1;
                })
            )
            .then(Commands.literal("serverorder")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("buy")
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> createServerBuyOrder(context))
                            )
                        )
                    )
                )
                .then(Commands.literal("sell")
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> createServerSellOrder(context))
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adminGive(context))
                    )
                )
            )
            .then(Commands.literal("take")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adminTake(context))
                    )
                )
            )
            .then(Commands.literal("set")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(context -> adminSet(context))
                    )
                )
            );
    }

    private static int showBalance(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        IAccountManager accounts = IAccountManager.getInstance();
        IBankAccount account = accounts.getOrCreatePlayerAccount(player.getUUID());
        EconomyConfig config = EconomyConfig.getInstance();
        context.getSource().sendSuccess(() ->
            Component.literal(player.getName().getString() + "'s balance: " +
                config.getCurrencySymbol() + account.getBalance().toPlainString() + " " + config.getCurrencyName()),
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
        EconomyConfig config = EconomyConfig.getInstance();
        context.getSource().sendSuccess(() ->
            Component.literal("Gave " + config.getCurrencySymbol() + amount.toPlainString() + " to " + target.getName().getString()),
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
            EconomyConfig config = EconomyConfig.getInstance();
            context.getSource().sendSuccess(() ->
                Component.literal("Took " + config.getCurrencySymbol() + amount.toPlainString() + " from " + target.getName().getString()),
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
        EconomyConfig config = EconomyConfig.getInstance();
        context.getSource().sendSuccess(() ->
            Component.literal("Set " + target.getName().getString() + "'s balance to " + config.getCurrencySymbol() + amount.toPlainString()),
            true
        );
        return 1;
    }

    private static int createServerBuyOrder(CommandContext<CommandSourceStack> context) {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        if (itemInput.getItem() == net.minecraft.world.item.Items.AIR) {
            context.getSource().sendFailure(Component.literal("Cannot create order for Air"));
            return 0;
        }
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal pricePerUnit = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
        if (id == null) id = new ResourceLocation("minecraft", itemInput.getItem().toString().toLowerCase().replace(':', '_'));
        ItemCommodity commodity = new ItemCommodity(id, itemInput.getItem(), BigDecimal.ZERO);

        OrderManager orderManager = Economy.getOrderManager();
        Order order = orderManager.createServerBuyOrder(commodity, quantity, pricePerUnit);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = order.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Server buy order created: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            true
        );
        return 1;
    }

    private static int createServerSellOrder(CommandContext<CommandSourceStack> context) {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        if (itemInput.getItem() == net.minecraft.world.item.Items.AIR) {
            context.getSource().sendFailure(Component.literal("Cannot create order for Air"));
            return 0;
        }
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal pricePerUnit = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
        if (id == null) id = new ResourceLocation("minecraft", itemInput.getItem().toString().toLowerCase().replace(':', '_'));
        ItemCommodity commodity = new ItemCommodity(id, itemInput.getItem(), BigDecimal.ZERO);

        OrderManager orderManager = Economy.getOrderManager();
        Order order = orderManager.createServerSellOrder(commodity, quantity, pricePerUnit);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = order.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Server sell order created: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            true
        );
        return 1;
    }

    private static void playMoneySound(ServerPlayer player) {
        if (player != null && player.serverLevel() != null) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                com.nstut.economy.sound.SoundRegistries.MONEY.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }
}
