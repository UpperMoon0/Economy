package com.nstut.economy.blocks;

import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VaultInventoryOpsTest extends MinecraftTestBase {

    private static List<ItemStack> slots(int slotCount) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }

    @Test
    @DisplayName("Payloads split across inventories by remainder, never re-offered in whole")
    void distributionCarriesRemaindersForward() {
        List<ItemStack> vaultA = new ArrayList<>();
        vaultA.add(new ItemStack(Items.DIAMOND, 48));
        List<ItemStack> vaultB = slots(1);

        NonNullList<ItemStack> payload = NonNullList.create();
        payload.add(new ItemStack(Items.DIAMOND, 64));

        NonNullList<ItemStack> leftover = VaultInventoryOps.distribute(List.of(vaultA, vaultB), payload);

        assertTrue(leftover.isEmpty(), "64 diamonds fit into 16+64 free space");
        assertEquals(64, vaultA.get(0).getCount(), "first vault filled to the brim");
        assertEquals(48, vaultB.get(0).getCount(), "remainder carried into next vault");
    }

    @Test
    @DisplayName("Overflow is reported exactly once instead of duplicating into later vaults")
    void overflowReportsExactLeftover() {
        List<ItemStack> vaultA = new ArrayList<>();
        vaultA.add(new ItemStack(Items.DIAMOND, 48));
        List<ItemStack> vaultB = new ArrayList<>();
        vaultB.add(new ItemStack(Items.DIAMOND, 32));
        vaultB.add(ItemStack.EMPTY);

        NonNullList<ItemStack> payload = NonNullList.create();
        payload.add(new ItemStack(Items.DIAMOND, 150));

        NonNullList<ItemStack> leftover = VaultInventoryOps.distribute(List.of(vaultA, vaultB), payload);

        assertEquals(38, VaultInventoryOps.total(leftover), "150 offered minus 112 accepted");
        assertEquals(64, vaultA.get(0).getCount());
        assertEquals(64, vaultB.get(0).getCount());
        assertEquals(64, vaultB.get(1).getCount());
    }

    @Test
    @DisplayName("Payloads flow through every slot of later vaults before overflowing")
    void remaindersFillLaterVaultSlotsCompletely() {
        List<ItemStack> vaultA = new ArrayList<>();
        vaultA.add(new ItemStack(Items.DIAMOND, 48));
        List<ItemStack> vaultB = new ArrayList<>();
        vaultB.add(new ItemStack(Items.DIAMOND, 32));
        vaultB.add(ItemStack.EMPTY);

        NonNullList<ItemStack> payload = NonNullList.create();
        payload.add(new ItemStack(Items.DIAMOND, 100));

        NonNullList<ItemStack> leftover = VaultInventoryOps.distribute(List.of(vaultA, vaultB), payload);

        assertTrue(leftover.isEmpty(), "112 free space accepts 100");
        assertEquals(64, vaultA.get(0).getCount());
        assertEquals(64, vaultB.get(0).getCount());
        assertEquals(52, vaultB.get(1).getCount());
    }

    @Test
    @DisplayName("Tagged stacks never merge into untagged partial slots")
    void nbtMismatchDoesNotOvercountSpace() {
        ItemStack named = new ItemStack(Items.DIAMOND, 10);
        CompoundTag tag = named.getOrCreateTag();
        tag.putString("display", "reward");

        List<ItemStack> vault = new ArrayList<>();
        vault.add(named);
        vault.add(new ItemStack(Items.STONE, 1));

        NonNullList<ItemStack> plain = NonNullList.create();
        plain.add(new ItemStack(Items.DIAMOND, 5));
        NonNullList<ItemStack> leftoverPlain = VaultInventoryOps.insert(vault, plain);
        assertEquals(5, VaultInventoryOps.total(leftoverPlain), "plain diamonds must not merge into tagged stack");
        assertEquals(10, vault.get(0).getCount());

        NonNullList<ItemStack> matching = NonNullList.create();
        ItemStack reward = new ItemStack(Items.DIAMOND, 5);
        reward.getOrCreateTag().putString("display", "reward");
        matching.add(reward);
        NonNullList<ItemStack> leftoverMatched = VaultInventoryOps.insert(vault, matching);
        assertTrue(leftoverMatched.isEmpty(), "identically tagged stacks merge");
        assertEquals(15, vault.get(0).getCount());
    }

    @Test
    @DisplayName("Simulation leaves source inventories untouched")
    void simulationIsNonMutating() {
        List<List<ItemStack>> inventories = new ArrayList<>();
        List<ItemStack> vaultA = new ArrayList<>();
        vaultA.add(new ItemStack(Items.DIAMOND, 60));
        inventories.add(vaultA);

        NonNullList<ItemStack> payload = NonNullList.create();
        payload.add(new ItemStack(Items.DIAMOND, 100));

        NonNullList<ItemStack> leftover = VaultInventoryOps.simulateDistribute(inventories, payload);

        assertEquals(96, VaultInventoryOps.total(leftover), "only 4 units would fit");
        assertEquals(60, vaultA.get(0).getCount(), "simulation must not mutate inventory");

        NonNullList<ItemStack> committed = VaultInventoryOps.distribute(inventories, payload);
        assertEquals(96, VaultInventoryOps.total(committed));
        assertEquals(64, vaultA.get(0).getCount());
    }

    @Test
    @DisplayName("Oversized single stacks are clamped to max stack size per empty slot")
    void oversizedStacksSplitAcrossEmptySlots() {
        List<ItemStack> vault = slots(3);
        NonNullList<ItemStack> payload = NonNullList.create();
        payload.add(new ItemStack(Items.DIAMOND, 200));

        NonNullList<ItemStack> leftover = VaultInventoryOps.insert(vault, payload);

        assertEquals(8, VaultInventoryOps.total(leftover));
        assertEquals(64, vault.get(0).getCount());
        assertEquals(64, vault.get(1).getCount());
        assertEquals(64, vault.get(2).getCount());

        List<ItemStack> small = slots(1);
        NonNullList<ItemStack> over = NonNullList.create();
        over.add(new ItemStack(Items.DIAMOND, 128));
        NonNullList<ItemStack> rest = VaultInventoryOps.insert(small, over);
        assertEquals(64, rest.get(0).getCount());
        assertEquals(64, small.get(0).getCount());
    }

    @Test
    @DisplayName("countAvailableSpace honors tags and max stack sizes")
    void availableSpaceCalculation() {
        List<ItemStack> vault = new ArrayList<>();
        vault.add(new ItemStack(Items.DIAMOND, 40));
        vault.add(ItemStack.EMPTY);
        vault.add(ItemStack.EMPTY);

        assertEquals(24 + 128, VaultInventoryOps.countAvailableSpace(vault, new ItemStack(Items.DIAMOND, 1)));
        assertEquals(2 * 16, VaultInventoryOps.countAvailableSpace(vault, new ItemStack(Items.ENDER_PEARL, 1)));
    }
}
