package com.nstut.economy.items;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Economy.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> MARKET = ITEMS.register("market", 
        () -> new BlockItem(BlockRegistries.MARKET.get(), new Item.Properties()));
    
    public static final RegistrySupplier<Item> TRADING = ITEMS.register("trading",
        () -> new BlockItem(BlockRegistries.TRADING.get(), new Item.Properties()));
}
