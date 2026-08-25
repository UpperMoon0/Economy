package com.nstut.neoforge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.command.EconomyCommands;
import com.nstut.economy.items.ItemRegistries;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.economy.server.EconomyServerLifecycle;
import com.nstut.economy.sound.SoundRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

@Mod(Economy.MOD_ID)
public final class EconomyNeoForge {

    public EconomyNeoForge() {
        BlockRegistries.init();
        ItemRegistries.init();
        SoundRegistries.init();
        MarketNetwork.init();
        Economy.init();

        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Vaults and tanks hold escrowed market goods; breaking one must not let
     * another player walk away with its contents.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        BlockEntity be = level.getBlockEntity(event.getPos());
        UUID owner = null;
        String messageKey = null;
        if (be instanceof VaultBlockEntity vault) {
            owner = vault.getOwner();
            messageKey = "message.economy.vault.not_owner";
        } else if (be instanceof TankBlockEntity tank) {
            owner = tank.getOwner();
            messageKey = "message.economy.tank.not_owner";
        }
        if (owner == null || owner.equals(player.getUUID())) return;
        if (player.hasPermissions(2)) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer && messageKey != null) {
            serverPlayer.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        EconomyServerLifecycle.load(level);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EconomyServerLifecycle.save();
        Economy.LOGGER.info("Economy data saved");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EconomyServerLifecycle.tick(event.getServer());
    }
}
