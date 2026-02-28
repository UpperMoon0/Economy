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
    private BigDecimal startingBalance = new BigDecimal("100.00");
    
    // Trading settings
    private double taxRate = 0.05; // 5%
    private BigDecimal minPrice = new BigDecimal("0.01");
    private BigDecimal maxPrice = new BigDecimal("1000000");
    private double maxPriceChangePercent = 50.0; // 50% fluctuation limit
    private boolean enableDynamicPricing = true;
    
    // Transaction limits
    private int maxTransactionHistory = 100;
    private int priceUpdateIntervalMinutes = 15;
    
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
    
    // Setters for configuration (would be called during config loading)
    public void setCurrencyName(String name) { this.currencyName = name; }
    public void setCurrencySymbol(String symbol) { this.currencySymbol = symbol; }
    public void setStartingBalance(BigDecimal balance) { this.startingBalance = balance; }
    public void setTaxRate(double rate) { this.taxRate = rate; }
}
