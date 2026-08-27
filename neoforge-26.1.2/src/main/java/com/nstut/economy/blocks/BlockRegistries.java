package com.nstut.economy.blocks;

import com.nstut.Economy;
import com.nstut.economy.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public class BlockRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Economy.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Economy.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Economy.MOD_ID);

    public static final DeferredBlock<Block> MARKET = BLOCKS.registerBlock("market", MarketBlock::new,
            props -> props.mapColor(MapColor.COLOR_GREEN)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final DeferredBlock<Block> VAULT = BLOCKS.registerBlock("vault", VaultBlock::new,
            props -> props.mapColor(MapColor.COLOR_GRAY)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final DeferredBlock<Block> TANK = BLOCKS.registerBlock("tank", TankBlock::new,
            props -> props.mapColor(MapColor.COLOR_GRAY)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VaultBlockEntity>> VAULT_BE =
            BLOCK_ENTITIES.register("vault", () -> new BlockEntityType<>(VaultBlockEntity::new, Set.of(VAULT.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK_BE =
            BLOCK_ENTITIES.register("tank", () -> new BlockEntityType<>(Services.PLATFORM::newTankBlockEntity, Set.of(TANK.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketMenu>> MARKET_MENU =
            MENUS.register("market", () -> IMenuTypeExtension.create((id, inv, data) -> new MarketMenu(id, inv)));
    public static final DeferredHolder<MenuType<?>, MenuType<VaultMenu>> VAULT_MENU =
            MENUS.register("vault", () -> IMenuTypeExtension.create((id, inv, data) -> new VaultMenu(id, inv)));
    public static final DeferredHolder<MenuType<?>, MenuType<TankMenu>> TANK_MENU =
            MENUS.register("tank", () -> IMenuTypeExtension.create((id, inv, data) -> {
                BlockPos pos = com.nstut.economy.client.ClientMenuContext.consumeTankPos();
                TankBlockEntity tank = pos != null && inv.player.level().getBlockEntity(pos) instanceof TankBlockEntity t ? t : null;
                return new TankMenu(id, inv, tank, new net.minecraft.world.inventory.SimpleContainerData(1), tank);
            }));

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
