package com.nstut.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ICommodity;
import com.nstut.economy.api.IOffer;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.core.TransactionContext;
import com.nstut.economy.trading.ItemCommodity;
import com.nstut.economy.trading.Offer;
import com.nstut.economy.trading.OfferManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
                            if (sender.getUUID().equals(receiver.getUUID())) {
                                context.getSource().sendFailure(Component.literal("You cannot pay yourself"));
                                return 0;
                            }
                            BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));
                            IAccountManager accounts = IAccountManager.getInstance();
                            IBankAccount senderAccount = accounts.getOrCreatePlayerAccount(sender.getUUID());
                            IBankAccount receiverAccount = accounts.getOrCreatePlayerAccount(receiver.getUUID());

                            EconomyConfig config = EconomyConfig.getInstance();
                            if (senderAccount.transferTo(receiverAccount, amount,
                                TransactionContext.transfer("Payment from " + sender.getName().getString(), receiver.getUUID()))) {
                                String amtStr = config.getCurrencySymbol() + amount.toPlainString();
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Paid " + amtStr + " to " + receiver.getName().getString()), false);
                                receiver.sendSystemMessage(Component.literal("Received " + amtStr + " from " + sender.getName().getString()));
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
            .then(Commands.literal("sell")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                    .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> createSellOrder(context))
                        )
                    )
                )
            )
            .then(Commands.literal("buy")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                    .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                            .executes(context -> createBuyOrder(context))
                        )
                    )
                )
            )
            .then(Commands.literal("order")
                .then(Commands.literal("buy")
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> createBuyOrder(context))
                            )
                        )
                    )
                )
                .then(Commands.literal("sell")
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> createSellOrder(context))
                            )
                        )
                    )
                )
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
            .then(Commands.literal("orders")
                .executes(context -> listOrders(context, null, rootName))
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                    .executes(context -> {
                        ItemInput itemInput = ItemArgument.getItem(context, "item");
                        return listOrders(context, itemInput.getItem(), rootName);
                    })
                )
            )
            .then(Commands.literal("myorders")
                .executes(context -> listMyOrders(context))
            )
            .then(Commands.literal("cancelorder")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> cancelOrder(context))
                )
            )
            .then(Commands.literal("acceptorder")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> acceptOrder(context))
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

        OfferManager offerManager = Economy.getOfferManager();
        Offer offer = offerManager.createServerBuyOrder(commodity, quantity, pricePerUnit);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
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

        OfferManager offerManager = Economy.getOfferManager();
        Offer offer = offerManager.createServerSellOrder(commodity, quantity, pricePerUnit);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Server sell order created: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            true
        );
        return 1;
    }

    private static int createSellOrder(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can create orders"));
            return 0;
        }

        ItemInput itemInput = ItemArgument.getItem(context, "item");
        if (itemInput.getItem() == net.minecraft.world.item.Items.AIR) {
            context.getSource().sendFailure(Component.literal("Cannot create order for Air"));
            return 0;
        }
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal pricePerUnit = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));
        ServerLevel level = context.getSource().getLevel();

        if (VaultManager.countItemInVaults(level, player.getUUID(), itemInput.getItem()) < quantity) {
            context.getSource().sendFailure(Component.literal("Not enough " +
                itemInput.getItem().getDescription().getString() + " in your vault(s). You have " +
                VaultManager.countItemInVaults(level, player.getUUID(), itemInput.getItem())));
            return 0;
        }

        NonNullList<ItemStack> reserved = NonNullList.create();
        if (!VaultManager.extractItemFromVaults(level, player.getUUID(), itemInput.getItem(), quantity, reserved)) {
            context.getSource().sendFailure(Component.literal("Failed to reserve items from vault(s)"));
            return 0;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
        if (id == null) id = new ResourceLocation("minecraft", itemInput.getItem().toString().toLowerCase().replace(':', '_'));
        ItemCommodity commodity = new ItemCommodity(id, itemInput.getItem(), BigDecimal.ZERO);

        OfferManager offerManager = Economy.getOfferManager();
        Offer offer = offerManager.createSellOffer(player.getUUID(), commodity, quantity, pricePerUnit, reserved, level);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = offer != null ? offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString() : pricePerUnit.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Sell order created/matched: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            false
        );
        return 1;
    }

    private static int createBuyOrder(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can create orders"));
            return 0;
        }

        ItemInput itemInput = ItemArgument.getItem(context, "item");
        if (itemInput.getItem() == net.minecraft.world.item.Items.AIR) {
            context.getSource().sendFailure(Component.literal("Cannot create order for Air"));
            return 0;
        }
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal pricePerUnit = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));
        ServerLevel level = context.getSource().getLevel();

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
        if (id == null) id = new ResourceLocation("minecraft", itemInput.getItem().toString().toLowerCase().replace(':', '_'));
        ItemCommodity commodity = new ItemCommodity(id, itemInput.getItem(), BigDecimal.ZERO);

        OfferManager offerManager = Economy.getOfferManager();
        Offer offer = offerManager.createBuyOffer(player.getUUID(), commodity, quantity, pricePerUnit, level);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = offer != null ? offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString() : pricePerUnit.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Buy order created/matched: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            false
        );
        return 1;
    }

    private static int listOrders(CommandContext<CommandSourceStack> context, Item filter, String rootName) {
        OfferManager offerManager = Economy.getOfferManager();
        List<Offer> allOffers = offerManager.getAllOffers();
        EconomyConfig config = EconomyConfig.getInstance();

        List<Offer> filtered = new ArrayList<>();
        for (Offer offer : allOffers) {
            if (filter != null && offer.getCommodity() instanceof ItemCommodity ic && ic.getItem() != filter) {
                continue;
            }
            filtered.add(offer);
        }

        if (filtered.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No active orders."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Active Orders ==="), false);
        for (int i = 0; i < filtered.size(); i++) {
            Offer offer = filtered.get(i);
            ICommodity commodity = offer.getCommodity();
            String typeStr = offer.getType() == IOffer.OfferType.SELL ? "[SELL]" : "[BUY]";
            String priceStr = offer.getPricePerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String totalStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String sellerName = offer.isServerOrder() ? "SERVER" : "?";
            if (!offer.isServerOrder() && context.getSource().getServer() != null) {
                var profile = context.getSource().getServer().getProfileCache().get(offer.getOwner());
                if (profile.isPresent()) sellerName = profile.get().getName();
            }

            String msg = String.format("#%d %s %dx %s @ %s%s each (total: %s%s) - %s",
                i + 1, typeStr, offer.getQuantity(),
                commodity.getDisplayName().getString(),
                config.getCurrencySymbol(), priceStr,
                config.getCurrencySymbol(), totalStr, sellerName);
            final int idx = i + 1;

            context.getSource().sendSuccess(() ->
                Component.literal(msg)
                    .withStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + rootName + " acceptorder " + idx))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to accept this order")))),
                false
            );
        }
        return filtered.size();
    }

    private static int listMyOrders(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }
        OfferManager offerManager = Economy.getOfferManager();
        List<Offer> myOffers = offerManager.getPlayerOffers(player.getUUID());
        EconomyConfig config = EconomyConfig.getInstance();

        if (myOffers.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have no active orders."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Your Orders ==="), false);
        for (int i = 0; i < myOffers.size(); i++) {
            Offer offer = myOffers.get(i);
            ICommodity commodity = offer.getCommodity();
            String typeStr = offer.getType() == IOffer.OfferType.SELL ? "[SELL]" : "[BUY]";
            String priceStr = offer.getPricePerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString();
            final int idx = i + 1;

            context.getSource().sendSuccess(() ->
                Component.literal(String.format("  #%d %s %dx %s @ %s%s each",
                    idx, typeStr, offer.getQuantity(),
                    commodity.getDisplayName().getString(),
                    config.getCurrencySymbol(), priceStr)),
                false
            );
        }
        return myOffers.size();
    }

    private static int cancelOrder(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        OfferManager offerManager = Economy.getOfferManager();
        List<Offer> myOffers = offerManager.getPlayerOffers(player.getUUID());

        if (index < 0 || index >= myOffers.size()) {
            context.getSource().sendFailure(Component.literal("Invalid order index. Use /economy myorders to list your orders."));
            return 0;
        }

        Offer offer = myOffers.get(index);
        if (offerManager.cancelOffer(offer.getOfferId(), player.getUUID())) {
            context.getSource().sendSuccess(() ->
                Component.literal("Cancelled order #" + (index + 1) + " (" +
                    offer.getCommodity().getDisplayName().getString() + ")"),
                false
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to cancel order."));
            return 0;
        }
    }

    private static int acceptOrder(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can accept orders"));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        OfferManager offerManager = Economy.getOfferManager();
        List<Offer> allOffers = offerManager.getAllOffers();

        if (index < 0 || index >= allOffers.size()) {
            context.getSource().sendFailure(Component.literal("Invalid order index. Use /economy orders to list active orders."));
            return 0;
        }

        Offer offer = allOffers.get(index);
        ServerLevel level = player.serverLevel();
        IOffer.TransactionResult result = offer.execute(player.getUUID(), level);

        if (result.success) {
            EconomyConfig config = EconomyConfig.getInstance();
            context.getSource().sendSuccess(() ->
                Component.literal("Transaction complete! Transferred " +
                    config.getCurrencySymbol() + result.amountTransferred.setScale(2, RoundingMode.HALF_UP).toPlainString()),
                false
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Transaction failed: " + result.message));
            return 0;
        }
    }
}
