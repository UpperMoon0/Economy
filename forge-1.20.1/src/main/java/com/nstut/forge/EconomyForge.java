package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.items.ItemRegistries;
import com.nstut.forge.network.MarketNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Economy.MOD_ID)
public final class EconomyForge {
    @SuppressWarnings("removal")
    public EconomyForge() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        BlockRegistries.BLOCKS.register(modEventBus);
        BlockRegistries.BLOCK_ENTITIES.register(modEventBus);
        BlockRegistries.MENUS.register(modEventBus);
        ItemRegistries.ITEMS.register(modEventBus);
        ItemRegistries.CREATIVE_TABS.register(modEventBus);

        MarketNetwork.init();

        Economy.init();
    }
}
