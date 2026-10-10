package com.nstut.economy.client;

import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.util.CommodityUtil;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.economy.network.HistoryEntry;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.ClipStack;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiAnimationUtil;
import com.nstut.openui.api.VStack;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.Badge;
import com.nstut.openui.controls.Card;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.Popover;
import com.nstut.openui.controls.Select;
import com.nstut.openui.controls.Tabs;
import com.nstut.openui.controls.TextField;
import com.nstut.openui.controls.Tooltip;
import com.nstut.openui.controls.Toast;
import com.nstut.openui.controls.VirtualList;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.state.Computed;
import com.nstut.openui.state.ReadableSignal;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.state.Subscription;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import com.nstut.economy.trading.EconomyFluidStack;
import com.nstut.economy.trading.FluidCommodity;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Supplier;

public class MarketScreen extends EconomyUiContainerScreen<MarketMenu> {

    private static final int SCREEN_W = 356;
    private static final int SCREEN_H = 248;
    private static final int SIDEBAR_W = 84;
    private static final int NARROW_THRESHOLD = 336;
    private static final int MAX_VISIBLE_CHART_STEPS = 15;
    private static final int TOOLTIP_MAX_WIDTH = 140;
    private static final SimpleDateFormat CHART_TIME_FMT = new SimpleDateFormat("MM/dd HH:mm:ss");

    enum MarketView { BROWSE, DETAIL, NEW_ORDER, ORDERS, PAY_PLAYER, PORTFOLIO, TEAM_TREASURY, CONTAINERS }
    enum OrdersTab { ACTIVE, HISTORY }
    enum CommodityTypeFilter { ALL, ITEMS, FLUIDS }
    enum BrowseActivityFilter { ALL, ACTIVE }
    enum BrowseSort { PRICE_ASC, PRICE_DESC, NAME_ASC, MOST_ACTIVE }
    enum HistoryFilter { ALL, SALES, PURCHASES }
    enum HistorySort { NEWEST, OLDEST, HIGHEST_TOTAL }
    enum ActiveOrderFilter { ALL, SELL, BUY, INFINITE }
    enum ActiveOrderSort { NEWEST, OLDEST, PRICE_ASC, PRICE_DESC }
    enum BrowseLayout { GRID, LIST }
    enum VariantStatus { ACTIVE, INACTIVE, ALL }
    enum VariantFilter { ALL, ENCHANTED, DAMAGED, NAMED, OTHER_METADATA }
    enum VariantSort { PRICE_ASC, PRICE_DESC, MOST_ORDERS, NAME_ASC, DURABILITY_DESC }

    record PendingConfirmation(String itemId, int quantity, String priceStr, boolean isSell,
                               boolean isInfinite, String action, String itemName, String totalPrice,
                               String commodityType) {}

    record ChartSample(double value, String tooltip) {}

    record BrowseGroup(String baseId, String commodityType, String displayName,
                       List<MarketNetwork.ItemCardData> variants,
                       int offerCount, String globalPrice, double priceChangePercent) {
        String key() { return commodityType + "|" + baseId; }
    }

    record VariantTraits(boolean enchanted, boolean damaged, boolean named,
                         boolean otherMetadata, boolean damageable, int durabilityRemaining) {}

    // Ã¢â€â‚¬Ã¢â€â‚¬ View & filter state Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private final Signal<MarketView> view = Signals.of(MarketView.BROWSE);
    private final Signal<OrdersTab> ordersTab = Signals.of(OrdersTab.ACTIVE);
    private final Signal<String> browseQuery = Signals.of("");
    private final Signal<BrowseActivityFilter> browseActivity = MarketClientPreferences.filterSignal("market.browse.activity", BrowseActivityFilter.ACTIVE);
    private final Signal<CommodityTypeFilter> browseType = MarketClientPreferences.filterSignal("market.browse.product", CommodityTypeFilter.ALL);
    private final Signal<BrowseSort> browseSort = MarketClientPreferences.filterSignal("market.browse.sort", BrowseSort.PRICE_ASC);
    private final Signal<BrowseLayout> browseLayout = Signals.of(
            MarketClientPreferences.isBrowseGridView() ? BrowseLayout.GRID : BrowseLayout.LIST);
    private final Signal<String> historyQuery = Signals.of("");
    private final Signal<HistoryFilter> historyFilter = MarketClientPreferences.filterSignal("market.history.trade", HistoryFilter.ALL);
    private final Signal<CommodityTypeFilter> historyType = MarketClientPreferences.filterSignal("market.history.product", CommodityTypeFilter.ALL);
    private final Signal<HistorySort> historySort = MarketClientPreferences.filterSignal("market.history.sort", HistorySort.NEWEST);
    private final Signal<String> activeOrdersQuery = Signals.of("");
    private final Signal<ActiveOrderFilter> activeOrderFilter = MarketClientPreferences.filterSignal("market.orders.order", ActiveOrderFilter.ALL);
    private final Signal<CommodityTypeFilter> activeOrderType = MarketClientPreferences.filterSignal("market.orders.product", CommodityTypeFilter.ALL);
    private final Signal<ActiveOrderSort> activeOrderSort = MarketClientPreferences.filterSignal("market.orders.sort", ActiveOrderSort.NEWEST);
    private final Signal<String> selectedItemId = Signals.of(null);
    private final Signal<String> selectedCommodityType = Signals.of(null);

    private final Signal<String> createCommodityQuery = Signals.of("");
    private final Signal<String> createCommodityId = Signals.of(null);
    private final Signal<String> createQty = Signals.of("");
    private final Signal<String> createPrice = Signals.of("");
    private final Signal<Boolean> createSellMode = Signals.of(true);
    private final Signal<Boolean> createInfinite = Signals.of(false);
    private final Signal<String> treasuryAmount = Signals.of("");
    private final Signal<Boolean> treasuryInfoExpanded = Signals.of(false);
    private final Signal<String> payPlayerQuery = Signals.of("");
    private final Signal<String> payAmount = Signals.of("");
    private final Signal<UUID> payPlayerId = Signals.of(null);
    private final Signal<List<MarketNetwork.PlayerTargetData>> playerSearchResults = Signals.of(List.of());
    private final Signal<MarketNetwork.ActiveOrderEntry> editingOrder = Signals.of(null);
    private final Signal<PendingConfirmation> pendingConfirmation = Signals.of(null);

    private final Signal<Integer> detailChartOffset = Signals.of(0);
    private final Signal<Integer> portfolioChartOffset = Signals.of(0);

    private final List<Computed<?>> computedList = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Signal<List<ItemSearchResult>> searchResults = Signals.of(List.of());
    private Subscription itemSearchSubscription;
    private Subscription playerSearchSubscription;
    private boolean initialDataRequested;
    private Component deferredTooltip;

    private <T> Computed<T> computed(java.util.function.Supplier<T> s) {
        Computed<T> c = Signals.computed(s);
        computedList.add(c);
        return c;
    }

    private final Computed<List<MarketNetwork.ItemCardData>> visibleBrowseCards = computed(() ->
            filterCards(browseQuery.get(), browseActivity.get(), browseType.get(), browseSort.get(), MarketClientStore.cards.get()));
    private final Computed<List<BrowseGroup>> visibleBrowseGroups = computed(() ->
            groupBrowseCards(visibleBrowseCards.get(), MarketClientStore.cards.get(), browseSort.get()));
    private final Computed<List<HistoryEntry>> visibleHistory = computed(() ->
            filterHistory(historyQuery.get(), historyFilter.get(), historyType.get(), historySort.get(), MarketClientStore.history.get()));
    private final Computed<List<MarketNetwork.ActiveOrderEntry>> visibleActiveOrders = computed(() ->
            filterActiveOrders(activeOrdersQuery.get(), activeOrderFilter.get(), activeOrderType.get(), activeOrderSort.get(), MarketClientStore.activeOrders.get()));
    private final Computed<List<ChartSample>> detailChartSamples = computed(() -> {
        MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
        List<ChartSample> out = new ArrayList<>();
        if (d != null && d.chart != null) {
            for (MarketNetwork.ChartPoint p : d.chart) {
                out.add(new ChartSample(p.price, CHART_TIME_FMT.format(new Date(p.timestamp))
                        + "\n" + Component.translatable("ui.economy.chart.tooltip.price", formatMoney(BigDecimal.valueOf(p.price))).getString()
                        + "\n" + Component.translatable("ui.economy.chart.tooltip.volume",
                        formatQty(p.quantity, isFluidCommodity(d.itemId))).getString()));
            }
        }
        return out;
    });
    private final Computed<List<ChartSample>> portfolioChartSamples = computed(() -> {
        List<MarketNetwork.PortfolioPointData> pts = MarketClientStore.portfolioPoints.get();
        List<ChartSample> out = new ArrayList<>();
        for (MarketNetwork.PortfolioPointData p : pts) {
            out.add(new ChartSample(Double.parseDouble(p.netWorth),
                    Component.translatable("ui.economy.chart.tooltip.net_worth",
                            formatMoneyCompact(new BigDecimal(p.netWorth))).getString()
                            + "\n" + Component.translatable("ui.economy.chart.tooltip.cash",
                            formatMoneyCompact(new BigDecimal(p.balance))).getString()
                            + "  |  " + Component.translatable("ui.economy.chart.tooltip.assets",
                            formatMoneyCompact(new BigDecimal(p.assets))).getString()));
        }
        return out;
    });

    private final Computed<List<OwnedOrder>> visibleDetailOrders = computed(this::getMyOrdersForDetail);
    private final Computed<List<MarketNetwork.OrderEntry>> visibleAsks = computed(() -> filterOrderColumn(true));
    private final Computed<List<MarketNetwork.OrderEntry>> visibleBids = computed(() -> filterOrderColumn(false));
    private final Computed<List<MarketNetwork.AssetHoldingData>> visibleHoldings =
            computed(() -> new ArrayList<>(MarketClientStore.assetHoldings.get()));
    private final Computed<List<MarketNetwork.VaultDetailEntry>> visibleContainers =
            computed(() -> new ArrayList<>(MarketClientStore.containerEntries.get()));
    private final Computed<Boolean> browseEmpty = computed(() -> visibleBrowseGroups.get().isEmpty());
    private final Computed<Boolean> activeEmpty = computed(() -> visibleActiveOrders.get().isEmpty());
    private final Computed<Boolean> historyEmpty = computed(() -> visibleHistory.get().isEmpty());
    private final Computed<Boolean> containersEmpty = computed(() -> visibleContainers.get().isEmpty());
    private final Computed<Boolean> holdingsEmpty = computed(() -> MarketClientStore.assetHoldings.get().isEmpty());
    private final Computed<Boolean> detailOrdersEmpty = computed(() -> visibleDetailOrders.get().isEmpty());
    private final Computed<Boolean> asksEmpty = computed(() -> visibleAsks.get().isEmpty());
    private final Computed<Boolean> bidsEmpty = computed(() -> visibleBids.get().isEmpty());

    private ButtonWidget browseBtn, newOrderBtn, ordersBtn, payPlayerBtn, portfolioBtn, treasuryBtn, containersBtn;
    private ButtonWidget newOrderSellBtn, newOrderBuyBtn;
    private Popover itemSearchPopover;
    private OverlayHandle itemSearchHandle;
    private Popover playerSearchPopover;
    private OverlayHandle playerSearchHandle;

    private static String t(String key) { return Component.translatable(key).getString(); }

