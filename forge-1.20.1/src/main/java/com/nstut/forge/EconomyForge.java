package com.nstut.forge;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import com.nstut.economy.items.ItemRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod(Economy.MOD_ID)
public final class EconomyForge {
    public EconomyForge(IEventBus modEventBus) {
        BlockRegistries.BLOCKS.register(modEventBus);
        BlockRegistries.BLOCK_ENTITIES.register(modEventBus);
        ItemRegistries.ITEMS.register(modEventBus);

        Economy.init();
    }
}
