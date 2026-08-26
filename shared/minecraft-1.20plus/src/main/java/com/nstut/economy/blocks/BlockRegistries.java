package com.nstut.economy.blocks;

import com.nstut.Economy;
import com.nstut.economy.platform.Services;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Economy.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Economy.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Economy.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<Block> MARKET = BLOCKS.register("market", MarketBlock::new);
    public static final RegistrySupplier<Block> VAULT = BLOCKS.register("vault", VaultBlock::new);
    public static final RegistrySupplier<Block> TANK = BLOCKS.register("tank", TankBlock::new);

    public static final RegistrySupplier<BlockEntityType<VaultBlockEntity>> VAULT_BE =
            BLOCK_ENTITIES.register("vault", () -> BlockEntityType.Builder.of(VaultBlockEntity::new, VAULT.get()).build(null));
    public static final RegistrySupplier<BlockEntityType<TankBlockEntity>> TANK_BE =
            BLOCK_ENTITIES.register("tank", () -> BlockEntityType.Builder.of(
                    Services.PLATFORM::newTankBlockEntity, TANK.get()).build(null));

    public static final RegistrySupplier<MenuType<MarketMenu>> MARKET_MENU =
            MENUS.register("market", () -> new MenuType<>(MarketMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistrySupplier<MenuType<VaultMenu>> VAULT_MENU =
            MENUS.register("vault", () -> new MenuType<>(VaultMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistrySupplier<MenuType<TankMenu>> TANK_MENU =
            MENUS.register("tank", () -> new MenuType<>((id, inv) -> {
                BlockPos pos = com.nstut.economy.client.ClientMenuContext.consumeTankPos();
                TankBlockEntity tank = pos != null && inv.player.level().getBlockEntity(pos) instanceof TankBlockEntity t ? t : null;
                return new TankMenu(id, inv, tank, new net.minecraft.world.inventory.SimpleContainerData(1), tank);
            }, FeatureFlags.DEFAULT_FLAGS));

    public static void init() {
        BLOCKS.register();
        BLOCK_ENTITIES.register();
        MENUS.register();
    }
}

