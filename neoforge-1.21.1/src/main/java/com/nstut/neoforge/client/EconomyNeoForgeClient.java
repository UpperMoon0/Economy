package com.nstut.neoforge.client;

import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.client.MarketScreen;
import com.nstut.economy.client.TankRenderer;
import com.nstut.economy.client.TankScreen;
import com.nstut.economy.client.VaultScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = "economy", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EconomyNeoForgeClient {

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(BlockRegistries.MARKET_MENU.get(), MarketScreen::new);
        event.register(BlockRegistries.VAULT_MENU.get(), VaultScreen::new);
        event.register(BlockRegistries.TANK_MENU.get(), TankScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockRegistries.TANK_BE.get(), TankRenderer::new);
    }
}
