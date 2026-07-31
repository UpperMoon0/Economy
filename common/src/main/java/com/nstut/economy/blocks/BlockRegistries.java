package com.nstut.economy.blocks;

import com.nstut.Economy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Economy.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Economy.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Economy.MOD_ID);

    public static final RegistryObject<Block> MARKET = BLOCKS.register("market", MarketBlock::new);
    public static final RegistryObject<Block> VAULT = BLOCKS.register("vault", VaultBlock::new);
    public static final RegistryObject<Block> TANK = BLOCKS.register("tank", TankBlock::new);

    public static final RegistryObject<BlockEntityType<VaultBlockEntity>> VAULT_BE =
            BLOCK_ENTITIES.register("vault", () -> BlockEntityType.Builder.of(VaultBlockEntity::new, VAULT.get()).build(null));
    public static final RegistryObject<BlockEntityType<TankBlockEntity>> TANK_BE =
            BLOCK_ENTITIES.register("tank", () -> BlockEntityType.Builder.of(TankBlockEntity::new, TANK.get()).build(null));

    public static final RegistryObject<MenuType<MarketMenu>> MARKET_MENU =
            MENUS.register("market", () -> new MenuType<>(MarketMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<VaultMenu>> VAULT_MENU =
            MENUS.register("vault", () -> new MenuType<>(VaultMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<TankMenu>> TANK_MENU =
            MENUS.register("tank", () -> IForgeMenuType.create((id, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                TankBlockEntity tank = inv.player.level().getBlockEntity(pos) instanceof TankBlockEntity t ? t : null;
                return new TankMenu(id, inv, tank, new net.minecraft.world.inventory.SimpleContainerData(1), tank);
            }));
}
