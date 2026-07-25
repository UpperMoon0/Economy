package com.nstut.economy.items;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Economy.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Economy.MOD_ID);

    public static final RegistryObject<Item> MARKET = ITEMS.register("market",
        () -> new BlockItem(BlockRegistries.MARKET.get(), new Item.Properties()));

    public static final RegistryObject<Item> VAULT = ITEMS.register("vault",
        () -> new BlockItem(BlockRegistries.VAULT.get(), new Item.Properties()));

    public static final RegistryObject<Item> COIN = ITEMS.register("coin",
        () -> new Item(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("tab", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.economy"))
            .icon(() -> new ItemStack(COIN.get()))
            .displayItems((params, output) -> {
                output.accept(COIN.get());
                output.accept(MARKET.get());
                output.accept(VAULT.get());
            })
            .build());
}
