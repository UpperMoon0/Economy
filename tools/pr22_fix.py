from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"expected snippet not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


order_path = "shared/minecraft-all/src/main/java/com/nstut/economy/trading/Order.java"
replace_once(
    order_path,
    """        this.persistedTypeId = persistedTypeId != null ? persistedTypeId : commodity.getTypeId();
        this.persistedPayload = persistedPayload != null ? persistedPayload : encodeSafely(commodity);
""",
    """        this.persistedTypeId = persistedTypeId != null ? persistedTypeId : commodity.getTypeId();
        CommodityPayload initialPayload = persistedPayload != null ? persistedPayload : encodeSafely(commodity);
        this.persistedPayload = initialPayload != null ? initialPayload : CommodityPayload.empty(1);
""",
)
replace_once(
    order_path,
    """    public EconomyOrderData.OrderSnapshot toSnapshot() {
        CommodityPayload payload = encodeSafely(commodity);
        if (payload != null) persistedPayload = payload;
        String legacyType = commodity.getType() == ICommodity.CommodityType.FLUID ? "FLUID"
""",
    """    public EconomyOrderData.OrderSnapshot toSnapshot() {
        CommodityPayload payload = encodeSafely(commodity);
        if (payload != null) {
            persistedTypeId = commodity.getTypeId();
            persistedPayload = payload;
        }
        String legacyType = commodity.getType() == ICommodity.CommodityType.FLUID ? "FLUID"
""",
)
replace_once(
    order_path,
    """    private static CommodityPayload encodeSafely(ICommodity commodity) {
        try { return EconomyApi.commodityTypes().handlerFor(commodity).encode(commodity); }
        catch (RuntimeException unavailable) { return CommodityPayload.empty(1); }
    }
""",
    """    private static CommodityPayload encodeSafely(ICommodity commodity) {
        try { return EconomyApi.commodityTypes().handlerFor(commodity).encode(commodity); }
        catch (RuntimeException unavailable) { return null; }
    }
""",
)

