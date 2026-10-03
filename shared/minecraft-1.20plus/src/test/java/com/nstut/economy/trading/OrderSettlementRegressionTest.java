package com.nstut.economy.trading;

import org.junit.jupiter.api.BeforeAll;

class OrderSettlementRegressionTest extends OrderSettlementRegressionCases {
    @BeforeAll static void bootstrapMinecraft() throws ClassNotFoundException {
        Class.forName("com.nstut.economy.test.MinecraftTestBase");
    }
}
