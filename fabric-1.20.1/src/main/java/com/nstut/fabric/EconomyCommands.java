package com.nstut.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nstut.Economy;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.config.EconomyConfig;
import com.nstut.economy.core.TransactionContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

/**
 * Registers economy commands for Fabric.
 */
public class EconomyCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerBalanceCommand(dispatcher);
            registerPayCommand(dispatcher);
            registerAdminCommands(dispatcher);
        });
    }
    
    private static void registerBalanceCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
            .executes(context -> {
                // Check own balance
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
        String balanceStr = account.getBalance().toString();
        
        context.getSource().sendSuccess(() -> 
            Component.literal(player.getName().getString() + "'s balance: " + 
                config.getCurrencySymbol() + balanceStr + " " + config.getCurrencyName()),
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
                        double amountDouble = DoubleArgumentType.getDouble(context, "amount");
                        BigDecimal amount = BigDecimal.valueOf(amountDouble);
                        
                        if (sender.getUUID().equals(target.getUUID())) {
                            context.getSource().sendFailure(Component.literal("You cannot pay yourself"));
                            return 0;
                        }
                        
                        IAccountManager accounts = IAccountManager.getInstance();
                        IBankAccount senderAccount = accounts.getOrCreatePlayerAccount(sender.getUUID());
                        IBankAccount targetAccount = accounts.getOrCreatePlayerAccount(target.getUUID());
                        
                        TransactionContext ctx = TransactionContext.transfer(
                            "Payment from " + sender.getName().getString(),
                            sender.getUUID()
                        );
                        
                        if (senderAccount.transferTo(targetAccount, amount, ctx)) {
                            EconomyConfig config = EconomyConfig.getInstance();
                            context.getSource().sendSuccess(() -> 
                                Component.literal("Paid " + config.getCurrencySymbol() + amount + " to " + target.getName().getString()),
                                false
                            );
                            target.displayClientMessage(
                                Component.literal("Received " + config.getCurrencySymbol() + amount + " from " + sender.getName().getString()),
                                false
                            );
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
                            double amountDouble = DoubleArgumentType.getDouble(context, "amount");
                            BigDecimal amount = BigDecimal.valueOf(amountDouble);
                            
                            IAccountManager accounts = IAccountManager.getInstance();
                            IBankAccount account = accounts.getOrCreatePlayerAccount(target.getUUID());
                            
                            TransactionContext ctx = TransactionContext.adminGive("Admin command");
                            if (account.credit(amount, ctx)) {
                                EconomyConfig config = EconomyConfig.getInstance();
                                context.getSource().sendSuccess(() -> 
                                    Component.literal("Gave " + config.getCurrencySymbol() + amount + " to " + target.getName().getString()),
                                    true
                                );
                                return 1;
                            }
                            return 0;
                        })
                    )
                )
            )
            .then(Commands.literal("take")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            double amountDouble = DoubleArgumentType.getDouble(context, "amount");
                            BigDecimal amount = BigDecimal.valueOf(amountDouble);
                            
                            IAccountManager accounts = IAccountManager.getInstance();
                            IBankAccount account = accounts.getOrCreatePlayerAccount(target.getUUID());
                            
                            TransactionContext ctx = TransactionContext.adminTake("Admin command");
                            if (account.debit(amount, ctx)) {
                                EconomyConfig config = EconomyConfig.getInstance();
                                context.getSource().sendSuccess(() -> 
                                    Component.literal("Took " + config.getCurrencySymbol() + amount + " from " + target.getName().getString()),
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
                            double amountDouble = DoubleArgumentType.getDouble(context, "amount");
                            BigDecimal amount = BigDecimal.valueOf(amountDouble);
                            
                            IAccountManager accounts = IAccountManager.getInstance();
                            com.nstut.economy.core.BankAccount account = 
                                (com.nstut.economy.core.BankAccount) accounts.getOrCreatePlayerAccount(target.getUUID());
                            
                            account.setBalance(amount);
                            
                            EconomyConfig config = EconomyConfig.getInstance();
                            context.getSource().sendSuccess(() -> 
                                Component.literal("Set " + target.getName().getString() + "'s balance to " + 
                                    config.getCurrencySymbol() + amount),
                                true
                            );
                            return 1;
                        })
                    )
                )
            )
        );
    }
}