helper = Path("shared/minecraft-all/src/main/java/com/nstut/economy/api/internal/AtomicStorageRestore.java")
helper.write_text(r'''package com.nstut.economy.api.internal;

import com.nstut.economy.blocks.TankManager;
import com.nstut.economy.blocks.VaultManager;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Transactional restoration for legacy pre-extracted Vault/Tank escrow. */
public final class AtomicStorageRestore {
    private AtomicStorageRestore() { }

    public static boolean restoreEscrow(ServerLevel level, UUID owner,
                                        Collection<ItemStack> items,
                                        Collection<EconomyFluidStack> fluids) {
        if (level == null || owner == null) return false;

        NonNullList<ItemStack> itemPayload = copyItems(items);
        List<EconomyFluidStack> fluidPayload = copyFluids(fluids);
        if (itemPayload.isEmpty() && fluidPayload.isEmpty()) return true;

        if (!itemPayload.isEmpty()
                && !VaultManager.simulateInsertItemStacksToVaults(level, owner, itemPayload).isEmpty()) {
            return false;
        }

        int fluidTotal = fluidPayload.stream().mapToInt(EconomyFluidStack::getAmount).sum();
        if (fluidTotal > 0) {
            EconomyFluidStack merged = TankManager.mergeFluids(fluidPayload);
            if (TankManager.simulateInsertFluidToTanks(level, owner, merged) < fluidTotal) return false;
        }

        var vaults = itemPayload.isEmpty() ? List.<Container>of() : VaultManager.getVaults(level, owner);
        List<NonNullList<ItemStack>> vaultSnapshots = snapshotVaults(vaults);
        var tanks = fluidPayload.isEmpty() ? List.of() : TankManager.getTanks(level, owner);
        List<EconomyFluidStack> tankSnapshots = snapshotTanks(tanks);

        Runnable rollback = () -> {
            restoreVaultSnapshots(vaults, vaultSnapshots);
            restoreTankSnapshots(tanks, tankSnapshots);
        };

        return commitWithRollback(() -> {
            if (!itemPayload.isEmpty()
                    && !VaultManager.insertItemStacksToVaults(level, owner, itemPayload).isEmpty()) {
                return false;
            }
            if (fluidTotal > 0) {
                int restored = 0;
                for (EconomyFluidStack stack : fluidPayload) {
                    restored += TankManager.restoreFluidToTanks(level, owner, stack.copy());
                }
                if (restored != fluidTotal) return false;
            }
            return true;
        }, rollback);
    }

    static boolean commitWithRollback(BooleanSupplier commit, Runnable rollback) {
        try {
            if (commit.getAsBoolean()) return true;
        } catch (RuntimeException failure) {
            rollback.run();
            throw failure;
        }
        rollback.run();
        return false;
    }

    private static NonNullList<ItemStack> copyItems(Collection<ItemStack> stacks) {
        NonNullList<ItemStack> copy = NonNullList.create();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static List<EconomyFluidStack> copyFluids(Collection<EconomyFluidStack> stacks) {
        List<EconomyFluidStack> copy = new ArrayList<>();
        if (stacks != null) {
            for (EconomyFluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static List<NonNullList<ItemStack>> snapshotVaults(List<? extends Container> vaults) {
        List<NonNullList<ItemStack>> snapshots = new ArrayList<>(vaults.size());
        for (Container vault : vaults) {
            NonNullList<ItemStack> snapshot = NonNullList.withSize(vault.getContainerSize(), ItemStack.EMPTY);
            for (int slot = 0; slot < vault.getContainerSize(); slot++) {
                ItemStack current = vault.getItem(slot);
                snapshot.set(slot, current == null ? ItemStack.EMPTY : current.copy());
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private static void restoreVaultSnapshots(List<? extends Container> vaults,
                                              List<NonNullList<ItemStack>> snapshots) {
        if (vaults.size() != snapshots.size()) {
            throw new IllegalStateException("Vault topology changed during atomic escrow rollback");
        }
        for (int i = 0; i < vaults.size(); i++) {
            Container vault = vaults.get(i);
            NonNullList<ItemStack> snapshot = snapshots.get(i);
            if (vault.getContainerSize() != snapshot.size()) {
                throw new IllegalStateException("Vault size changed during atomic escrow rollback");
            }
            for (int slot = 0; slot < snapshot.size(); slot++) vault.setItem(slot, snapshot.get(slot).copy());
            for (int slot = 0; slot < snapshot.size(); slot++) {
                if (!com.nstut.economy.compat.Compat.stacksEqual(vault.getItem(slot), snapshot.get(slot))) {
                    throw new IllegalStateException("Could not roll back Vault escrow mutation atomically");
                }
            }
        }
    }

    private static List<EconomyFluidStack> snapshotTanks(List<?> tanks) {
        List<EconomyFluidStack> snapshots = new ArrayList<>(tanks.size());
        for (Object tank : tanks) {
            try {
                java.lang.reflect.Method getFluid = tank.getClass().getMethod("getFluid");
                snapshots.add(((EconomyFluidStack) getFluid.invoke(tank)).copy());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not snapshot Tank for atomic escrow restore", failure);
            }
        }
        return snapshots;
    }

    private static void restoreTankSnapshots(List<?> tanks, List<EconomyFluidStack> snapshots) {
        if (tanks.size() != snapshots.size()) {
            throw new IllegalStateException("Tank topology changed during atomic escrow rollback");
        }
        for (int i = 0; i < tanks.size(); i++) {
            Object tank = tanks.get(i);
            EconomyFluidStack snapshot = snapshots.get(i);
            try {
                java.lang.reflect.Method setFluid = tank.getClass().getMethod("setFluid", EconomyFluidStack.class);
                java.lang.reflect.Method getFluid = tank.getClass().getMethod("getFluid");
                setFluid.invoke(tank, snapshot.copy());
                EconomyFluidStack restored = (EconomyFluidStack) getFluid.invoke(tank);
                if (!sameFluid(restored, snapshot)) {
                    throw new IllegalStateException("Could not roll back Tank escrow mutation atomically");
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not roll back Tank escrow mutation atomically", failure);
            }
        }
    }

    private static boolean sameFluid(EconomyFluidStack left, EconomyFluidStack right) {
        if (left == null || left.isEmpty()) return right == null || right.isEmpty();
        if (right == null || right.isEmpty()) return false;
        return left.getAmount() == right.getAmount() && left.isFluidEqual(right);
    }
}
''')

manager_paths = [
    "shared/minecraft-1.20plus/src/main/java/com/nstut/economy/trading/OrderManager.java",
    "neoforge-26.1.2/src/main/java/com/nstut/economy/trading/OrderManager.java",
]

