package com.nstut.neoforge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.command.EconomyCommands;
import com.nstut.economy.items.ItemRegistries;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.economy.network.NetworkChannel;
import com.nstut.economy.server.EconomyServerLifecycle;
import com.nstut.economy.sound.SoundRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

@Mod(Economy.MOD_ID)
public final class EconomyNeoForge {

    public EconomyNeoForge(IEventBus modEventBus) {
        BlockRegistries.init(modEventBus);
        ItemRegistries.init(modEventBus);
        SoundRegistries.init(modEventBus);
        MarketNetwork.init();
        Economy.init();

        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    /**
     * Vaults and tanks hold escrowed market goods; breaking one must not let
     * another player walk away with its contents.
     */
    private void onBlockBreak(BreakBlockEvent event) {
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
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer && messageKey != null) {
            serverPlayer.sendOverlayMessage(Component.translatable(messageKey));
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        EconomyServerLifecycle.load(level);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        EconomyServerLifecycle.save();
        Economy.LOGGER.info("Economy data saved");
    }

    private void onServerTick(ServerTickEvent.Post event) {
        EconomyServerLifecycle.tick(event.getServer());
    }
}
