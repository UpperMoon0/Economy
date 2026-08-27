package com.nstut.fabric.client;

import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.client.MarketScreen;
import com.nstut.economy.client.TankRenderer;
import com.nstut.economy.client.TankScreen;
import com.nstut.economy.client.VaultScreen;
import com.nstut.economy.testing.LiveJoinClientProbe;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class EconomyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(BlockRegistries.MARKET_MENU.get(), MarketScreen::new);
        MenuScreens.register(BlockRegistries.VAULT_MENU.get(), VaultScreen::new);
        MenuScreens.register(BlockRegistries.TANK_MENU.get(), TankScreen::new);
        BlockEntityRenderers.register(BlockRegistries.TANK_BE.get(), TankRenderer::new);
        com.nstut.economy.network.NetworkChannel.registerClientReceivers();
        LiveJoinClientProbe.register();
    }
}