    static String fitText(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) return font.plainSubstrByWidth(ellipsis, maxWidth);
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }

    static void drawWrappedText(GuiGraphics g, Font font, String text,
                                int boxX, int boxY, int boxWidth, int boxHeight,
                                int color, int maxLines) {
        if (text == null || text.isEmpty() || boxWidth <= 0 || boxHeight <= 0 || maxLines <= 0) return;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), boxWidth);
        int count = Math.min(maxLines, lines.size());
        if (count <= 0) return;

        int lineStep = font.lineHeight + 2;
        int totalHeight = count * font.lineHeight + Math.max(0, count - 1) * 2;
        int drawY = boxY + Math.max(0, (boxHeight - totalHeight) / 2);

        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            StringBuilder plain = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            String lineText = plain.toString();
            if (i == count - 1 && lines.size() > maxLines) {
                lineText = fitText(font, lineText + "...", boxWidth);
            }
            UiRender.text(g, font, lineText, boxX, drawY + i * lineStep, color);
        }
    }
    public MarketScreen(MarketMenu menu, net.minecraft.world.entity.player.Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SCREEN_W;
        this.imageHeight = SCREEN_H;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Network handler delegates (kept as thin bridges to the store) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    public static void handleSyncItemList(MarketNetwork.SyncItemListPacket pkt) {
        boolean hadTreasury = treasuryAvailable();
        boolean hadPayPlayerAccess = payPlayerAvailable();
        String previousPrincipal = MarketClientStore.marketPrincipal.get();
        MarketClientStore.applySyncItemList(pkt);
        boolean principalChanged = !java.util.Objects.equals(previousPrincipal, MarketClientStore.marketPrincipal.get());
        boolean payPlayerAccessChanged = hadPayPlayerAccess != payPlayerAvailable();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MarketScreen screen) {
            if (!treasuryAvailable() && screen.view.get() == MarketView.TEAM_TREASURY) {
                screen.view.set(MarketView.BROWSE);
            }
            if (!payPlayerAvailable() && screen.view.get() == MarketView.PAY_PLAYER) {
                screen.view.set(MarketView.BROWSE);
            }
            if (hadTreasury != treasuryAvailable() || principalChanged || payPlayerAccessChanged) screen.rebuildUI();
            if (principalChanged) screen.onViewEntered(screen.view.get());
            String id = screen.view.get() == MarketView.NEW_ORDER
                    ? screen.selectedCreateCommodityId() : screen.selectedItemId.get();
            String type = screen.view.get() == MarketView.NEW_ORDER
                    ? (id != null && isFluidCommodity(id) ? "FLUID" : "ITEM") : screen.selectedCommodityType.get();
            if (id != null && !id.isBlank()) {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id, type));
            }
        }
    }

    public static void handleSyncItemDetail(MarketNetwork.SyncItemDetailPacket pkt) {
        MarketClientStore.applySyncItemDetail(pkt);
    }

    public static void handleSyncOrderHistory(MarketNetwork.SyncOrderHistoryPacket pkt) {
        MarketClientStore.applySyncOrderHistory(pkt);
    }

    public static void handleSyncVaultInfo(MarketNetwork.SyncVaultInfoPacket pkt) {
        MarketClientStore.applySyncVaultInfo(pkt);
    }

    public static void handleSyncPortfolio(MarketNetwork.SyncPortfolioPacket pkt) {
        MarketClientStore.applySyncPortfolio(pkt);
    }

    public static void handleSyncActiveOrders(MarketNetwork.SyncActiveOrdersPacket pkt) {
        MarketClientStore.applySyncActiveOrders(pkt);
    }

    public static void handleSyncPlayerList(MarketNetwork.SyncPlayerListPacket pkt) {
        MarketClientStore.applySyncPlayerList(pkt);
    }

    public static void handleActionResult(MarketNetwork.MarketActionResultPacket pkt) {
        Component title = Component.translatable(actionToastTitleKey(pkt.action, pkt.result));
        Component message = Component.translatable(pkt.messageKey, pkt.args.toArray());
        Minecraft minecraft = Minecraft.getInstance();
        if (pkt.action == MarketNetwork.Action.PAYMENT && pkt.result == MarketNetwork.Result.SUCCESS
                && minecraft.screen instanceof MarketScreen market) {
            market.payAmount.set("");
            market.payPlayerQuery.set("");
            market.payPlayerId.set(null);
            market.hidePlayerSearch();
        }
        if (minecraft.screen instanceof EconomyUiContainerScreen<?> screen && screen.uiRuntime() != null) {
            Toast toast = new Toast(switch (pkt.result) { case SUCCESS -> Toast.Type.SUCCESS; case WARNING -> Toast.Type.WARNING; case ERROR -> Toast.Type.ERROR; }, title, message, 3500, null);
            Toast.show(screen.uiRuntime().overlays(), toast);
        } else if (minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(message, false);
        }
    }

    private static String actionToastTitleKey(MarketNetwork.Action action, MarketNetwork.Result result) {
        String suffix = result == MarketNetwork.Result.SUCCESS ? "success" : "failed";
        return "ui.economy.toast.title." + switch (action) {
            case CREATE_ORDER -> "create_" + suffix;
            case ACCEPT_ORDER -> "accept_" + suffix;
            case CANCEL_ORDER -> "cancel_" + suffix;
            case EDIT_ORDER -> "edit_" + suffix;
            case WALLET -> "wallet_" + suffix;
            case TREASURY -> "treasury_" + suffix;
            case STORAGE -> "storage_" + suffix;
            case PAYMENT -> "payment_" + suffix;
        };
    }

    private void showLocalValidationToast(MarketNetwork.Action action, String messageKey, Object... args) {
        Component title = Component.translatable(actionToastTitleKey(action, MarketNetwork.Result.WARNING));
        Component message = Component.translatable(messageKey, args);
        if (uiRuntime() != null) {
            Toast.show(uiRuntime().overlays(), new Toast(Toast.Type.WARNING, title, message, 3500, null));
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui != null) minecraft.gui.setOverlayMessage(message, false);
        }
    }

    @Override
    protected void init() {
        // Market has no vanilla inventory slots, so its viewport may resize freely.
        // Never impose a minimum larger than the current logical window.
        this.imageWidth = Math.max(1, Math.min(SCREEN_W, this.width - 16));
        this.imageHeight = Math.max(1, Math.min(SCREEN_H, this.height - 16));
        super.init();
        if (!initialDataRequested) {
            initialDataRequested = true;
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
        }
    }

    @Override
    protected void renderBackgroundLayer(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        deferredTooltip = null;
        renderBaseShell(g);
    }

    @Override
    protected void renderForegroundLayer(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (deferredTooltip == null) return;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String line : deferredTooltip.getString().split("\\n", -1)) {
            lines.addAll(font.split(Component.literal(line), TOOLTIP_MAX_WIDTH));
        }
        Tooltip.drawHover(g, font, lines, mouseX, mouseY, 0, 0, this.width, this.height);
        deferredTooltip = null;
    }

    @Override
    public void removed() {
        for (Computed<?> c : computedList) c.close();
        for (Subscription s : subscriptions) s.close();
        if (itemSearchSubscription != null) {
            itemSearchSubscription.close();
            itemSearchSubscription = null;
        }
        hideItemSearch();
        itemSearchHandle = null;
        itemSearchPopover = null;
        if (playerSearchSubscription != null) {
            playerSearchSubscription.close();
            playerSearchSubscription = null;
        }
        hidePlayerSearch();
        playerSearchHandle = null;
        playerSearchPopover = null;
        computedList.clear();
        subscriptions.clear();
        super.removed();
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Build root UI Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    @Override
    protected UIComponent buildUI() {
        for (Subscription s : subscriptions) s.close();
        subscriptions.clear();
        if (itemSearchSubscription != null) {
            itemSearchSubscription.close();
            itemSearchSubscription = null;
        }
        if (playerSearchSubscription != null) {
            playerSearchSubscription.close();
            playerSearchSubscription = null;
        }
        hidePlayerSearch();
        if (!treasuryAvailable() && view.get() == MarketView.TEAM_TREASURY) view.set(MarketView.BROWSE);
        if (!payPlayerAvailable() && view.get() == MarketView.PAY_PLAYER) view.set(MarketView.BROWSE);
        subscriptions.add(view.subscribe(v -> {
            updateNav(v);
            onViewEntered(v);
        }));
        subscriptions.add(createSellMode.subscribe(b -> {
            if (newOrderSellBtn != null) newOrderSellBtn.setActive(b);
            if (newOrderBuyBtn != null) newOrderBuyBtn.setActive(!b);
            if (b) createInfinite.set(false);
        }));
        subscriptions.add(MarketClientStore.teamWallet.subscribe(ignored -> updateNav(view.get())));
        subscriptions.add(MarketClientStore.marketPrincipal.subscribe(ignored -> updateNav(view.get())));
        return Ui.padding(8, Ui.responsive(ctx -> buildShell(ctx.width())));
    }

    private static boolean treasuryAvailable() {
        var team = MarketClientStore.teamWallet.get();
        return team != null && team.visible();
    }

    private static boolean payPlayerAvailable() {
        if (MarketClientStore.isPersonalPrincipal()) return true;
        var team = MarketClientStore.teamWallet.get();
        return team != null && team.visible() && team.canPayout();
    }

    private static Component payPlayerNavTooltip() {
        if (MarketClientStore.isPersonalPrincipal()) {
            return Component.translatable("ui.economy.payment.nav_personal_hint");
        }
        var team = MarketClientStore.teamWallet.get();
        if (team == null || !team.visible()) {
            return Component.translatable("ui.economy.payment.nav_team_unavailable");
        }
        if (!team.canPayout()) {
            return Component.translatable("ui.economy.payment.nav_team_permission", humanizeEnum(team.payoutRole()));
        }
        return Component.translatable("ui.economy.payment.nav_team_hint");
    }

    private UIComponent buildShell(int availableWidth) {
        boolean narrow = availableWidth > 0 && availableWidth < NARROW_THRESHOLD;
        UIComponent shell = narrow ? buildNarrowShell() : buildWideShell();
        updateNav(view.get());
        return shell;
    }

    private UIComponent buildWideShell() {
        HStack main = new HStack().gap(8);
        main.fillWidth();
        main.fillHeight();

        VStack sidebar = new VStack().gap(5);
        sidebar.width(SIDEBAR_W);
        sidebar.fillHeight();
        sidebar.addChild(Ui.text(Component.translatable("ui.economy.market.subtitle")).style(TextStyle.TITLE));
        sidebar.addChild(EconomyUiComponents.walletBadge(
                MarketClientStore.balance,
                MarketClientStore.teamWallet,
                MarketClientStore.marketPrincipal,
                () -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.SelectMarketWalletPacket(
                        MarketClientStore.isPersonalPrincipal()))));
        sidebar.addChild(Ui.divider());
        VStack navList = new VStack().gap(5);
        navList.fillWidth();
        buildNav(navList);
        UIComponent navScroll = Ui.scroll(navList);
        navScroll.flex();
        sidebar.addChild(navScroll);
        sidebar.addChild(buildThemeToggle());
        main.addChild(sidebar);

        main.addChild(buildContent());
        return main;
    }

    private UIComponent buildNarrowShell() {
        VStack root = new VStack().gap(4);
        root.fillWidth();
        root.fillHeight();

        HStack top = new HStack().gap(6);
        top.addChild(Ui.text(Component.translatable("ui.economy.market.subtitle")).style(TextStyle.TITLE));
        top.addChild(Ui.spacer().flex());
        top.addChild(buildThemeToggle());
        root.addChild(top);

        HStack walletRow = new HStack().gap(4);
        walletRow.fillWidth();
        walletRow.addChild(EconomyUiComponents.walletBadge(
                MarketClientStore.balance,
                MarketClientStore.teamWallet,
                MarketClientStore.marketPrincipal,
                () -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.SelectMarketWalletPacket(
                        MarketClientStore.isPersonalPrincipal()))));
        root.addChild(walletRow);

        Select<MarketView> nav = Ui.select(view);
        nav.option(Component.translatable("ui.economy.nav.browse"), MarketView.BROWSE);
        nav.option(Component.translatable("ui.economy.nav.new_order"), MarketView.NEW_ORDER);
        nav.option(Component.literal(ordersNavLabel()), MarketView.ORDERS);
        if (payPlayerAvailable()) {
            nav.option(Component.translatable("ui.economy.nav.pay_player"), MarketView.PAY_PLAYER);
        }
        nav.option(Component.translatable("ui.economy.nav.portfolio"), MarketView.PORTFOLIO);
        if (treasuryAvailable()) {
            nav.option(Component.translatable("ui.economy.nav.team_treasury"), MarketView.TEAM_TREASURY);
        }
        nav.option(Component.translatable("ui.economy.nav.containers"), MarketView.CONTAINERS);
        nav.fillWidth();
        root.addChild(nav);
        root.addChild(buildContent());
        return root;
    }

    private UIComponent buildContent() {
        VStack content = new VStack().gap(6);
        content.flex();
        UIComponent switcher = Ui.switcher(view)
                .when(MarketView.BROWSE, this::buildBrowseView)
                .when(MarketView.DETAIL, this::buildDetailView)
                .when(MarketView.NEW_ORDER, this::buildNewOrderView)
                .when(MarketView.ORDERS, this::buildOrdersView)
                .when(MarketView.PAY_PLAYER, this::buildPayPlayerView)
                .when(MarketView.PORTFOLIO, this::buildPortfolioView)
                .when(MarketView.TEAM_TREASURY, this::buildTeamTreasuryView)
                .when(MarketView.CONTAINERS, this::buildContainersView);
        switcher.flex();
        content.addChild(switcher);
        return content;
    }

    private void buildNav(VStack sidebar) {
        browseBtn = navButton(t("ui.economy.nav.browse"), () -> switchView(MarketView.BROWSE));
        newOrderBtn = navButton(t("ui.economy.nav.new_order"), () -> switchView(MarketView.NEW_ORDER));
        ordersBtn = navButton(ordersNavLabel(), () -> switchView(MarketView.ORDERS));
        payPlayerBtn = navButton(t("ui.economy.nav.pay_player"), () -> switchView(MarketView.PAY_PLAYER));
        portfolioBtn = navButton(t("ui.economy.nav.portfolio"), () -> switchView(MarketView.PORTFOLIO));
        treasuryBtn = navButton(t("ui.economy.nav.team_treasury"), () -> switchView(MarketView.TEAM_TREASURY));
        containersBtn = navButton(t("ui.economy.nav.containers"), () -> switchView(MarketView.CONTAINERS));
        sidebar.addChild(browseBtn);
        sidebar.addChild(newOrderBtn);
        sidebar.addChild(ordersBtn);
        sidebar.addChild(payPlayerBtn);
        sidebar.addChild(portfolioBtn);
        if (treasuryAvailable()) sidebar.addChild(treasuryBtn);
        sidebar.addChild(containersBtn);
    }

    private ButtonWidget navButton(String label, Runnable action) {
        return new MarqueeNavButton(Component.literal(label), action);
    }

    private String ordersNavLabel() {
        return t(MarketClientStore.isTeamPrincipal()
                ? "ui.economy.nav.team_orders"
                : "ui.economy.nav.my_orders");
    }

    private void updateNav(MarketView v) {
        if (browseBtn != null) browseBtn.setActive(v == MarketView.BROWSE);
        if (newOrderBtn != null) newOrderBtn.setActive(v == MarketView.NEW_ORDER);
        if (ordersBtn != null) ordersBtn.setActive(v == MarketView.ORDERS);
        if (payPlayerBtn != null) {
            boolean available = payPlayerAvailable();
            payPlayerBtn.enabled(available);
            payPlayerBtn.setActive(available && v == MarketView.PAY_PLAYER);
            payPlayerBtn.tooltip(payPlayerNavTooltip());
        }
        if (portfolioBtn != null) portfolioBtn.setActive(v == MarketView.PORTFOLIO);
        if (treasuryBtn != null) treasuryBtn.setActive(v == MarketView.TEAM_TREASURY);
        if (containersBtn != null) containersBtn.setActive(v == MarketView.CONTAINERS);
    }

    private void onViewEntered(MarketView v) {
        if (v == MarketView.PAY_PLAYER && !payPlayerAvailable()) return;
        if (v == MarketView.TEAM_TREASURY && !treasuryAvailable()) return;
        switch (v) {
            case BROWSE -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
            case ORDERS -> {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestActiveOrdersPacket());
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestOrderHistoryPacket());
            }
            case PAY_PLAYER -> {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPlayerListPacket());
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
            }
            case PORTFOLIO -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
            case TEAM_TREASURY -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
            case CONTAINERS -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestVaultInfoPacket());
            case DETAIL -> {
                String id = selectedItemId.get();
                if (id != null) MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id, selectedCommodityType.get()));
            }
            case NEW_ORDER -> {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
                String id = selectedCreateCommodityId();
                if (id != null && !id.isEmpty()) MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id, selectedCommodityType.get()));
            }
        }
    }

    private void switchView(MarketView v) {
        if (v == MarketView.PAY_PLAYER && !payPlayerAvailable()) return;
        if (v == MarketView.TEAM_TREASURY && !treasuryAvailable()) return;
        hideItemSearch();
        hidePlayerSearch();
        view.set(v);
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ BROWSE Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private UIComponent buildBrowseView() {
        VStack v = new VStack().gap(4);
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 15; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                String listingCount = Component.translatable("ui.economy.browse.product_groups", visibleBrowseGroups.get().size()).getString();
                listingCount = fitText(f, listingCount, Math.max(0, width));
                int listingX = x + width - f.width(listingCount);
                UiRender.text(g, f, listingCount, listingX, y + 2, c.onSurfaceMuted());
            }
        });

        HStack searchBar = new HStack().gap(4);
        TextField search = Ui.textField(browseQuery);
        search.placeholder(t("ui.economy.browse.search_placeholder"));
        search.flex();
        searchBar.addChild(search);
        ButtonWidget layoutBtn = Ui.button(
                (Supplier<Component>) () -> Component.translatable(browseLayout.get() == BrowseLayout.GRID ? "ui.economy.browse.grid" : "ui.economy.browse.rows"),
                () -> {
                    BrowseLayout next = browseLayout.get() == BrowseLayout.GRID ? BrowseLayout.LIST : BrowseLayout.GRID;
                    browseLayout.set(next);
                    MarketClientPreferences.setBrowseGridView(next == BrowseLayout.GRID);
                }).ghost();
        searchBar.addChild(layoutBtn);
        v.addChild(searchBar);

        HStack filters = new HStack().gap(4);
        filters.addChild(filterSelect(t("ui.economy.filter.activity"), browseActivity,
                Map.of(BrowseActivityFilter.ALL, t("ui.economy.opt.all"), BrowseActivityFilter.ACTIVE, t("ui.economy.opt.active"))));
        filters.addChild(filterSelect(t("ui.economy.filter.product"), browseType,
                Map.of(CommodityTypeFilter.ALL, t("ui.economy.opt.all"), CommodityTypeFilter.ITEMS, t("ui.economy.opt.items"), CommodityTypeFilter.FLUIDS, t("ui.economy.opt.fluids"))));
        filters.addChild(filterSelect(t("ui.economy.filter.sort"), browseSort,
                Map.of(BrowseSort.PRICE_ASC, t("ui.economy.opt.price_asc"), BrowseSort.PRICE_DESC, t("ui.economy.opt.price_desc"),
                        BrowseSort.NAME_ASC, t("ui.economy.opt.name_asc"), BrowseSort.MOST_ACTIVE, t("ui.economy.opt.most_active"))));
        v.addChild(filters);

        UIComponent listings = Ui.switcher(browseEmpty)
                .when(false, () -> Ui.switcher(browseLayout)
                        .when(BrowseLayout.GRID, () -> Ui.virtualGrid(visibleBrowseGroups, this::buildCommodityGroupCard)
                                .key(BrowseGroup::key)
                                .minCellWidth(120)
                                .cellHeight(44)
                                .gap(4)
                                .flex())
                        .when(BrowseLayout.LIST, () -> Ui.list(visibleBrowseGroups, this::buildCommodityGroupRow)
                                .key(BrowseGroup::key)
                                .itemHeight(44)
                                .flex()))
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_listings")));
        listings.flex();
        v.addChild(listings);
        v.flex();
        return v;
    }

    private <T extends Enum<T>> UIComponent filterSelect(String label, Signal<T> signal, Map<T, String> labels) {
        VStack group = new VStack().gap(1);
        group.flex();
        group.addChild(Ui.text(Component.literal(label)).style(TextStyle.CAPTION));
        Select<T> sel = Ui.select(signal);
        for (Map.Entry<T, String> e : labels.entrySet()) {
            sel.option(Component.literal(e.getValue()), e.getKey());
        }
        group.addChild(sel);
        return group;
    }

    private UIComponent buildCommodityGroupCard(BrowseGroup group) {
        MarketNetwork.ItemCardData first = group.variants().get(0);
        String iconId = group.variants().size() == 1 ? first.itemId : group.baseId();
        Component hover = browseGroupTooltip(group);
        return new UIComponent() {
            {
                height(44);
                tooltip(hover);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 44; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = uiRuntime().theme().colors();
                boolean hovered = mx >= x && mx < x + width && my >= y && my < y + height;
                UiRender.surface(g, x, y, width, height, 4,
                        hovered ? colors.surfaceRaised() : colors.surface(),
                        hovered ? colors.primary() : colors.borderSubtle(), false, colors);
                CommodityIconComponent.drawIcon(g, iconId, x + 6, y + 12, 16, 16);
                int textX = x + 28;
                int textWidth = Math.max(1, width - 34);

                String title = group.variants().size() == 1
                        ? getItemDisplayName(first.itemId, first.displayName)
                        : group.displayName();
                if (group.variants().size() == 1 && isExactVariantId(first.itemId)) {
                    String metadata = compactVariantFacetSummary(
                            variantPresentation(first.itemId, first.displayName), 2);
                    if (!metadata.isBlank()) title = title + " \u00B7 " + metadata;
                }
                drawMarqueeText(g, f, title, textX, y + 4, textWidth, colors.onSurface(), false);

                if (group.globalPrice() != null && !group.globalPrice().isEmpty() && !group.globalPrice().equals("--")) {
                    String price = formatMoneyCompact(parsePrice(group.globalPrice()));
                    if (group.variants().size() > 1) {
                        price = Component.translatable("ui.economy.card.from_price", price).getString();
                    }
                    String change = formatPriceChange(group.priceChangePercent());
                    drawPriceChangeRowMarquee(g, f, price, change,
                            textX, y + 16, textWidth, colors.primary(), changeColor(group.priceChangePercent()));
                } else {
                    UiRender.text(g, f, "--", textX, y + 16, colors.onSurfaceMuted());
                }

                String orderText = group.offerCount() > 0
                        ? EconomyFormatUtil.formatCount(group.offerCount(), "order", "orders")
                        : t("ui.economy.card.no_orders");
                String footer = group.variants().size() > 1
                        ? Component.translatable("ui.economy.card.variants", group.variants().size()).getString()
                                + " \u00B7 " + orderText
                        : orderText;
                drawMarqueeText(g, f, footer, textX, y + 28, textWidth, colors.onSurfaceMuted(), false);
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    if (group.variants().size() == 1) {
                        MarketNetwork.ItemCardData card = group.variants().get(0);
                        openDetail(card.itemId, card.commodityType);
                    } else {
                        showVariantPicker(group);
                    }
                    return true;
                }
                return false;
            }
        };
    }

    private UIComponent buildCommodityGroupRow(BrowseGroup group) {
        return buildCommodityGroupCard(group);
    }

    private Component browseGroupTooltip(BrowseGroup group) {
        if (group.variants().size() == 1) {
            MarketNetwork.ItemCardData card = group.variants().get(0);
            return commodityTooltip(card.itemId, card.displayName);
        }
        StringBuilder text = new StringBuilder(group.displayName());
        text.append("\n").append(Component.translatable("ui.economy.variant.choose_hint").getString());
        int limit = Math.min(6, group.variants().size());
        for (int i = 0; i < limit; i++) {
            MarketNetwork.ItemCardData card = group.variants().get(i);
            text.append("\n\u2022 ").append(getItemDisplayName(card.itemId, card.displayName));
        }
        if (group.variants().size() > limit) {
            text.append("\n\u2026 +").append(group.variants().size() - limit);
        }
        return Component.literal(text.toString());
    }

    private void showVariantPicker(BrowseGroup group) {
        List<MarketNetwork.ItemCardData> variants = List.copyOf(group.variants());
        Map<String, VariantPresentation> presentationCache = new HashMap<>();
        Map<String, VariantTraits> traitsCache = new HashMap<>();
        Map<String, String> searchCache = new HashMap<>();

        // Picker controls are modal-local. Opening a different product must start
        // from a clean query/filter/sort state rather than inheriting transient UI.
        Signal<String> variantQuery = Signals.of("");
        Signal<VariantStatus> variantStatus = Signals.of(VariantStatus.ACTIVE);
        Signal<VariantFilter> variantFilter = Signals.of(VariantFilter.ALL);
        Signal<VariantSort> variantSort = Signals.of(VariantSort.PRICE_ASC);

        Map<VariantFilter, String> filterLabels = availableVariantFilters(variants, traitsCache);
        Map<VariantSort, String> sortLabels = availableVariantSorts(variants, traitsCache);

        Computed<List<MarketNetwork.ItemCardData>> rows = Signals.computed(() ->
                filterVariantRows(variants, variantQuery, variantStatus, variantFilter, variantSort,
                        presentationCache, traitsCache, searchCache));
        Computed<Boolean> rowsEmpty = Signals.computed(() -> rows.get().isEmpty());
        OverlayHandle[] holder = new OverlayHandle[1];

        VirtualList<MarketNetwork.ItemCardData> list = Ui.list(rows, card ->
                        buildVariantPickerRow(card, holder,
                                presentationCache.computeIfAbsent(card.itemId,
                                        ignored -> variantPresentation(card.itemId, card.displayName))))
                .key(card -> card.itemId)
                .itemHeight(34)
                .gap(2);
        VStack body = new VStack().gap(4);
        body.addChild(Ui.heading(Component.literal(group.displayName())));
        body.addChild(Ui.text(Component.translatable("ui.economy.variant.choose_hint"))
                .style(TextStyle.CAPTION).wrap());

        TextField search = Ui.textField(variantQuery);
        search.placeholder(t("ui.economy.variant.search_placeholder"));
        search.fillWidth();
        body.addChild(search);

        HStack controls = new HStack().gap(4);
        Map<VariantStatus, String> statusLabels = new LinkedHashMap<>();
        statusLabels.put(VariantStatus.ACTIVE, t("ui.economy.variant.status.active"));
        statusLabels.put(VariantStatus.INACTIVE, t("ui.economy.variant.status.inactive"));
        statusLabels.put(VariantStatus.ALL, t("ui.economy.opt.all"));
        controls.addChild(filterSelect(t("ui.economy.variant.status"), variantStatus, statusLabels));
        controls.addChild(filterSelect(t("ui.economy.variant.filter"), variantFilter, filterLabels));
        controls.addChild(filterSelect(t("ui.economy.filter.sort"), variantSort, sortLabels));
        body.addChild(controls);

        body.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 11; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = uiRuntime().theme().colors();
                String count = Component.translatable("ui.economy.variant.result_count",
                        rows.get().size(), variants.size()).getString();
                UiRender.text(g, f, fitText(f, count, width), x, y + 1, colors.onSurfaceMuted());
            }
        });

        UIComponent results = Ui.switcher(rowsEmpty)
                .when(false, () -> list)
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.variant.no_results")));
        results.flex();
        body.addChild(results);

        body.addChild(Ui.button(Component.translatable("gui.cancel"), () -> {
            if (holder[0] != null) holder[0].close();
        }).secondary());

        Card card = new Card(body).elevated(true).outlined(true).padding(10);
        // Reserve a fixed modal viewport: controls/header/footer stay fixed while
        // the variant list takes only the remaining height and scrolls internally.
        card.width(310).height(270);
        holder[0] = Dialog.show(uiRuntime().overlays(), card, true, true, () -> {
            rowsEmpty.close();
            rows.close();
        });
    }

    private Map<VariantFilter, String> availableVariantFilters(
            List<MarketNetwork.ItemCardData> variants, Map<String, VariantTraits> traitsCache) {
        LinkedHashMap<VariantFilter, String> labels = new LinkedHashMap<>();
        labels.put(VariantFilter.ALL, t("ui.economy.opt.all"));
        for (VariantFilter candidate : List.of(
                VariantFilter.ENCHANTED, VariantFilter.DAMAGED,
                VariantFilter.NAMED, VariantFilter.OTHER_METADATA)) {
            boolean present = false;
            for (MarketNetwork.ItemCardData card : variants) {
                VariantTraits traits = traitsCache.computeIfAbsent(card.itemId, this::variantTraits);
                if (matchesVariantFilter(traits, candidate)) {
                    present = true;
                    break;
                }
            }
            if (!present) continue;
            labels.put(candidate, switch (candidate) {
                case ENCHANTED -> t("ui.economy.variant.filter.enchanted");
                case DAMAGED -> t("ui.economy.variant.filter.damaged");
                case NAMED -> t("ui.economy.variant.filter.named");
                case OTHER_METADATA -> t("ui.economy.variant.filter.other_metadata");
                case ALL -> t("ui.economy.opt.all");
            });
        }
        return labels;
    }

    private Map<VariantSort, String> availableVariantSorts(
            List<MarketNetwork.ItemCardData> variants, Map<String, VariantTraits> traitsCache) {
        LinkedHashMap<VariantSort, String> labels = new LinkedHashMap<>();
        labels.put(VariantSort.PRICE_ASC, t("ui.economy.opt.price_asc"));
        labels.put(VariantSort.PRICE_DESC, t("ui.economy.opt.price_desc"));
        labels.put(VariantSort.MOST_ORDERS, t("ui.economy.variant.sort.most_orders"));
        labels.put(VariantSort.NAME_ASC, t("ui.economy.opt.name_asc"));
        boolean hasDamageable = false;
        for (MarketNetwork.ItemCardData card : variants) {
            VariantTraits traits = traitsCache.computeIfAbsent(card.itemId, this::variantTraits);
            if (traits.damageable()) {
                hasDamageable = true;
                break;
            }
        }
        if (hasDamageable) labels.put(VariantSort.DURABILITY_DESC, t("ui.economy.variant.sort.durability"));
        return labels;
    }

    private List<MarketNetwork.ItemCardData> filterVariantRows(
            List<MarketNetwork.ItemCardData> variants,
            Signal<String> querySignal,
            Signal<VariantStatus> statusSignal,
            Signal<VariantFilter> filterSignal,
            Signal<VariantSort> sortSignal,
            Map<String, VariantPresentation> presentationCache,
            Map<String, VariantTraits> traitsCache,
            Map<String, String> searchCache) {
        String rawQuery = querySignal.get();
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        VariantStatus status = statusSignal.get();
        VariantFilter filter = filterSignal.get();
        List<MarketNetwork.ItemCardData> result = new ArrayList<>();

        for (MarketNetwork.ItemCardData card : variants) {
            VariantTraits traits = traitsCache.computeIfAbsent(card.itemId, this::variantTraits);
            if (status == VariantStatus.ACTIVE && card.offerCount <= 0) continue;
            if (status == VariantStatus.INACTIVE && card.offerCount > 0) continue;
            if (!matchesVariantFilter(traits, filter)) continue;
            if (terms.length > 0) {
                String haystack = searchCache.computeIfAbsent(card.itemId, ignored -> {
                    VariantPresentation presentation = presentationCache.computeIfAbsent(card.itemId,
                            ignoredPresentation -> variantPresentation(card.itemId, card.displayName));
                    StringBuilder searchable = new StringBuilder();
                    searchable.append(card.itemId).append(' ')
                            .append(presentation.displayName()).append(' ');
                    for (String facet : presentation.facets()) searchable.append(facet).append(' ');
                    searchable.append(presentation.tooltip().getString());
                    return searchable.toString().toLowerCase(Locale.ROOT);
                });
                boolean matches = true;
                for (String term : terms) {
                    if (!term.isEmpty() && !haystack.contains(term)) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) continue;
            }
            result.add(card);
        }

        VariantSort sort = sortSignal.get();
        switch (sort) {
            case PRICE_ASC -> result.sort((a, b) -> {
                int cmp = parsePrice(a.globalPrice).compareTo(parsePrice(b.globalPrice));
                return cmp != 0 ? cmp : a.itemId.compareToIgnoreCase(b.itemId);
            });
            case PRICE_DESC -> result.sort((a, b) -> {
                int cmp = parsePrice(b.globalPrice).compareTo(parsePrice(a.globalPrice));
                return cmp != 0 ? cmp : a.itemId.compareToIgnoreCase(b.itemId);
            });
            case MOST_ORDERS -> result.sort((a, b) -> {
                int cmp = Integer.compare(b.offerCount, a.offerCount);
                return cmp != 0 ? cmp : a.itemId.compareToIgnoreCase(b.itemId);
            });
            case NAME_ASC -> result.sort((a, b) -> {
                String an = presentationCache.computeIfAbsent(a.itemId,
                        ignored -> variantPresentation(a.itemId, a.displayName)).displayName();
                String bn = presentationCache.computeIfAbsent(b.itemId,
                        ignored -> variantPresentation(b.itemId, b.displayName)).displayName();
                int cmp = an.compareToIgnoreCase(bn);
                return cmp != 0 ? cmp : a.itemId.compareToIgnoreCase(b.itemId);
            });
            case DURABILITY_DESC -> result.sort((a, b) -> {
                VariantTraits at = traitsCache.computeIfAbsent(a.itemId, this::variantTraits);
                VariantTraits bt = traitsCache.computeIfAbsent(b.itemId, this::variantTraits);
                int cmp = Integer.compare(bt.durabilityRemaining(), at.durabilityRemaining());
                return cmp != 0 ? cmp : a.itemId.compareToIgnoreCase(b.itemId);
            });
        }
        return List.copyOf(result);
    }

    private VariantTraits variantTraits(String itemId) {
        ItemStack stack = CommodityIconComponent.stackForCommodity(itemId);
        if (stack == null || stack.isEmpty()) {
            return new VariantTraits(false, false, false, false, false, Integer.MIN_VALUE);
        }
        boolean damageable = stack.isDamageableItem();
        boolean damaged = damageable && stack.getDamageValue() > 0;
        boolean enchanted = stack.isEnchanted();
        ItemStack baseline = new ItemStack(stack.getItem());
        boolean named = !stack.getHoverName().getString().equalsIgnoreCase(baseline.getHoverName().getString());
        boolean metadataDiffers = hasCanonicalMetadataDifference(stack);
        boolean otherMetadata = metadataDiffers && !enchanted && !damaged && !named;
        int durabilityRemaining = damageable
                ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue())
                : Integer.MIN_VALUE;
        return new VariantTraits(enchanted, damaged, named, otherMetadata, damageable, durabilityRemaining);
    }

    private static boolean matchesVariantFilter(VariantTraits traits, VariantFilter filter) {
        if (filter == null || filter == VariantFilter.ALL) return true;
        return switch (filter) {
            case ALL -> true;
            case ENCHANTED -> traits.enchanted();
            case DAMAGED -> traits.damaged();
            case NAMED -> traits.named();
            case OTHER_METADATA -> traits.otherMetadata();
        };
    }

    private UIComponent buildVariantPickerRow(MarketNetwork.ItemCardData card, OverlayHandle[] holder,
                                              VariantPresentation presentation) {
        Component hover = presentation.tooltip();
        String displayName = presentation.displayName();
        String meta = compactVariantFacetSummary(presentation, 2);
        return new UIComponent() {
            {
                height(34);
                tooltip(hover);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 34; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = uiRuntime().theme().colors();
                boolean hovered = mx >= x && mx < x + width && my >= y && my < y + height;
                if (hovered) UiRender.roundedRect(g, x, y, width, height, 3, colors.surfaceRaised());
                CommodityIconComponent.drawIcon(g, card.itemId, x + 4, y + 9, 16, 16);

                int textX = x + 24;
                int rightWidth = Math.min(88, Math.max(0, width / 3));
                int textWidth = Math.max(1, width - textX + x - rightWidth - 4);
                UiRender.text(g, f, fitText(f, displayName, textWidth),
                        textX, y + 4, colors.onSurface());
                if (!meta.isBlank()) {
                    drawMarqueeText(g, f, meta, textX, y + 17, textWidth, colors.onSurfaceMuted(), false);
                }

                String price = card.globalPrice == null || card.globalPrice.isEmpty() || card.globalPrice.equals("--")
                        ? "--" : formatMoneyCompact(parsePrice(card.globalPrice));
                String fittedPrice = fitText(f, price, Math.max(1, rightWidth - (price.equals("--") ? 0 : 10)));
                int priceGroupWidth = f.width(fittedPrice) + (price.equals("--") ? 0 : 10);
                int priceX = x + width - priceGroupWidth - 4;
                if (!price.equals("--")) {
                    EconomyUiComponents.drawCoin(g, priceX, y + 3);
                    priceX += 10;
                }
                UiRender.text(g, f, fittedPrice, priceX, y + 4, colors.primary());

                String orders = card.offerCount > 0
                        ? EconomyFormatUtil.formatCount(card.offerCount, "order", "orders")
                        : t("ui.economy.card.no_orders");
                String fittedOrders = fitText(f, orders, rightWidth);
                UiRender.text(g, f, fittedOrders,
                        x + width - f.width(fittedOrders) - 4, y + 17, colors.onSurfaceMuted());
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    if (holder[0] != null) holder[0].close();
                    openDetail(card.itemId, card.commodityType);
                    return true;
                }
                return false;
            }
        };
    }

    private void openDetail(String id, String commodityType) {
        selectedItemId.set(id);
        selectedCommodityType.set(commodityType);
        MarketClientStore.detail.set(null);
        detailChartOffset.set(0);
        switchView(MarketView.DETAIL);
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id, commodityType));
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ DETAIL Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private UIComponent buildDetailView() {
        VStack v = new VStack().gap(4);
        v.flex();
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 18; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
                if (d != null) {
                    double change = detailChangePercent(d);
                    String changeStr = formatPriceChange(change);
                    boolean fluid = isFluidCommodity(d.itemId);
                    String stock = fluid ? Component.translatable("ui.economy.detail.in_tank", formatFluidAmountDetailed(d.vaultCount)).getString()
                            : Component.translatable("ui.economy.detail.in_vault", formatItemAmount(d.vaultCount)).getString();
                    stock = fitText(f, stock, Math.max(1, width / 2));
                    int stockX = x + width - f.width(stock);
                    int leftWidth = Math.max(0, stockX - x - 6);
                    changeStr = fitText(f, changeStr, Math.max(0, leftWidth / 3));
                    String title = getItemDisplayName(d.itemId, d.displayName);
                    int titleMaxWidth = Math.max(0, leftWidth - f.width(changeStr) - 8);
                    int titleAreaWidth = Math.min(f.width(title), titleMaxWidth);
                    int changeGap = titleAreaWidth <= 0 || changeStr.isEmpty() ? 0 : 8;
                    drawMarqueeText(g, f, title, x, y + 4, titleAreaWidth, c.onSurface(), false);
                    UiRender.text(g, f, changeStr, x + titleAreaWidth + changeGap, y + 4, changeColor(change));
                    UiRender.text(g, f, stock, stockX, y + 4, c.primary());
                } else if (selectedItemId.get() != null) {
                    UiRender.text(g, f, fitText(f, selectedItemId.get(), width), x, y + 4, c.onSurfaceMuted());
                }
            }
        });

        v.addChild(new TrendChartComponent(detailChartSamples, detailChartOffset, false));

        HStack cols = new HStack().gap(6);
        cols.height(58);
        cols.addChild(buildOrderColumn(true));
        cols.addChild(buildOrderColumn(false));
        v.addChild(cols);

        v.addChild(Ui.text(Component.translatable("ui.economy.detail.my_orders")).style(TextStyle.HEADING));
        UIComponent myOrders = Ui.switcher(detailOrdersEmpty)
                .when(false, () -> Ui.list(visibleDetailOrders, this::buildMyOrderRow)
                        .key(o -> o.e().orderId)
                        .itemHeight(18))
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_orders_item")));
        myOrders.flex();
        v.addChild(myOrders);
        v.addChild(Ui.button(Component.translatable("ui.economy.action.create_order"), () -> {
            setCreateCommoditySelection(selectedItemId.get());
            switchView(MarketView.NEW_ORDER);
        }).primary());
        return v;
    }

    private double detailChangePercent(MarketNetwork.SyncItemDetailPacket d) {
        if (d.chart == null || d.chart.isEmpty()) return Double.NaN;
        List<MarketNetwork.ChartPoint> pts = d.chart;
        double cur = pts.get(pts.size() - 1).price;
        double prev = cur;
        for (int i = pts.size() - 2; i >= 0; i--) {
            if (pts.get(i).price != cur) { prev = pts.get(i).price; break; }
        }
        return prev > 0 ? ((cur - prev) / (double) prev) * 100.0 : Double.NaN;
    }

    private List<OwnedOrder> getMyOrdersForDetail() {
        MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
        List<OwnedOrder> res = new ArrayList<>();
        if (d == null) return res;
        for (MarketNetwork.OrderEntry e : d.asks) if (e.isPlayerOwned) res.add(new OwnedOrder(e, true));
        for (MarketNetwork.OrderEntry e : d.bids) if (e.isPlayerOwned) res.add(new OwnedOrder(e, false));
        return res;
    }

    private UIComponent buildOrderColumn(boolean isAsks) {
        VStack v = new VStack().gap(2);
        v.flex();
        v.addChild(Ui.text(Component.translatable(isAsks ? "ui.economy.detail.sell_orders" : "ui.economy.detail.buy_orders")).style(TextStyle.HEADING));
        ReadableSignal<List<MarketNetwork.OrderEntry>> data = isAsks ? visibleAsks : visibleBids;
        Computed<Boolean> colEmpty = isAsks ? asksEmpty : bidsEmpty;
        UIComponent orders = Ui.switcher(colEmpty)
                .when(false, () -> Ui.list(data, e -> buildOtherOrderRow(e, isAsks)).itemHeight(18).flex())
                .when(true, () -> Ui.emptyState(Component.translatable(isAsks ? "ui.economy.empty.no_sell_orders" : "ui.economy.empty.no_buy_orders")));
        orders.flex();
        v.addChild(orders);
        return v;
    }

    private List<MarketNetwork.OrderEntry> filterOrderColumn(boolean isAsks) {
        MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
        List<MarketNetwork.OrderEntry> src = isAsks ? (d == null ? List.of() : d.asks) : (d == null ? List.of() : d.bids);
        List<MarketNetwork.OrderEntry> res = new ArrayList<>();
        for (MarketNetwork.OrderEntry e : src) if (!e.isPlayerOwned) res.add(e);
        return res;
    }

    private UIComponent buildOtherOrderRow(MarketNetwork.OrderEntry e, boolean isAsks) {
        return new UIComponent() {
            {
                height(18);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 18; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y + 1, width, 16, 2, c.surfaceRaised());
                }
                int clr = e.isServerOrder ? c.primary() : (isAsks ? c.danger() : c.success());
                String line = e.price + " x " + (e.isInfinite ? "\u221e" : (isFluidCommodity(MarketClientStore.detail.get() == null ? "" : MarketClientStore.detail.get().itemId)
                        ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity)));
                String sellerName = e.isServerOrder
                        ? t("ui.economy.orders.server_badge")
                        : e.sellerName;
                drawOrderRowMarquee(g, f, sellerName, line, x + 3, y + 3,
                        Math.max(0, width - 6), e.isServerOrder, clr, c);
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    openCreateOrderWithPrefill(!isAsks, e.price, e.quantity);
                    return true;
                }
                return false;
            }
        };
    }

    private record OwnedOrder(MarketNetwork.OrderEntry e, boolean isSell) {}

    private UIComponent buildMyOrderRow(OwnedOrder o) {
        MarketNetwork.OrderEntry e = o.e();
        HStack row = new HStack().gap(4);
        row.height(18);
        UIComponent info = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 18; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y + 1, width, 16, 2, c.surfaceRaised());
                }
                String detailItemId = MarketClientStore.detail.get() == null ? "" : MarketClientStore.detail.get().itemId;
                String line = e.price + " x " + (e.isInfinite ? "\u221e" : (isFluidCommodity(detailItemId)
                        ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity)));
                String sideLabel = o.isSell() ? t("ui.economy.opt.sell") : t("ui.economy.opt.buy");
                int lineWidth = Math.max(0, width - 6 - f.width(sideLabel) - 6 - 10);
                line = fitText(f, line, lineWidth);
                int lineX = x + width - f.width(line) - 3;
                int coinX = lineX - 10;
                String side = fitText(f, sideLabel, Math.max(0, coinX - (x + 3) - 6));
                UiRender.text(g, f, side, x + 3, y + 4, o.isSell() ? c.danger() : c.success());
                EconomyUiComponents.drawCoin(g, coinX, y + 3);
                UiRender.text(g, f, line, lineX, y + 4, c.onSurface());
            }
        };
        info.flex();
        row.addChild(info);
        ButtonWidget edit = Ui.button(t("ui.economy.action.edit"), () -> openEditOrder(e)).ghost().small();
        edit.height(14);
        ButtonWidget cancel = Ui.button(t("ui.economy.action.cancel"),
                () -> confirmOrderCancellation(e.orderId, detailItemId(), detailItemName(), o.isSell(),
                        e.price, e.quantity, e.isInfinite, e.identity)).danger().small();
        cancel.height(14);
        row.addChild(edit);
        row.addChild(cancel);
        return row;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ NEW ORDER Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private UIComponent buildNewOrderView() {
        VStack v = new VStack().gap(4);
        v.fillWidth();
        v.addChild(Ui.button(Component.translatable("ui.economy.action.back"), () -> switchView(MarketView.BROWSE)).ghost());
        v.addChild(Ui.divider());

        HStack modeRow = new HStack().gap(4);
        newOrderSellBtn = Ui.button(Component.translatable("ui.economy.action.sell_order"), () -> { createSellMode.set(true); }).danger();
        newOrderBuyBtn = Ui.button(Component.translatable("ui.economy.action.buy_order"), () -> { createSellMode.set(false); }).success();
        newOrderSellBtn.flex(); newOrderBuyBtn.flex();
        modeRow.addChild(newOrderSellBtn); modeRow.addChild(newOrderBuyBtn);
        v.addChild(modeRow);

        TextField idField = new TextField(createCommodityQuery) {
            @Override public void onFocusGained() {
                super.onFocusGained();
                refreshItemSearchResults(createCommodityQuery.get(), true);
            }
        };
        Runnable updateSearchPlaceholder = () -> idField.placeholder(t(createSellMode.get()
                ? "ui.economy.new_order.search_sell_placeholder"
                : "ui.economy.new_order.search_buy_placeholder"));
        updateSearchPlaceholder.run();
        v.addChild(idField);
        setupItemSearchPopover(idField);
        subscriptions.add(createSellMode.subscribe(sell -> {
            updateSearchPlaceholder.run();
            if (sell) {
                String selected = createCommodityId.get();
                if (selected != null && getVaultStockForItem(selected) <= 0) {
                    setCreateCommoditySelection(null);
                }
            }
            if (idField.isFocused()) refreshItemSearchResults(createCommodityQuery.get(), true);
            else hideItemSearch();
        }));

        v.addChild(Ui.switcher(createSellMode)
                .when(true, () -> buildSellSelectionSummary(idField))
                .when(false, () -> Ui.spacer().height(0)));

        v.addChild(new OrderQuantityControl(createQty, createSellMode, createInfinite, () -> {
            String id = selectedCreateCommodityId();
            int stock = getVaultStockForItem(id);
            if (stock > 0) createQty.set(String.valueOf(stock));
        }, t("ui.economy.new_order.qty_placeholder"), t("ui.economy.new_order.unlimited"),
                t("ui.economy.action.max"), t("ui.economy.action.infinite")));

        TextField priceField = Ui.textField(createPrice);
        Runnable updatePricePlaceholder = () -> priceField.placeholder(t(isFluidCommodity(selectedCreateCommodityId())
                ? "ui.economy.new_order.price_placeholder_fluid"
                : "ui.economy.new_order.price_placeholder_item"));
        updatePricePlaceholder.run();
        subscriptions.add(createCommodityQuery.subscribe(q -> updatePricePlaceholder.run()));
        subscriptions.add(createCommodityId.subscribe(id -> updatePricePlaceholder.run()));
        v.addChild(priceField);

        UIComponent identityRow = Ui.text(() -> Component.translatable(
                        MarketClientStore.isTeamPrincipal()
                                ? "ui.economy.new_order.identity.team"
                                : "ui.economy.new_order.identity.personal"))
                .style(TextStyle.CAPTION).nowrap().ellipsis();
        identityRow.height(14);
        v.addChild(identityRow);

        ButtonWidget submit = Ui.button(Component.translatable("ui.economy.action.submit"), this::submitOffer).primary();
        v.addChild(submit);

        updateCreateModeButtons(newOrderSellBtn, newOrderBuyBtn);
        return Ui.scroll(v).flex();
    }

    private void updateCreateModeButtons(ButtonWidget sell, ButtonWidget buy) {
        sell.setActive(createSellMode.get());
        buy.setActive(!createSellMode.get());
    }

    private UIComponent buildSellSelectionSummary(TextField searchField) {
        return new UIComponent() {
            {
                height(30);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 30; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = uiRuntime().theme().colors();
                String id = createCommodityId.get();
                if (id == null || id.isBlank()) {
                    String query = createCommodityQuery.get();
                    boolean noMatch = query != null && !query.isBlank() && searchResults.get().isEmpty();
                    String hint = t(noMatch
                            ? "ui.economy.new_order.no_storage_match"
                            : "ui.economy.new_order.sell_select_hint");
                    drawWrappedText(g, f, hint, x + 2, y, Math.max(1, width - 4), height,
                            noMatch ? colors.danger() : colors.onSurfaceMuted(), 2);
                    return;
                }

                boolean fluid = isFluidCommodity(id);
                int stock = getVaultStockForItem(id);
                UiRender.surface(g, x, y, width, height, 3,
                        colors.surface(), colors.borderSubtle(), false, colors);
                CommodityIconComponent.drawIcon(g, id, x + 4, y + (height - 16) / 2, 16, 16);

                int textX = x + 24;
                String qty = formatQty(stock, fluid);
                int qtyWidth = Math.min(Math.max(36, f.width(qty) + 4), Math.max(36, width / 3));
                int textWidth = Math.max(1, width - (textX - x) - qtyWidth - 8);
                VariantPresentation presentation = variantPresentation(id, id);
                String title = presentation.displayName();
                if (isExactVariantId(id)) {
                    String metadata = compactVariantFacetSummary(presentation, 2);
                    if (!metadata.isBlank()) title = title + " \u00B7 " + metadata;
                }
                drawMarqueeText(g, f, title, textX, y + 4,
                        textWidth, colors.onSurface(), false);
                drawMarqueeText(g, f, baseCommodityId(id), textX, y + 16,
                        textWidth, colors.onSurfaceMuted(), false);
                String fittedQty = fitText(f, qty, qtyWidth);
                UiRender.text(g, f, fittedQty,
                        x + width - f.width(fittedQty) - 4,
                        y + Math.max(0, (height - f.lineHeight) / 2), colors.primary());

                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    deferredTooltip = presentation.tooltip();
                }
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    searchField.requestFocus();
                    refreshItemSearchResults(createCommodityQuery.get(), true);
                    return true;
                }
                return false;
            }
        };
    }

    private void setupItemSearchPopover(TextField anchor) {
        if (itemSearchSubscription != null) {
            itemSearchSubscription.close();
            itemSearchSubscription = null;
        }
        hideItemSearch();
        VirtualList<ItemSearchResult> list = Ui.list(searchResults, this::buildSearchResultRow)
                .itemHeight(30)
                .gap(2);
        list.height(150);
        itemSearchPopover = Ui.popover(anchor, list).matchAnchorWidth();
        itemSearchSubscription = createCommodityQuery.subscribe(q -> {
            String selected = createCommodityId.get();
            if (selected != null && java.util.Objects.equals(q, getPrimaryItemDisplayName(selected, selected))) {
                searchResults.set(List.of());
                hideItemSearch();
                return;
            }
            createCommodityId.set(null);
            if (!createSellMode.get()) requestCommodityDetailIfExact(q);
            refreshItemSearchResults(q, true);
        });
    }

    private void refreshItemSearchResults(String query, boolean openWhenAvailable) {
        List<ItemSearchResult> results;
        if (createSellMode.get()) {
            results = getStoredItemSearchResults(query);
        } else {
            if (query == null || query.length() < 2) {
                searchResults.set(List.of());
                hideItemSearch();
                return;
            }
            results = getItemSearchResults(query);
        }

        searchResults.set(results);
        if (results.isEmpty()) {
            hideItemSearch();
        } else if (openWhenAvailable && !isItemSearchOpen() && uiRuntime() != null) {
            itemSearchHandle = itemSearchPopover.show(uiRuntime().overlays());
        }
    }

    private UIComponent buildSearchResultRow(ItemSearchResult r) {
        boolean exactVariant = isExactVariantId(r.itemId);
        int rowHeight = 30;
        Component hover = commodityTooltip(r.itemId, r.displayName);
        return new UIComponent() {
            {
                height(rowHeight);
                tooltip(hover);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return rowHeight; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y, width, rowHeight, 2, colors.surfaceRaised());
                }
                CommodityIconComponent.drawIcon(g, r.itemId, x + 2, y + (rowHeight - 16) / 2, 16, 16);

                int textX = x + 22;
                String quantity = r.owned ? formatQty(r.quantity, isFluidCommodity(r.itemId)) : "";
                int rightWidth = r.owned ? Math.min(72, Math.max(36, width / 4)) : 0;
                int textWidth = Math.max(1, width - (textX - x) - rightWidth - 4);
                VariantPresentation presentation = variantPresentation(r.itemId, r.displayName);
                String title = presentation.displayName();
                if (exactVariant) {
                    String metadata = compactVariantFacetSummary(presentation, 2);
                    if (!metadata.isBlank()) title = title + " \u00B7 " + metadata;
                }
                String itemIdLine = baseCommodityId(r.itemId);

                drawMarqueeText(g, f, title, textX, y + 4,
                        textWidth, colors.onSurface(), false);
                if (itemIdLine != null && !itemIdLine.isBlank()) {
                    drawMarqueeText(g, f, itemIdLine, textX, y + 16,
                            textWidth, colors.onSurfaceMuted(), false);
                }

                if (r.owned) {
                    String fitted = fitText(f, quantity, rightWidth);
                    UiRender.text(g, f, fitted,
                            x + width - f.width(fitted) - 4,
                            y + Math.max(0, (rowHeight - f.lineHeight) / 2), colors.primary());
                }
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    selectCommodity(r.itemId);
                    return true;
                }
                return false;
            }
        };
    }

    private void hideItemSearch() {
        if (itemSearchHandle != null) {
            itemSearchHandle.close();
            itemSearchHandle = null;
        }
    }

    private boolean isItemSearchOpen() {
        return itemSearchHandle != null && itemSearchHandle.isOpen();
    }

    private String selectedCreateCommodityId() {
        String selected = createCommodityId.get();
        if (selected != null && !selected.isBlank()) return selected;
        String query = createCommodityQuery.get();
        return query == null ? null : query.trim();
    }

    private void setCreateCommoditySelection(String id) {
        if (id == null || id.isBlank()) {
            createCommodityId.set(null);
            createCommodityQuery.set("");
            return;
        }
        createCommodityId.set(id);
        createCommodityQuery.set(getPrimaryItemDisplayName(id, id));
    }

    private void selectCommodity(String id) {
        setCreateCommoditySelection(id);
        hideItemSearch();
        String type = isFluidCommodity(id) ? "FLUID" : "ITEM";
        selectedCommodityType.set(type);
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id, type));
    }

    private void requestCommodityDetailIfExact(String query) {
        var economyId = CommoditySearchInput.tryParseExactId(query);
        if (economyId == null) return;
        ResourceLocation id = ResourceLocation.tryParse(economyId.toString());
        if (id == null) return;
        String base = com.nstut.economy.trading.ItemVariant.baseItemId(economyId).toString();
        var baseId = new ResourceLocation(base);
        boolean knownItem = BuiltInRegistries.ITEM.containsKey(baseId);
        boolean knownFluid = base.equals(id.toString()) && BuiltInRegistries.FLUID.containsKey(id)
                && CommodityUtil.isCanonicalFluid(BuiltInRegistries.FLUID.get(id));
        if (knownItem || knownFluid) {
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id.toString(),
                    knownItem && knownFluid ? null : knownFluid ? "FLUID" : "ITEM"));
        }
    }

    private void openCreateOrderWithPrefill(boolean isSell, String rawPrice, int qty) {
        createSellMode.set(isSell);
        createInfinite.set(false);
        String target = selectedItemId.get();
        if ((target == null || target.isEmpty()) && MarketClientStore.detail.get() != null) {
            target = MarketClientStore.detail.get().itemId;
        }
        if (target != null) setCreateCommoditySelection(target);
        String clean = rawPrice == null ? "" : rawPrice.replaceAll("[^0-9.]", "").trim();
        try {
            if (!clean.isEmpty()) clean = String.format(Locale.ROOT, "%.2f", Double.parseDouble(clean));
        } catch (Exception ignored) {}
        if (!clean.isEmpty()) createPrice.set(clean);
        int prefill = qty;
        MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
        if (isSell && d != null && d.vaultCount >= 0) prefill = Math.min(qty, d.vaultCount);
        createQty.set(prefill > 0 ? String.valueOf(prefill) : "");
        switchView(MarketView.NEW_ORDER);
    }

    private void submitOffer() {
        String id = selectedCreateCommodityId();
        if (id == null || id.isEmpty()) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.item_required"); return; }
        String priceStr = createPrice.get().trim();
        if (priceStr.isEmpty()) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.price_required"); return; }
        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) <= 0) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.price_positive"); return; }
        } catch (NumberFormatException e) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.price_number"); return; }

        boolean inf = !createSellMode.get() && createInfinite.get();
        String qtyStr = createQty.get().trim();
        int qty = 1;
        if (!inf) {
            try {
                qty = Integer.parseInt(qtyStr);
                if (qty <= 0) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.qty_positive"); return; }
            } catch (NumberFormatException ignored) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.qty_number"); return; }
        }
        if (createSellMode.get()) {
            int stock = getVaultStockForItem(id);
            if (stock <= 0 || qty > stock) {
                showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, isFluidCommodity(id) ? "ui.economy.error.insufficient_fluid" : "ui.economy.error.insufficient_vault");
                return;
            }
        } else if (!inf) {
            try {
                BigDecimal total = totalPrice(price, qty, id);
                BigDecimal bal = new BigDecimal(selectedWalletBalance());
                if (total.compareTo(bal) > 0) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.insufficient_funds", bal); return; }
            } catch (NumberFormatException ignored) { showLocalValidationToast(MarketNetwork.Action.CREATE_ORDER, "ui.economy.error.balance_verify"); return; }
        }
        String commodityType = isFluidCommodity(id) ? "FLUID" : "ITEM";
        String dispName = getItemDisplayName(id, id);
        boolean fluid = isFluidCommodity(id);
        String totalStr = inf ? "\u221e (" + (fluid ? "Per bucket: " : "Per unit: ") + price.toPlainString() + ")"
                : formatMoney(totalPrice(price, qty, id));
        pendingConfirmation.set(new PendingConfirmation(id, qty, price.toPlainString(), createSellMode.get(), inf,
                 createSellMode.get() ? t("ui.economy.opt.sell") : t("ui.economy.opt.buy"), dispName, totalStr, commodityType));
        showConfirmation();
    }

    private void showConfirmation() {
        PendingConfirmation p = pendingConfirmation.get();
        if (p == null) return;
        boolean fluid = isFluidCommodity(p.itemId);
        String qtyStr = p.isInfinite ? "\u221e" : EconomyFormatUtil.formatCommodityQuantity(p.quantity, fluid);
        String msg = Component.translatable("ui.economy.confirm.message", p.action, qtyStr, getItemDisplayName(p.itemId, p.itemName)).getString();
        OverlayHandle[] holder = new OverlayHandle[1];
        boolean[] actionTaken = new boolean[1];

        UIComponent totalRow = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 12; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = theme().colors();
                String label = t("ui.economy.confirm.total_label");
                UiRender.text(g, f, label, x, y + 2, colors.onSurface());
                int coinX = x + f.width(label) + 4;
                EconomyUiComponents.drawCoin(g, coinX, y + 1);
                UiRender.text(g, f, p.totalPrice, coinX + 10, y + 2, colors.primary());
            }
        };
        totalRow.fillWidth();

        HStack actions = new HStack().gap(6).justify(com.nstut.openui.layout.Justification.END);
        actions.addChild(Ui.button(Component.translatable("gui.cancel"), () -> {
            if (actionTaken[0]) return;
            actionTaken[0] = true;
            pendingConfirmation.set(null);
            if (holder[0] != null) holder[0].close();
        }).danger());
        actions.addChild(Ui.button(Component.translatable("gui.ok"), () -> {
            if (actionTaken[0]) return;
            actionTaken[0] = true;
            if (holder[0] != null) holder[0].close();
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CreateOrderPacket(
                    p.itemId, p.quantity, p.priceStr, p.isSell, p.isInfinite, p.commodityType));
            String id = p.itemId;
            pendingConfirmation.set(null);
            selectedItemId.set(id);
            selectedCommodityType.set(p.commodityType);
            MarketClientStore.detail.set(null);
            switchView(MarketView.DETAIL);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id, p.commodityType));
        }).primary());

        VStack body = new VStack().gap(10);
        body.addChild(Ui.heading(Component.translatable("ui.economy.confirm.title")));
        body.addChild(Ui.text(Component.literal(msg)));
        body.addChild(totalRow);
        body.addChild(Ui.text(Component.translatable("ui.economy.confirm.account", selectedWalletLabel())));
        body.addChild(Ui.text(Component.translatable("ui.economy.confirm.storage",
                MarketClientStore.isTeamPrincipal() ? selectedWalletLabel() : t("ui.economy.principal.personal"))));
        body.addChild(actions);

        Card card = new Card(body).elevated(true).outlined(true).padding(14);
        card.width(240).minHeight(108);
        holder[0] = Dialog.show(uiRuntime().overlays(), card, true, true, () -> {
            if (!actionTaken[0]) {
                actionTaken[0] = true;
                pendingConfirmation.set(null);
            }
        });
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ ORDERS Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private UIComponent buildOrdersView() {
        VStack v = new VStack().gap(4);
        v.flex();
        v.addChild(Ui.tabs(ordersTab)
                .tab(OrdersTab.ACTIVE, t("ui.economy.orders.active"))
                .tab(OrdersTab.HISTORY, t("ui.economy.orders.history")));
        UIComponent ordersSwitcher = Ui.switcher(ordersTab)
                .when(OrdersTab.ACTIVE, this::buildActiveOrdersList)
                .when(OrdersTab.HISTORY, this::buildHistoryView);
        ordersSwitcher.flex();
        v.addChild(ordersSwitcher);
        updateOrdersSubtabs();
        return v;
    }

    private void updateOrdersSubtabs() {
        // active tab indicator handled by Tabs selection
    }

    private UIComponent buildActiveOrdersList() {
        VStack v = new VStack().gap(4);
        v.flex();
        VStack toolbar = new VStack().gap(3);
        TextField search = Ui.textField(activeOrdersQuery);
        search.placeholder(t("ui.economy.new_order.search_placeholder"));
        search.fillWidth();
        toolbar.addChild(search);
        HStack filters = new HStack().gap(4);
        UIComponent orderFilter = filterSelect(t("ui.economy.filter.order"), activeOrderFilter,
                Map.of(ActiveOrderFilter.ALL, t("ui.economy.opt.all"), ActiveOrderFilter.SELL, t("ui.economy.opt.sell"),
                        ActiveOrderFilter.BUY, t("ui.economy.opt.buy"), ActiveOrderFilter.INFINITE, t("ui.economy.opt.infinite")));
        UIComponent productFilter = filterSelect(t("ui.economy.filter.product"), activeOrderType,
                Map.of(CommodityTypeFilter.ALL, t("ui.economy.opt.all"), CommodityTypeFilter.ITEMS, t("ui.economy.opt.items"), CommodityTypeFilter.FLUIDS, t("ui.economy.opt.fluids")));
        UIComponent sortFilter = filterSelect(t("ui.economy.filter.sort"), activeOrderSort,
                Map.of(ActiveOrderSort.NEWEST, t("ui.economy.opt.newest"), ActiveOrderSort.OLDEST, t("ui.economy.opt.oldest"),
                        ActiveOrderSort.PRICE_ASC, t("ui.economy.opt.price_asc"), ActiveOrderSort.PRICE_DESC, t("ui.economy.opt.price_desc")));
        orderFilter.flex();
        productFilter.flex();
        sortFilter.flex();
        filters.addChild(orderFilter);
        filters.addChild(productFilter);
        filters.addChild(sortFilter);
        toolbar.addChild(filters);
        v.addChild(toolbar);
        UIComponent activeList = Ui.switcher(activeEmpty)
                .when(false, () -> Ui.list(visibleActiveOrders, this::buildActiveOrderRow)
                        .key(e -> e.orderId)
                        .itemHeight(34)
                        .flex())
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_active_trades")));
        activeList.flex();
        v.addChild(activeList);
        return v;
    }

    private UIComponent buildActiveOrderRow(MarketNetwork.ActiveOrderEntry e) {
        HStack row = new HStack().gap(4);
        row.height(34);
        UIComponent info = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 34; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                boolean hovered = mx >= x && mx < x + width && my >= y && my < y + height;
                UiRender.roundedOutline(g, x, y + 1, width, 32, 3,
                        hovered ? c.surfaceRaised() : c.surface(),
                        hovered ? c.border() : c.borderSubtle());
                CommodityIconComponent.drawIcon(g, e.itemId, x + 4, y + 9, 16, 16);
                String side = e.isSell ? t("ui.economy.opt.sell") : t("ui.economy.opt.buy");
                side = fitText(f, side, Math.max(0, width / 4));
                UiRender.text(g, f, side, x + 24, y + 4, e.isSell ? c.danger() : c.success());
                int nameX = x + 24 + f.width(side) + 6;
                String name = fitText(f, getItemDisplayName(e.itemId, e.displayName),
                        Math.max(0, x + width - nameX - 4));
                UiRender.text(g, f, name, nameX, y + 4, c.onSurface());
                String qty = e.isInfinite
                        ? t("ui.economy.orders.quantity_infinite")
                        : Component.translatable("ui.economy.orders.quantity_progress",
                                isFluidCommodity(e.itemId) ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity),
                                isFluidCommodity(e.itemId) ? formatFluidAmount(e.initialQuantity) : formatItemAmount(e.initialQuantity)).getString();
                drawPriceChangeRowMarquee(g, f, e.price, " \u2022 " + qty,
                        x + 24, y + 17, Math.max(0, width - 28),
                        c.primary(), c.onSurfaceMuted());
            }
        };
        info.flex();
        row.addChild(info);
        ButtonWidget edit = Ui.button(t("ui.economy.action.edit"), () -> openEditOrder(e)).ghost().small();
        edit.width(40).height(18);
        ButtonWidget cancel = Ui.button(t("ui.economy.action.cancel"),
                () -> confirmOrderCancellation(e.orderId, e.itemId, e.displayName, e.isSell,
                        e.price, e.quantity, e.isInfinite, e.identity)).danger().small();
        cancel.width(48).height(18);
        row.addChild(edit);
        row.addChild(cancel);
        return row;
    }

    private String detailItemId() {
        MarketNetwork.SyncItemDetailPacket detail = MarketClientStore.detail.get();
        return detail == null ? "" : detail.itemId;
    }

    private String detailItemName() {
        MarketNetwork.SyncItemDetailPacket detail = MarketClientStore.detail.get();
        return detail == null ? "" : detail.displayName;
    }

    private void confirmOrderCancellation(UUID orderId, String itemId, String itemName, boolean isSell,
                                          String price, int quantity, boolean infinite,
                                          com.nstut.economy.api.MarketIdentity identity) {
        OverlayHandle[] holder = new OverlayHandle[1];
        boolean[] actionTaken = new boolean[1];
        boolean fluid = isFluidCommodity(itemId);
        String side = t(isSell ? "ui.economy.opt.sell" : "ui.economy.opt.buy");
        String commodityName = getItemDisplayName(itemId, itemName);
        String remaining = infinite ? t("ui.economy.orders.quantity_infinite")
                : EconomyFormatUtil.formatCommodityQuantity(quantity, fluid);

        UIComponent orderSummary = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 28; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = theme().colors();
                CommodityIconComponent.drawIcon(g, itemId, x, y + 6, 16, 16);
                String name = fitText(f, commodityName, Math.max(0, width - 24));
                UiRender.text(g, f, name, x + 22, y + 2, colors.onSurface());
                String quantityText = Component.translatable(
                        "ui.economy.cancel_order.remaining", remaining).getString();
                int priceX = x + width - f.width(price);
                quantityText = fitText(f, quantityText, Math.max(0, priceX - 14 - (x + 22)));
                UiRender.text(g, f, quantityText, x + 22, y + 16, colors.onSurfaceMuted());
                EconomyUiComponents.drawCoin(g, priceX - 10, y + 15);
                UiRender.text(g, f, price, priceX, y + 16, colors.primary());
            }
        };
        orderSummary.fillWidth();

        HStack actions = new HStack().gap(6).justify(com.nstut.openui.layout.Justification.END);
        actions.addChild(Ui.button(Component.translatable("ui.economy.cancel_order.keep"), () -> {
            if (actionTaken[0]) return;
            actionTaken[0] = true;
            if (holder[0] != null) holder[0].close();
        }).primary());
        actions.addChild(Ui.button(Component.translatable("ui.economy.cancel_order.confirm"), () -> {
            if (actionTaken[0]) return;
            actionTaken[0] = true;
            if (holder[0] != null) holder[0].close();
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(orderId));
        }).danger());

        VStack body = new VStack().gap(10);
        body.addChild(Ui.heading(Component.translatable("ui.economy.cancel_order.title")));
        body.addChild(Ui.text(Component.translatable(
                "ui.economy.cancel_order.message", side, commodityName)).wrap().maxLines(2));
        body.addChild(orderSummary);
        body.addChild(Ui.text(Component.translatable("ui.economy.cancel_order.account", orderAccountLabel(identity)))
                .style(TextStyle.CAPTION));
        body.addChild(Ui.text(Component.translatable(isSell
                ? "ui.economy.cancel_order.restore_sell"
                : "ui.economy.cancel_order.restore_buy")).style(TextStyle.CAPTION).wrap().maxLines(2));
        body.addChild(actions);

        Card card = new Card(body).elevated(true).outlined(true).padding(14);
        card.width(230).minHeight(124);
        holder[0] = Dialog.show(uiRuntime().overlays(), card, true, true, () -> actionTaken[0] = true);
    }

    private void openEditOrder(MarketNetwork.ActiveOrderEntry e) {
        editingOrder.set(e);
        showEditDialog(e);
    }

    private void openEditOrder(MarketNetwork.OrderEntry e) {
        MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
        String id = d != null ? d.itemId : "";
        String name = d != null ? d.displayName : "";
        boolean isSell = d != null && d.asks.contains(e);
        openEditOrder(new MarketNetwork.ActiveOrderEntry(e.orderId, id, name, e.price, e.quantity, e.quantity, isSell, e.isInfinite, 0));
    }

    private void showEditDialog(MarketNetwork.ActiveOrderEntry e) {
        Signal<String> qtySig = Signals.of(String.valueOf(Math.max(1, e.quantity)));
        Signal<String> priceSig = Signals.of(e.price);
        Signal<Boolean> infSig = Signals.of(e.isInfinite);

        OrderQuantityControl qtyControl = new OrderQuantityControl(qtySig, Signals.of(e.isSell), infSig, null,
                t("ui.economy.new_order.qty_field"), t("ui.economy.new_order.unlimited"),
                t("ui.economy.action.max"), t("ui.economy.action.infinite"));
        qtyControl.fillWidth();
        TextField priceField = Ui.textField(priceSig);
        priceField.placeholder(t(isFluidCommodity(e.itemId)
                ? "ui.economy.new_order.price_field_fluid"
                : "ui.economy.new_order.price_field_item"));
        priceField.fillWidth();
        VStack body = new VStack().gap(4);
        body.addChild(Ui.text(Component.translatable(e.isSell ? "ui.economy.new_order.title_sell" : "ui.economy.new_order.title_buy")).style(TextStyle.HEADING));
        body.addChild(Ui.text(getItemDisplayName(e.itemId, e.displayName)).style(TextStyle.LABEL));
        body.addChild(Ui.text(Component.translatable("ui.economy.edit_order.account", orderAccountLabel(e.identity)))
                .style(TextStyle.CAPTION));
        body.addChild(qtyControl);
        body.addChild(priceField);
        HStack actions = new HStack().gap(4).justify(com.nstut.openui.layout.Justification.END);
        actions.addChild(Ui.button(t("ui.economy.action.save"), () -> {
            String pr = priceSig.get().trim();
            BigDecimal price;
            try {
                price = new BigDecimal(pr);
                if (price.compareTo(BigDecimal.ZERO) <= 0) { showLocalValidationToast(MarketNetwork.Action.EDIT_ORDER, "ui.economy.error.price_zero"); return; }
            } catch (Exception ex) { showLocalValidationToast(MarketNetwork.Action.EDIT_ORDER, "ui.economy.error.price_invalid"); return; }
            int newQty = e.quantity > 0 ? e.quantity : 1;
            boolean inf = infSig.get();
            if (!inf) {
                try {
                    newQty = Integer.parseInt(qtySig.get().trim());
                    if (newQty <= 0) { showLocalValidationToast(MarketNetwork.Action.EDIT_ORDER, "ui.economy.error.qty_zero"); return; }
                } catch (Exception ex) { showLocalValidationToast(MarketNetwork.Action.EDIT_ORDER, "ui.economy.error.qty_invalid"); return; }
            }
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.EditOrderPacket(e.orderId, newQty, price.toPlainString(), inf));
            editingOrder.set(null);
            editingDialogHandle.close();
        }).primary());
        actions.addChild(Ui.button(t("ui.economy.action.cancel"), () -> {
            editingOrder.set(null);
            editingDialogHandle.close();
        }).danger());
        body.addChild(actions);

        editingDialogHandle = Dialog.show(uiRuntime().overlays(), body);
    }

    private OverlayHandle editingDialogHandle;

    private UIComponent buildHistoryView() {
        VStack v = new VStack().gap(4);
        v.flex();
        VStack toolbar = new VStack().gap(3);
        TextField search = Ui.textField(historyQuery);
        search.placeholder(t("ui.economy.history.search_placeholder"));
        search.fillWidth();
        toolbar.addChild(search);
        HStack filters = new HStack().gap(4);
        UIComponent tradeFilter = filterSelect(t("ui.economy.filter.trade"), historyFilter,
                Map.of(HistoryFilter.ALL, t("ui.economy.opt.all"), HistoryFilter.SALES, t("ui.economy.opt.sales"), HistoryFilter.PURCHASES, t("ui.economy.opt.purchases")));
        UIComponent productFilter = filterSelect(t("ui.economy.filter.product"), historyType,
                Map.of(CommodityTypeFilter.ALL, t("ui.economy.opt.all"), CommodityTypeFilter.ITEMS, t("ui.economy.opt.items"), CommodityTypeFilter.FLUIDS, t("ui.economy.opt.fluids")));
        UIComponent sortFilter = filterSelect(t("ui.economy.filter.sort"), historySort,
                Map.of(HistorySort.NEWEST, t("ui.economy.opt.newest"), HistorySort.OLDEST, t("ui.economy.opt.oldest"),
                        HistorySort.HIGHEST_TOTAL, t("ui.economy.opt.highest_total")));
        tradeFilter.flex();
        productFilter.flex();
        sortFilter.flex();
        filters.addChild(tradeFilter);
        filters.addChild(productFilter);
        filters.addChild(sortFilter);
        toolbar.addChild(filters);
        v.addChild(toolbar);
        UIComponent historyList = Ui.switcher(historyEmpty)
                        .when(false, () -> Ui.list(visibleHistory, this::buildHistoryRow)
                        .key(e -> e.itemId + ":" + e.timestamp + ":" + e.counterparty)
                        .itemHeight(28)
                        .flex())
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_trades")));
        historyList.flex();
        v.addChild(historyList);
        return v;
    }

    private static final java.text.SimpleDateFormat DATE_FMT = new java.text.SimpleDateFormat("MM/dd HH:mm");

    private UIComponent buildHistoryRow(HistoryEntry e) {
        return new UIComponent() {
            {
                height(28);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 28; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y + 1, width, 26, 3, c.surfaceRaised());
                }
                CommodityIconComponent.drawIcon(g, e.itemId, x + 4, y + 6, 16, 16);
                String side = e.wasSell ? t("ui.economy.opt.sell") : t("ui.economy.opt.buy");
                side = fitText(f, side, Math.max(0, width / 4));
                UiRender.text(g, f, side, x + 24, y + 4, e.wasSell ? c.danger() : c.success());
                String date = fitText(f, DATE_FMT.format(new Date(e.timestamp)), Math.max(0, width / 2));
                int dateX = x + width - f.width(date) - 4;
                int nameX = x + 24 + f.width(side) + 6;
                String name = fitText(f, getItemDisplayName(e.itemId, e.displayName),
                        Math.max(0, dateX - nameX - 6));
                UiRender.text(g, f, name, nameX, y + 4, c.onSurface());
                UiRender.text(g, f, date, dateX, y + 4, c.onSurfaceMuted());
                EconomyUiComponents.drawCoin(g, x + 24, y + 17);
                String pq = e.price + " x " + (isFluidCommodity(e.itemId) ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity));
                String dir = e.wasSell ? t("ui.economy.direction.to") + " " : t("ui.economy.direction.from") + " ";
                String counterparty = fitText(f, dir + e.counterparty, Math.max(0, width / 2));
                int counterpartyX = x + width - f.width(counterparty) - 4;
                pq = fitText(f, pq, Math.max(0, counterpartyX - (x + 35) - 6));
                UiRender.text(g, f, pq, x + 35, y + 16, c.primary());
                UiRender.text(g, f, counterparty, counterpartyX, y + 16, c.onSurfaceMuted());
            }
        };
    }

    // Player payment ------------------------------------------------------------

    private UIComponent buildPayPlayerView() {
        VStack v = new VStack().gap(6);
        v.fillWidth();
        v.addChild(Ui.heading(Component.translatable("ui.economy.payment.title")));
        v.addChild(Ui.text(() -> Component.translatable(
                        MarketClientStore.isTeamPrincipal()
                                ? "ui.economy.payment.source_team"
                                : "ui.economy.payment.source_personal"))
                .style(TextStyle.CAPTION).wrap().ellipsis(false));

        TextField playerField = new TextField(payPlayerQuery) {
            @Override public void onFocusGained() {
                super.onFocusGained();
                refreshPlayerSearchResults(payPlayerQuery.get(), true);
            }
        };
        playerField.placeholder(t("ui.economy.payment.search_placeholder"));
        playerField.fillWidth();
        v.addChild(playerField);
        setupPlayerSearchPopover(playerField);

        TextField amountField = Ui.textField(payAmount);
        amountField.placeholder(t("ui.economy.payment.amount_placeholder"));
        amountField.fillWidth();
        v.addChild(amountField);

        ButtonWidget pay = Ui.button(Component.translatable("ui.economy.payment.pay"), () -> {
            UUID target = payPlayerId.get();
            if (target == null) return;
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.PlayerPaymentPacket(target, payAmount.get().trim()));
        }).primary();
        pay.fillWidth();
        Runnable updatePayEnabled = () -> {
            boolean amountValid = false;
            try {
                amountValid = new BigDecimal(payAmount.get().trim()).signum() > 0;
            } catch (RuntimeException ignored) {}
            pay.enabled(payPlayerId.get() != null && amountValid);
        };
        updatePayEnabled.run();
        subscriptions.add(payAmount.subscribe(ignored -> updatePayEnabled.run()));
        subscriptions.add(payPlayerId.subscribe(ignored -> updatePayEnabled.run()));
        v.addChild(pay);
        return v;
    }

    private void setupPlayerSearchPopover(TextField anchor) {
        if (playerSearchSubscription != null) {
            playerSearchSubscription.close();
            playerSearchSubscription = null;
        }
        hidePlayerSearch();
        VirtualList<MarketNetwork.PlayerTargetData> list = Ui.list(playerSearchResults, this::buildPlayerSearchResultRow)
                .itemHeight(28)
                .gap(2);
        list.height(150);
        playerSearchPopover = Ui.popover(anchor, list).matchAnchorWidth();
        playerSearchSubscription = payPlayerQuery.subscribe(query -> {
            MarketNetwork.PlayerTargetData selected = selectedPayTarget();
            if (selected != null && java.util.Objects.equals(query, selected.name)) {
                playerSearchResults.set(List.of());
                hidePlayerSearch();
                return;
            }
            payPlayerId.set(null);
            refreshPlayerSearchResults(query, true);
        });
        subscriptions.add(MarketClientStore.playerTargets.subscribe(ignored ->
                refreshPlayerSearchResults(payPlayerQuery.get(), playerFieldFocused(anchor))));
    }

    private boolean playerFieldFocused(TextField field) {
        return field != null && field.isFocused();
    }

    private void refreshPlayerSearchResults(String query, boolean openWhenAvailable) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MarketNetwork.PlayerTargetData> matches = new ArrayList<>();
        for (MarketNetwork.PlayerTargetData player : MarketClientStore.playerTargets.get()) {
            if (player == null) continue;
            String search = (player.name + " " + player.playerId).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || search.contains(q)) matches.add(player);
        }
        playerSearchResults.set(List.copyOf(matches));
        if (matches.isEmpty()) {
            hidePlayerSearch();
        } else if (openWhenAvailable && !isPlayerSearchOpen() && uiRuntime() != null) {
            playerSearchHandle = playerSearchPopover.show(uiRuntime().overlays());
        }
    }

    private UIComponent buildPlayerSearchResultRow(MarketNetwork.PlayerTargetData player) {
        return new UIComponent() {
            { height(28); }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 28; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme colors = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y, width, height, 2, colors.surfaceRaised());
                }
                drawPlayerHead(g, player.playerId, x + 3, y + 4, 20);
                String label = player.name + " - " + player.playerId;
                drawMarqueeText(g, f, label, x + 28, y + 9, Math.max(1, width - 32), colors.onSurface(), false);
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    payPlayerId.set(player.playerId);
                    payPlayerQuery.set(player.name);
                    hidePlayerSearch();
                    return true;
                }
                return false;
            }
        };
    }

    private MarketNetwork.PlayerTargetData selectedPayTarget() {
        UUID id = payPlayerId.get();
        if (id == null) return null;
        for (MarketNetwork.PlayerTargetData player : MarketClientStore.playerTargets.get()) {
            if (player != null && id.equals(player.playerId)) return player;
        }
        return null;
    }

    private void hidePlayerSearch() {
        if (playerSearchHandle != null) {
            playerSearchHandle.close();
            playerSearchHandle = null;
        }
    }

    private boolean isPlayerSearchOpen() {
        return playerSearchHandle != null && playerSearchHandle.isOpen();
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ PORTFOLIO Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private UIComponent buildPortfolioView() {
        VStack v = new VStack().gap(4);
        v.flex();
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 24; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                BigDecimal nw = BigDecimal.ZERO, bal = BigDecimal.ZERO, ass = BigDecimal.ZERO;
                List<MarketNetwork.PortfolioPointData> pts = MarketClientStore.portfolioPoints.get();
                if (!pts.isEmpty()) {
                    var last = pts.get(pts.size() - 1);
                    nw = new BigDecimal(last.netWorth); bal = new BigDecimal(last.balance); ass = new BigDecimal(last.assets);
                }
                int boxW = (width - 8) / 3;
                drawStatBox(g, f, x, y, boxW, height, t("ui.economy.portfolio.net_worth"), formatMoneyCompact(nw), c.primary(), c);
                drawStatBox(g, f, x + boxW + 4, y, boxW, height, t("ui.economy.portfolio.liquid_cash"), formatMoneyCompact(bal), c.success(), c);
                drawStatBox(g, f, x + 2 * (boxW + 4), y, boxW, height, t("ui.economy.portfolio.vault_assets"), formatMoneyCompact(ass), c.primary(), c);
                if (my >= y && my < y + height) {
                    if (mx >= x && mx < x + boxW) {
                        deferredTooltip = Component.translatable("ui.economy.portfolio.tooltip.net_worth");
                    } else if (mx >= x + boxW + 4 && mx < x + 2 * boxW + 4) {
                        deferredTooltip = Component.translatable("ui.economy.portfolio.tooltip.liquid_cash");
                    } else if (mx >= x + 2 * (boxW + 4) && mx < x + 3 * boxW + 8) {
                        deferredTooltip = Component.translatable("ui.economy.portfolio.tooltip.vault_assets");
                    }
                }
            }
        });
        v.addChild(new TrendChartComponent(portfolioChartSamples, portfolioChartOffset, true));
        UIComponent holdingsList = Ui.switcher(holdingsEmpty)
                .when(false, () -> Ui.list(visibleHoldings, this::buildHoldingRow)
                        .key(h -> h.itemId)
                        .itemHeight(26)
                        .flex())
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_holdings")));
        holdingsList.flex();
        v.addChild(holdingsList);
        return v;
    }

    private void drawStatBox(GuiGraphics g, Font f, int bx, int by, int bw, int bh, String label, String value, int valueColor, ColorScheme c) {
        UiRender.surface(g, bx, by, bw, bh, 3, c.surface(), c.borderSubtle(), false, c);
        drawMarqueeText(g, f, label, bx + 4, by + 3, Math.max(0, bw - 8), c.onSurfaceMuted(), true);
        EconomyUiComponents.drawCoin(g, bx + 4, by + 13);
        drawMarqueeText(g, f, value, bx + 14, by + 13, Math.max(0, bw - 18), valueColor, false);
    }

    private static void drawPlayerHead(GuiGraphics g, UUID playerId, int x, int y, int size) {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection != null ? connection.getPlayerInfo(playerId) : null;
        ResourceLocation skin = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(playerId);
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    /** Draws text inside a hard clip and ping-pongs it only when it exceeds the available width. */
    private static void drawMarqueeText(GuiGraphics g, Font f, String text, int tx, int ty,
                                        int maxWidth, int color, boolean centerWhenFitting) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) return;
        int textWidth = f.width(text);
        int drawX = centerWhenFitting && textWidth <= maxWidth
                ? tx + (maxWidth - textWidth) / 2
                : tx - UiAnimationUtil.pingPongOffset(textWidth, maxWidth, Util.getMillis());
        ClipStack.push(g, tx, ty, maxWidth, f.lineHeight);
        try {
            UiRender.text(g, f, text, drawX, ty, color);
        } finally {
            ClipStack.pop(g);
        }
    }

    /** Draws the coin, price, and complete change label as one clipped overflow row. */
    private static void drawPriceChangeRowMarquee(GuiGraphics g, Font f, String price, String change,
                                                   int tx, int ty, int maxWidth,
                                                   int priceColor, int changeColor) {
        if (maxWidth <= 0 || price == null || price.isEmpty() || change == null || change.isEmpty()) return;
        int coinWidth = 9;
        int gap = 5;
        int contentWidth = coinWidth + f.width(price) + gap + f.width(change);
        int drawX = tx - UiAnimationUtil.pingPongOffset(contentWidth, maxWidth, Util.getMillis());
        ClipStack.push(g, tx, ty - 1, maxWidth, f.lineHeight + 2);
        try {
            EconomyUiComponents.drawCoin(g, drawX, ty);
            int priceX = drawX + coinWidth;
            UiRender.text(g, f, price, priceX, ty, priceColor);
            UiRender.text(g, f, change, priceX + f.width(price) + gap, ty, changeColor);
        } finally {
            ClipStack.pop(g);
        }
    }

    private static void drawSellerBadge(GuiGraphics g, Font f, String text, int bx, int by,
                                        int maxWidth, boolean serverOrder, ColorScheme colors) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) return;
        int badgeWidth = Math.min(maxWidth, EconomyUiComponents.badgeWidth(f, text));
        if (badgeWidth <= 4) return;
        int background = serverOrder ? colors.primaryDim() : colors.surfaceVariant();
        int border = serverOrder ? colors.primary() : colors.border();
        int textColor = serverOrder ? colors.onPrimary() : colors.onSurface();
        UiRender.pill(g, bx, by, badgeWidth, EconomyUiComponents.BADGE_HEIGHT, background, border);
        drawMarqueeText(g, f, text, bx + 4, by + 2, Math.max(0, badgeWidth - 8), textColor, false);
    }

    /** Draws the seller badge and order values as one clipped, ping-ponging row. */
    private static void drawOrderRowMarquee(GuiGraphics g, Font f, String seller, String orderText,
                                            int tx, int ty, int maxWidth, boolean serverOrder,
                                            int orderColor, ColorScheme colors) {
        if (maxWidth <= 0 || seller == null || seller.isEmpty() || orderText == null || orderText.isEmpty()) return;
        int badgeWidth = EconomyUiComponents.badgeWidth(f, seller);
        int gap = 6;
        int priceWidth = 10 + f.width(orderText);
        int contentWidth = badgeWidth + gap + priceWidth;
        int drawX = tx - UiAnimationUtil.pingPongOffset(contentWidth, maxWidth, Util.getMillis());
        ClipStack.push(g, tx, ty - 1, maxWidth, Math.max(EconomyUiComponents.BADGE_HEIGHT + 2, f.lineHeight + 2));
        try {
            drawSellerBadge(g, f, seller, drawX, ty, badgeWidth, serverOrder, colors);
            int coinX = drawX + badgeWidth + gap;
            EconomyUiComponents.drawCoin(g, coinX, ty);
            UiRender.text(g, f, orderText, coinX + 10, ty + 1, orderColor);
        } finally {
            ClipStack.pop(g);
        }
    }

    private UIComponent buildHoldingRow(MarketNetwork.AssetHoldingData h) {
        return new UIComponent() {
            {
                height(26);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 26; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y + 1, width, 24, 3, c.surfaceRaised());
                }
                CommodityIconComponent.drawIcon(g, h.itemId, x + 4, y + 5, 16, 16);
                String qty = isFluidCommodity(h.itemId) ? formatFluidAmount(h.quantity) : "x" + formatCompact(h.quantity);
                String val = fitText(f, formatMoneyCompact(new BigDecimal(h.totalValue)), Math.max(0, width / 3));
                int valueX = x + width - f.width(val) - 4;
                int coinX = valueX - 10;
                qty = fitText(f, qty, Math.max(0, width / 3));
                int qtyX = coinX - f.width(qty) - 8;
                int nameX = x + 24;
                String name = fitText(f, getItemDisplayName(h.itemId, h.displayName),
                        Math.max(0, qtyX - nameX - 6));
                UiRender.text(g, f, name, nameX, y + 8, c.onSurface());
                UiRender.text(g, f, qty, qtyX, y + 8, c.onSurfaceMuted());
                EconomyUiComponents.drawCoin(g, coinX, y + 8);
                UiRender.text(g, f, val, valueX, y + 8, c.primary());
            }
        };
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ CONTAINERS Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private UIComponent buildTeamTreasuryView() {
        VStack v = new VStack().gap(6);
        v.fillWidth();

        v.addChild(new UIComponent() {
            { height(66); }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 66; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                MarketClientStore.TeamWalletState team = MarketClientStore.teamWallet.get();
                if (team == null || !team.visible()) {
                    return;
                }
                UiRender.text(g, f, fitText(f, t("ui.economy.treasury.title") + " / " + team.teamName(), Math.max(0, width - 8)), x + 4, y + 3, c.primary());
                String teamBal = t("ui.economy.treasury.team_balance") + ": " + formatMoneyCompact(new BigDecimal(team.teamBalance()));
                String personalBal = t("ui.economy.treasury.personal_balance") + ": " + formatMoneyCompact(new BigDecimal(MarketClientStore.balance.get()));
                EconomyUiComponents.drawCoin(g, x + 4, y + 20);
                UiRender.text(g, f, teamBal, x + 14, y + 21, c.onSurface());
                EconomyUiComponents.drawCoin(g, x + 4, y + 35);
                UiRender.text(g, f, personalBal, x + 14, y + 36, c.onSurfaceMuted());
                String role = t("ui.economy.treasury.role") + ": " + humanizeEnum(team.role());
                UiRender.text(g, f, fitText(f, role, Math.max(0, width - 8)), x + 4, y + 51, c.onSurfaceMuted());
            }
        });

        TextField amount = Ui.textField(treasuryAmount);
        amount.placeholder(t("ui.economy.treasury.amount"));
        amount.fillWidth();
        v.addChild(amount);

        ButtonWidget deposit = Ui.button(Component.translatable("ui.economy.treasury.deposit"), () ->
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.TeamTreasuryPacket(
                        treasuryAmount.get().trim()))).success();
        deposit.fillWidth();
        v.addChild(deposit);

        Runnable permissions = () -> {
            MarketClientStore.TeamWalletState team = MarketClientStore.teamWallet.get();
            boolean visible = team != null && team.visible();
            boolean canDeposit = visible && team.canDeposit();
            deposit.enabled(canDeposit);
            Component unavailable = Component.translatable("ui.economy.treasury.unavailable_hint");
            deposit.tooltip(!visible ? unavailable : canDeposit
                    ? Component.translatable("ui.economy.treasury.deposit_rule")
                    : Component.translatable("ui.economy.toast.treasury_permission_denied"));
        };
        permissions.run();
        subscriptions.add(MarketClientStore.teamWallet.subscribe(ignored -> permissions.run()));

        ButtonWidget infoToggle = Ui.button(
                Component.literal(treasuryInfoExpanded.get() ? "^ " : "v ")
                        .append(Component.translatable("ui.economy.treasury.info")),
                () -> treasuryInfoExpanded.set(!treasuryInfoExpanded.get())).outline().small();
        infoToggle.fillWidth();
        infoToggle.setActive(treasuryInfoExpanded.get());
        subscriptions.add(treasuryInfoExpanded.subscribe(expanded -> {
            infoToggle.setActive(expanded);
            infoToggle.setLabel(Component.literal(expanded ? "^ " : "v ")
                    .append(Component.translatable("ui.economy.treasury.info")));
        }));
        v.addChild(infoToggle);

        UIComponent infoPanel = Ui.switcher(treasuryInfoExpanded)
                .when(false, () -> Ui.spacer().height(0))
                .when(true, () -> {
                    VStack rules = new VStack().gap(3);
                    rules.fillWidth();
                    rules.addChild(Ui.text(() -> Component.translatable("ui.economy.treasury.deposit_rule"))
                            .style(TextStyle.CAPTION).wrap().ellipsis(false));
                    rules.addChild(Ui.text(() -> {
                                MarketClientStore.TeamWalletState team = MarketClientStore.teamWallet.get();
                                return Component.translatable("ui.economy.treasury.market_rule",
                                        humanizeEnum(team != null ? team.spendRole() : "OFFICER"));
                            }).style(TextStyle.CAPTION).wrap().ellipsis(false));
                    rules.addChild(Ui.text(() -> {
                                MarketClientStore.TeamWalletState team = MarketClientStore.teamWallet.get();
                                return Component.translatable("ui.economy.treasury.payout_rule",
                                        humanizeEnum(team != null ? team.payoutRole() : "OWNER"));
                            }).style(TextStyle.CAPTION).wrap().ellipsis(false));
                    rules.addChild(Ui.text(() -> Component.translatable("ui.economy.treasury.settlement_rule"))
                            .style(TextStyle.CAPTION).wrap().ellipsis(false));

                    UIComponent rulesScroll = Ui.scroll(rules);
                    rulesScroll.fillWidth();
                    rulesScroll.flex();
                    Card card = new Card(rulesScroll).padding(6).outlined(true).elevated(false);
                    card.fillWidth();
                    card.flex();
                    return card;
                });
        infoPanel.fillWidth();
        infoPanel.flex();
        v.addChild(infoPanel);
        v.flex();
        return v;
    }

    private UIComponent buildContainersView() {
        VStack v = new VStack().gap(4);
        v.flex();
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 24; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                int vaults = 0, tanks = 0; long items = 0, fluid = 0;
                for (MarketNetwork.VaultDetailEntry e : MarketClientStore.containerEntries.get()) {
                    if (e.tank) { tanks++; fluid += e.totalItems; } else { vaults++; items += e.totalItems; }
                }
                int boxW = (width - 12) / 4;
                drawStatBox(g, f, x, y, boxW, height, t("ui.economy.containers.vaults"), formatCompact(vaults), c.primary(), c);
                drawStatBox(g, f, x + boxW + 4, y, boxW, height, t("ui.economy.containers.tanks"), formatCompact(tanks), c.primary(), c);
                drawStatBox(g, f, x + 2 * (boxW + 4), y, boxW, height, t("ui.economy.containers.items"), Component.translatable("ui.economy.containers.items_unit", formatCompact(items)).getString(), c.success(), c);
                drawStatBox(g, f, x + 3 * (boxW + 4), y, boxW, height, t("ui.economy.containers.fluid"), Component.translatable("ui.economy.containers.fluid_unit", formatCompact(fluid)).getString(), c.success(), c);
            }
        });
        UIComponent containersList = Ui.switcher(containersEmpty)
                .when(false, () -> Ui.responsive(ctx -> {
                    boolean stackedHeader = ctx.width() > 0 && ctx.width() < containerInlineHeaderMinWidth();
                    return Ui.list(visibleContainers, e -> buildContainerRow(e, stackedHeader))
                            .key(e -> e.dimension + ":" + e.x + "," + e.y + "," + e.z)
                            .itemHeight(stackedHeader ? 48 : 40)
                            .flex();
                }))
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_containers")));
        containersList.flex();
        v.addChild(containersList);
        return v;
    }

    private int containerInlineHeaderMinWidth() {
        int maxTitleWidth = 0;
        int vaultIndex = 0;
        int tankIndex = 0;
        for (MarketNetwork.VaultDetailEntry entry : MarketClientStore.containerEntries.get()) {
            int index = entry.tank ? ++tankIndex : ++vaultIndex;
            String prefix = entry.tank ? t("ui.economy.container.tank_prefix") : t("ui.economy.container.vault_prefix");
            maxTitleWidth = Math.max(maxTitleWidth, font.width(prefix + index));
        }
        if (maxTitleWidth == 0) {
            maxTitleWidth = Math.max(
                    font.width(t("ui.economy.container.vault_prefix") + "1"),
                    font.width(t("ui.economy.container.tank_prefix") + "1"));
        }

        int modeWidth = Math.max(
                configStateChipPreferredWidth(t("ui.economy.container.mode_both")),
                Math.max(
                        configStateChipPreferredWidth(t("ui.economy.container.mode_input")),
                        configStateChipPreferredWidth(t("ui.economy.container.mode_output"))));
        int ownerWidth = Math.max(
                configStateChipPreferredWidth(t("ui.economy.principal.personal")),
                configStateChipPreferredWidth(t("ui.economy.principal.team")));
        int statusWidth = Math.max(
                new Badge(Component.translatable("ui.economy.container.active"), Badge.Variant.SUCCESS).preferredWidth(font),
                new Badge(Component.translatable("ui.economy.container.full"), Badge.Variant.DANGER).preferredWidth(font));

        // The title is flex/ellipsis, so do not require its full preferred width before keeping
        // the header inline. Reserve only a small readable slice; the chips get priority.
        int titleReserve = Math.min(maxTitleWidth, 24);
        // Card padding (8), three 4px gaps, and a small scrollbar/layout safety margin.
        return titleReserve + modeWidth + ownerWidth + statusWidth + 8 + 12 + 6;
    }

    private int configStateChipPreferredWidth(String label) {
        return EconomyUiComponents.configStateButton(Component.literal(label), () -> {}).preferredWidth(font);
    }

    private UIComponent buildContainerRow(MarketNetwork.VaultDetailEntry e, boolean stackedHeader) {
        int idx = 1;
        for (MarketNetwork.VaultDetailEntry other : MarketClientStore.containerEntries.get()) {
            if (other == e) break;
            if (other.tank == e.tank) idx++;
        }

        boolean full = e.usedSlots >= e.totalSlots;
        String title = (e.tank ? t("ui.economy.container.tank_prefix") : t("ui.economy.container.vault_prefix")) + idx;
        String location = e.dimension.replace("minecraft:", "") + " (" + e.x + ", " + e.y + ", " + e.z + ")";
        String capacity = e.tank
                ? formatFluidAmount(e.usedSlots) + "/" + formatFluidAmount(e.totalSlots)
                : formatCompact(e.usedSlots) + "/" + formatCompact(e.totalSlots) + " " + t("ui.economy.containers.slots");
        String modeLabel = switch (e.mode) {
            case 1 -> t("ui.economy.container.mode_input");
            case 2 -> t("ui.economy.container.mode_output");
            default -> t("ui.economy.container.mode_both");
        };
        String modeTooltipKey = switch (e.mode) {
            case 1 -> "ui.economy.container.tooltip.mode_input";
            case 2 -> "ui.economy.container.tooltip.mode_output";
            default -> "ui.economy.container.tooltip.mode_both";
        };

        ButtonWidget modeChip = EconomyUiComponents.configStateButton(Component.literal(modeLabel), () -> {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(e.x, e.y, e.z);
            if (e.tank) MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleTankModePacket(e.dimension, pos));
            else MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleVaultModePacket(e.dimension, pos));
        });
        modeChip.height(14);
        boolean canChangeMode = !e.teamOwned || e.canReassign;
        modeChip.enabled(canChangeMode);
        modeChip.tooltip(Component.translatable(canChangeMode ? modeTooltipKey : "ui.economy.container.mode_admin_hint"));

        String transferLabel = t(e.teamOwned
                ? "ui.economy.principal.team"
                : "ui.economy.principal.personal");
        ButtonWidget ownerChip = EconomyUiComponents.configStateButton(Component.literal(transferLabel), () ->
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.SetStorageOwnerPacket(
                        e.dimension, new net.minecraft.core.BlockPos(e.x, e.y, e.z), e.tank, !e.teamOwned)));
        ownerChip.height(14);
        ownerChip.enabled(e.canReassign);
        ownerChip.tooltip(Component.translatable(e.canReassign
                ? "ui.economy.container.owner_transfer_tooltip"
                : "ui.economy.container.owner_admin_required"));

        Badge statusBadge = new Badge(
                Component.translatable(full ? "ui.economy.container.full" : "ui.economy.container.active"),
                full ? Badge.Variant.DANGER : Badge.Variant.SUCCESS);
        statusBadge.tooltip(Component.translatable(full
                ? "ui.economy.container.tooltip.full"
                : "ui.economy.container.tooltip.active"));

        VStack content = new VStack().gap(1);
        content.fillWidth();

        UIComponent titleText = Ui.text(Component.literal(title)).style(TextStyle.LABEL).nowrap().ellipsis();
        if (stackedHeader) {
            HStack titleRow = new HStack();
            titleRow.fillWidth();
            titleText.flex();
            titleRow.addChild(titleText);
            content.addChild(titleRow);

            HStack chipRow = new HStack().gap(4);
            chipRow.fillWidth();
            chipRow.addChild(Ui.spacer().flex());
            chipRow.addChild(modeChip);
            chipRow.addChild(ownerChip);
            chipRow.addChild(statusBadge);
            content.addChild(chipRow);
        } else {
            HStack header = new HStack().gap(4);
            header.fillWidth();
            titleText.flex();
            header.addChild(titleText);
            header.addChild(modeChip);
            header.addChild(ownerChip);
            header.addChild(statusBadge);
            content.addChild(header);
        }

        HStack meta = new HStack().gap(4);
        meta.fillWidth();
        UIComponent locationText = Ui.text(Component.literal(location)).style(TextStyle.CAPTION).nowrap().ellipsis();
        locationText.flex();
        meta.addChild(locationText);
        meta.addChild(Ui.text(Component.literal(capacity)).style(TextStyle.CAPTION).nowrap());
        content.addChild(meta);

        Card card = new Card(content).padding(4).outlined(true).elevated(false).hoverable(true);
        card.fillWidth();
        card.height(stackedHeader ? 48 : 40);
        return card;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Chart component Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    private class TrendChartComponent extends UIComponent {
        private final ReadableSignal<List<ChartSample>> data;
        private final Signal<Integer> offset;
        private final boolean showBalance;
        TrendChartComponent(ReadableSignal<List<ChartSample>> data, Signal<Integer> offset, boolean showBalance) {
            this.data = data; this.offset = offset; this.showBalance = showBalance;
            fillWidth();
        }
        @Override public int preferredWidth(Font f) { return 0; }
        @Override public int preferredHeight(Font f) { return showBalance ? 48 : 40; }

        private final class ChartLayout {
            final double min, max, graphMin, graphRange;
            final int plotLeft, plotRight, plotTop, plotBottom;
            final int liveX, liveY, liveW, liveH, badgeX, badgeY, badgeW, badgeH;
            final String liveText, currentText;

            ChartLayout(Font f, List<ChartSample> points, int off) {
                double low = Double.POSITIVE_INFINITY;
                double high = Double.NEGATIVE_INFINITY;
                for (ChartSample point : points) {
                    low = Math.min(low, point.value);
                    high = Math.max(high, point.value);
                }
                min = low;
                max = high;
                graphMin = EconomyFormatUtil.chartGraphMin(min, max);
                graphRange = EconomyFormatUtil.chartGraphRange(min, max);

                currentText = formatChartMoney(points.get(points.size() - 1).value);
                badgeW = EconomyUiComponents.coinBadgeWidth(f, currentText);
                badgeH = 12;
                badgeX = x + width - badgeW - 4;
                liveText = off == 0 ? t("ui.economy.chart.live") : t("ui.economy.chart.live_scroll");
                liveW = f.width(liveText) + 8;
                liveH = 12;
                liveX = badgeX - liveW - 4;
                liveY = y + 2;
                int axisWidth = Math.max(f.width(formatChartMoney(max)), f.width(formatChartMoney(min))) + 18;
                plotLeft = x + axisWidth;
                plotRight = Math.max(plotLeft + 1, liveX - 6);
                plotTop = y + 14;
                plotBottom = Math.max(plotTop + 1, y + height - 9);
                int latestY = valueToY(points.get(points.size() - 1).value);
                badgeY = Math.max(y + 2, Math.min(y + height - badgeH - 2, latestY - badgeH / 2));
            }

            int pointX(int index, int count) {
                return plotLeft + index * (plotRight - plotLeft) / Math.max(1, count - 1);
            }

            int valueToY(double value) {
                return plotBottom - (int) Math.round((value - graphMin) / graphRange * (plotBottom - plotTop));
            }
        }

        private List<ChartSample> visiblePoints(List<ChartSample> points, int off) {
            int end = Math.min(points.size(), Math.max(MAX_VISIBLE_CHART_STEPS, points.size() - off));
            return points.subList(Math.max(0, end - MAX_VISIBLE_CHART_STEPS), end);
        }

        private static void drawChartLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
            int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
            int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
            int error = dx + dy;
            while (true) {
                g.fill(x0, y0, x0 + 1, y0 + 1, color);
                if (x0 == x1 && y0 == y1) return;
                int twiceError = error * 2;
                if (twiceError >= dy) { error += dy; x0 += sx; }
                if (twiceError <= dx) { error += dx; y0 += sy; }
            }
        }

        @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
            ColorScheme c = uiRuntime().theme().colors();
            UiRender.surface(g, x, y, width, height, 4, c.input(), c.borderSubtle(), false, c);
            List<ChartSample> pts = data.get();
            if (pts.size() < 2) {
                UiRender.text(g, f, t("ui.economy.chart.no_data"), x + (width - f.width(t("ui.economy.chart.no_data"))) / 2, y + height / 2 - 4, c.onSurfaceMuted());
                return;
            }
            int total = pts.size();
            int maxOff = Math.max(0, total - MAX_VISIBLE_CHART_STEPS);
            int off = Math.min(offset.get(), maxOff);
            List<ChartSample> vis = visiblePoints(pts, off);
            ChartLayout layout = new ChartLayout(f, vis, off);
            EconomyUiComponents.drawCoin(g, x + 3, y + 2);
            UiRender.text(g, f, formatChartMoney(layout.max), x + 13, y + 3, c.onSurfaceMuted());
            EconomyUiComponents.drawCoin(g, x + 3, y + height - 12);
            UiRender.text(g, f, formatChartMoney(layout.min), x + 13, y + height - 11, c.onSurfaceMuted());
            boolean liveHov = mx >= layout.liveX && mx < layout.liveX + layout.liveW && my >= layout.liveY && my < layout.liveY + layout.liveH;
            EconomyUiComponents.drawBadge(g, f, layout.liveText,
                    layout.liveX + layout.liveW, layout.liveY,
                    off > 0 ? Badge.Variant.SUCCESS : Badge.Variant.PRIMARY, liveHov, c);
            int guide = UiRender.alpha(c.borderSubtle(), 90);
            int middle = (layout.plotTop + layout.plotBottom) / 2;
            g.fill(layout.plotLeft, layout.plotTop, layout.plotRight, layout.plotTop + 1, guide);
            g.fill(layout.plotLeft, middle, layout.plotRight, middle + 1, guide);
            g.fill(layout.plotLeft, layout.plotBottom, layout.plotRight, layout.plotBottom + 1, guide);
            int n = vis.size();
            for (int i = 0; i < n; i++) {
                double val = vis.get(i).value;
                int x0 = layout.pointX(i, n), y0 = layout.valueToY(val);
                if (i > 0) {
                    drawChartLine(g, layout.pointX(i - 1, n), layout.valueToY(vis.get(i - 1).value), x0, y0, c.primary());
                }
                boolean nodeHov = mx >= x0 - 4 && mx <= x0 + 4 && my >= y0 - 4 && my <= y0 + 4;
                if (i == n - 1 || nodeHov) {
                    int radius = nodeHov ? 2 : 1;
                    g.fill(x0 - radius, y0 - radius, x0 + radius + 1, y0 + radius + 1, nodeHov ? 0xFFFFFFFF : c.primary());
                }
                if (nodeHov) {
                    deferredTooltip = Component.literal(vis.get(i).tooltip);
                }
            }
            EconomyUiComponents.drawCoinBadge(g, f, layout.currentText,
                    layout.badgeX + layout.badgeW, layout.badgeY,
                    Badge.Variant.PRIMARY, false, c);
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            List<ChartSample> pts = data.get();
            if (pts.size() < 2) return false;
            int total = pts.size();
            int maxOff = Math.max(0, total - MAX_VISIBLE_CHART_STEPS);
            int off = Math.min(offset.get(), maxOff);
            ChartLayout layout = new ChartLayout(font, visiblePoints(pts, off), off);
            if (mx >= layout.liveX && mx < layout.liveX + layout.liveW && my >= layout.liveY && my < layout.liveY + layout.liveH) {
                if (offset.get() > 0) offset.set(0);
                return true;
            }
            return false;
        }

        @Override public boolean mouseScrolled(double mx, double my, double delta) {
            List<ChartSample> pts = data.get();
            if (pts.size() < 2) return false;
            int total = pts.size();
            int maxOff = Math.max(0, total - MAX_VISIBLE_CHART_STEPS);
            if (maxOff == 0) return false;
            int off = Math.min(offset.get(), maxOff);
            ChartLayout layout = new ChartLayout(font, visiblePoints(pts, off), off);
            if (mx >= layout.plotLeft && mx < layout.plotRight && my >= layout.plotTop && my < layout.plotBottom) {
                if (delta < 0) offset.set(Math.min(maxOff, off + 1));
                else if (delta > 0) offset.set(Math.max(0, off - 1));
                return true;
            }
            return false;
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ Shared helpers Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    static String formatCompact(double val) { return EconomyFormatUtil.formatCompact(val); }
    static String formatCompact(BigDecimal val) { return EconomyFormatUtil.formatCompact(val); }
    static String formatCompact(long val) { return EconomyFormatUtil.formatCompact(val); }
    static String formatCompact(String str) { return EconomyFormatUtil.formatCompact(str); }
    private String orderAccountLabel(com.nstut.economy.api.MarketIdentity identity) {
        if (identity == null || identity.principal().kind() == com.nstut.economy.api.AccountKind.PLAYER) {
            return t("ui.economy.principal.personal");
        }
        if (identity.principal().kind() == com.nstut.economy.api.AccountKind.TEAM) {
            return Component.translatable("ui.economy.wallet.team", identity.principal().id().toString().substring(0, 8)).getString();
        }
        return identity.principal().kind().name();
    }

    private String selectedWalletBalance() {
        MarketClientStore.TeamWalletState team = MarketClientStore.teamWallet.get();
        return MarketClientStore.isTeamPrincipal()
                ? (team != null && team.visible() ? team.teamBalance() : "0") : MarketClientStore.balance.get();
    }

    private String selectedWalletLabel() {
        MarketClientStore.TeamWalletState team = MarketClientStore.teamWallet.get();
        if (MarketClientStore.isTeamPrincipal()) {
            return team != null && team.visible()
                    ? Component.translatable("ui.economy.wallet.team", team.teamName()).getString()
                    : t("ui.economy.wallet.team_unavailable");
        }
        return t("ui.economy.principal.personal");
    }

    private static String humanizeEnum(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    static String formatMoney(BigDecimal val) { return EconomyFormatUtil.formatMoney(val); }
    static String formatMoneyCompact(BigDecimal val) { return EconomyFormatUtil.formatMoneyCompact(val); }
    static String formatChartMoney(double val) { return EconomyFormatUtil.formatMoneyCompact(BigDecimal.valueOf(val)); }
    static String formatPriceChange(double p) { return EconomyFormatUtil.formatPriceChange(p); }
    int changeColor(double p) {
        ColorScheme c = uiRuntime().theme().colors();
        if (Double.isNaN(p) || p == 0) return c.onSurfaceMuted();
        return p > 0 ? c.success() : c.danger();
    }
    static String formatFluidAmount(int a) { return EconomyFormatUtil.formatFluidAmount(a); }
    static String formatFluidAmountDetailed(int a) { return EconomyFormatUtil.formatFluidAmountDetailed(a); }
    static String formatItemAmount(int a) { return EconomyFormatUtil.formatItemAmount(a); }
    static String formatQty(int q, boolean fluid) { return fluid ? formatFluidAmount(q) : formatItemAmount(q); }

    static BigDecimal totalPrice(BigDecimal quotedPrice, int quantity, String itemId) {
        return isFluidCommodity(itemId)
                ? FluidCommodity.totalFromBucketQuote(quotedPrice, quantity)
                : quotedPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal parsePrice(String s) {
        if (s == null || s.equals("--") || s.isEmpty()) return BigDecimal.valueOf(999999999);
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.valueOf(999999999); }
    }

    private static boolean isFluidCommodity(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        try {
            Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(itemId));
            return fluid != net.minecraft.world.level.material.Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid);
        } catch (RuntimeException ignored) { return false; }
    }

    static String getItemDisplayName(String itemId, String rawName) {
        if (isExactVariantId(itemId)) return getPrimaryItemDisplayName(itemId, rawName);
        return resolveRegisteredDisplayName(itemId, rawName);
    }

    private static String getPrimaryItemDisplayName(String itemId, String rawName) {
        if (!isExactVariantId(itemId)) return resolveRegisteredDisplayName(itemId, rawName);
        String baseName = resolveRegisteredDisplayName(baseCommodityId(itemId), rawName);
        List<Component> tooltip = commodityTooltipLines(itemId);
        if (!tooltip.isEmpty()) {
            String first = tooltip.get(0).getString().trim();
            if (!first.isEmpty()) return first;
        }
        return baseName;
    }

    private static String resolveRegisteredDisplayName(String itemId, String rawName) {
        if (itemId != null && !itemId.isEmpty()) {
            try {
                ResourceLocation rl = new ResourceLocation(itemId);
                Fluid fluid = BuiltInRegistries.FLUID.get(rl);
                if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && !com.nstut.economy.platform.Services.FLUID.isAir(fluid)) {
                    String name = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
                    if (isResolvedDisplayName(name, itemId)) return name;
                    if (rawName != null) {
                        String translated = Component.translatable(rawName).getString();
                        if (isResolvedDisplayName(translated, itemId)) return translated;
                    }
                    return humanizeResourcePath(rl.getPath());
                }
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != net.minecraft.world.item.Items.AIR) {
                    String name = new ItemStack(item).getHoverName().getString();
                    if (name != null && !name.isEmpty() && !name.startsWith("tagprefix.") && !name.startsWith("item.")) return name;
                }
            } catch (Exception ignored) {}
        }
        if (rawName != null && !rawName.isEmpty()) {
            try {
                String translated = Component.translatable(rawName).getString();
                if (translated != null && !translated.isEmpty() && !translated.equals(rawName)) return translated;
            } catch (Exception ignored) {}
            return rawName;
        }
        return itemId != null ? itemId : "";
    }


    static String baseCommodityId(String itemId) {
        if (itemId == null || itemId.isBlank()) return itemId;
        try {
            return com.nstut.economy.trading.ItemVariant.baseItemId(
                    com.nstut.economy.api.EconomyId.parse(itemId)).toString();
        } catch (RuntimeException ignored) {
            return itemId;
        }
    }

    static boolean isExactVariantId(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        String base = baseCommodityId(itemId);
        return base != null && !itemId.equals(base);
    }


    private record VariantPresentation(String displayName, List<String> facets, Component tooltip) {}

    private static List<Component> commodityTooltipLines(String itemId) {
        if (!isExactVariantId(itemId)) return List.of();
        try {
            ItemStack stack = CommodityIconComponent.stackForCommodity(itemId);
            return tooltipLinesForStack(stack);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<Component> tooltipLinesForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        try {
            return Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<String> meaningfulTooltipDetails(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return new ArrayList<>();
        String first = lines.get(0).getString().trim();
        List<String> details = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).getString().trim();
            if (line.isEmpty() || line.equalsIgnoreCase(first)) continue;
            if (line.endsWith(":") || line.startsWith("When in ")) continue;
            details.add(line);
        }
        return details;
    }

    private static boolean removeFirstIgnoreCase(List<String> values, String target) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(target)) {
                values.remove(i);
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static boolean hasCanonicalMetadataDifference(ItemStack stack) {
        if (stack == null || stack.isEmpty() || Minecraft.getInstance().level == null) return false;
        try {
            var registries = Minecraft.getInstance().level.registryAccess();
            String exact = com.nstut.economy.compat.Compat.canonicalItemStack(registries, stack);
            String baseline = com.nstut.economy.compat.Compat.canonicalItemStack(
                    registries, new ItemStack(stack.getItem()));
            return !exact.equals(baseline);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static VariantPresentation variantPresentation(String itemId, String rawName) {
        String displayName = getPrimaryItemDisplayName(itemId, rawName);
        if (!isExactVariantId(itemId)) {
            return new VariantPresentation(displayName, List.of(), Component.literal(displayName));
        }

        ItemStack stack = CommodityIconComponent.stackForCommodity(itemId);
        if (stack == null || stack.isEmpty()) {
            return new VariantPresentation(displayName, List.of(), Component.literal(displayName));
        }

        List<Component> exactTooltip = tooltipLinesForStack(stack);
        ItemStack baselineStack = new ItemStack(stack.getItem());
        List<Component> baselineTooltip = tooltipLinesForStack(baselineStack);
        List<String> baselineDetails = meaningfulTooltipDetails(baselineTooltip);
        List<String> facets = new ArrayList<>();

        for (String detail : meaningfulTooltipDetails(exactTooltip)) {
            if (removeFirstIgnoreCase(baselineDetails, detail)) continue;
            if (!containsIgnoreCase(facets, detail)) facets.add(detail);
        }

        String durability = null;
        if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            durability = Component.translatable(
                    "ui.economy.variant.durability", remaining, stack.getMaxDamage()).getString();
            if (!containsIgnoreCase(facets, durability)) facets.add(durability);
        }

        String baselineName = baselineStack.getHoverName().getString();
        boolean nameRepresentsMetadata = displayName != null
                && baselineName != null
                && !displayName.equalsIgnoreCase(baselineName);
        boolean metadataDiffers = hasCanonicalMetadataDifference(stack);
        boolean genericMetadataFacet = false;
        if (facets.isEmpty() && metadataDiffers && !nameRepresentsMetadata) {
            facets.add(t("ui.economy.variant.additional_metadata"));
            genericMetadataFacet = true;
        }

        StringBuilder tooltipText = new StringBuilder();
        for (Component line : exactTooltip) {
            String value = line.getString().trim();
            if (value.isEmpty()) continue;
            if (!tooltipText.isEmpty()) tooltipText.append("\n");
            tooltipText.append(value);
        }
        if (tooltipText.isEmpty()) tooltipText.append(displayName);

        if (durability != null) {
            boolean alreadyShown = false;
            for (Component line : exactTooltip) {
                String value = line.getString().toLowerCase(Locale.ROOT);
                if (value.contains("durability")) {
                    alreadyShown = true;
                    break;
                }
            }
            if (!alreadyShown) tooltipText.append("\n").append(durability);
        }
        if (genericMetadataFacet
                && !tooltipText.toString().toLowerCase(Locale.ROOT)
                .contains(t("ui.economy.variant.additional_metadata").toLowerCase(Locale.ROOT))) {
            tooltipText.append("\n").append(t("ui.economy.variant.additional_metadata"));
        }
        return new VariantPresentation(displayName, List.copyOf(facets),
                Component.literal(tooltipText.toString()));
    }

    private static String compactVariantFacetSummary(VariantPresentation presentation, int maxFacets) {
        if (presentation == null || presentation.facets().isEmpty() || maxFacets <= 0) return "";
        List<String> facets = presentation.facets();
        int shown = Math.min(maxFacets, facets.size());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (!text.isEmpty()) text.append(" \u00B7 ");
            text.append(facets.get(i));
        }
        if (facets.size() > shown) {
            if (!text.isEmpty()) text.append(" \u00B7 ");
            text.append(Component.translatable(
                    "ui.economy.variant.more", facets.size() - shown).getString());
        }
        return text.toString();
    }

    private static String variantMetadataSummary(String itemId) {
        return compactVariantFacetSummary(variantPresentation(itemId, itemId), 2);
    }

    private static Component commodityTooltip(String itemId, String rawName) {
        return variantPresentation(itemId, rawName).tooltip();
    }

    private static void drawVariantFacetLines(GuiGraphics g, Font font, List<String> lines,
                                              int x, int y, int width, int color) {
        for (int i = 0; i < lines.size(); i++) {
            drawMarqueeText(g, font, lines.get(i), x, y + i * 12, width, color, false);
        }
    }

    private static String commoditySearchText(String itemId, String rawName) {
        StringBuilder text = new StringBuilder();
        if (itemId != null) text.append(itemId).append(' ');
        VariantPresentation presentation = variantPresentation(itemId, rawName);
        text.append(presentation.displayName()).append(' ');
        for (String facet : presentation.facets()) text.append(facet).append(' ');
        for (Component line : commodityTooltipLines(itemId)) {
            text.append(line.getString()).append(' ');
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isResolvedDisplayName(String name, String resourceId) {
        return name != null && !name.isBlank() && !name.equals(resourceId)
                && !name.startsWith("fluid.") && !name.startsWith("block.");
    }

    private static String humanizeResourcePath(String path) {
        String[] words = path.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? path : result.toString();
    }

    private static boolean matchesCommodityTypeFilter(String itemId, String commodityType, CommodityTypeFilter mode) {
        if (mode == CommodityTypeFilter.ALL) return true;
        boolean fluid = commodityType != null ? commodityType.equalsIgnoreCase("FLUID") : isFluidCommodity(itemId);
        return CommodityUtil.matchesTypeFilter(fluid, mode.ordinal());
    }

    private List<MarketNetwork.ItemCardData> filterCards(String q, BrowseActivityFilter act, CommodityTypeFilter type, BrowseSort sort, List<MarketNetwork.ItemCardData> cards) {
        List<MarketNetwork.ItemCardData> f = new ArrayList<>();
        String query = q.toLowerCase().trim();
        for (MarketNetwork.ItemCardData c : cards) {
            if (!query.isEmpty() && !commoditySearchText(c.itemId, c.displayName).contains(query)) continue;
            if (act == BrowseActivityFilter.ACTIVE && c.offerCount <= 0) continue;
            if (!matchesCommodityTypeFilter(c.itemId, c.commodityType, type)) continue;
            f.add(c);
        }
        f.sort((a, b) -> {
            return switch (sort) {
                case PRICE_ASC -> parsePrice(a.globalPrice).compareTo(parsePrice(b.globalPrice));
                case PRICE_DESC -> parsePrice(b.globalPrice).compareTo(parsePrice(a.globalPrice));
                case NAME_ASC -> a.displayName.compareToIgnoreCase(b.displayName);
                case MOST_ACTIVE -> Integer.compare(b.offerCount, a.offerCount);
            };
        });
        return f;
    }

    private List<BrowseGroup> groupBrowseCards(List<MarketNetwork.ItemCardData> cards,
                                               List<MarketNetwork.ItemCardData> catalog, BrowseSort sort) {
        // Browse controls product visibility and summaries; the picker owns variant filtering.
        Map<String, List<MarketNetwork.ItemCardData>> fullGroups =
                com.nstut.economy.util.BrowseGrouping.visibleCatalogGroups(cards, catalog, card -> {
                    String base = "ITEM".equalsIgnoreCase(card.commodityType) ? baseCommodityId(card.itemId) : card.itemId;
                    return card.commodityType + "|" + base;
                });
        Map<String, List<MarketNetwork.ItemCardData>> grouped = new LinkedHashMap<>();
        for (MarketNetwork.ItemCardData card : cards) {
            boolean item = "ITEM".equalsIgnoreCase(card.commodityType);
            String base = item ? baseCommodityId(card.itemId) : card.itemId;
            String key = card.commodityType + "|" + base;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(card);
        }

        List<BrowseGroup> groups = new ArrayList<>();
        for (List<MarketNetwork.ItemCardData> variants : grouped.values()) {
            MarketNetwork.ItemCardData first = variants.get(0);
            boolean item = "ITEM".equalsIgnoreCase(first.commodityType);
            String base = item ? baseCommodityId(first.itemId) : first.itemId;
            String displayName = resolveRegisteredDisplayName(base, first.displayName);
            int offers = 0;
            MarketNetwork.ItemCardData bestPrice = first;
            for (MarketNetwork.ItemCardData variant : variants) {
                offers += Math.max(0, variant.offerCount);
                if (parsePrice(variant.globalPrice).compareTo(parsePrice(bestPrice.globalPrice)) < 0) {
                    bestPrice = variant;
                }
            }
            groups.add(new BrowseGroup(base, first.commodityType, displayName, List.copyOf(fullGroups.get(first.commodityType + "|" + base)),
                    offers, bestPrice.globalPrice, bestPrice.priceChangePercent));
        }

        groups.sort((a, b) -> switch (sort) {
            case PRICE_ASC -> parsePrice(a.globalPrice()).compareTo(parsePrice(b.globalPrice()));
            case PRICE_DESC -> parsePrice(b.globalPrice()).compareTo(parsePrice(a.globalPrice()));
            case NAME_ASC -> a.displayName().compareToIgnoreCase(b.displayName());
            case MOST_ACTIVE -> Integer.compare(b.offerCount(), a.offerCount());
        });
        return groups;
    }

    private List<HistoryEntry> filterHistory(String q, HistoryFilter filt, CommodityTypeFilter type, HistorySort sort, List<HistoryEntry> entries) {
        List<HistoryEntry> f = new ArrayList<>();
        if (entries == null) return f;
        String query = q.toLowerCase().trim();
        for (HistoryEntry e : entries) {
            if (e == null) continue;
            if (filt == HistoryFilter.SALES && !e.wasSell) continue;
            if (filt == HistoryFilter.PURCHASES && e.wasSell) continue;
            if (!matchesCommodityTypeFilter(e.itemId, null, type)) continue;
            if (!query.isEmpty() && !commoditySearchText(e.itemId, e.displayName).contains(query)
                    && !e.counterparty.toLowerCase(Locale.ROOT).contains(query)) continue;
            f.add(e);
        }
        f.sort((a, b) -> {
            return switch (sort) {
                case NEWEST -> Long.compare(b.timestamp, a.timestamp);
                case OLDEST -> Long.compare(a.timestamp, b.timestamp);
                case HIGHEST_TOTAL -> totalPrice(new BigDecimal(b.price), b.quantity, b.itemId)
                        .compareTo(totalPrice(new BigDecimal(a.price), a.quantity, a.itemId));
            };
        });
        return f;
    }

    private List<MarketNetwork.ActiveOrderEntry> filterActiveOrders(String q, ActiveOrderFilter filt, CommodityTypeFilter type, ActiveOrderSort sort, List<MarketNetwork.ActiveOrderEntry> entries) {
        List<MarketNetwork.ActiveOrderEntry> f = new ArrayList<>();
        if (entries == null) return f;
        String query = q.toLowerCase().trim();
        for (MarketNetwork.ActiveOrderEntry e : entries) {
            if (e == null) continue;
            if (!query.isEmpty() && !commoditySearchText(e.itemId, e.displayName).contains(query)) continue;
            if (filt == ActiveOrderFilter.SELL && !e.isSell) continue;
            if (filt == ActiveOrderFilter.BUY && e.isSell) continue;
            if (filt == ActiveOrderFilter.INFINITE && (!e.isInfinite || e.isSell)) continue;
            if (!matchesCommodityTypeFilter(e.itemId, null, type)) continue;
            f.add(e);
        }
        f.sort((a, b) -> {
            return switch (sort) {
                case NEWEST -> Long.compare(b.createdAt, a.createdAt);
                case OLDEST -> Long.compare(a.createdAt, b.createdAt);
                case PRICE_ASC -> parsePrice(a.price).compareTo(parsePrice(b.price));
                case PRICE_DESC -> parsePrice(b.price).compareTo(parsePrice(a.price));
            };
        });
        return f;
    }

    private int getVaultStockForItem(String query) {
        if (query == null || query.trim().isEmpty()) return 0;
        String q = query.trim();
        MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
        if (d != null && (d.itemId.equalsIgnoreCase(q) || d.displayName.equalsIgnoreCase(q))) return d.vaultCount;
        for (var h : MarketClientStore.assetHoldings.get()) {
            if (h.itemId.equalsIgnoreCase(q) || h.displayName.equalsIgnoreCase(q)) return h.quantity;
        }
        return 0;
    }

    private List<ItemSearchResult> getStoredItemSearchResults(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ItemSearchResult> results = new ArrayList<>();
        for (MarketNetwork.AssetHoldingData holding : MarketClientStore.assetHoldings.get()) {
            if (holding == null || holding.quantity <= 0) continue;
            if (!q.isEmpty() && !commoditySearchText(holding.itemId, holding.displayName).contains(q)) continue;
            results.add(new ItemSearchResult(holding.itemId, holding.displayName, holding.quantity, true));
        }
        results.sort((a, b) -> {
            int byName = getPrimaryItemDisplayName(a.itemId, a.displayName)
                    .compareToIgnoreCase(getPrimaryItemDisplayName(b.itemId, b.displayName));
            if (byName != 0) return byName;
            return variantMetadataSummary(a.itemId).compareToIgnoreCase(variantMetadataSummary(b.itemId));
        });
        return results.size() > 100 ? List.copyOf(results.subList(0, 100)) : List.copyOf(results);
    }

    private List<ItemSearchResult> getItemSearchResults(String query) {
        if (query == null || query.length() < 2) return List.of();
        String q = query.toLowerCase(Locale.ROOT);
        List<ItemSearchResult> results = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MarketNetwork.ItemCardData card : MarketClientStore.cards.get()) {
            String id = card.itemId;
            String name = getItemDisplayName(id, card.displayName);
            if (commoditySearchText(id, card.displayName).contains(q) && seen.add(id)) {
                results.add(new ItemSearchResult(id, name));
                if (results.size() >= 50) return results;
            }
        }
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            String name = new ItemStack(item).getHoverName().getString();
            String rlStr = rl.toString();
            if ((name.toLowerCase(Locale.ROOT).contains(q) || rlStr.contains(q)) && seen.add(rlStr)) {
                results.add(new ItemSearchResult(rlStr, name));
                if (results.size() >= 50) return results;
            }
        }
        for (ResourceLocation rl : BuiltInRegistries.FLUID.keySet()) {
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            if (!CommodityUtil.isCanonicalFluid(fluid)) continue;
            String name = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
            String rlStr = rl.toString();
            if ((name.toLowerCase(Locale.ROOT).contains(q) || rlStr.contains(q)) && seen.add(rlStr)) {
                results.add(new ItemSearchResult(rlStr, name));
                if (results.size() >= 50) return results;
            }
        }
        return results;
    }

    /**
     * Sidebar navigation button that preserves the OpenUI button behavior while
     * hard-clipping its label and ping-ponging only when the text is wider than
     * the available interior. This keeps long/localized labels inside the border.
     */
    private static final class MarqueeNavButton extends ButtonWidget {
        private final Component marqueeLabel;
        private boolean suppressBaseLabel;

        MarqueeNavButton(Component label, Runnable action) {
            super(label);
            this.marqueeLabel = label;
            onPress(action);
            alignLeft();
            activeIndicator();
            height(18);
        }

        @Override
        public Component getLabel() {
            return suppressBaseLabel ? Component.empty() : marqueeLabel;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
            suppressBaseLabel = true;
            try {
                super.render(g, font, mx, my, pt);
            } finally {
                suppressBaseLabel = false;
            }

            int textX = x + 9;
            int textY = y + (height - font.lineHeight) / 2;
            int textWidth = Math.max(0, width - 15);
            int textColor = isEnabled()
                    ? theme().colors().onSurface()
                    : theme().colors().onSurfaceDisabled();
            drawMarqueeText(g, font, marqueeLabel.getString(), textX, textY, textWidth, textColor, false);
        }
    }
    private static class ItemSearchResult {
        final String itemId;
        final String displayName;
        final int quantity;
        final boolean owned;

        ItemSearchResult(String itemId, String displayName) {
            this(itemId, displayName, 0, false);
        }

        ItemSearchResult(String itemId, String displayName, int quantity, boolean owned) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.quantity = quantity;
            this.owned = owned;
        }
    }
}
