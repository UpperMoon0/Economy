package com.nstut.economy.config;

import com.nstut.economy.api.TeamEconomyMode;
import com.nstut.economy.api.TeamRole;

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

    // Optional shared team economy. Default remains fully backward-compatible.
    private TeamEconomyMode teamEconomyMode = TeamEconomyMode.PERSONAL_ONLY;
    private TeamRole teamViewRole = TeamRole.MEMBER;
    private TeamRole teamDepositRole = TeamRole.MEMBER;
    private TeamRole teamSpendRole = TeamRole.OFFICER;
    private TeamRole teamAdminRole = TeamRole.OWNER;

    private EconomyConfig() {}
    
    public static EconomyConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EconomyConfig();
        }
        return INSTANCE;
    }
    
    /** Persistent server configuration, loaded before team integration is exposed. */
    public void loadTeamConfig(java.nio.file.Path path) throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        if (java.nio.file.Files.exists(path)) {
            try (var input = java.nio.file.Files.newInputStream(path)) { properties.load(input); }
        }
        teamEconomyMode = TeamEconomyMode.valueOf(properties.getProperty("mode", "PERSONAL_ONLY").trim());
        teamViewRole = configuredRole(properties, "viewRole", TeamRole.MEMBER);
        teamDepositRole = configuredRole(properties, "depositRole", TeamRole.MEMBER);
        teamSpendRole = configuredRole(properties, "spendRole", TeamRole.OFFICER);
        teamAdminRole = configuredRole(properties, "adminRole", TeamRole.OWNER);
        if (!java.nio.file.Files.exists(path)) {
            java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());
            properties.setProperty("mode", teamEconomyMode.name());
            properties.setProperty("viewRole", teamViewRole.name());
            properties.setProperty("depositRole", teamDepositRole.name());
            properties.setProperty("spendRole", teamSpendRole.name());
            properties.setProperty("adminRole", teamAdminRole.name());
            try (var output = java.nio.file.Files.newOutputStream(path)) {
                properties.store(output, "Economy team wallets. Restart the server after editing. Modes: PERSONAL_ONLY, HYBRID, TEAM_PRIMARY. Roles: MEMBER, OFFICER, OWNER.");
            }
        }
    }
    private static TeamRole configuredRole(java.util.Properties properties, String name, TeamRole fallback) {
        TeamRole role = TeamRole.valueOf(properties.getProperty(name, fallback.name()).trim());
        if (role == TeamRole.NONE) throw new IllegalArgumentException(name + " must require MEMBER, OFFICER, or OWNER");
        return role;
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
    public TeamEconomyMode getTeamEconomyMode() { return teamEconomyMode; }
    public TeamRole getTeamViewRole() { return teamViewRole; }
    public TeamRole getTeamDepositRole() { return teamDepositRole; }
    public TeamRole getTeamSpendRole() { return teamSpendRole; }
    public TeamRole getTeamAdminRole() { return teamAdminRole; }

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
    public void setTeamEconomyMode(TeamEconomyMode mode) { this.teamEconomyMode = java.util.Objects.requireNonNull(mode, "mode"); }
    public void setTeamViewRole(TeamRole role) { this.teamViewRole = java.util.Objects.requireNonNull(role, "role"); }
    public void setTeamDepositRole(TeamRole role) { this.teamDepositRole = java.util.Objects.requireNonNull(role, "role"); }
    public void setTeamSpendRole(TeamRole role) { this.teamSpendRole = java.util.Objects.requireNonNull(role, "role"); }
    public void setTeamAdminRole(TeamRole role) { this.teamAdminRole = java.util.Objects.requireNonNull(role, "role"); }
}
