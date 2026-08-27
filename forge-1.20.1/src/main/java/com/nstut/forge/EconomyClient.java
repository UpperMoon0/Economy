package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.client.MarketScreen;
import com.nstut.economy.client.TankRenderer;
import com.nstut.economy.client.TankScreen;
import com.nstut.economy.client.VaultScreen;
import com.nstut.economy.testing.LiveJoinClientProbe;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Economy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EconomyClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BlockRegistries.MARKET_MENU.get(), MarketScreen::new);
            MenuScreens.register(BlockRegistries.VAULT_MENU.get(), VaultScreen::new);
            MenuScreens.register(BlockRegistries.TANK_MENU.get(), TankScreen::new);
            Economy.LOGGER.info("Registered Forge menu screens: market, vault, tank (missing screens: {})",
                    MenuScreens.selfTest());
            LiveJoinClientProbe.register();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockRegistries.TANK_BE.get(), TankRenderer::new);
    }
}
