package com.nstut.economy.trading;

import com.nstut.economy.util.OrderInputValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidPricingTest {

    @Test
    void bucketQuotesConvertToPreciseInternalMillibucketPrices() {
        BigDecimal perMb = FluidCommodity.pricePerMb(new BigDecimal("0.01"));

        assertEquals(new BigDecimal("0.00001"), perMb);
        assertEquals(new BigDecimal("0.01"), FluidCommodity.pricePerBucket(perMb));
        assertEquals(new BigDecimal("0.00500"),
                FluidCommodity.totalFromBucketQuote(new BigDecimal("0.01"), 500));
    }

    @Test
    void domainValidationAllowsFluidPrecisionButNotItemSubCentPrices() {
        BigDecimal internalFluidPrice = FluidCommodity.pricePerMb(new BigDecimal("0.01"));

        assertTrue(OrderInputValidator.isValidNewOrder(1_000, internalFluidPrice, true));
        assertFalse(OrderInputValidator.isValidNewOrder(1, internalFluidPrice, false));
    }
}
