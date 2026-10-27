package com.nstut.economy.data;

import org.junit.jupiter.api.BeforeAll;

class PortfolioValuationTest extends PortfolioValuationCases {
    @BeforeAll static void bootstrapMinecraft() throws ClassNotFoundException {
        Class.forName("com.nstut.economy.test.MinecraftTestBase");
    }
}
