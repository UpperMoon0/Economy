package com.nstut.economy.blocks;

import com.nstut.Economy;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

public class BlockRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Economy.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> MARKET = BLOCKS.register("market", MarketBlock::new);
    public static final RegistrySupplier<Block> TRADING = BLOCKS.register("trading", TradingBlock::new);
}
