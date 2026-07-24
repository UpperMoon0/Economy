package com.nstut.economy.blocks;

import com.nstut.Economy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Economy.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Economy.MOD_ID);

    public static final RegistryObject<Block> MARKET = BLOCKS.register("market", MarketBlock::new);
    public static final RegistryObject<Block> TRADING = BLOCKS.register("trading", TradingBlock::new);
    public static final RegistryObject<Block> VAULT = BLOCKS.register("vault", VaultBlock::new);

    public static final RegistryObject<BlockEntityType<VaultBlockEntity>> VAULT_BE =
            BLOCK_ENTITIES.register("vault", () -> BlockEntityType.Builder.of(VaultBlockEntity::new, VAULT.get()).build(null));
}
