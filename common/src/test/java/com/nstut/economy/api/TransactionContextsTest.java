package com.nstut.economy.api;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionContextsTest {
    @Test
    void publicFactoryCreatesStableTransferContext() {
        UUID actor = UUID.randomUUID();
        ITransactionContext context = TransactionContexts.transfer("team payment", actor);
        assertEquals(TransactionCauses.TRANSFER, context.getCauseId());
        assertEquals(ITransactionContext.TransactionType.TRANSFER, context.getType());
        assertEquals(actor.toString(), context.getSource());
        assertEquals("team payment", context.getDescription());
        assertNotNull(context.getTransactionId());
        assertNotNull(context.getTimestamp());
    }

    @Test
    void metadataIsCopiedAndImmutable() {
        var source = new java.util.HashMap<String, String>();
        source.put("key", "value");
        ITransactionContext context = TransactionContexts.of(
                EconomyId.of("example", "salary"), "salary", "addon", source);
        source.put("key", "mutated");
        assertEquals(Map.of("key", "value"), context.getMetadata());
        assertThrows(UnsupportedOperationException.class, () -> context.getMetadata().put("x", "y"));
    }
}