for path in manager_paths:
    p = Path(path)
    text = p.read_text()
    if "import com.nstut.economy.api.internal.AtomicStorageRestore;" not in text:
        text = text.replace(
            "import com.nstut.economy.api.StorageReservation;\n",
            "import com.nstut.economy.api.StorageReservation;\nimport com.nstut.economy.api.internal.AtomicStorageRestore;\n",
            1,
        )

    pattern = r"        NonNullList<ItemStack> itemRemainder = copyStacks\(reservedItems\);\n        List<EconomyFluidStack> fluidRemainder = copyFluidStacks\(reservedFluids\);\n\n.*?\n        int retainedItems ="
    replacement = """        NonNullList<ItemStack> itemRemainder = copyStacks(reservedItems);
        List<EconomyFluidStack> fluidRemainder = copyFluidStacks(reservedFluids);

        if (level != null && (!itemRemainder.isEmpty() || !fluidRemainder.isEmpty())
                && AtomicStorageRestore.restoreEscrow(level, owner, itemRemainder, fluidRemainder)) {
            itemRemainder.clear();
            fluidRemainder.clear();
        }

        int retainedItems ="""
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1 and "AtomicStorageRestore.restoreEscrow(level, owner, itemRemainder, fluidRemainder)" not in text:
        raise SystemExit(f"rejectCancelledSell shape not recognized in {path}")

    text = text.replace(
        "if (!extracted.isEmpty()) com.nstut.economy.blocks.VaultManager.insertItemStacksToVaults(level, requester, extracted);",
        """if (!extracted.isEmpty() && !AtomicStorageRestore.restoreEscrow(level, requester, extracted, List.of())) {
                            Economy.LOGGER.error("Could not atomically restore partial Vault extraction while editing order {}", orderId);
                        }""",
    )
    text = text.replace(
        "for (EconomyFluidStack fs : drained) com.nstut.economy.blocks.TankManager.restoreFluidToTanks(level, requester, fs);",
        """if (!drained.isEmpty() && !AtomicStorageRestore.restoreEscrow(level, requester, List.of(), drained)) {
                            Economy.LOGGER.error("Could not atomically restore partial Tank extraction while editing order {}", orderId);
                        }""",
    )

    item_start = text.index("    private static boolean returnItemsToVaults(")
    fluid_start = text.index("    private static boolean returnFluidToTanks(", item_start)
    item_fn = '''    private static boolean returnItemsToVaults(net.minecraft.server.level.ServerLevel level, UUID requester,
                                               Order order, int qty) {
        NonNullList<ItemStack> returnItems = NonNullList.create();
        int countToReturn = qty;
        for (ItemStack stack : order.getReservedItems()) {
            if (countToReturn <= 0) break;
            if (stack == null || stack.isEmpty()) continue;
            int take = Math.min(countToReturn, stack.getCount());
            ItemStack part = stack.copy(); part.setCount(take); returnItems.add(part); countToReturn -= take;
        }
        if (countToReturn > 0 || returnItems.isEmpty()) return false;
        if (!AtomicStorageRestore.restoreEscrow(level, requester, returnItems, List.of())) {
            Economy.LOGGER.error("Vault restoration failed atomically while editing order {}; escrow left untouched", orderIdSafe(order));
            return false;
        }
        order.consumeEscrow(qty);
        return true;
    }

'''
    text = text[:item_start] + item_fn + text[fluid_start:]

    fluid_start = text.index("    private static boolean returnFluidToTanks(")
    fluid_end = text.index("    private static UUID orderIdSafe", fluid_start)
    fluid_fn = '''    private static boolean returnFluidToTanks(net.minecraft.server.level.ServerLevel level, UUID requester,
                                              Order order, int qty) {
        List<EconomyFluidStack> parts = new ArrayList<>();
        int toTake = qty;
        for (EconomyFluidStack fs : order.getReservedFluids()) {
            if (toTake <= 0) break;
            if (fs == null || fs.isEmpty()) continue;
            EconomyFluidStack part = fs.copy(); part.setAmount(Math.min(toTake, fs.getAmount())); parts.add(part); toTake -= part.getAmount();
        }
        if (toTake > 0 || parts.isEmpty()) return false;
        if (!AtomicStorageRestore.restoreEscrow(level, requester, List.of(), parts)) {
            Economy.LOGGER.error("Tank restoration failed atomically while editing order {}; escrow left untouched", orderIdSafe(order));
            return false;
        }
        order.consumeEscrow(qty);
        return true;
    }

'''
    text = text[:fluid_start] + fluid_fn + text[fluid_end:]

    cancel_anchor = text.index("    public boolean cancelOrder(UUID orderId, UUID requester, net.minecraft.server.level.ServerLevel level)")
    cancel_items = text.index("            if (!order.getReservedItems().isEmpty()) {", cancel_anchor)
    cancel_end = text.index("        }\n\n        if (order.cancelInternal())", cancel_items)
    cancel_restore = '''            if (!AtomicStorageRestore.restoreEscrow(level, requester,
                    order.getReservedItems(), order.getReservedFluids())) {
                Economy.LOGGER.error("Storage restoration failed atomically while cancelling order {}; order kept intact", orderId);
                return false;
            }
'''
    text = text[:cancel_items] + cancel_restore + text[cancel_end:]
    p.write_text(text)

