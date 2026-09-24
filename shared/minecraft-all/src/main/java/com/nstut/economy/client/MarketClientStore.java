package com.nstut.economy.client;

import com.nstut.economy.network.HistoryEntry;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;

import java.util.List;

/**
 * Signal-backed client cache for market synchronisation data. Network handlers
 * push immutable snapshots here; screens derive their views from these signals
 * so the UI never polls inside render().
 */
public final class MarketClientStore {
    private MarketClientStore() {}

    public record TeamWalletState(
            boolean visible,
            String mode,
            String teamName,
            String teamBalance,
            String role,
            boolean canDeposit,
            boolean canSpend,
            String spendRole
    ) {
        public TeamWalletState {
            mode = mode == null || mode.isBlank() ? "PERSONAL_ONLY" : mode;
            teamName = teamName == null ? "" : teamName;
            teamBalance = teamBalance == null || teamBalance.isBlank() ? "0" : teamBalance;
            role = role == null || role.isBlank() ? "NONE" : role;
            spendRole = spendRole == null || spendRole.isBlank() ? "OFFICER" : spendRole;
        }

        public static TeamWalletState hidden(String mode, String spendRole) {
            return new TeamWalletState(false, mode, "", "0", "NONE", false, false, spendRole);
        }
    }

    public static final Signal<List<MarketNetwork.ItemCardData>> cards = Signals.of(List.of());
    /** Personal/player balance. Kept under the legacy name for existing screen code. */
    public static final Signal<String> balance = Signals.of("0");
    public static final Signal<TeamWalletState> teamWallet =
            Signals.of(TeamWalletState.hidden("PERSONAL_ONLY", "OFFICER"));
    /** Economic principal used by current market-order actions; remains PLAYER until issue #29. */
    public static final Signal<String> marketPrincipal = Signals.of("PLAYER");
    public static final Signal<Integer> vaultCount = Signals.of(0);
    public static final Signal<MarketNetwork.SyncItemDetailPacket> detail = Signals.of(null);
    public static final Signal<List<HistoryEntry>> history = Signals.of(List.of());
    public static final Signal<List<MarketNetwork.VaultDetailEntry>> containerEntries = Signals.of(List.of());
    public static final Signal<List<MarketNetwork.PortfolioPointData>> portfolioPoints = Signals.of(List.of());
    public static final Signal<List<MarketNetwork.AssetHoldingData>> assetHoldings = Signals.of(List.of());
    public static final Signal<List<MarketNetwork.ActiveOrderEntry>> activeOrders = Signals.of(List.of());

    public static void applySyncItemList(MarketNetwork.SyncItemListPacket pkt) {
        Signals.batch(() -> {
            cards.set(List.copyOf(pkt.cards));
            balance.set(pkt.balance);
            teamWallet.set(pkt.teamWalletVisible
                    ? new TeamWalletState(
                            true,
                            pkt.teamMode,
                            pkt.teamName,
                            pkt.teamBalance,
                            pkt.teamRole,
                            pkt.teamCanDeposit,
                            pkt.teamCanSpend,
                            pkt.teamSpendRole)
                    : TeamWalletState.hidden(pkt.teamMode, pkt.teamSpendRole));
            marketPrincipal.set(pkt.marketPrincipal);
            vaultCount.set(pkt.vaultCount);
        });
    }

    public static void applySyncItemDetail(MarketNetwork.SyncItemDetailPacket pkt) {
        detail.set(pkt);
    }

    public static void applySyncOrderHistory(MarketNetwork.SyncOrderHistoryPacket pkt) {
        history.set(List.copyOf(pkt.entries));
    }

    public static void applySyncVaultInfo(MarketNetwork.SyncVaultInfoPacket pkt) {
        containerEntries.set(List.copyOf(pkt.entries));
    }

    public static void applySyncPortfolio(MarketNetwork.SyncPortfolioPacket pkt) {
        Signals.batch(() -> {
            portfolioPoints.set(List.copyOf(pkt.points));
            assetHoldings.set(List.copyOf(pkt.holdings));
        });
    }

    public static void applySyncActiveOrders(MarketNetwork.SyncActiveOrdersPacket pkt) {
        activeOrders.set(List.copyOf(pkt.entries));
    }
}


