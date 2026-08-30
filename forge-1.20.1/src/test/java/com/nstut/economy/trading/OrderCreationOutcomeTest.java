package com.nstut.economy.trading;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.ITransactionRecord;
import com.nstut.economy.core.AccountManagerHolder;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the explicit creation outcomes: a fully-matched order must report
 * FILLED (never REJECTED just because no remainder remains on the book),
 * partial matches report the remaining quantity, and rejections carry the
 * exact client-facing reason.
 */
class OrderCreationOutcomeTest extends MinecraftTestBase {

    private static final class FakeAccount implements IBankAccount {
        private final UUID owner;
        private BigDecimal balance;

        FakeAccount(UUID owner, BigDecimal starting) {
            this.owner = owner;
            this.balance = starting;
        }

        @Override
        public UUID getOwner() {
            return owner;
        }

        @Override
        public BigDecimal getBalance() {
            return balance;
        }

        @Override
        public boolean credit(BigDecimal amount, ITransactionContext ctx) {
            balance = balance.add(amount);
            return true;
        }

        @Override
        public boolean debit(BigDecimal amount, ITransactionContext ctx) {
            if (balance.compareTo(amount) < 0) {
                return false;
            }
            balance = balance.subtract(amount);
            return true;
        }

        @Override
        public boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext ctx) {
            if (!debit(amount, ctx)) {
                return false;
            }
            return target.credit(amount, ctx);
        }

        @Override
        public List<ITransactionRecord> getRecentTransactions(int count) {
            return Collections.emptyList();
        }
    }

    private static final class FakeAccountManager implements IAccountManager {
        private final Map<UUID, IBankAccount> accounts = new HashMap<>();
        private final IBankAccount serverAccount = new FakeAccount(new UUID(0, 0), new BigDecimal("1000000"));

        @Override
        public java.util.Optional<IBankAccount> getPlayerAccount(UUID player) {
            return java.util.Optional.ofNullable(accounts.get(player));
        }

        @Override
        public IBankAccount getOrCreatePlayerAccount(UUID player) {
            if (OrderManager.SERVER_ID.equals(player)) {
                return serverAccount;
            }
            return accounts.computeIfAbsent(player, id -> new FakeAccount(id, BigDecimal.ZERO));
        }

        @Override
        public boolean hasAccount(UUID player) {
            return accounts.containsKey(player);
        }

        @Override
        public IBankAccount getServerAccount() {
            return serverAccount;
        }

        @Override
        public IBankAccount getTaxAccount() {
            return serverAccount;
        }

        @Override
        public boolean deleteAccount(UUID player) {
            return accounts.remove(player) != null;
        }
    }

    private FakeAccountManager accounts;
    private UUID buyer;
    private ItemCommodity iron;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountManager();
        AccountManagerHolder.setInstance(accounts);
        buyer = UUID.randomUUID();
        iron = new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"),
                Items.IRON_INGOT, BigDecimal.ZERO);
    }

    @AfterEach
    void tearDown() {
        AccountManagerHolder.setInstance(null);
    }

    @Test
    @DisplayName("A fully-matched BUY reports FILLED, not REJECTED")
    void fullyFilledBuyReportsFilled() {
        OrderManager manager = new OrderManager();
        manager.createServerSellOrder(iron, 10, new BigDecimal("4"));
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("50"), null);

        CreateOrderResult result = manager.createBuyOrder(buyer, iron, 10, new BigDecimal("5"), false, null);

        assertEquals(CreateOrderResult.Status.FILLED, result.status());
        assertEquals(10, result.filledQuantity());
        assertNull(result.remainingOrder());
        assertTrue(manager.getBuyOrders(iron).isEmpty());
        assertTrue(manager.getSellOrders(iron).isEmpty());
        assertEquals(0, new BigDecimal("10").compareTo(accounts.getPlayerAccount(buyer).get().getBalance()));
        assertEquals(0, new BigDecimal("1000040").compareTo(accounts.getServerAccount().getBalance()));
    }

    @Test
    @DisplayName("A partially-matched BUY reports PARTIALLY_FILLED with the remainder posted")
    void partiallyFilledBuyReportsRemainder() {
        OrderManager manager = new OrderManager();
        manager.createServerSellOrder(iron, 4, new BigDecimal("4"));
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("100"), null);

        CreateOrderResult result = manager.createBuyOrder(buyer, iron, 10, new BigDecimal("5"), false, null);

        assertEquals(CreateOrderResult.Status.PARTIALLY_FILLED, result.status());
        assertEquals(4, result.filledQuantity());
        assertNotNull(result.remainingOrder());
        assertEquals(6, result.remainingOrder().getQuantity());
        assertEquals(1, manager.getBuyOrders(iron).size());
    }

    @Test
    @DisplayName("An unmatched BUY reports POSTED with the full quantity")
    void unmatchedBuyReportsPosted() {
        OrderManager manager = new OrderManager();

        CreateOrderResult result = manager.createBuyOrder(buyer, iron, 10, new BigDecimal("5"), false, null);

        assertEquals(CreateOrderResult.Status.POSTED, result.status());
        assertEquals(0, result.filledQuantity());
        assertEquals(10, result.remainingOrder().getQuantity());
    }

    @Test
    @DisplayName("A fully-matched SELL reports FILLED")
    void fullyFilledSellReportsFilled() {
        OrderManager manager = new OrderManager();
        UUID seller = UUID.randomUUID();
        manager.createServerBuyOrder(iron, 10, new BigDecimal("5"));

        CreateOrderResult result = manager.createSellOrder(seller, iron, 10, new BigDecimal("4"));

        assertEquals(CreateOrderResult.Status.FILLED, result.status());
        assertEquals(10, result.filledQuantity());
        assertNull(result.remainingOrder());
        assertTrue(manager.getBuyOrders(iron).isEmpty());
        assertEquals(0, new BigDecimal("40").compareTo(accounts.getPlayerAccount(seller).get().getBalance()));
    }

    @Test
    @DisplayName("An unmatched SELL reports POSTED with the full quantity")
    void unmatchedSellReportsPosted() {
        OrderManager manager = new OrderManager();
        UUID seller = UUID.randomUUID();

        CreateOrderResult result = manager.createSellOrder(seller, iron, 4, BigDecimal.ONE);

        assertEquals(CreateOrderResult.Status.POSTED, result.status());
        assertEquals(4, result.remainingOrder().getQuantity());
    }

    @Test
    @DisplayName("Rejected creations carry the exact client-facing reason")
    void rejectionsCarryExactReasons() {
        OrderManager manager = new OrderManager();

        CreateOrderResult overMax = manager.createBuyOrder(buyer, iron, 4, new BigDecimal("2000000"));
        assertEquals(CreateOrderResult.Status.REJECTED, overMax.status());
        assertEquals("ui.economy.error.price_above_max", overMax.errorKey());
        assertEquals(List.of("1000000"), overMax.errorArgs());

        CreateOrderResult badQty = manager.createSellOrder(buyer, iron, 0, BigDecimal.ONE);
        assertEquals(CreateOrderResult.Status.REJECTED, badQty.status());
        assertEquals("ui.economy.error.qty_limit", badQty.errorKey());

        CreateOrderResult belowMin = manager.createBuyOrder(buyer, iron, 4, new BigDecimal("0.001"));
        assertEquals(CreateOrderResult.Status.REJECTED, belowMin.status());
        assertEquals("ui.economy.error.price_below_min", belowMin.errorKey());

        assertTrue(manager.getAllOrders().isEmpty());
    }
}