# Bring 26.1.2 unreadable-codec quarantine to parity with the shared manager.
neo = Path("neoforge-26.1.2/src/main/java/com/nstut/economy/trading/OrderManager.java")
text = neo.read_text()
text = text.replace(
    '''                Economy.LOGGER.error("Failed to load persisted order {} for item {}; recovery snapshot preserved in world data",
                        snap.orderId, snap.itemId, e);
                quarantineOrder(snap, "order failed to deserialize");''',
    '''                Economy.LOGGER.error("Failed to load persisted order {} for item {}; preserving unreadable snapshot for addon recovery",
                        snap.orderId, snap.itemId, e);
                quarantineOrder(snap, "order failed to deserialize", true);''',
)
old_quarantine = '''    private void quarantineOrder(EconomyOrderData.OrderSnapshot snap, String reason) {
        if (!hasRecoveryState(snap)) return;
        if (quarantinedOrders.put(snap.orderId, snap) == null) {'''
new_quarantine = '''    private void quarantineOrder(EconomyOrderData.OrderSnapshot snap, String reason) {
        quarantineOrder(snap, reason, false);
    }

    private void quarantineOrder(EconomyOrderData.OrderSnapshot snap, String reason, boolean preserveWithoutRecoveryState) {
        if (snap == null || (!preserveWithoutRecoveryState && !hasRecoveryState(snap))) return;
        if (quarantinedOrders.put(snap.orderId, snap) == null) {'''
if old_quarantine in text:
    text = text.replace(old_quarantine, new_quarantine, 1)
elif new_quarantine not in text:
    raise SystemExit("26.1.2 quarantine shape not recognized")
neo.write_text(text)

replace_once(
    "shared/minecraft-all/src/main/java/com/nstut/economy/api/IOrderManager.java",
    "    /** Server orders use the same concrete order object and return null only when domain validation rejects creation. */\n",
    "    /** Server orders use the same concrete order object and return null when domain validation rejects creation or OrderCreatePre cancels it. */\n",
)

