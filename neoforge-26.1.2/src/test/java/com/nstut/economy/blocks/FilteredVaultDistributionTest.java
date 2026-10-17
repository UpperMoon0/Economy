package com.nstut.economy.blocks;


import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(net.neoforged.testframework.junit.EphemeralTestServerProvider.class)
class FilteredVaultDistributionTest {
    @Test void rejectedVaultIsSkippedAndOverflowReachesNextAllowedVault(net.minecraft.server.MinecraftServer server) {
        var rejected = new ArrayList<>(List.of(ItemStack.EMPTY));
        var first = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT, 60)));
        var second = new ArrayList<>(List.of(ItemStack.EMPTY));
        var payload = new ItemStack(Items.IRON_INGOT, 12);
        var left = VaultInventoryOps.distribute(List.of(rejected, first, second), List.of(payload), (i, s) -> i != 0);
        assertTrue(left.isEmpty()); assertTrue(rejected.get(0).isEmpty());
        assertEquals(64, first.get(0).getCount()); assertEquals(8, second.get(0).getCount());
        assertEquals(12, payload.getCount());
    }
    @Test void filteringPreservesExactPrefixAndMetadata(net.minecraft.server.MinecraftServer server) {
        var inventory = new ArrayList<>(List.of(ItemStack.EMPTY));
        var first = new ItemStack(Items.DIAMOND, 8);
        var later = new ItemStack(Items.GOLD_INGOT, 4);
        var left = VaultInventoryOps.distribute(List.of(inventory), List.of(first, later), (i, s) -> s.is(Items.GOLD_INGOT));
        assertTrue(inventory.get(0).isEmpty(), "Later variants must stay behind an unaccepted prefix");
        assertEquals(2, left.size()); assertEquals(8, left.get(0).getCount());
        assertTrue(left.get(1).is(Items.GOLD_INGOT));
    }
    @Test void simulationOnCopiedSlotsMatchesCommit(net.minecraft.server.MinecraftServer server) {
        var slots = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT, 60)));
        var incoming = List.of(new ItemStack(Items.IRON_INGOT, 12));
        var simulated = VaultInventoryOps.distribute(List.of(VaultInventoryOps.copySlots(slots)), incoming, (i, s) -> true);
        assertEquals(60, slots.get(0).getCount());
        var actual = VaultInventoryOps.distribute(List.of(slots), incoming, (i, s) -> true);
        assertEquals(VaultInventoryOps.total(simulated), VaultInventoryOps.total(actual));
        assertEquals(8, actual.get(0).getCount());
    }
}
