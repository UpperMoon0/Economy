package com.nstut.economy.blocks;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Slot-level item insertion primitives shared by vault block entities and the
 * market transaction layer. Every operation reports exact remainders so callers
 * can run simulate-then-commit transactions without ever losing or duplicating
 * items.
 */
public final class VaultInventoryOps {
    private VaultInventoryOps() {}

    public static int countAvailableSpace(List<ItemStack> slots, ItemStack stack) {
        int space = 0;
        int maxStack = stack.getMaxStackSize();
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) {
                space += maxStack;
            } else if (com.nstut.economy.compat.Compat.stacksEqual(slot, stack)) {
                space += maxStack - slot.getCount();
            }
        }
        return space;
    }

    /**
     * Inserts as much of {@code incoming} into {@code slots} as fits, mutating
     * {@code slots} in place. Returns exactly what did not fit; an empty result
     * means the full payload was stored.
     */
    public static NonNullList<ItemStack> insert(List<ItemStack> slots, List<ItemStack> incoming) {
        NonNullList<ItemStack> leftovers = NonNullList.create();
        for (ItemStack stack : incoming) {
            if (stack == null || stack.isEmpty()) continue;
            ItemStack remainder = stack.copy();
            for (int i = 0; i < slots.size() && !remainder.isEmpty(); i++) {
                ItemStack slot = slots.get(i);
                if (slot.isEmpty()) {
                    int put = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                    ItemStack placed = remainder.copy();
                    placed.setCount(put);
                    slots.set(i, placed);
                    remainder.shrink(put);
                } else if (com.nstut.economy.compat.Compat.stacksEqual(slot, remainder)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int add = Math.min(space, remainder.getCount());
                    if (add > 0) {
                        slot.grow(add);
                        remainder.shrink(add);
                    }
                }
            }
            if (!remainder.isEmpty()) leftovers.add(remainder);
        }
        return leftovers;
    }

    public static List<ItemStack> copySlots(List<ItemStack> slots) {
        List<ItemStack> copy = new ArrayList<>(slots.size());
        for (ItemStack slot : slots) {
            copy.add(slot == null ? ItemStack.EMPTY : slot.copy());
        }
        return copy;
    }

    public static NonNullList<ItemStack> simulateInsert(List<ItemStack> slots, List<ItemStack> incoming) {
        return insert(copySlots(slots), incoming);
    }

    /**
     * Distributes a payload across multiple inventories in order while preserving
     * prefix accounting. Once any exact stack is only partially accepted, no later
     * stack is attempted. Therefore a numeric accepted count always identifies the
     * exact prefix removed from the payload, which legacy escrow consumers rely on.
     * Mutates the given slot lists.
     */
    public static NonNullList<ItemStack> distribute(List<List<ItemStack>> inventories, List<ItemStack> incoming) {
        NonNullList<ItemStack> leftovers = NonNullList.create();
        boolean blocked = false;
        for (ItemStack stack : incoming) {
            if (stack == null || stack.isEmpty()) continue;
            if (blocked) {
                leftovers.add(stack.copy());
                continue;
            }

            NonNullList<ItemStack> remainder = NonNullList.create();
            remainder.add(stack.copy());
            for (List<ItemStack> slots : inventories) {
                if (remainder.isEmpty()) break;
                remainder = insert(slots, remainder);
            }
            if (!remainder.isEmpty()) {
                for (ItemStack rest : remainder) {
                    if (rest != null && !rest.isEmpty()) leftovers.add(rest.copy());
                }
                blocked = true;
            }
        }
        return leftovers;
    }

    public static NonNullList<ItemStack> simulateDistribute(List<List<ItemStack>> inventories, List<ItemStack> incoming) {
        List<List<ItemStack>> copies = new ArrayList<>(inventories.size());
        for (List<ItemStack> slots : inventories) {
            copies.add(copySlots(slots));
        }
        return distribute(copies, incoming);
    }

    public static int total(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) total += stack.getCount();
        }
        return total;
    }
}
