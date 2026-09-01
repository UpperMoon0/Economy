package com.nstut.economy.api;

import java.util.EnumMap;
import java.util.Map;

/** Built-in namespaced transaction causes plus legacy enum compatibility. */
public final class TransactionCauses {
    public static final EconomyId CREDIT = EconomyId.of("economy", "credit");
    public static final EconomyId DEBIT = EconomyId.of("economy", "debit");
    public static final EconomyId TRANSFER = EconomyId.of("economy", "transfer");
    public static final EconomyId TRADE = EconomyId.of("economy", "trade");
    public static final EconomyId TAX = EconomyId.of("economy", "tax");
    public static final EconomyId ADMIN_GIVE = EconomyId.of("economy", "admin_give");
    public static final EconomyId ADMIN_TAKE = EconomyId.of("economy", "admin_take");
    public static final EconomyId STARTING_BALANCE = EconomyId.of("economy", "starting_balance");
    public static final EconomyId CUSTOM = EconomyId.of("economy", "custom");

    private static final Map<ITransactionContext.TransactionType, EconomyId> IDS =
            new EnumMap<>(ITransactionContext.TransactionType.class);

    static {
        IDS.put(ITransactionContext.TransactionType.CREDIT, CREDIT);
        IDS.put(ITransactionContext.TransactionType.DEBIT, DEBIT);
        IDS.put(ITransactionContext.TransactionType.TRANSFER, TRANSFER);
        IDS.put(ITransactionContext.TransactionType.TRADE, TRADE);
        IDS.put(ITransactionContext.TransactionType.TAX, TAX);
        IDS.put(ITransactionContext.TransactionType.ADMIN_GIVE, ADMIN_GIVE);
        IDS.put(ITransactionContext.TransactionType.ADMIN_TAKE, ADMIN_TAKE);
        IDS.put(ITransactionContext.TransactionType.STARTING_BALANCE, STARTING_BALANCE);
        IDS.put(ITransactionContext.TransactionType.CUSTOM, CUSTOM);
    }

    private TransactionCauses() { }

    public static EconomyId fromLegacy(ITransactionContext.TransactionType type) {
        return IDS.getOrDefault(type, CUSTOM);
    }

    public static ITransactionContext.TransactionType toLegacy(EconomyId cause) {
        for (Map.Entry<ITransactionContext.TransactionType, EconomyId> entry : IDS.entrySet()) {
            if (entry.getValue().equals(cause)) {
                return entry.getKey();
            }
        }
        return ITransactionContext.TransactionType.CUSTOM;
    }
}
