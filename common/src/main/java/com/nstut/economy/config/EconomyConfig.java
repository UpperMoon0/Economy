package com.nstut.economy.config;

import java.math.BigDecimal;

/**
 * Configuration for the economy system.
 * In a real implementation, this would load from a config file.
 */
public class EconomyConfig {
    
    private static EconomyConfig INSTANCE;
    
    // Currency settings
    private String currencyName = "Coin";
    private String currencySymbol = "¤";
    private BigDecimal startingBalance = BigDecimal.ZERO;
    
    // Trading settings
    private double taxRate = 0.05; // 5%
    private BigDecimal minPrice = new BigDecimal("0.01");
    private BigDecimal maxPrice = new BigDecimal("1000000");
    private double maxPriceChangePercent = 50.0; // 50% fluctuation limit
    private boolean enableDynamicPricing = true;
    
    // Transaction limits
    private int maxTransactionHistory = 100;
    private int priceUpdateIntervalMinutes = 15;

    // Order limits enforced server-side on every order mutation
    private int maxOrderQuantity = 1_000_000;
    private int maxPriceScale = 4;
    private int maxPriceDigits = 18;

    // Ownership: when false, tanks expose no fluid capability to pipes/automation
    private boolean allowExternalAutomation = false;

    private EconomyConfig() {}
    
    public static EconomyConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EconomyConfig();
        }
        return INSTANCE;
    }
    
    // Getters
    public String getCurrencyName() { return currencyName; }
    public String getCurrencySymbol() { return currencySymbol; }
    public BigDecimal getStartingBalance() { return startingBalance; }
    public double getTaxRate() { return taxRate; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public double getMaxPriceChangePercent() { return maxPriceChangePercent; }
    public boolean isEnableDynamicPricing() { return enableDynamicPricing; }
    public int getMaxTransactionHistory() { return maxTransactionHistory; }
    public int getPriceUpdateIntervalMinutes() { return priceUpdateIntervalMinutes; }
    public int getMaxOrderQuantity() { return maxOrderQuantity; }
    public int getMaxPriceScale() { return maxPriceScale; }
    public int getMaxPriceDigits() { return maxPriceDigits; }
    public boolean isExternalAutomationAllowed() { return allowExternalAutomation; }

    // Setters for configuration (would be called during config loading)
    public void setCurrencyName(String name) { this.currencyName = name; }
    public void setCurrencySymbol(String symbol) { this.currencySymbol = symbol; }
    public void setStartingBalance(BigDecimal balance) { this.startingBalance = balance; }
    public void setTaxRate(double rate) { this.taxRate = rate; }
    public void setMaxOrderQuantity(int maxOrderQuantity) {
        this.maxOrderQuantity = Math.max(1, maxOrderQuantity);
    }
    public void setMaxPriceScale(int maxPriceScale) {
        this.maxPriceScale = Math.max(0, maxPriceScale);
    }
    public void setMaxPriceDigits(int maxPriceDigits) {
        this.maxPriceDigits = Math.max(1, maxPriceDigits);
    }
    public void setAllowExternalAutomation(boolean allow) { this.allowExternalAutomation = allow; }
}
