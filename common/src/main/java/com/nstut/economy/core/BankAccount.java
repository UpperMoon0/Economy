package com.nstut.economy.core;

import com.nstut.economy.api.EconomyEvents;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.api.ITransactionContext;
import com.nstut.economy.api.ITransactionRecord;
import com.nstut.economy.api.TransactionCauses;
import com.nstut.economy.config.EconomyConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Default server-side {@link IBankAccount} implementation. */
public class BankAccount implements IBankAccount {
    private static final Object TRANSFER_TIE_LOCK = new Object();

    private final UUID owner;
    private BigDecimal balance;
    private final List<ITransactionRecord> transactionHistory;
    private final int maxHistory;
    private final Consumer<BigDecimal> onBalanceChanged;

    public BankAccount(UUID owner, BigDecimal initialBalance) {
        this(owner, initialBalance, null);
    }

    public BankAccount(UUID owner, BigDecimal initialBalance, Consumer<BigDecimal> onBalanceChanged) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.balance = Objects.requireNonNull(initialBalance, "initialBalance");
        this.maxHistory = EconomyConfig.getInstance().getMaxTransactionHistory();
        this.transactionHistory = new LinkedList<>();
        this.onBalanceChanged = onBalanceChanged;
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    @Override
    public synchronized BigDecimal getBalance() {
        return balance;
    }

    @Override
    public synchronized boolean credit(BigDecimal amount, ITransactionContext ctx) {
        if (!validAmount(amount) || ctx == null) {
            return false;
        }
        BigDecimal previous = balance;
        EconomyEvents.BalanceChangePre pre = EconomyEvents.post(
                new EconomyEvents.BalanceChangePre(owner, previous, amount, ctx));
        if (pre.isCancelled()) {
            return false;
        }

        balance = balance.add(amount);
        recordTransaction(ctx, amount, null);
        notifyBalanceChanged();
        EconomyEvents.post(new EconomyEvents.BalanceChanged(owner, previous, balance, amount, ctx));
        return true;
    }

    @Override
    public synchronized boolean debit(BigDecimal amount, ITransactionContext ctx) {
        if (!validAmount(amount) || ctx == null || balance.compareTo(amount) < 0) {
            return false;
        }
        BigDecimal previous = balance;
        BigDecimal delta = amount.negate();
        EconomyEvents.BalanceChangePre pre = EconomyEvents.post(
                new EconomyEvents.BalanceChangePre(owner, previous, delta, ctx));
        if (pre.isCancelled()) {
            return false;
        }

        balance = balance.subtract(amount);
        recordTransaction(ctx, delta, null);
        notifyBalanceChanged();
        EconomyEvents.post(new EconomyEvents.BalanceChanged(owner, previous, balance, delta, ctx));
        return true;
    }

