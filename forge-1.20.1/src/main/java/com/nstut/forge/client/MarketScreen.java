package com.nstut.forge.client;

import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.util.CommodityUtil;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.forge.network.HistoryEntry;
import com.nstut.forge.network.MarketNetwork;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.VStack;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.Badge;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.Popover;
import com.nstut.openui.controls.Select;
import com.nstut.openui.controls.Tabs;
import com.nstut.openui.controls.TextField;
import com.nstut.openui.controls.VirtualList;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.state.Computed;
import com.nstut.openui.state.ReadableSignal;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.state.Subscription;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class MarketScreen extends EconomyUiContainerScreen<MarketMenu> {

    private static final int SCREEN_W = 356;
    private static final int SCREEN_H = 248;
    private static final int SIDEBAR_W = 84;
    private static final int MAX_VISIBLE_CHART_STEPS = 15;
    private static final SimpleDateFormat CHART_TIME_FMT = new SimpleDateFormat("MM/dd HH:mm:ss");

    enum MarketView { BROWSE, DETAIL, NEW_ORDER, ORDERS, PORTFOLIO, CONTAINERS }
    enum OrdersTab { ACTIVE, HISTORY }
    enum CommodityTypeFilter { ALL, ITEMS, FLUIDS }
    enum BrowseActivityFilter { ALL, ACTIVE }
    enum BrowseSort { PRICE_ASC, PRICE_DESC, NAME_ASC, MOST_ACTIVE }
    enum HistoryFilter { ALL, SALES, PURCHASES }
    enum HistorySort { NEWEST, OLDEST, HIGHEST_TOTAL }
    enum ActiveOrderFilter { ALL, SELL, BUY, INFINITE }
    enum ActiveOrderSort { NEWEST, OLDEST, PRICE_ASC, PRICE_DESC }
    enum BrowseLayout { GRID, LIST }

    record PendingConfirmation(String itemId, int quantity, String priceStr, boolean isSell,
                               boolean isInfinite, String action, String itemName, String totalPrice,
                               String commodityType) {}

    record ChartSample(double value, String tooltip) {}

    // ── View & filter state ───────────────────────────────────────────────
    private final Signal<MarketView> view = Signals.of(MarketView.BROWSE);
    private final Signal<OrdersTab> ordersTab = Signals.of(OrdersTab.ACTIVE);
    private final Signal<String> browseQuery = Signals.of("");
    private final Signal<BrowseActivityFilter> browseActivity = Signals.of(BrowseActivityFilter.ALL);
    private final Signal<CommodityTypeFilter> browseType = Signals.of(CommodityTypeFilter.ALL);
    private final Signal<BrowseSort> browseSort = Signals.of(BrowseSort.PRICE_ASC);
    private final Signal<BrowseLayout> browseLayout = Signals.of(
            MarketClientPreferences.isBrowseGridView() ? BrowseLayout.GRID : BrowseLayout.LIST);
    private final Signal<String> historyQuery = Signals.of("");
    private final Signal<HistoryFilter> historyFilter = Signals.of(HistoryFilter.ALL);
    private final Signal<CommodityTypeFilter> historyType = Signals.of(CommodityTypeFilter.ALL);
    private final Signal<HistorySort> historySort = Signals.of(HistorySort.NEWEST);
    private final Signal<String> activeOrdersQuery = Signals.of("");
    private final Signal<ActiveOrderFilter> activeOrderFilter = Signals.of(ActiveOrderFilter.ALL);
    private final Signal<CommodityTypeFilter> activeOrderType = Signals.of(CommodityTypeFilter.ALL);
    private final Signal<ActiveOrderSort> activeOrderSort = Signals.of(ActiveOrderSort.NEWEST);
    private final Signal<String> selectedItemId = Signals.of(null);

    private final Signal<String> createCommodityQuery = Signals.of("");
    private final Signal<String> createQty = Signals.of("");
    private final Signal<String> createPrice = Signals.of("");
    private final Signal<Boolean> createSellMode = Signals.of(true);
    private final Signal<Boolean> createInfinite = Signals.of(false);
    private final Signal<String> createError = Signals.of(null);
    private final Signal<MarketNetwork.ActiveOrderEntry> editingOrder = Signals.of(null);
    private final Signal<PendingConfirmation> pendingConfirmation = Signals.of(null);

    private final Signal<Integer> detailChartOffset = Signals.of(0);
    private final Signal<Integer> portfolioChartOffset = Signals.of(0);

    private final List<Computed<?>> computedList = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    private <T> Computed<T> computed(java.util.function.Supplier<T> s) {
        Computed<T> c = Signals.computed(s);
        computedList.add(c);
        return c;
    }

    private final Computed<List<MarketNetwork.ItemCardData>> visibleBrowseCards = computed(() ->
            filterCards(browseQuery.get(), browseActivity.get(), browseType.get(), browseSort.get(), MarketClientStore.cards.get()));
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
                        + "\nPrice: $" + p.price + "\nVolume: " + formatQty(p.quantity, isFluidCommodity(d.itemId))));
            }
        }
        return out;
    });
    private final Computed<List<ChartSample>> portfolioChartSamples = computed(() -> {
        List<MarketNetwork.PortfolioPointData> pts = MarketClientStore.portfolioPoints.get();
        List<ChartSample> out = new ArrayList<>();
        for (MarketNetwork.PortfolioPointData p : pts) {
            out.add(new ChartSample(Double.parseDouble(p.netWorth),
                    "Net Worth: " + formatCompact(Double.parseDouble(p.netWorth))
                            + "\nCash: " + formatCompact(Double.parseDouble(p.balance))
                            + "  |  Assets: " + formatCompact(Double.parseDouble(p.assets))));
        }
        return out;
    });

    private ButtonWidget browseBtn, newOrderBtn, ordersBtn, portfolioBtn, containersBtn;
    private Popover itemSearchPopover;
    private OverlayHandle itemSearchHandle;
    private boolean itemSearchShown;

    public MarketScreen(MarketMenu menu, net.minecraft.world.entity.player.Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SCREEN_W;
        this.imageHeight = SCREEN_H;
    }

    // ── Network handler delegates (kept as thin bridges to the store) ──────
    public static void handleSyncItemList(MarketNetwork.SyncItemListPacket pkt) {
        MarketClientStore.applySyncItemList(pkt);
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

    @Override
    protected void init() {
        super.init();
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
    }

    @Override
    public void removed() {
        for (Computed<?> c : computedList) c.close();
        for (Subscription s : subscriptions) s.close();
        computedList.clear();
        subscriptions.clear();
        super.removed();
    }

    // ── Build root UI ─────────────────────────────────────────────────────
    @Override
    protected UIComponent buildUI() {
        HStack main = new HStack().gap(0);
        main.fillWidth();
        main.fillHeight();

        VStack sidebar = new VStack().gap(5);
        sidebar.width(SIDEBAR_W);
        sidebar.fillHeight();
        sidebar.addChild(Ui.text("ECONOMY").style(TextStyle.TITLE));
        sidebar.addChild(Ui.text("MARKET"));
        sidebar.addChild(EconomyUiComponents.balancePill(MarketClientStore.balance));
        sidebar.addChild(Ui.divider());
        buildNav(sidebar);
        sidebar.addChild(Ui.spacer());
        sidebar.addChild(buildThemeToggle());
        main.addChild(sidebar);

        VStack content = new VStack().gap(6);
        content.flex();
        content.addChild(Ui.switcher(view)
                .when(MarketView.BROWSE, this::buildBrowseView)
                .when(MarketView.DETAIL, this::buildDetailView)
                .when(MarketView.NEW_ORDER, this::buildNewOrderView)
                .when(MarketView.ORDERS, this::buildOrdersView)
                .when(MarketView.PORTFOLIO, this::buildPortfolioView)
                .when(MarketView.CONTAINERS, this::buildContainersView));
        main.addChild(content);

        subscriptions.add(view.subscribe(v -> {
            updateNav(v);
            onViewEntered(v);
        }));
        updateNav(MarketView.BROWSE);
        return main;
    }

    private void buildNav(VStack sidebar) {
        browseBtn = navButton("Browse", () -> switchView(MarketView.BROWSE));
        newOrderBtn = navButton("New Order", () -> switchView(MarketView.NEW_ORDER));
        ordersBtn = navButton("Orders", () -> switchView(MarketView.ORDERS));
        portfolioBtn = navButton("Portfolio", () -> switchView(MarketView.PORTFOLIO));
        containersBtn = navButton("Containers", () -> switchView(MarketView.CONTAINERS));
        sidebar.addChild(browseBtn);
        sidebar.addChild(newOrderBtn);
        sidebar.addChild(ordersBtn);
        sidebar.addChild(portfolioBtn);
    }

    private ButtonWidget navButton(String label, Runnable action) {
        ButtonWidget b = Ui.button(Component.literal(label), action).alignLeft().activeIndicator();
        b.height(18);
        return b;
    }

    private void updateNav(MarketView v) {
        if (browseBtn != null) browseBtn.setActive(v == MarketView.BROWSE);
        if (newOrderBtn != null) newOrderBtn.setActive(v == MarketView.NEW_ORDER);
        if (ordersBtn != null) ordersBtn.setActive(v == MarketView.ORDERS);
        if (portfolioBtn != null) portfolioBtn.setActive(v == MarketView.PORTFOLIO);
        if (containersBtn != null) containersBtn.setActive(v == MarketView.CONTAINERS);
    }

    private void onViewEntered(MarketView v) {
        switch (v) {
            case BROWSE -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
            case ORDERS -> {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestActiveOrdersPacket());
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestOrderHistoryPacket());
            }
            case PORTFOLIO -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
            case CONTAINERS -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestVaultInfoPacket());
            case DETAIL -> {
                String id = selectedItemId.get();
                if (id != null) MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id));
            }
            case NEW_ORDER -> {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
                String id = createCommodityQuery.get();
                if (id != null && !id.isEmpty()) MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id));
            }
        }
    }

    private void switchView(MarketView v) {
        view.set(v);
    }

    // ── BROWSE ────────────────────────────────────────────────────────────
    private UIComponent buildBrowseView() {
        VStack v = new VStack().gap(4);
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 15; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                g.drawString(f, "MARKETPLACE", x, y + 2, c.onSurface());
                String listingCount = visibleBrowseCards.get().size() + " live listings";
                g.drawString(f, listingCount, x + width - f.width(listingCount), y + 2, c.onSurfaceMuted());
            }
        });

        HStack searchBar = new HStack().gap(4);
        TextField search = Ui.textField(browseQuery);
        search.placeholder("Search products...");
        search.flex();
        searchBar.addChild(search);
        ButtonWidget layoutBtn = Ui.button(browseLayout.get() == BrowseLayout.GRID ? "Grid" : "Rows",
                () -> browseLayout.set(browseLayout.get() == BrowseLayout.GRID ? BrowseLayout.LIST : BrowseLayout.GRID)).ghost();
        searchBar.addChild(layoutBtn);
        v.addChild(searchBar);

        HStack filters = new HStack().gap(4);
        filters.addChild(filterSelect("Activity", browseActivity,
                Map.of(BrowseActivityFilter.ALL, "All", BrowseActivityFilter.ACTIVE, "Active")));
        filters.addChild(filterSelect("Product", browseType,
                Map.of(CommodityTypeFilter.ALL, "All", CommodityTypeFilter.ITEMS, "Items", CommodityTypeFilter.FLUIDS, "Fluids")));
        filters.addChild(filterSelect("Sort", browseSort,
                Map.of(BrowseSort.PRICE_ASC, "Price ▲", BrowseSort.PRICE_DESC, "Price ▼",
                        BrowseSort.NAME_ASC, "Name A-Z", BrowseSort.MOST_ACTIVE, "Most Active")));
        v.addChild(filters);

        v.addChild(Ui.switcher(browseLayout)
                .when(BrowseLayout.GRID, () -> Ui.grid(visibleBrowseCards, this::buildCommodityCard).minCellWidth(150).cellHeight(44))
                .when(BrowseLayout.LIST, () -> Ui.list(visibleBrowseCards, this::buildCommodityRow).itemHeight(44)));
        v.flex();
        return v;
    }

    private <T extends Enum<T>> Select<T> filterSelect(String label, Signal<T> signal, Map<T, String> labels) {
        Select<T> sel = Ui.select(signal);
        for (Map.Entry<T, String> e : labels.entrySet()) {
            sel.option(Component.literal(e.getValue()), e.getKey());
        }
        return sel;
    }

    private UIComponent buildCommodityCard(MarketNetwork.ItemCardData card) {
        return new UIComponent() {
            {
                height(44);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 44; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                boolean hovered = mx >= x && mx < x + width && my >= y && my < y + height;
                UiRender.surface(g, x, y, width, height, 4, hovered ? c.surfaceRaised() : c.surface(), hovered ? c.primary() : c.borderSubtle(), false);
                CommodityIconComponent.drawIcon(g, card.itemId, x + 6, y + 12, 16, 16);
                int textX = x + 28;
                int textWidth = Math.max(1, width - 34);
                g.drawString(f, f.plainSubstrByWidth(getItemDisplayName(card.itemId, card.displayName), textWidth), textX, y + 4, c.onSurface());
                String countText = card.offerCount > 0
                        ? EconomyFormatUtil.formatCount(card.offerCount, "order", "orders")
                        : "No active orders";
                if (card.globalPrice != null && !card.globalPrice.isEmpty() && !card.globalPrice.equals("--")) {
                    EconomyUiComponents.drawCoin(g, textX, y + 16);
                    String price = formatCompact(parsePrice(card.globalPrice));
                    g.drawString(f, price, textX + 9, y + 16, c.primary());
                    String change = formatPriceChange(card.priceChangePercent);
                    g.drawString(f, change, textX + 9 + f.width(price) + 5, y + 16, changeColor(card.priceChangePercent));
                } else {
                    g.drawString(f, "--", textX, y + 16, c.onSurfaceMuted());
                }
                g.drawString(f, f.plainSubstrByWidth(countText, textWidth), textX, y + 28, c.onSurfaceMuted());
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    openDetail(card.itemId);
                    return true;
                }
                return false;
            }
        };
    }

    private UIComponent buildCommodityRow(MarketNetwork.ItemCardData card) {
        return buildCommodityCard(card);
    }

    private void openDetail(String id) {
        selectedItemId.set(id);
        MarketClientStore.detail.set(null);
        detailChartOffset.set(0);
        switchView(MarketView.DETAIL);
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id));
    }

    // ── DETAIL ─────────────────────────────────────────────────────────────
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
                    String title = getItemDisplayName(d.itemId, d.displayName);
                    g.drawString(f, title, x, y + 4, c.onSurface());
                    double change = detailChangePercent(d);
                    String changeStr = formatPriceChange(change);
                    g.drawString(f, changeStr, x + f.width(title) + 8, y + 4, changeColor(change));
                    boolean fluid = isFluidCommodity(d.itemId);
                    String stock = fluid ? "In Tank: " + formatFluidAmountDetailed(d.vaultCount)
                            : "In Vault: " + formatItemAmount(d.vaultCount);
                    g.drawString(f, stock, x + width - f.width(stock), y + 4, c.primary());
                } else if (selectedItemId.get() != null) {
                    g.drawString(f, selectedItemId.get(), x, y + 4, c.onSurfaceMuted());
                }
            }
        });

        v.addChild(new TrendChartComponent(detailChartSamples, detailChartOffset, false));

        HStack cols = new HStack().gap(6);
        cols.flex();
        cols.addChild(buildOrderColumn(true));
        cols.addChild(buildOrderColumn(false));
        v.addChild(cols);

        v.addChild(Ui.text("MY ORDERS").style(TextStyle.HEADING));
        v.addChild(Ui.list(computed(() -> getMyOrdersForDetail()), this::buildMyOrderRow).itemHeight(18));
        v.addChild(Ui.button("Create Order for this Item", () -> {
            createCommodityQuery.set(selectedItemId.get());
            switchView(MarketView.NEW_ORDER);
        }).primary());
        return v;
    }

    private double detailChangePercent(MarketNetwork.SyncItemDetailPacket d) {
        if (d.chart == null || d.chart.isEmpty()) return Double.NaN;
        List<MarketNetwork.ChartPoint> pts = d.chart;
        int cur = pts.get(pts.size() - 1).price;
        int prev = cur;
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
        v.addChild(Ui.text(isAsks ? "SELL ORDERS" : "BUY ORDERS").style(TextStyle.HEADING));
        ReadableSignal<List<MarketNetwork.OrderEntry>> data = computed(() -> {
            MarketNetwork.SyncItemDetailPacket d = MarketClientStore.detail.get();
            List<MarketNetwork.OrderEntry> src = isAsks ? (d == null ? List.of() : d.asks) : (d == null ? List.of() : d.bids);
            List<MarketNetwork.OrderEntry> res = new ArrayList<>();
            for (MarketNetwork.OrderEntry e : src) if (!e.isPlayerOwned) res.add(e);
            return res;
        });
        v.addChild(Ui.list(data, e -> buildOtherOrderRow(e, isAsks)).itemHeight(18));
        return v;
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
                g.drawString(f, (e.isServerOrder ? "[SERVER] " : "") + e.sellerName, x + 3, y + 4, c.onSurface());
                String line = e.price + " x " + (e.isInfinite ? "∞" : (isFluidCommodity(MarketClientStore.detail.get() == null ? "" : MarketClientStore.detail.get().itemId)
                        ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity)));
                g.drawString(f, line, x + width - f.width(line) - 3, y + 4, clr);
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
                    String type = o.isSell() ? "SELL" : "from you";
                    g.drawString(f, type, x + 3, y + 4, o.isSell() ? c.danger() : c.success());
                String line = e.price + " x " + (e.isInfinite ? "∞" : formatItemAmount(e.quantity));
                g.drawString(f, line, x + width - f.width(line) - 3, y + 4, c.onSurface());
                int editW = 30, cancelW = 38;
                int cancelX = x + width - cancelW - 3;
                int editX = cancelX - editW - 3;
                drawSmallButton(g, f, editX, y + 2, editW, 14, "Edit", c.primary(), mx, my);
                drawSmallButton(g, f, cancelX, y + 2, cancelW, 14, "Cancel", c.danger(), mx, my);
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                int cancelW = 38, editW = 30;
                int cancelX = x + width - cancelW - 3;
                int editX = cancelX - editW - 3;
                if (mx >= editX && mx < cancelX && my >= y + 2 && my < y + 16) { openEditOrder(e); return true; }
                if (mx >= cancelX && mx < cancelX + cancelW && my >= y + 2 && my < y + 16) {
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId));
                    return true;
                }
                return false;
            }
        };
    }

    private void drawSmallButton(GuiGraphics g, Font f, int bx, int by, int bw, int bh, String label, int color, int mx, int my) {
        ColorScheme c = uiRuntime().theme().colors();
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        UiRender.pill(g, bx, by, bw, bh, hov ? c.surfaceRaised() : c.surface(), color);
        g.drawString(f, label, bx + (bw - f.width(label)) / 2, by + 3, color);
    }

    // ── NEW ORDER ──────────────────────────────────────────────────────────
    private UIComponent buildNewOrderView() {
        VStack v = new VStack().gap(4);
        v.addChild(Ui.button("< Back", () -> switchView(MarketView.BROWSE)).ghost());
        v.addChild(Ui.divider());

        HStack modeRow = new HStack().gap(4);
        ButtonWidget sellBtn = Ui.button("SELL ORDER", () -> { createSellMode.set(true); }).danger();
        ButtonWidget buyBtn = Ui.button("BUY ORDER", () -> { createSellMode.set(false); }).success();
        sellBtn.flex(); buyBtn.flex();
        modeRow.addChild(sellBtn); modeRow.addChild(buyBtn);
        v.addChild(modeRow);

        TextField idField = Ui.textField(createCommodityQuery);
        idField.placeholder("Search item name or ID...");
        v.addChild(idField);
        setupItemSearchPopover(idField);

        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return createSellMode.get() ? 14 : 0; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                if (!createSellMode.get()) return;
                String id = createCommodityQuery.get();
                int stock = getVaultStockForItem(id);
                boolean fluid = isFluidCommodity(id);
                String msg = fluid ? "Tank Stock: " + formatFluidAmountDetailed(stock)
                        : "Vault Stock: " + formatItemAmount(stock);
                g.drawString(f, msg, x + 2, y + 3, stock > 0 ? theme().colors().success() : theme().colors().danger());
            }
        });

        HStack qtyRow = new HStack().gap(4);
        TextField qtyField = Ui.textField(createQty);
        qtyField.placeholder("Quantity (e.g. 10)");
        qtyField.flex();
        qtyRow.addChild(qtyField);
        qtyRow.addChild(Ui.button("MAX", () -> {
            String id = createCommodityQuery.get();
            int stock = getVaultStockForItem(id);
            if (stock > 0) createQty.set(String.valueOf(stock));
        }).ghost());
        qtyRow.addChild(Ui.button("∞", () -> createInfinite.set(!createInfinite.get())).ghost());
        v.addChild(qtyRow);

        TextField priceField = Ui.textField(createPrice);
        priceField.placeholder("Price per unit (e.g. 150)");
        v.addChild(priceField);

        ButtonWidget submit = Ui.button("Submit Order", this::submitOffer).primary();
        v.addChild(submit);

        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return createError.get() != null ? 12 : 0; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                String err = createError.get();
                if (err != null) g.drawString(f, err, x, y, theme().colors().danger());
            }
        });

        subscriptions.add(createSellMode.subscribe(b -> {
            sellBtn.setActive(b);
            buyBtn.setActive(!b);
        }));
        updateCreateModeButtons(sellBtn, buyBtn);
        return v;
    }

    private void updateCreateModeButtons(ButtonWidget sell, ButtonWidget buy) {
        sell.setActive(createSellMode.get());
        buy.setActive(!createSellMode.get());
    }

    private void setupItemSearchPopover(TextField anchor) {
        VirtualList<MarketNetwork.ItemCardData> listHolder = null;
        itemSearchPopover = Ui.popover(anchor, new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 0; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {}
        });
        subscriptions.add(createCommodityQuery.subscribe(q -> {
            if (q != null && q.length() >= 2) {
                List<ItemSearchResult> results = getItemSearchResults(q);
                if (!results.isEmpty()) {
                    List<Component> lines = new ArrayList<>();
                    for (ItemSearchResult r : results) lines.add(Component.literal(getItemDisplayName(r.itemId, r.displayName)));
                    UIComponent content = Ui.list(computed(() -> results.stream().map(r -> new UIComponent() {
                        @Override public int preferredWidth(Font f) { return 0; }
                        @Override public int preferredHeight(Font f) { return 16; }
                        @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                            CommodityIconComponent.drawIcon(g, r.itemId, x, y, 16, 16);
                            g.drawString(f, f.plainSubstrByWidth(getItemDisplayName(r.itemId, r.displayName), width - 20), x + 20, y + 4, uiRuntime().theme().colors().onSurface());
                        }
                        @Override public boolean mouseClicked(double mx, double my, int button) {
                            if (mx >= x && mx < x + width && my >= y && my < y + height) {
                                selectCommodity(r.itemId);
                                return true;
                            }
                            return false;
                        }
                    }).toList()), this::identityRow).itemHeight(16);
                    itemSearchPopover = Ui.popover(anchor, content);
                    if (!itemSearchShown && uiRuntime() != null) {
                        itemSearchHandle = itemSearchPopover.show(uiRuntime().overlays());
                        itemSearchShown = true;
                    }
                } else {
                    hideItemSearch();
                }
            } else {
                hideItemSearch();
            }
        }));
    }

    private UIComponent identityRow(UIComponent c) { return c; }

    private void hideItemSearch() {
        if (itemSearchShown && itemSearchHandle != null) {
            itemSearchHandle.close();
            itemSearchShown = false;
        }
    }

    private void selectCommodity(String id) {
        createCommodityQuery.set(id);
        hideItemSearch();
    }

    private void openCreateOrderWithPrefill(boolean isSell, String rawPrice, int qty) {
        createSellMode.set(isSell);
        createInfinite.set(false);
        String target = selectedItemId.get();
        if ((target == null || target.isEmpty()) && MarketClientStore.detail.get() != null) {
            target = MarketClientStore.detail.get().itemId;
        }
        if (target != null) createCommodityQuery.set(target);
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
        createError.set(null);
        String id = createCommodityQuery.get();
        if (id == null || id.isEmpty()) { createError.set("Item ID is required."); return; }
        String priceStr = createPrice.get().trim();
        if (priceStr.isEmpty()) { createError.set("Price is required."); return; }
        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) <= 0) { createError.set("Price must be greater than 0."); return; }
        } catch (NumberFormatException e) { createError.set("Price must be a valid number."); return; }

        boolean inf = !createSellMode.get() && createInfinite.get();
        String qtyStr = createQty.get().trim();
        int qty = 1;
        if (!inf) {
            try {
                qty = Integer.parseInt(qtyStr);
                if (qty <= 0) { createError.set("Quantity must be greater than 0."); return; }
            } catch (NumberFormatException ignored) { createError.set("Quantity must be a valid number."); return; }
        }
        if (createSellMode.get()) {
            int stock = getVaultStockForItem(id);
            if (stock > 0 && qty > stock) {
                createError.set(isFluidCommodity(id) ? "Not enough fluid in tank." : "Not enough in vault.");
                return;
            }
        } else if (!inf) {
            try {
                BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
                BigDecimal bal = new BigDecimal(MarketClientStore.balance.get());
                if (total.compareTo(bal) > 0) { createError.set("Insufficient funds. Balance: " + bal + "."); return; }
            } catch (NumberFormatException ignored) { createError.set("Cannot verify balance."); return; }
        }
        String commodityType = isFluidCommodity(id) ? "FLUID" : "ITEM";
        String dispName = getItemDisplayName(id, id);
        String totalStr = inf ? "∞ (Per unit: " + price.toPlainString() + ")"
                : price.multiply(BigDecimal.valueOf(qty)).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        pendingConfirmation.set(new PendingConfirmation(id, qty, price.toPlainString(), createSellMode.get(), inf,
                createSellMode.get() ? "Sell" : "Buy", dispName, totalStr, commodityType));
        showConfirmation();
    }

    private void showConfirmation() {
        PendingConfirmation p = pendingConfirmation.get();
        if (p == null) return;
        boolean fluid = isFluidCommodity(p.itemId);
        String qtyStr = p.isInfinite ? "∞" : EconomyFormatUtil.formatCommodityQuantity(p.quantity, fluid);
        String msg = p.action + " " + qtyStr + " of " + getItemDisplayName(p.itemId, p.itemName);
        OverlayHandle[] holder = new OverlayHandle[1];
        holder[0] = Dialog.confirm(uiRuntime().overlays(),
                Component.literal("CONFIRM TRANSACTION"),
                Component.literal(msg + "\nTotal: " + p.totalPrice),
                () -> {
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CreateOrderPacket(
                            p.itemId, p.quantity, p.priceStr, p.isSell, p.isInfinite, p.commodityType));
                    String id = p.itemId;
                    pendingConfirmation.set(null);
                    selectedItemId.set(id);
                    MarketClientStore.detail.set(null);
                    switchView(MarketView.DETAIL);
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id));
                },
                () -> pendingConfirmation.set(null));
    }

    // ── ORDERS ─────────────────────────────────────────────────────────────
    private UIComponent buildOrdersView() {
        VStack v = new VStack().gap(4);
        v.flex();
        v.addChild(Ui.tabs(ordersTab)
                .tab(OrdersTab.ACTIVE, "Active Orders")
                .tab(OrdersTab.HISTORY, "Trade History"));
        v.addChild(Ui.switcher(ordersTab)
                .when(OrdersTab.ACTIVE, this::buildActiveOrdersList)
                .when(OrdersTab.HISTORY, this::buildHistoryView));
        updateOrdersSubtabs();
        return v;
    }

    private void updateOrdersSubtabs() {
        // active tab indicator handled by Tabs selection
    }

    private UIComponent buildActiveOrdersList() {
        VStack v = new VStack().gap(4);
        v.flex();
        HStack bar = new HStack().gap(4);
        TextField search = Ui.textField(activeOrdersQuery);
        search.placeholder("Search item name or ID...");
        search.flex();
        bar.addChild(search);
        bar.addChild(filterSelect("Order", activeOrderFilter,
                Map.of(ActiveOrderFilter.ALL, "All", ActiveOrderFilter.SELL, "Sell",
                        ActiveOrderFilter.BUY, "Buy", ActiveOrderFilter.INFINITE, "Infinite")));
        bar.addChild(filterSelect("Product", activeOrderType,
                Map.of(CommodityTypeFilter.ALL, "All", CommodityTypeFilter.ITEMS, "Items", CommodityTypeFilter.FLUIDS, "Fluids")));
        bar.addChild(filterSelect("Sort", activeOrderSort,
                Map.of(ActiveOrderSort.NEWEST, "Newest", ActiveOrderSort.OLDEST, "Oldest",
                        ActiveOrderSort.PRICE_ASC, "Price ▲", ActiveOrderSort.PRICE_DESC, "Price ▼")));
        v.addChild(bar);
        v.addChild(Ui.list(visibleActiveOrders, this::buildActiveOrderRow).itemHeight(36));
        return v;
    }

    private UIComponent buildActiveOrderRow(MarketNetwork.ActiveOrderEntry e) {
        return new UIComponent() {
            {
                height(36);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 36; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y + 1, width, 34, 3, c.surfaceRaised());
                }
                CommodityIconComponent.drawIcon(g, e.itemId, x + 4, y + 10, 16, 16);
                g.drawString(f, e.isSell ? "SELL" : "BUY", x + 24, y + 4, e.isSell ? c.danger() : c.success());
                String name = f.plainSubstrByWidth(getItemDisplayName(e.itemId, e.displayName), Math.max(30, width - 120));
                g.drawString(f, name, x + 56, y + 4, c.onSurface());
                EconomyUiComponents.drawCoin(g, x + 24, y + 19);
                String qty = e.isInfinite ? "Qty: ∞"
                        : (isFluidCommodity(e.itemId) ? "Qty: " + formatFluidAmount(e.quantity) + " / " + formatFluidAmount(e.initialQuantity)
                        : "Qty: " + formatItemAmount(e.quantity) + " / " + formatItemAmount(e.initialQuantity));
                g.drawString(f, e.price + " | " + qty, x + 35, y + 18, c.primary());
                int editW = 30, cancelW = 38;
                int cancelX = x + width - cancelW - 4;
                int editX = cancelX - editW - 4;
                drawSmallButton(g, f, editX, y + 8, editW, 20, "Edit", c.primary(), mx, my);
                drawSmallButton(g, f, cancelX, y + 8, cancelW, 20, "Cancel", c.danger(), mx, my);
            }
            @Override public boolean mouseClicked(double mx, double my, int button) {
                int cancelW = 38, editW = 30;
                int cancelX = x + width - cancelW - 4;
                int editX = cancelX - editW - 4;
                if (mx >= editX && mx < cancelX && my >= y + 8 && my < y + 28) { openEditOrder(e); return true; }
                if (mx >= cancelX && mx < cancelX + cancelW && my >= y + 8 && my < y + 28) {
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId));
                    return true;
                }
                return false;
            }
        };
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
        Signal<String> qtySig = Signals.of(e.isInfinite ? "∞" : String.valueOf(e.quantity));
        Signal<String> priceSig = Signals.of(e.price);
        Signal<Boolean> infSig = Signals.of(e.isInfinite);
        Signal<String> errSig = Signals.of((String) null);

        TextField qtyField = Ui.textField(qtySig);
        qtyField.placeholder("Quantity");
        TextField priceField = Ui.textField(priceSig);
        priceField.placeholder("Price");
        ButtonWidget infBtn = Ui.button("∞", () -> infSig.set(!infSig.get())).ghost();

        VStack body = new VStack().gap(4);
        body.addChild(Ui.text(e.isSell ? "[SELL ORDER]" : "[BUY ORDER]").style(TextStyle.HEADING));
        body.addChild(Ui.text(getItemDisplayName(e.itemId, e.displayName)).style(TextStyle.LABEL));
        if (!e.isSell) body.addChild(infBtn);
        body.addChild(qtyField);
        body.addChild(priceField);
        body.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return errSig.get() != null ? 12 : 0; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                String err = errSig.get();
                if (err != null) g.drawString(f, err, x, y, theme().colors().danger());
            }
        });
        HStack actions = new HStack().gap(4);
        actions.addChild(Ui.button("Save", () -> {
            errSig.set(null);
            String pr = priceSig.get().trim();
            BigDecimal price;
            try {
                price = new BigDecimal(pr);
                if (price.compareTo(BigDecimal.ZERO) <= 0) { errSig.set("Price must be > 0"); return; }
            } catch (Exception ex) { errSig.set("Invalid price"); return; }
            int newQty = e.quantity > 0 ? e.quantity : 1;
            boolean inf = infSig.get();
            if (!inf) {
                try {
                    newQty = Integer.parseInt(qtySig.get().trim());
                    if (newQty <= 0) { errSig.set("Qty must be > 0"); return; }
                } catch (Exception ex) { errSig.set("Invalid quantity"); return; }
            }
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.EditOrderPacket(e.orderId, newQty, price.toPlainString(), inf));
            editingOrder.set(null);
            editingDialogHandle.close();
        }).primary());
        actions.addChild(Ui.button("Cancel", () -> {
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
        HStack bar = new HStack().gap(4);
        TextField search = Ui.textField(historyQuery);
        search.placeholder("Search item or player...");
        search.flex();
        bar.addChild(search);
        bar.addChild(filterSelect("Trade", historyFilter,
                Map.of(HistoryFilter.ALL, "All", HistoryFilter.SALES, "Sales", HistoryFilter.PURCHASES, "Purchases")));
        bar.addChild(filterSelect("Product", historyType,
                Map.of(CommodityTypeFilter.ALL, "All", CommodityTypeFilter.ITEMS, "Items", CommodityTypeFilter.FLUIDS, "Fluids")));
        bar.addChild(filterSelect("Sort", historySort,
                Map.of(HistorySort.NEWEST, "Newest", HistorySort.OLDEST, "Oldest", HistorySort.HIGHEST_TOTAL, "Highest $")));
        v.addChild(bar);
        v.addChild(Ui.list(visibleHistory, this::buildHistoryRow).itemHeight(28));
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
                g.drawString(f, e.wasSell ? "SELL" : "BUY", x + 24, y + 4, e.wasSell ? c.danger() : c.success());
                String date = DATE_FMT.format(new Date(e.timestamp));
                String name = f.plainSubstrByWidth(getItemDisplayName(e.itemId, e.displayName), Math.max(30, width - f.width(date) - 40));
                g.drawString(f, name, x + 56, y + 4, c.onSurface());
                g.drawString(f, date, x + width - f.width(date) - 4, y + 4, c.onSurfaceMuted());
                EconomyUiComponents.drawCoin(g, x + 24, y + 17);
                String pq = e.price + " x " + (isFluidCommodity(e.itemId) ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity));
                g.drawString(f, pq, x + 35, y + 16, c.primary());
                g.drawString(f, (e.wasSell ? "to " : "from ") + e.counterparty, x + width - f.width((e.wasSell ? "to " : "from ") + e.counterparty) - 4, y + 16, c.onSurfaceMuted());
            }
        };
    }

    // ── PORTFOLIO ──────────────────────────────────────────────────────────
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
                drawStatBox(g, f, x, y, boxW, height, "NET WORTH", formatCompact(nw), c.primary(), c);
                drawStatBox(g, f, x + boxW + 4, y, boxW, height, "LIQUID CASH", formatCompact(bal), c.success(), c);
                drawStatBox(g, f, x + 2 * (boxW + 4), y, boxW, height, "VAULT ASSETS", formatCompact(ass), c.primary(), c);
            }
        });
        v.addChild(new TrendChartComponent(portfolioChartSamples, portfolioChartOffset, true));
        v.addChild(Ui.list(computed(() -> new ArrayList<>(MarketClientStore.assetHoldings.get())), this::buildHoldingRow).itemHeight(26));
        return v;
    }

    private void drawStatBox(GuiGraphics g, Font f, int bx, int by, int bw, int bh, String label, String value, int valueColor, ColorScheme c) {
        UiRender.surface(g, bx, by, bw, bh, 3, c.surface(), c.borderSubtle(), false);
        g.drawString(f, label, bx + (bw - f.width(label)) / 2, by + 3, c.onSurfaceMuted());
        EconomyUiComponents.drawCoin(g, bx + 4, by + 13);
        g.drawString(f, value, bx + 14, by + 13, valueColor);
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
                g.drawString(f, f.plainSubstrByWidth(getItemDisplayName(h.itemId, h.displayName), width - 110), x + 24, y + 8, c.onSurface());
                String qty = isFluidCommodity(h.itemId) ? formatFluidAmount(h.quantity) : "x" + formatCompact(h.quantity);
                g.drawString(f, qty, x + width - 110, y + 8, c.onSurfaceMuted());
                String val = formatCompact(h.totalValue);
                EconomyUiComponents.drawCoin(g, x + width - f.width(val) - 14, y + 8);
                g.drawString(f, val, x + width - f.width(val) - 4, y + 8, c.primary());
            }
        };
    }

    // ── CONTAINERS ─────────────────────────────────────────────────────────
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
                drawStatBox(g, f, x, y, boxW, height, "VAULTS", formatCompact(vaults), c.primary(), c);
                drawStatBox(g, f, x + boxW + 4, y, boxW, height, "TANKS", formatCompact(tanks), c.primary(), c);
                drawStatBox(g, f, x + 2 * (boxW + 4), y, boxW, height, "ITEMS", formatCompact(items) + " items", c.success(), c);
                drawStatBox(g, f, x + 3 * (boxW + 4), y, boxW, height, "FLUID", formatCompact(fluid) + " mB", c.success(), c);
            }
        });
        v.addChild(Ui.list(computed(() -> new ArrayList<>(MarketClientStore.containerEntries.get())), this::buildContainerRow).itemHeight(40));
        return v;
    }

    private UIComponent buildContainerRow(MarketNetwork.VaultDetailEntry e) {
        return new UIComponent() {
            {
                height(40);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 40; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y + 1, width, 38, 3, c.surfaceRaised());
                }
                int idx = 1;
                for (MarketNetwork.VaultDetailEntry o : MarketClientStore.containerEntries.get()) {
                    if (o == e) break;
                    if (o.tank == e.tank) idx++;
                }
                g.drawString(f, (e.tank ? "Tank #" : "Vault #") + idx, x + 4, y + 3, c.primary());
                boolean full = e.usedSlots >= e.totalSlots;
                String badge = full ? "FULL" : "ACTIVE";
                int badgeW = f.width(badge) + 6;
                int badgeX = x + width - badgeW - 4;
                UiRender.pill(g, badgeX, y + 2, badgeW, 11, full ? c.dangerDeep() : c.successDeep(), full ? c.danger() : c.success());
                g.drawString(f, badge, badgeX + 3, y + 3, full ? c.danger() : c.success());
                String modeBadge = switch (e.mode) {
                    case 1 -> "INPUT ONLY"; case 2 -> "OUTPUT ONLY"; default -> "BOTH";
                };
                int modeW = f.width(modeBadge) + 6;
                int modeX = badgeX - modeW - 4;
                int modeBg = e.mode == 1 ? c.dangerDeep() : (e.mode == 2 ? c.successDeep() : c.primaryDim());
                int modeBorder = e.mode == 1 ? c.danger() : (e.mode == 2 ? c.success() : c.primary());
                UiRender.pill(g, modeX, y + 2, modeW, 11, modeBg, modeBorder);
                g.drawString(f, modeBadge, modeX + 3, y + 3, modeBorder);
                String loc = e.dimension.replace("minecraft:", "") + " (" + e.x + ", " + e.y + ", " + e.z + ")";
                g.drawString(f, loc, x + 4, y + 17, c.onSurfaceMuted());
                String cap = e.tank ? formatFluidAmount(e.usedSlots) + "/" + formatFluidAmount(e.totalSlots)
                        : formatCompact(e.usedSlots) + "/" + formatCompact(e.totalSlots) + " Slots";
                g.drawString(f, cap, x + width - f.width(cap) - 4, y + 17, c.onSurface());
            }
        };
    }

    // ── Chart component ────────────────────────────────────────────────────
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

        @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
            ColorScheme c = uiRuntime().theme().colors();
            UiRender.surface(g, x, y, width, height, 4, c.input(), c.borderSubtle(), false);
            List<ChartSample> pts = data.get();
            if (pts.size() < 2) {
                g.drawString(f, "Not enough data points yet", x + (width - f.width("Not enough data points yet")) / 2, y + height / 2 - 4, c.onSurfaceMuted());
                return;
            }
            int total = pts.size();
            int maxOff = Math.max(0, total - MAX_VISIBLE_CHART_STEPS);
            int off = Math.min(offset.get(), maxOff);
            int end = Math.min(total, Math.max(MAX_VISIBLE_CHART_STEPS, total - off));
            int start = Math.max(0, end - MAX_VISIBLE_CHART_STEPS);
            List<ChartSample> vis = pts.subList(start, end);
            double max = Double.MIN_VALUE, min = Double.MAX_VALUE;
            for (ChartSample s : vis) { if (s.value > max) max = s.value; if (s.value < min) min = s.value; }
            if (max == min) { max += 10; min = Math.max(0, min - 10); }
            double range = max - min;
            g.drawString(f, formatCompact(max), x + 3, y + 3, c.onSurfaceMuted());
            g.drawString(f, formatCompact(min), x + 3, y + height - 11, c.onSurfaceMuted());
            double cur = vis.get(vis.size() - 1).value;
            String curStr = formatCompact(cur);
            int badgeW = f.width(curStr) + 8, badgeH = 12;
            int badgeX = x + width - badgeW - 4;
            int rawY = y + height - 6 - (int) ((cur - min) / range * (height - 12));
            int badgeY = Math.max(y + 2, Math.min(y + height - badgeH - 2, rawY - 5));
            String liveStr = off == 0 ? "LIVE" : "▶ LIVE";
            int liveW = f.width(liveStr) + 6, liveX = badgeX - liveW - 4, liveY = y + 2, liveH = 11;
            boolean liveHov = mx >= liveX && mx < liveX + liveW && my >= liveY && my < liveY + liveH;
            UiRender.pill(g, liveX, liveY, liveW, liveH, off > 0 ? (liveHov ? c.successHover() : c.successDeep()) : (liveHov ? c.surfaceRaised() : c.primaryDim()), off > 0 ? c.success() : c.primary());
            g.drawString(f, liveStr, liveX + 3, liveY + 2, off > 0 ? 0xFFFFFFFF : c.primary());
            int chartLeft = x + 26, chartRight = liveX - 4;
            int n = vis.size();
            for (int i = 0; i < n; i++) {
                double val = vis.get(i).value;
                int x0 = chartLeft + i * (chartRight - chartLeft) / Math.max(1, n - 1);
                int y0 = y + height - 6 - (int) ((val - min) / range * (height - 12));
                if (i > 0) {
                    double prev = vis.get(i - 1).value;
                    int xPrev = chartLeft + (i - 1) * (chartRight - chartLeft) / Math.max(1, n - 1);
                    int yPrev = y + height - 6 - (int) ((prev - min) / range * (height - 12));
                    g.fill(xPrev, yPrev, xPrev + 1, yPrev + 1, c.primary());
                    g.fill(x0, y0, x0 + 1, y0 + 1, c.primary());
                }
                boolean nodeHov = mx >= x0 - 3 && mx <= x0 + 3 && my >= y0 - 3 && my <= y0 + 3;
                g.fill(x0 - 1, y0 - 1, x0 + 2, y0 + 2, nodeHov ? 0xFFFFFFFF : c.primary());
                if (nodeHov) {
                    List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                    for (String ln : vis.get(i).tooltip.split("\n")) lines.addAll(f.split(Component.literal(ln), 160));
                    g.renderTooltip(f, lines, mx, my);
                }
            }
            UiRender.pill(g, badgeX, badgeY, badgeW, badgeH, c.primaryDim(), c.primary());
            g.drawString(f, curStr, badgeX + 4, badgeY + 2, c.primary());
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            List<ChartSample> pts = data.get();
            if (pts.size() < 2) return false;
            int total = pts.size();
            int maxOff = Math.max(0, total - MAX_VISIBLE_CHART_STEPS);
            int off = Math.min(offset.get(), maxOff);
            int end = Math.min(total, Math.max(MAX_VISIBLE_CHART_STEPS, total - off));
            int start = Math.max(0, end - MAX_VISIBLE_CHART_STEPS);
            int badgeW = font.width(formatCompact(pts.get(Math.min(end, pts.size()) - 1).value)) + 8;
            int badgeX = x + width - badgeW - 4;
            int liveW = font.width("▶ LIVE") + 10, liveX = badgeX - liveW - 4, liveY = y + 2, liveH = 11;
            if (mx >= liveX && mx < liveX + liveW && my >= liveY && my < liveY + liveH) {
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
            int chartLeft = x + 26;
            int chartRight = x + width - (font.width(formatCompact(pts.get(pts.size() - 1).value)) + 12) - 8;
            if (mx >= chartLeft && mx < chartRight && my >= y && my < y + height) {
                int off = offset.get();
                if (delta < 0) offset.set(Math.min(maxOff, off + 1));
                else if (delta > 0) offset.set(Math.max(0, off - 1));
                return true;
            }
            return false;
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────────
    static String formatCompact(double val) { return EconomyFormatUtil.formatCompact(val); }
    static String formatCompact(BigDecimal val) { return EconomyFormatUtil.formatCompact(val); }
    static String formatCompact(long val) { return EconomyFormatUtil.formatCompact(val); }
    static String formatCompact(String str) { return EconomyFormatUtil.formatCompact(str); }
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

    private BigDecimal parsePrice(String s) {
        if (s == null || s.equals("--") || s.isEmpty()) return BigDecimal.valueOf(999999999);
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.valueOf(999999999); }
    }

    private static boolean isFluidCommodity(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        try {
            Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(itemId));
            return fluid != net.minecraft.world.level.material.Fluids.EMPTY && !fluid.getFluidType().isAir();
        } catch (RuntimeException ignored) { return false; }
    }

    static String getItemDisplayName(String itemId, String rawName) {
        if (itemId != null && !itemId.isEmpty()) {
            try {
                ResourceLocation rl = new ResourceLocation(itemId);
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != net.minecraft.world.item.Items.AIR) {
                    String name = new ItemStack(item).getHoverName().getString();
                    if (name != null && !name.isEmpty() && !name.startsWith("tagprefix.") && !name.startsWith("item.")) return name;
                }
                Fluid fluid = BuiltInRegistries.FLUID.get(rl);
                if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    String name = new FluidStack(fluid, 1000).getDisplayName().getString();
                    if (name != null && !name.isEmpty()) return name;
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

    private static boolean matchesCommodityTypeFilter(String itemId, String commodityType, CommodityTypeFilter mode) {
        if (mode == CommodityTypeFilter.ALL) return true;
        boolean fluid = commodityType != null ? commodityType.equalsIgnoreCase("FLUID") : isFluidCommodity(itemId);
        return CommodityUtil.matchesTypeFilter(fluid, mode.ordinal());
    }

    private List<MarketNetwork.ItemCardData> filterCards(String q, BrowseActivityFilter act, CommodityTypeFilter type, BrowseSort sort, List<MarketNetwork.ItemCardData> cards) {
        List<MarketNetwork.ItemCardData> f = new ArrayList<>();
        String query = q.toLowerCase().trim();
        for (MarketNetwork.ItemCardData c : cards) {
            if (!query.isEmpty() && !c.displayName.toLowerCase().contains(query) && !c.itemId.toLowerCase().contains(query)) continue;
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

    private List<HistoryEntry> filterHistory(String q, HistoryFilter filt, CommodityTypeFilter type, HistorySort sort, List<HistoryEntry> entries) {
        List<HistoryEntry> f = new ArrayList<>();
        if (entries == null) return f;
        String query = q.toLowerCase().trim();
        for (HistoryEntry e : entries) {
            if (e == null) continue;
            if (filt == HistoryFilter.SALES && !e.wasSell) continue;
            if (filt == HistoryFilter.PURCHASES && e.wasSell) continue;
            if (!matchesCommodityTypeFilter(e.itemId, null, type)) continue;
            if (!query.isEmpty() && !e.displayName.toLowerCase().contains(query) && !e.itemId.toLowerCase().contains(query) && !e.counterparty.toLowerCase().contains(query)) continue;
            f.add(e);
        }
        f.sort((a, b) -> {
            return switch (sort) {
                case NEWEST -> Long.compare(b.timestamp, a.timestamp);
                case OLDEST -> Long.compare(a.timestamp, b.timestamp);
                case HIGHEST_TOTAL -> new BigDecimal(b.price).multiply(BigDecimal.valueOf(b.quantity))
                        .compareTo(new BigDecimal(a.price).multiply(BigDecimal.valueOf(a.quantity)));
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
            if (!query.isEmpty() && !e.displayName.toLowerCase().contains(query) && !e.itemId.toLowerCase().contains(query)) continue;
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

    private List<ItemSearchResult> getItemSearchResults(String query) {
        if (query == null || query.length() < 2) return List.of();
        String q = query.toLowerCase(Locale.ROOT);
        List<ItemSearchResult> results = new ArrayList<>();
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            String name = new ItemStack(item).getHoverName().getString();
            String rlStr = rl.toString();
            if (name.toLowerCase(Locale.ROOT).contains(q) || rlStr.contains(q)) {
                results.add(new ItemSearchResult(rlStr, name));
                if (results.size() >= 50) break;
            }
        }
        for (ResourceLocation rl : BuiltInRegistries.FLUID.keySet()) {
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            if (!CommodityUtil.isCanonicalFluid(fluid)) continue;
            String name = new FluidStack(fluid, 1000).getDisplayName().getString();
            String rlStr = rl.toString();
            if (name.toLowerCase(Locale.ROOT).contains(q) || rlStr.contains(q)) {
                results.add(new ItemSearchResult(rlStr, name));
                if (results.size() >= 50) break;
            }
        }
        return results;
    }

    private static class ItemSearchResult {
        final String itemId;
        final String displayName;
        ItemSearchResult(String itemId, String displayName) { this.itemId = itemId; this.displayName = displayName; }
    }
}
