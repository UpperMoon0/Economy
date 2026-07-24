package com.nstut.economy.items;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Economy.MOD_ID);

    public static final RegistryObject<Item> MARKET = ITEMS.register("market",
        () -> new BlockItem(BlockRegistries.MARKET.get(), new Item.Properties()));

    public static final RegistryObject<Item> TRADING = ITEMS.register("trading",
        () -> new BlockItem(BlockRegistries.TRADING.get(), new Item.Properties()));

    public static final RegistryObject<Item> VAULT = ITEMS.register("vault",
        () -> new BlockItem(BlockRegistries.VAULT.get(), new Item.Properties()));
}
