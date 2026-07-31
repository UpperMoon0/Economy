package com.nstut.economy.blocks;

import com.nstut.economy.data.EconomyAccountData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TankManager {
    private static final Map<UUID, List<EconomyAccountData.VaultRecord>> tanks = new ConcurrentHashMap<>();
    private static EconomyAccountData savedData;

    public static void setAccountData(EconomyAccountData data) {
        savedData = data;
        tanks.clear();
        for (Map.Entry<UUID, List<EconomyAccountData.VaultRecord>> e : data.getTanks().entrySet())
            tanks.put(e.getKey(), new CopyOnWriteArrayList<>(e.getValue()));
    }

    public static void register(UUID owner, BlockPos pos, String dimension) {
        List<EconomyAccountData.VaultRecord> list = tanks.computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>());
        BlockPos p = pos.immutable();
        String dim = dimension != null ? dimension : "minecraft:overworld";
        for (EconomyAccountData.VaultRecord r : list) {
            if (r.pos.equals(p) && r.dimension.equals(dim)) return;
        }
        list.add(new EconomyAccountData.VaultRecord(p, dim));
        if (savedData != null) savedData.addTank(owner, pos, dimension);
    }

    public static void register(UUID owner, BlockPos pos) {
        register(owner, pos, "minecraft:overworld");
    }

    public static void unregister(UUID owner, BlockPos pos, String dimension) {
        List<EconomyAccountData.VaultRecord> list = tanks.get(owner);
        if (list != null) {
            BlockPos p = pos.immutable();
            String dim = dimension != null ? dimension : "minecraft:overworld";
            list.removeIf(r -> r.pos.equals(p) && r.dimension.equals(dim));
            if (savedData != null) savedData.removeTank(owner, pos, dimension);
        }
    }

    public static void unregister(BlockPos pos) {
        for (Map.Entry<UUID, List<EconomyAccountData.VaultRecord>> e : tanks.entrySet()) {
            BlockPos p = pos.immutable();
            e.getValue().removeIf(r -> r.pos.equals(p));
        }
    }

    public static void unregister(UUID owner) {
        tanks.remove(owner);
        if (savedData != null) savedData.getTanks().remove(owner);
    }

    public static boolean hasTank(UUID owner) {
        List<EconomyAccountData.VaultRecord> list = tanks.get(owner);
        return list != null && !list.isEmpty();
    }

    public static List<EconomyAccountData.VaultRecord> getTankRecords(UUID owner) {
        return tanks.getOrDefault(owner, Collections.emptyList());
    }

    @Nullable
    public static TankBlockEntity getTank(Level level, UUID owner) {
        List<TankBlockEntity> list = getTanks(level, owner);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<TankBlockEntity> getTanks(Level level, UUID owner) {
        List<EconomyAccountData.VaultRecord> records = tanks.get(owner);
        if (records == null || records.isEmpty()) return Collections.emptyList();
        List<TankBlockEntity> result = new ArrayList<>();
        for (EconomyAccountData.VaultRecord record : records) {
            Level targetLevel = level;
            if (level.getServer() != null) {
                ResourceLocation dimRl = new ResourceLocation(record.dimension);
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimRl);
                ServerLevel serverLevel = level.getServer().getLevel(key);
                if (serverLevel != null) targetLevel = serverLevel;
            }
            if (targetLevel.getBlockEntity(record.pos) instanceof TankBlockEntity tank) {
                result.add(tank);
            }
        }
        return result;
    }

    public static int countFluidInTanks(Level level, UUID owner, Fluid fluid) {
        int count = 0;
        for (TankBlockEntity t : getTanks(level, owner)) {
            if (t.getMode().canSupplyMarket()) {
                if (t.getFluid().getFluid() == fluid) {
                    count += t.getFluidAmount();
                }
            }
        }
        return count;
    }

    public static int countAvailableFluidSpaceInTanks(Level level, UUID owner, Fluid fluid) {
        int space = 0;
        for (TankBlockEntity t : getTanks(level, owner)) {
            if (t.getMode().canReceiveMarket()) {
                if (t.getFluid().isEmpty()) {
                    space += t.getCapacity();
                } else if (t.getFluid().getFluid() == fluid) {
                    space += t.getCapacity() - t.getFluidAmount();
                }
            }
        }
        return space;
    }

    public static int extractFluidFromTanks(Level level, UUID owner, Fluid fluid, int amount, List<FluidStack> destination) {
        if (countFluidInTanks(level, owner, fluid) < amount) return 0;
        int remaining = amount;
        for (TankBlockEntity t : getTanks(level, owner)) {
            if (remaining <= 0) break;
            if (!t.getMode().canSupplyMarket()) continue;
            if (t.getFluid().getFluid() != fluid) continue;
            int take = Math.min(remaining, t.getFluidAmount());
            FluidStack drained = t.drain(take);
            if (!drained.isEmpty()) {
                destination.add(drained);
                remaining -= drained.getAmount();
            }
        }
        return amount - remaining;
    }

    public static int insertFluidToTanks(Level level, UUID owner, FluidStack stack) {
        return insertFluidToTanks(level, owner, stack, true);
    }

    public static int restoreFluidToTanks(Level level, UUID owner, FluidStack stack) {
        return insertFluidToTanks(level, owner, stack, false);
    }

    private static int insertFluidToTanks(Level level, UUID owner, FluidStack stack, boolean requireOutputMode) {
        int remaining = stack.getAmount();
        for (TankBlockEntity t : getTanks(level, owner)) {
            if (remaining <= 0) break;
            if (requireOutputMode && !t.getMode().canReceiveMarket()) continue;
            if (!t.getFluid().isEmpty() && !t.getFluid().isFluidEqual(stack)) continue;
            FluidStack toInsert = stack.copy();
            toInsert.setAmount(remaining);
            int filled = t.fill(toInsert);
            remaining -= filled;
        }
        return stack.getAmount() - remaining;
    }
}
