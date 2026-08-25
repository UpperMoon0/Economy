package com.nstut.fabric.client;

import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.client.MarketScreen;
import com.nstut.economy.client.TankRenderer;
import com.nstut.economy.client.TankScreen;
import com.nstut.economy.client.VaultScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;

public class EconomyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenRegistry.register(BlockRegistries.MARKET_MENU.get(), MarketScreen::new);
        ScreenRegistry.register(BlockRegistries.VAULT_MENU.get(), VaultScreen::new);
        ScreenRegistry.register(BlockRegistries.TANK_MENU.get(), TankScreen::new);
        BlockEntityRenderers.register(BlockRegistries.TANK_BE.get(), TankRenderer::new);
    }
}
