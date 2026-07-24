package com.nstut.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

        registerBalanceCommand(dispatcher);
        registerPayCommand(dispatcher);
        registerAdminCommands(dispatcher);
        registerVaultCommand(dispatcher);
        registerSellCommand(dispatcher, buildContext);
        registerBuyCommand(dispatcher, buildContext);
        registerOffersCommand(dispatcher, buildContext);
        registerMyOffersCommand(dispatcher);
        registerCancelOfferCommand(dispatcher);
        registerAcceptOfferCommand(dispatcher, buildContext);
    }

    private static void registerBalanceCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
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

    private static void registerPayCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                            context.getSource().sendFailure(Component.literal("This command can only be used by players"));
                            return 0;
                        }
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

                        if (sender.getUUID().equals(target.getUUID())) {
                            context.getSource().sendFailure(Component.literal("You cannot pay yourself"));
                            return 0;
                        }

                        IAccountManager accounts = IAccountManager.getInstance();
                        IBankAccount senderAccount = accounts.getOrCreatePlayerAccount(sender.getUUID());
                        IBankAccount targetAccount = accounts.getOrCreatePlayerAccount(target.getUUID());

                        if (senderAccount.transferTo(targetAccount, amount,
                                TransactionContext.transfer("Payment from " + sender.getName().getString(), sender.getUUID()))) {
                            EconomyConfig config = EconomyConfig.getInstance();
                            String amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
                            context.getSource().sendSuccess(() ->
                                Component.literal("Paid " + config.getCurrencySymbol() + amt + " to " + target.getName().getString()),
                                false
                            );
                            target.sendSystemMessage(Component.literal("Received " + config.getCurrencySymbol() + amt + " from " + sender.getName().getString()));
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("Insufficient funds"));
                            return 0;
                        }
                    })
                )
            )
        );
    }

    private static void registerAdminCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("economy")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
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
                        })
                    )
                )
            )
            .then(Commands.literal("take")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
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
                        })
                    )
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(context -> {
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
                        })
                    )
                )
            )
        );
    }

    private static void registerVaultCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vault")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(Component.literal("This command can only be used by players"));
                    return 0;
                }
                BlockPos pos = VaultManager.getVaultPos(player.getUUID());
                if (pos == null) {
                    context.getSource().sendFailure(Component.literal("You do not have a Vault block. Place one to store trade items."));
                    return 0;
                }
                context.getSource().sendSuccess(() ->
                    Component.literal("Your vault is at " + pos.toShortString() +
                        " in dimension " + player.level().dimension().location()),
                    false
                );
                return 1;
            })
        );
    }

    private static void registerSellCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("sell")
            .then(Commands.argument("item", ItemArgument.item(buildContext))
                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                    .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> createSellOffer(context))
                    )
                )
            )
        );
    }

    private static int createSellOffer(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can create offers"));
            return 0;
        }

        ItemInput itemInput = ItemArgument.getItem(context, "item");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal pricePerUnit = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));
        ServerLevel level = context.getSource().getLevel();

        VaultBlockEntity vault = VaultManager.getVault(level, player.getUUID());
        if (vault == null) {
            context.getSource().sendFailure(Component.literal("You need a Vault block to sell items. Place one first."));
            return 0;
        }

        if (vault.countItem(itemInput.getItem()) < quantity) {
            context.getSource().sendFailure(Component.literal("Not enough " +
                itemInput.getItem().getDescription().getString() + " in your vault. You have " +
                vault.countItem(itemInput.getItem())));
            return 0;
        }

        NonNullList<ItemStack> reserved = NonNullList.create();
        if (!vault.extractItem(itemInput.getItem(), quantity, reserved)) {
            context.getSource().sendFailure(Component.literal("Failed to reserve items from vault"));
            return 0;
        }

        ResourceLocation id = new ResourceLocation("economy:" +
            itemInput.getItem().toString().toLowerCase().replace(':', '_'));
        ItemCommodity commodity = new ItemCommodity(id, itemInput.getItem(), BigDecimal.ZERO);

        OfferManager offerManager = Economy.getOfferManager();
        Offer offer = offerManager.createSellOffer(player.getUUID(), commodity, quantity, pricePerUnit, reserved);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Sell offer created: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            false
        );
        return 1;
    }

    private static void registerBuyCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("buy")
            .then(Commands.argument("item", ItemArgument.item(buildContext))
                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 3456))
                    .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> createBuyOffer(context))
                    )
                )
            )
        );
    }

    private static int createBuyOffer(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can create offers"));
            return 0;
        }

        ItemInput itemInput = ItemArgument.getItem(context, "item");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        BigDecimal pricePerUnit = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));

        ResourceLocation id = new ResourceLocation("economy:" +
            itemInput.getItem().toString().toLowerCase().replace(':', '_'));
        ItemCommodity commodity = new ItemCommodity(id, itemInput.getItem(), BigDecimal.ZERO);

        OfferManager offerManager = Economy.getOfferManager();
        Offer offer = offerManager.createBuyOffer(player.getUUID(), commodity, quantity, pricePerUnit);

        EconomyConfig config = EconomyConfig.getInstance();
        String priceStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        context.getSource().sendSuccess(() ->
            Component.literal("Buy offer created: " + quantity + "x " +
                commodity.getDisplayName().getString() + " @ " + config.getCurrencySymbol() +
                pricePerUnit.setScale(2, RoundingMode.HALF_UP).toPlainString() + " each (total: " +
                config.getCurrencySymbol() + priceStr + ")"),
            false
        );
        return 1;
    }

    private static void registerOffersCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("offers")
            .executes(context -> listOffers(context, null))
            .then(Commands.argument("item", ItemArgument.item(buildContext))
                .executes(context -> {
                    ItemInput itemInput = ItemArgument.getItem(context, "item");
                    return listOffers(context, itemInput.getItem());
                })
            )
        );
    }

    private static int listOffers(CommandContext<CommandSourceStack> context, net.minecraft.world.item.Item filter) {
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
            context.getSource().sendSuccess(() -> Component.literal("No active offers."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Active Offers ==="), false);
        for (int i = 0; i < filtered.size(); i++) {
            Offer offer = filtered.get(i);
            ICommodity commodity = offer.getCommodity();
            String typeStr = offer.getType() == IOffer.OfferType.SELL ? "[SELL]" : "[BUY]";
            String priceStr = offer.getPricePerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String totalStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String sellerName = "?";
            if (context.getSource().getServer() != null) {
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
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acceptoffer " + idx))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to accept this offer")))),
                false
            );
        }
        return filtered.size();
    }

    private static void registerMyOffersCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("myoffers")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(Component.literal("Only players can use this command"));
                    return 0;
                }
                OfferManager offerManager = Economy.getOfferManager();
                List<Offer> myOffers = offerManager.getPlayerOffers(player.getUUID());
                EconomyConfig config = EconomyConfig.getInstance();

                if (myOffers.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("You have no active offers."), false);
                    return 1;
                }

                context.getSource().sendSuccess(() -> Component.literal("=== Your Offers ==="), false);
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
            })
        );
    }

    private static void registerCancelOfferCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("canceloffer")
            .then(Commands.argument("index", IntegerArgumentType.integer(1))
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Only players can use this command"));
                        return 0;
                    }
                    int index = IntegerArgumentType.getInteger(context, "index") - 1;
                    OfferManager offerManager = Economy.getOfferManager();
                    var optOffer = offerManager.getPlayerOfferByIndex(player.getUUID(), index);

                    if (optOffer.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Invalid offer index. Use /myoffers to see your offers."));
                        return 0;
                    }

                    Offer offer = optOffer.get();
                    ICommodity commodity = offer.getCommodity();

                    if (offer.cancel()) {
                        if (offer.getType() == IOffer.OfferType.SELL && !offer.getReservedItems().isEmpty()) {
                            ServerLevel level = context.getSource().getLevel();
                            VaultBlockEntity vault = VaultManager.getVault(level, player.getUUID());
                            if (vault != null) {
                                vault.insertItemStacks(offer.getReservedItems());
                            }
                        }
                        offerManager.cancelOffer(offer.getOfferId(), player.getUUID());

                        context.getSource().sendSuccess(() ->
                            Component.literal("Cancelled offer #" + (index + 1) + " (" +
                                commodity.getDisplayName().getString() + ")"),
                            false
                        );
                    } else {
                        context.getSource().sendFailure(Component.literal("Could not cancel offer."));
                    }
                    return 1;
                })
            )
        );
    }

    private static void registerAcceptOfferCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("acceptoffer")
            .then(Commands.argument("index", IntegerArgumentType.integer(1))
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Only players can use this command"));
                        return 0;
                    }
                    int index = IntegerArgumentType.getInteger(context, "index") - 1;
                    OfferManager offerManager = Economy.getOfferManager();
                    ServerLevel level = context.getSource().getLevel();
                    EconomyConfig config = EconomyConfig.getInstance();

                    var optOffer = offerManager.getGlobalOfferByIndex(index);
                    if (optOffer.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Invalid offer index. Use /offers to see available offers."));
                        return 0;
                    }

                    Offer offer = optOffer.get();
                    if (offer.getOwner().equals(player.getUUID())) {
                        context.getSource().sendFailure(Component.literal("You cannot accept your own offer."));
                        return 0;
                    }

                    IOffer.OfferType type = offer.getType();
                    ICommodity commodity = offer.getCommodity();

                    IOffer.TransactionResult result = offer.execute(player.getUUID(), level);
                    if (result.success) {
                        offerManager.cleanupOffers();

                        String typeStr = type == IOffer.OfferType.SELL ? "bought" : "sold";
                        String priceStr = offer.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
                        context.getSource().sendSuccess(() ->
                            Component.literal("Successfully " + typeStr + " " + offer.getQuantity() + "x " +
                                commodity.getDisplayName().getString() + " for " +
                                config.getCurrencySymbol() + priceStr),
                            false
                        );

                        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(offer.getOwner());
                        if (ownerPlayer != null) {
                            String theirTypeStr = type == IOffer.OfferType.SELL ? "sold" : "bought";
                            ownerPlayer.sendSystemMessage(Component.literal("Your offer was " + theirTypeStr + " by " +
                                player.getName().getString() + ": " + offer.getQuantity() + "x " +
                                commodity.getDisplayName().getString()));
                        }
                    } else {
                        context.getSource().sendFailure(Component.literal("Trade failed: " + result.message));
                    }
                    return result.success ? 1 : 0;
                })
            )
        );
    }
}
