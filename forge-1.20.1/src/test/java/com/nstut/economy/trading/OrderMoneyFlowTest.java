package com.nstut.economy.trading;

import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.IOrder;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.ITransactionRecord;
import com.nstut.economy.core.AccountManagerHolder;
import com.nstut.economy.test.MinecraftTestBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
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
 * Account-balance assertions around the order money flows. These tests pin the
 * transfer directions: buyers pay sellers exactly once, refunds never flow
 * buyer->seller, and server buy orders draw from the server account.
 */
class OrderMoneyFlowTest extends MinecraftTestBase {

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
        private final IBankAccount serverAccount = new FakeAccount(new UUID(0, 0), new BigDecimal("100000"));

        @Override
        public java.util.Optional<IBankAccount> getPlayerAccount(UUID player) {
            return java.util.Optional.ofNullable(accounts.get(player));
        }

        @Override
        public IBankAccount getOrCreatePlayerAccount(UUID player) {
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
    private UUID seller;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountManager();
        AccountManagerHolder.setInstance(accounts);
        buyer = UUID.randomUUID();
        seller = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        AccountManagerHolder.setInstance(null);
    }

    private ItemCommodity ironCommodity() {
        return new ItemCommodity(new ResourceLocation("minecraft", "iron_ingot"),
                Items.IRON_INGOT, BigDecimal.ZERO);
    }

    private Order sellOrder(int qty, String price, int reserved) {
        NonNullList<ItemStack> escrow = NonNullList.create();
        escrow.add(new ItemStack(Items.IRON_INGOT, reserved));
        return new Order(seller, ironCommodity(), qty, new BigDecimal(price),
                IOrder.OrderType.SELL, null, escrow);
    }

    @Test
    @DisplayName("Partial sell fills charge the buyer once and pay the seller")
    void partialSellMovesFundsFromBuyerToSeller() {
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("1000"), null);
        Order order = sellOrder(10, "5", 10);

        IOrder.TransactionResult result = order.executePartial(buyer, 4, null);

        assertTrue(result.success);
        assertEquals(4, result.quantityTransferred);
        assertEquals(0, new BigDecimal("980").compareTo(accounts.getPlayerAccount(buyer).get().getBalance()));
        assertEquals(0, new BigDecimal("20").compareTo(accounts.getPlayerAccount(seller).get().getBalance()));
        assertEquals(6, order.getQuantity());
        assertEquals(6, order.getEscrowedItemCount());
    }

    @Test
    @DisplayName("Full sell executes charge the buyer the total price")
    void fullSellChargesBuyerTotalPrice() {
        accounts.getOrCreatePlayerAccount(buyer).credit(new BigDecimal("50"), null);
        Order order = sellOrder(10, "5", 10);

        IOrder.TransactionResult result = order.execute(buyer, (net.minecraft.server.level.ServerLevel) null);

        assertTrue(result.success);
        assertEquals(0, BigDecimal.ZERO.compareTo(accounts.getPlayerAccount(buyer).get().getBalance()));
        assertEquals(0, new BigDecimal("50").compareTo(accounts.getPlayerAccount(seller).get().getBalance()));
        assertEquals(0, order.getQuantity());
    }

    @Test
    @DisplayName("Partial buy fills pay the seller from the order owner's account")
    void partialBuyMovesFundsFromOwnerToSeller() {
        IBankAccount ownerAccount = accounts.getOrCreatePlayerAccount(buyer);
        ownerAccount.credit(new BigDecimal("100"), null);
        Order order = new Order(buyer, ironCommodity(), 10, new BigDecimal("2"),
                IOrder.OrderType.BUY, null);

        IOrder.TransactionResult result = order.executePartial(seller, 10, null);

        assertTrue(result.success);
        assertEquals(10, result.quantityTransferred);
        assertEquals(0, new BigDecimal("80").compareTo(ownerAccount.getBalance()));
        assertEquals(0, new BigDecimal("20").compareTo(accounts.getPlayerAccount(seller).get().getBalance()));
        assertEquals(0, order.getQuantity());
    }

    @Test
    @DisplayName("Buy orders cap the fill to what the buyer can afford")
    void buyOrderCapsFillToAffordableQuantity() {
        IBankAccount ownerAccount = accounts.getOrCreatePlayerAccount(buyer);
        ownerAccount.credit(new BigDecimal("6"), null);
        Order order = new Order(buyer, ironCommodity(), 10, new BigDecimal("2"),
                IOrder.OrderType.BUY, null);

        IOrder.TransactionResult result = order.executePartial(seller, 10, null);

        assertTrue(result.success);
        assertEquals(3, result.quantityTransferred);
        assertEquals(0, BigDecimal.ZERO.compareTo(ownerAccount.getBalance()));
        assertEquals(0, new BigDecimal("6").compareTo(accounts.getPlayerAccount(seller).get().getBalance()));
        assertEquals(7, order.getQuantity());
    }

    @Test
    @DisplayName("Server buy orders pay sellers from the server account")
    void serverBuyOrderPaysFromServerAccount() {
        Order order = new Order(OrderManager.SERVER_ID, ironCommodity(), 10, new BigDecimal("2"),
                IOrder.OrderType.BUY, null);
        order.setServerOrder(true);

        IOrder.TransactionResult result = order.executePartial(seller, 4, null);

        assertTrue(result.success);
        assertEquals(0, new BigDecimal("99992").compareTo(accounts.getServerAccount().getBalance()));
        assertEquals(0, new BigDecimal("8").compareTo(accounts.getPlayerAccount(seller).get().getBalance()));
        assertEquals(6, order.getQuantity());
    }

    @Test
    @DisplayName("Direct cancellation cannot bypass manager escrow restoration")
    void directCancelRefusesToOrphanEscrow() {
        Order order = sellOrder(10, "5", 10);

        assertTrue(order.canCancel());
        assertEquals(10, order.getEscrowedItemCount());
        assertFalse(order.cancel());
        assertTrue(order.canCancel(), "failed direct cancellation must leave the order active");
        assertEquals(10, order.getQuantity());
        assertEquals(10, order.getEscrowedItemCount(), "failed direct cancellation must preserve escrow exactly");

        Order exhausted = sellOrder(1, "5", 1);
        exhausted.consumeEscrow(1);
        exhausted.reduceQuantity(1);
        assertFalse(exhausted.canCancel());
        assertFalse(exhausted.cancel());
    }
}
