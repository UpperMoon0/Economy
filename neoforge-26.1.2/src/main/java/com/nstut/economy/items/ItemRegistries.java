package com.nstut.economy.items;

import com.nstut.Economy;
import com.nstut.economy.blocks.BlockRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegistries {
    public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS =
            net.neoforged.neoforge.registries.DeferredRegister.createItems(Economy.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Economy.MOD_ID);

    public static final DeferredItem<BlockItem> MARKET = ITEMS.registerSimpleBlockItem(BlockRegistries.MARKET);

    public static final DeferredItem<BlockItem> VAULT = ITEMS.registerSimpleBlockItem(BlockRegistries.VAULT);

    public static final DeferredItem<BlockItem> TANK = ITEMS.registerSimpleBlockItem(BlockRegistries.TANK);

    public static final DeferredItem<Item> COIN = ITEMS.registerSimpleItem("coin");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("tab", () ->
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

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