    @Override
    public boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext ctx) {
        if (target == null || target == this || !validAmount(amount) || ctx == null) {
            return target == this && validAmount(amount);
        }
        if (target instanceof BankAccount bankTarget) {
            return transferToBuiltIn(bankTarget, amount, ctx);
        }
        return transferToExternal(target, amount, ctx);
    }

    private boolean transferToBuiltIn(BankAccount target, BigDecimal amount, ITransactionContext ctx) {
        int ownerOrder = owner.compareTo(target.owner);
        if (ownerOrder == 0) {
            synchronized (TRANSFER_TIE_LOCK) {
                synchronized (this) {
                    synchronized (target) {
                        return transferLocked(target, amount, ctx);
                    }
                }
            }
        }

        BankAccount first = ownerOrder < 0 ? this : target;
        BankAccount second = ownerOrder < 0 ? target : this;
        synchronized (first) {
            synchronized (second) {
                return transferLocked(target, amount, ctx);
            }
        }
    }

    /** Both this account and target are locked by the caller. */
    private boolean transferLocked(BankAccount target, BigDecimal amount, ITransactionContext ctx) {
        if (balance.compareTo(amount) < 0) {
            return false;
        }

        EconomyEvents.TransferPre transferPre = EconomyEvents.post(
                new EconomyEvents.TransferPre(owner, target.owner, amount, ctx));
        if (transferPre.isCancelled()) {
            return false;
        }

        BigDecimal sourceBefore = balance;
        BigDecimal targetBefore = target.balance;
        EconomyEvents.BalanceChangePre sourcePre = EconomyEvents.post(
                new EconomyEvents.BalanceChangePre(owner, sourceBefore, amount.negate(), ctx));
        ITransactionContext targetCtx = counterpartContext(ctx, owner);
        EconomyEvents.BalanceChangePre targetPre = EconomyEvents.post(
                new EconomyEvents.BalanceChangePre(target.owner, targetBefore, amount, targetCtx));
        if (sourcePre.isCancelled() || targetPre.isCancelled()) {
            return false;
        }

        // Commit both legs while both built-in accounts are locked.
        balance = balance.subtract(amount);
        target.balance = target.balance.add(amount);
        recordTransaction(ctx, amount.negate(), target.owner);
        target.recordTransaction(targetCtx, amount, owner);
        notifyBalanceChanged();
        target.notifyBalanceChanged();

        EconomyEvents.post(new EconomyEvents.BalanceChanged(
                owner, sourceBefore, balance, amount.negate(), ctx));
        EconomyEvents.post(new EconomyEvents.BalanceChanged(
                target.owner, targetBefore, target.balance, amount, targetCtx));
        EconomyEvents.post(new EconomyEvents.TransferCompleted(owner, target.owner, amount, ctx));
        return true;
    }

    /**
     * For an arbitrary third-party implementation, credit the target first.
     * The source remains locked and untouched if the target rejects or throws.
     * A third-party account must obey the IBankAccount contract: returning true
     * from credit means the credit committed successfully.
     */
    private synchronized boolean transferToExternal(IBankAccount target, BigDecimal amount,
                                                    ITransactionContext ctx) {
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        EconomyEvents.TransferPre transferPre = EconomyEvents.post(
                new EconomyEvents.TransferPre(owner, target.getOwner(), amount, ctx));
        if (transferPre.isCancelled()) {
            return false;
        }

        BigDecimal sourceBefore = balance;
        EconomyEvents.BalanceChangePre sourcePre = EconomyEvents.post(
                new EconomyEvents.BalanceChangePre(owner, sourceBefore, amount.negate(), ctx));
        if (sourcePre.isCancelled()) {
            return false;
        }

        ITransactionContext targetCtx = counterpartContext(ctx, owner);
        try {
            if (!target.credit(amount, targetCtx)) {
                return false;
            }
        } catch (RuntimeException rejected) {
            return false;
        }

        balance = balance.subtract(amount);
        recordTransaction(ctx, amount.negate(), target.getOwner());
        notifyBalanceChanged();
        EconomyEvents.post(new EconomyEvents.BalanceChanged(
                owner, sourceBefore, balance, amount.negate(), ctx));
        EconomyEvents.post(new EconomyEvents.TransferCompleted(owner, target.getOwner(), amount, ctx));
        return true;
    }

    private static ITransactionContext counterpartContext(ITransactionContext original, UUID source) {
        return new TransactionContext(
                original.getTransactionId(),
                original.getTimestamp(),
                TransactionCauses.TRANSFER,
                "Transfer from " + source,
                source.toString(),
                original.getMetadata());
    }

    @Override
    public List<ITransactionRecord> getRecentTransactions(int count) {
        if (count <= 0) {
            return List.of();
        }
        synchronized (transactionHistory) {
            int limit = Math.min(count, transactionHistory.size());
            return List.copyOf(new ArrayList<>(transactionHistory.subList(0, limit)));
        }
    }

    private void recordTransaction(ITransactionContext ctx, BigDecimal amount, UUID counterparty) {
        TransactionRecord record = new TransactionRecord(
                ctx.getTransactionId(),
                ctx.getTimestamp(),
                ctx.getCauseId(),
                amount,
                balance,
                counterparty,
                ctx.getDescription(),
                ctx.getMetadata());
        synchronized (transactionHistory) {
            transactionHistory.add(0, record);
            while (transactionHistory.size() > maxHistory) {
                transactionHistory.remove(transactionHistory.size() - 1);
            }
        }
    }

    /** Sets the balance directly for trusted server/admin operations. */
    public synchronized void setBalance(BigDecimal newBalance) {
        this.balance = Objects.requireNonNull(newBalance, "newBalance");
        notifyBalanceChanged();
    }

    private static boolean validAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private void notifyBalanceChanged() {
        if (onBalanceChanged != null) {
            onBalanceChanged.accept(balance);
        }
    }
}
