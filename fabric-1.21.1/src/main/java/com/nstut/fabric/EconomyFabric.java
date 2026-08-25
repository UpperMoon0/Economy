package com.nstut.fabric;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.command.EconomyCommands;
import com.nstut.economy.items.ItemRegistries;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.economy.server.EconomyServerLifecycle;
import com.nstut.economy.sound.SoundRegistries;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EconomyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        BlockRegistries.init();
        ItemRegistries.init();
        SoundRegistries.init();
        MarketNetwork.init();
        Economy.init();

        // Vaults and tanks hold escrowed market goods; breaking one must not
        // let another player walk away with its contents.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                canBreak(world, player, pos));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EconomyCommands.register(dispatcher));

        ServerWorldEvents.LOAD.register(this::onWorldLoad);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
    }

    private boolean canBreak(Level world, Player player, net.minecraft.core.BlockPos pos) {
        if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return true;
        BlockEntity be = world.getBlockEntity(pos);
        java.util.UUID owner = null;
        String messageKey = null;
        if (be instanceof com.nstut.economy.blocks.VaultBlockEntity vault) {
            owner = vault.getOwner();
            messageKey = "message.economy.vault.not_owner";
        } else if (be instanceof com.nstut.economy.blocks.TankBlockEntity tank) {
            owner = tank.getOwner();
            messageKey = "message.economy.tank.not_owner";
        }
        if (owner == null || owner.equals(player.getUUID())) return true;
        if (player.hasPermissions(2)) return true;
        serverPlayer.displayClientMessage(Component.translatable(messageKey), true);
        return false;
    }

    private void onWorldLoad(MinecraftServer server, ServerLevel world) {
        if (!Level.OVERWORLD.equals(world.dimension())) return;
        EconomyServerLifecycle.load(world);
    }

    private void onServerStopping(MinecraftServer server) {
        EconomyServerLifecycle.save();
        Economy.LOGGER.info("Economy data saved");
    }

    private void onEndServerTick(MinecraftServer server) {
        EconomyServerLifecycle.tick(server);
    }
}
