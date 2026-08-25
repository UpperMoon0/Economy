package com.nstut.economy.items;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Economy.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Economy.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<Item> MARKET = ITEMS.register("market",
        () -> new BlockItem(BlockRegistries.MARKET.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> VAULT = ITEMS.register("vault",
        () -> new BlockItem(BlockRegistries.VAULT.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> TANK = ITEMS.register("tank",
        () -> new BlockItem(BlockRegistries.TANK.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> COIN = ITEMS.register("coin",
        () -> new Item(new Item.Properties()));

    public static final RegistrySupplier<CreativeModeTab> TAB = CREATIVE_TABS.register("tab", () ->
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.economy"))
            .icon(() -> new ItemStack(COIN.get()))
            .displayItems((params, output) -> {
                output.accept(COIN.get());
                output.accept(MARKET.get());
                output.accept(VAULT.get());
                output.accept(TANK.get());
            })
            .build());

    public static void init() {
        ITEMS.register();
        CREATIVE_TABS.register();
    }
}
