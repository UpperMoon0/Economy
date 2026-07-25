package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.data.EconomyAccountData;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.data.EconomyTradeData;
import com.nstut.economy.data.TradeLedger;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Economy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EconomyEvents {

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
