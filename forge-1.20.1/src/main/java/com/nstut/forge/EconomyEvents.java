package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.data.EconomyAccountData;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Economy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EconomyEvents {

    /**
     * Vaults and tanks hold escrowed market goods; breaking one must not let
     * another player walk away with its contents. Owners and sufficiently
     * privileged operators can always break their own or unclaimed blocks.
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
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() != Level.OVERWORLD) return;

            EconomyAccountData accountData = EconomyAccountData.get(serverLevel);
            EconomyOrderData orderData = EconomyOrderData.get(serverLevel);
            EconomyTradeData tradeData = EconomyTradeData.get(serverLevel);

            Economy.getAccountManager().loadFrom(accountData);

            OrderManager orderManager = Economy.getOrderManager();
            orderManager.setOrderData(orderData);
            orderManager.loadFrom(orderData);

            TradeLedger.setTradeData(tradeData);
            VaultManager.setAccountData(accountData);
            TankManager.setAccountData(accountData);

            Economy.LOGGER.info("Economy data loaded for dimension {}", serverLevel.dimension().location());
        }
    }

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() != Level.OVERWORLD) return;
            if (++tickCounter % 20 == 0) {
                Economy.getOrderManager().matchAllPendingOrders(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() != Level.OVERWORLD) return;

            Economy.getAccountManager().saveAll();
            Economy.getOrderManager().saveAll();
            Economy.LOGGER.debug("Economy data saved for dimension {}", serverLevel.dimension().location());
        }
    }
}