payload_test = Path("forge-1.20.1/src/test/java/com/nstut/economy/api/AddonPayloadPersistenceRegressionTest.java")
payload_test.write_text(r'''package com.nstut.economy.api;

import com.nstut.Economy;
import com.nstut.economy.data.EconomyOrderData;
import com.nstut.economy.test.MinecraftTestBase;
import com.nstut.economy.trading.Order;
import com.nstut.economy.trading.OrderManager;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AddonPayloadPersistenceRegressionTest extends MinecraftTestBase {
    private static final EconomyId TYPE = EconomyId.of("payloadreview", "commodity");
    private static final EconomyId ID = EconomyId.of("payloadreview", "token");

    @BeforeEach
    void setUp() {
        EconomyApi.commodityTypes().unregister(TYPE);
        EconomyApi.commodityTypes().register(new Handler(false));
        Economy.ensureApiRegistrations();
    }

    @AfterEach
    void tearDown() {
        EconomyApi.commodityTypes().unregister(TYPE);
    }

    @Test
    @DisplayName("Active addon orders keep their last known payload while encode is unavailable")
    void activeOrderPreservesPayloadAcrossCodecOutage() {
        Order original = new Order(UUID.randomUUID(), new Commodity("blue"), 4,
                new BigDecimal("2.5"), IOrder.OrderType.BUY, null);
        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(original.toSnapshot());

        OrderManager manager = new OrderManager();
        manager.loadFrom(data);
        assertEquals(Map.of("variant", "blue"), data.getOrders().get(original.getOrderId()).commodityPayload);

        EconomyApi.commodityTypes().unregister(TYPE);
        manager.saveAll();
        EconomyOrderData.OrderSnapshot preserved = data.getOrders().get(original.getOrderId());
        assertNotNull(preserved);
        assertEquals(TYPE.toString(), preserved.commodityTypeId);
        assertEquals(Map.of("variant", "blue"), preserved.commodityPayload,
                "missing handler must not rewrite a known addon payload to empty");

        EconomyApi.commodityTypes().register(new Handler(false));
        manager.loadFrom(data);
        Commodity recovered = (Commodity) manager.getOrder(original.getOrderId()).orElseThrow().getCommodity();
        assertEquals("blue", recovered.variant);
    }

    @Test
    @DisplayName("Transient encoder exceptions preserve the last known addon payload")
    void activeOrderPreservesPayloadWhenEncoderThrows() {
        Order original = new Order(UUID.randomUUID(), new Commodity("blue"), 4,
                new BigDecimal("2.5"), IOrder.OrderType.BUY, null);
        EconomyOrderData data = new EconomyOrderData();
        data.putOrder(original.toSnapshot());
        OrderManager manager = new OrderManager();
        manager.loadFrom(data);

        EconomyApi.commodityTypes().unregister(TYPE);
        EconomyApi.commodityTypes().register(new Handler(true));
        manager.saveAll();

        assertEquals(Map.of("variant", "blue"), data.getOrders().get(original.getOrderId()).commodityPayload);
    }

    private static final class Commodity implements ICommodity {
        private final String variant;
        private Commodity(String variant) { this.variant = variant; }
        @Override public EconomyId getId() { return ID; }
        @Override public CommodityType getType() { return CommodityType.CUSTOM; }
        @Override public EconomyId getTypeId() { return TYPE; }
        @Override public Component getDisplayName() { return Component.literal("Payload " + variant); }
        @Override public BigDecimal getBasePrice() { return BigDecimal.ZERO; }
        @Override public boolean hasDynamicPricing() { return false; }
        @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
        @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
        @Override public boolean insertInto(IStorage storage, int amount) { return false; }
    }

    private static final class Handler implements ICommodityTypeHandler {
        private final boolean throwOnEncode;
        private Handler(boolean throwOnEncode) { this.throwOnEncode = throwOnEncode; }
        @Override public EconomyId id() { return TYPE; }
        @Override public int currentSchemaVersion() { return 1; }
        @Override public boolean supports(ICommodity commodity) { return commodity instanceof Commodity; }
        @Override public CommodityPayload encode(ICommodity commodity) {
            if (throwOnEncode) throw new IllegalStateException("transient encoder failure");
            return new CommodityPayload(1, Map.of("variant", ((Commodity) commodity).variant));
        }
        @Override public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
            if (!ID.equals(commodityId)) throw new IllegalArgumentException("unexpected commodity " + commodityId);
            return new Commodity(payload.values().getOrDefault("variant", "missing"));
        }
    }
}
''')

atomic_test = Path("forge-1.20.1/src/test/java/com/nstut/economy/api/internal/AtomicStorageRestoreRegressionTest.java")
atomic_test.parent.mkdir(parents=True, exist_ok=True)
atomic_test.write_text(r'''package com.nstut.economy.api.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtomicStorageRestoreRegressionTest {
    @Test
    @DisplayName("Commit divergence rolls storage back before reporting restore failure")
    void failedCommitRollsBackMutation() {
        AtomicInteger storage = new AtomicInteger(10);
        boolean restored = AtomicStorageRestore.commitWithRollback(
                () -> { storage.set(14); return false; },
                () -> storage.set(10));

        assertFalse(restored);
        assertEquals(10, storage.get(), "failed commit must not leave partially restored units in storage");
    }

    @Test
    @DisplayName("Commit exceptions also roll storage back")
    void throwingCommitRollsBackMutation() {
        AtomicInteger storage = new AtomicInteger(10);
        assertThrows(IllegalStateException.class, () -> AtomicStorageRestore.commitWithRollback(
                () -> { storage.set(14); throw new IllegalStateException("commit divergence"); },
                () -> storage.set(10)));
        assertEquals(10, storage.get());
    }

    @Test
    @DisplayName("Cancel and quantity-decrease paths use the atomic escrow restorer on both implementations")
    void managerRestorePathsAreWiredAtomically() throws IOException {
        Path root = Path.of(System.getProperty("economy.repoRoot"));
        for (String relative : new String[] {
                "shared/minecraft-1.20plus/src/main/java/com/nstut/economy/trading/OrderManager.java",
                "neoforge-26.1.2/src/main/java/com/nstut/economy/trading/OrderManager.java"
        }) {
            String source = Files.readString(root.resolve(relative));
            assertTrue(source.contains("AtomicStorageRestore.restoreEscrow(level, requester, returnItems, List.of())"), relative);
            assertTrue(source.contains("AtomicStorageRestore.restoreEscrow(level, requester, List.of(), parts)"), relative);
            assertTrue(source.contains("AtomicStorageRestore.restoreEscrow(level, requester,\n                    order.getReservedItems(), order.getReservedFluids())"), relative);
        }
    }
}
''')
