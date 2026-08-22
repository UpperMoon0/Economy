package com.nstut.forge.client;

import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.util.CommodityUtil;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.forge.network.HistoryEntry;
import com.nstut.forge.network.MarketNetwork;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.ClipStack;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiAnimationUtil;
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
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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
import java.util.function.Supplier;

public class MarketScreen extends EconomyUiContainerScreen<MarketMenu> {

    private static final int SCREEN_W = 356;
    private static final int SCREEN_H = 248;
    private static final int SIDEBAR_W = 84;
    private static final int NARROW_THRESHOLD = 336;
    private static final int MAX_VISIBLE_CHART_STEPS = 15;
    private static final int TOOLTIP_MAX_WIDTH = 140;
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
    private final Signal<List<ItemSearchResult>> searchResults = Signals.of(List.of());
    private Subscription itemSearchSubscription;
    private boolean initialDataRequested;
    private Component deferredTooltip;

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
                        + "\n" + Component.translatable("ui.economy.chart.tooltip.price", p.price).getString()
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
                            formatCompact(Double.parseDouble(p.netWorth))).getString()
                            + "\n" + Component.translatable("ui.economy.chart.tooltip.cash",
                            formatCompact(Double.parseDouble(p.balance))).getString()
                            + "  |  " + Component.translatable("ui.economy.chart.tooltip.assets",
                            formatCompact(Double.parseDouble(p.assets))).getString()));
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
    private final Computed<Boolean> browseEmpty = computed(() -> visibleBrowseCards.get().isEmpty());
    private final Computed<Boolean> activeEmpty = computed(() -> visibleActiveOrders.get().isEmpty());
    private final Computed<Boolean> historyEmpty = computed(() -> visibleHistory.get().isEmpty());
    private final Computed<Boolean> containersEmpty = computed(() -> visibleContainers.get().isEmpty());
    private final Computed<Boolean> holdingsEmpty = computed(() -> MarketClientStore.assetHoldings.get().isEmpty());
    private final Computed<Boolean> detailOrdersEmpty = computed(() -> visibleDetailOrders.get().isEmpty());
    private final Computed<Boolean> asksEmpty = computed(() -> visibleAsks.get().isEmpty());
    private final Computed<Boolean> bidsEmpty = computed(() -> visibleBids.get().isEmpty());

    private ButtonWidget browseBtn, newOrderBtn, ordersBtn, portfolioBtn, containersBtn;
    private ButtonWidget newOrderSellBtn, newOrderBuyBtn;
    private Popover itemSearchPopover;
    private OverlayHandle itemSearchHandle;
    private boolean itemSearchShown;

    private static String t(String key) { return Component.translatable(key).getString(); }

    static String fitText(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) return font.plainSubstrByWidth(ellipsis, maxWidth);
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }

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
        g.renderTooltip(font, lines, mouseX, mouseY);
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
        itemSearchShown = false;
        computedList.clear();
        subscriptions.clear();
        super.removed();
    }

    // ── Build root UI ─────────────────────────────────────────────────────
    @Override
    protected UIComponent buildUI() {
        for (Subscription s : subscriptions) s.close();
        subscriptions.clear();
        if (itemSearchSubscription != null) {
            itemSearchSubscription.close();
            itemSearchSubscription = null;
        }
        subscriptions.add(view.subscribe(v -> {
            updateNav(v);
            onViewEntered(v);
        }));
        subscriptions.add(createSellMode.subscribe(b -> {
            if (newOrderSellBtn != null) newOrderSellBtn.setActive(b);
            if (newOrderBuyBtn != null) newOrderBuyBtn.setActive(!b);
        }));
        return Ui.padding(8, Ui.responsive(ctx -> buildShell(ctx.width())));
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
        sidebar.addChild(Ui.text(Component.translatable("ui.economy.brand")).style(TextStyle.TITLE));
        sidebar.addChild(Ui.text(Component.translatable("ui.economy.market.subtitle")));
        sidebar.addChild(EconomyUiComponents.balancePill(MarketClientStore.balance));
        sidebar.addChild(Ui.divider());
        buildNav(sidebar);
        sidebar.addChild(Ui.spacer());
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
        top.addChild(Ui.text(Component.translatable("ui.economy.brand")).style(TextStyle.TITLE));
        top.addChild(Ui.text(Component.translatable("ui.economy.market.subtitle")).style(TextStyle.CAPTION));
        top.addChild(Ui.spacer().flex());
        top.addChild(buildThemeToggle());
        root.addChild(top);

        Select<MarketView> nav = Ui.select(view);
        nav.option(Component.translatable("ui.economy.nav.browse"), MarketView.BROWSE);
        nav.option(Component.translatable("ui.economy.nav.new_order"), MarketView.NEW_ORDER);
        nav.option(Component.translatable("ui.economy.nav.orders"), MarketView.ORDERS);
        nav.option(Component.translatable("ui.economy.nav.portfolio"), MarketView.PORTFOLIO);
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
                .when(MarketView.PORTFOLIO, this::buildPortfolioView)
                .when(MarketView.CONTAINERS, this::buildContainersView);
        switcher.flex();
        content.addChild(switcher);
        return content;
    }

    private void buildNav(VStack sidebar) {
        browseBtn = navButton(t("ui.economy.nav.browse"), () -> switchView(MarketView.BROWSE));
        newOrderBtn = navButton(t("ui.economy.nav.new_order"), () -> switchView(MarketView.NEW_ORDER));
        ordersBtn = navButton(t("ui.economy.nav.orders"), () -> switchView(MarketView.ORDERS));
        portfolioBtn = navButton(t("ui.economy.nav.portfolio"), () -> switchView(MarketView.PORTFOLIO));
        containersBtn = navButton(t("ui.economy.nav.containers"), () -> switchView(MarketView.CONTAINERS));
        sidebar.addChild(browseBtn);
        sidebar.addChild(newOrderBtn);
        sidebar.addChild(ordersBtn);
        sidebar.addChild(portfolioBtn);
        sidebar.addChild(containersBtn);
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
                String listingCount = Component.translatable("ui.economy.browse.live_listings", visibleBrowseCards.get().size()).getString();
                listingCount = fitText(f, listingCount, Math.max(0, width / 2));
                int listingX = x + width - f.width(listingCount);
                String heading = fitText(f, t("ui.economy.browse.heading"), Math.max(0, listingX - x - 6));
                UiRender.text(g, f, heading, x, y + 2, c.onSurface());
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
                        .when(BrowseLayout.GRID, () -> Ui.virtualGrid(visibleBrowseCards, this::buildCommodityCard)
                                .key(c -> c.itemId)
                                .minCellWidth(120)
                                .cellHeight(44)
                                .gap(4)
                                .flex())
                        .when(BrowseLayout.LIST, () -> Ui.list(visibleBrowseCards, this::buildCommodityRow)
                                .key(c -> c.itemId)
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
                UiRender.surface(g, x, y, width, height, 4, hovered ? c.surfaceRaised() : c.surface(), hovered ? c.primary() : c.borderSubtle(), false, c);
                CommodityIconComponent.drawIcon(g, card.itemId, x + 6, y + 12, 16, 16);
                int textX = x + 28;
                int textWidth = Math.max(1, width - 34);
                UiRender.text(g, f, fitText(f, getItemDisplayName(card.itemId, card.displayName), textWidth), textX, y + 4, c.onSurface());
                String countText = card.offerCount > 0
                        ? EconomyFormatUtil.formatCount(card.offerCount, "order", "orders")
                        : t("ui.economy.card.no_orders");
                if (card.globalPrice != null && !card.globalPrice.isEmpty() && !card.globalPrice.equals("--")) {
                    EconomyUiComponents.drawCoin(g, textX, y + 16);
                    int priceX = textX + 9;
                    String change = formatPriceChange(card.priceChangePercent);
                    int changeWidth = Math.min(f.width(change), Math.max(0, textWidth / 2));
                    change = fitText(f, change, changeWidth);
                    String price = fitText(f, formatCompact(parsePrice(card.globalPrice)),
                            Math.max(0, textX + textWidth - priceX - f.width(change) - 5));
                    UiRender.text(g, f, price, priceX, y + 16, c.primary());
                    UiRender.text(g, f, change, priceX + f.width(price) + 5, y + 16, changeColor(card.priceChangePercent));
                } else {
                    UiRender.text(g, f, "--", textX, y + 16, c.onSurfaceMuted());
                }
                UiRender.text(g, f, fitText(f, countText, textWidth), textX, y + 28, c.onSurfaceMuted());
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
                    double change = detailChangePercent(d);
                    String changeStr = formatPriceChange(change);
                    boolean fluid = isFluidCommodity(d.itemId);
                    String stock = fluid ? Component.translatable("ui.economy.detail.in_tank", formatFluidAmountDetailed(d.vaultCount)).getString()
                            : Component.translatable("ui.economy.detail.in_vault", formatItemAmount(d.vaultCount)).getString();
                    stock = fitText(f, stock, Math.max(1, width / 2));
                    int stockX = x + width - f.width(stock);
                    int leftWidth = Math.max(0, stockX - x - 6);
                    changeStr = fitText(f, changeStr, Math.max(0, leftWidth / 3));
                    String title = fitText(f, getItemDisplayName(d.itemId, d.displayName),
                            Math.max(0, leftWidth - f.width(changeStr) - 8));
                    int changeGap = title.isEmpty() || changeStr.isEmpty() ? 0 : 8;
                    UiRender.text(g, f, title, x, y + 4, c.onSurface());
                    UiRender.text(g, f, changeStr, x + f.width(title) + changeGap, y + 4, changeColor(change));
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
                String line = e.price + " x " + (e.isInfinite ? "∞" : (isFluidCommodity(MarketClientStore.detail.get() == null ? "" : MarketClientStore.detail.get().itemId)
                        ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity)));
                line = fitText(f, line, Math.max(0, width - 6));
                int lineX = x + width - f.width(line) - 3;
                String sellerName = e.isServerOrder
                        ? Component.translatable("ui.economy.orders.server_seller", e.sellerName).getString()
                        : e.sellerName;
                String seller = fitText(f, sellerName,
                        Math.max(0, lineX - (x + 3) - 6));
                UiRender.text(g, f, seller, x + 3, y + 4, c.onSurface());
                UiRender.text(g, f, line, lineX, y + 4, clr);
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
                String line = e.price + " x " + (e.isInfinite ? "∞" : (isFluidCommodity(detailItemId)
                        ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity)));
                line = fitText(f, line, Math.max(0, width - 6));
                int lineX = x + width - f.width(line) - 3;
                String side = fitText(f, o.isSell() ? t("ui.economy.opt.sell") : t("ui.economy.opt.buy"),
                        Math.max(0, lineX - (x + 3) - 6));
                UiRender.text(g, f, side, x + 3, y + 4, o.isSell() ? c.danger() : c.success());
                UiRender.text(g, f, line, lineX, y + 4, c.onSurface());
            }
        };
        info.flex();
        row.addChild(info);
        ButtonWidget edit = Ui.button(t("ui.economy.action.edit"), () -> openEditOrder(e)).ghost().small();
        edit.height(14);
        ButtonWidget cancel = Ui.button(t("ui.economy.action.cancel"),
                () -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId))).danger().small();
        cancel.height(14);
        row.addChild(edit);
        row.addChild(cancel);
        return row;
    }

    // ── NEW ORDER ──────────────────────────────────────────────────────────
    private UIComponent buildNewOrderView() {
        VStack v = new VStack().gap(4);
        v.addChild(Ui.button(Component.translatable("ui.economy.action.back"), () -> switchView(MarketView.BROWSE)).ghost());
        v.addChild(Ui.divider());

        HStack modeRow = new HStack().gap(4);
        newOrderSellBtn = Ui.button(Component.translatable("ui.economy.action.sell_order"), () -> { createSellMode.set(true); }).danger();
        newOrderBuyBtn = Ui.button(Component.translatable("ui.economy.action.buy_order"), () -> { createSellMode.set(false); }).success();
        newOrderSellBtn.flex(); newOrderBuyBtn.flex();
        modeRow.addChild(newOrderSellBtn); modeRow.addChild(newOrderBuyBtn);
        v.addChild(modeRow);

        TextField idField = Ui.textField(createCommodityQuery);
        idField.placeholder(t("ui.economy.new_order.search_placeholder"));
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
                String msg = fluid ? Component.translatable("ui.economy.new_order.tank_stock", formatFluidAmountDetailed(stock)).getString()
                        : Component.translatable("ui.economy.new_order.vault_stock", formatItemAmount(stock)).getString();
                UiRender.text(g, f, msg, x + 2, y + 3, stock > 0 ? theme().colors().success() : theme().colors().danger());
            }
        });

        HStack qtyRow = new HStack().gap(4);
        TextField qtyField = Ui.textField(createQty);
        qtyField.placeholder(t("ui.economy.new_order.qty_placeholder"));
        qtyField.flex();
        qtyRow.addChild(qtyField);
        qtyRow.addChild(Ui.button(t("ui.economy.action.max"), () -> {
            String id = createCommodityQuery.get();
            int stock = getVaultStockForItem(id);
            if (stock > 0) createQty.set(String.valueOf(stock));
        }).ghost());
        qtyRow.addChild(Ui.button(t("ui.economy.action.infinite"), () -> createInfinite.set(!createInfinite.get())).ghost());
        v.addChild(qtyRow);

        TextField priceField = Ui.textField(createPrice);
        priceField.placeholder(t("ui.economy.new_order.price_placeholder"));
        v.addChild(priceField);

        ButtonWidget submit = Ui.button(Component.translatable("ui.economy.action.submit"), this::submitOffer).primary();
        v.addChild(submit);

        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return createError.get() != null ? 12 : 0; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                String err = createError.get();
                if (err != null) UiRender.text(g, f, err, x, y, theme().colors().danger());
            }
        });

        updateCreateModeButtons(newOrderSellBtn, newOrderBuyBtn);
        return v;
    }

    private void updateCreateModeButtons(ButtonWidget sell, ButtonWidget buy) {
        sell.setActive(createSellMode.get());
        buy.setActive(!createSellMode.get());
    }

    private void setupItemSearchPopover(TextField anchor) {
        if (itemSearchSubscription != null) {
            itemSearchSubscription.close();
            itemSearchSubscription = null;
        }
        hideItemSearch();
        VirtualList<ItemSearchResult> list = Ui.list(searchResults, this::buildSearchResultRow).itemHeight(16);
        itemSearchPopover = Ui.popover(anchor, list);
        itemSearchSubscription = createCommodityQuery.subscribe(q -> {
            if (q != null && q.length() >= 2) {
                List<ItemSearchResult> results = getItemSearchResults(q);
                searchResults.set(results);
                if (results.isEmpty()) {
                    hideItemSearch();
                } else if (!itemSearchShown && uiRuntime() != null) {
                    itemSearchHandle = itemSearchPopover.show(uiRuntime().overlays());
                    itemSearchShown = true;
                }
            } else {
                searchResults.set(List.of());
                hideItemSearch();
            }
        });
    }

    private UIComponent buildSearchResultRow(ItemSearchResult r) {
        return new UIComponent() {
            {
                height(16);
            }
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 16; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                ColorScheme c = uiRuntime().theme().colors();
                if (mx >= x && mx < x + width && my >= y && my < y + height) {
                    UiRender.roundedRect(g, x, y, width, 16, 2, c.surfaceRaised());
                }
                CommodityIconComponent.drawIcon(g, r.itemId, x, y, 16, 16);
                UiRender.text(g, f, f.plainSubstrByWidth(getItemDisplayName(r.itemId, r.displayName), width - 20),
                        x + 20, y + 4, c.onSurface());
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
        if (id == null || id.isEmpty()) { createError.set(t("ui.economy.error.item_required")); return; }
        String priceStr = createPrice.get().trim();
        if (priceStr.isEmpty()) { createError.set(t("ui.economy.error.price_required")); return; }
        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) <= 0) { createError.set(t("ui.economy.error.price_positive")); return; }
        } catch (NumberFormatException e) { createError.set(t("ui.economy.error.price_number")); return; }

        boolean inf = !createSellMode.get() && createInfinite.get();
        String qtyStr = createQty.get().trim();
        int qty = 1;
        if (!inf) {
            try {
                qty = Integer.parseInt(qtyStr);
                if (qty <= 0) { createError.set(t("ui.economy.error.qty_positive")); return; }
            } catch (NumberFormatException ignored) { createError.set(t("ui.economy.error.qty_number")); return; }
        }
        if (createSellMode.get()) {
            int stock = getVaultStockForItem(id);
            if (stock > 0 && qty > stock) {
                createError.set(isFluidCommodity(id) ? t("ui.economy.error.insufficient_fluid") : t("ui.economy.error.insufficient_vault"));
                return;
            }
        } else if (!inf) {
            try {
                BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
                BigDecimal bal = new BigDecimal(MarketClientStore.balance.get());
                if (total.compareTo(bal) > 0) { createError.set(Component.translatable("ui.economy.error.insufficient_funds", bal).getString()); return; }
            } catch (NumberFormatException ignored) { createError.set(t("ui.economy.error.balance_verify")); return; }
        }
        String commodityType = isFluidCommodity(id) ? "FLUID" : "ITEM";
        String dispName = getItemDisplayName(id, id);
        String totalStr = inf ? "∞ (Per unit: " + price.toPlainString() + ")"
                : price.multiply(BigDecimal.valueOf(qty)).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        pendingConfirmation.set(new PendingConfirmation(id, qty, price.toPlainString(), createSellMode.get(), inf,
                 createSellMode.get() ? t("ui.economy.opt.sell") : t("ui.economy.opt.buy"), dispName, totalStr, commodityType));
        showConfirmation();
    }

    private void showConfirmation() {
        PendingConfirmation p = pendingConfirmation.get();
        if (p == null) return;
        boolean fluid = isFluidCommodity(p.itemId);
        String qtyStr = p.isInfinite ? "∞" : EconomyFormatUtil.formatCommodityQuantity(p.quantity, fluid);
        String msg = Component.translatable("ui.economy.confirm.message", p.action, qtyStr, getItemDisplayName(p.itemId, p.itemName)).getString();
        OverlayHandle[] holder = new OverlayHandle[1];
        holder[0] = Dialog.confirm(uiRuntime().overlays(),
                Component.translatable("ui.economy.confirm.title"),
                Component.literal(msg + "\n" + Component.translatable("ui.economy.confirm.total", p.totalPrice).getString()),
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
                EconomyUiComponents.drawCoin(g, x + 24, y + 18);
                String qty = e.isInfinite
                        ? t("ui.economy.orders.quantity_infinite")
                        : Component.translatable("ui.economy.orders.quantity_progress",
                                isFluidCommodity(e.itemId) ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity),
                                isFluidCommodity(e.itemId) ? formatFluidAmount(e.initialQuantity) : formatItemAmount(e.initialQuantity)).getString();
                int metadataX = x + 35;
                int metadataWidth = Math.max(0, x + width - metadataX - 4);
                String price = fitText(f, e.price, metadataWidth);
                UiRender.text(g, f, price, metadataX, y + 17, c.primary());
                String quantity = fitText(f, " • " + qty, Math.max(0, metadataWidth - f.width(price)));
                UiRender.text(g, f, quantity, metadataX + f.width(price), y + 17, c.onSurfaceMuted());
            }
        };
        info.flex();
        row.addChild(info);
        ButtonWidget edit = Ui.button(t("ui.economy.action.edit"), () -> openEditOrder(e)).ghost().small();
        edit.width(40).height(18);
        ButtonWidget cancel = Ui.button(t("ui.economy.action.cancel"),
                () -> MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId))).danger().small();
        cancel.width(48).height(18);
        row.addChild(edit);
        row.addChild(cancel);
        return row;
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
        qtyField.placeholder(t("ui.economy.new_order.qty_field"));
        TextField priceField = Ui.textField(priceSig);
        priceField.placeholder(t("ui.economy.new_order.price_field"));
        ButtonWidget infBtn = Ui.button(t("ui.economy.action.infinite"), () -> infSig.set(!infSig.get())).ghost();

        VStack body = new VStack().gap(4);
        body.addChild(Ui.text(Component.translatable(e.isSell ? "ui.economy.new_order.title_sell" : "ui.economy.new_order.title_buy")).style(TextStyle.HEADING));
        body.addChild(Ui.text(getItemDisplayName(e.itemId, e.displayName)).style(TextStyle.LABEL));
        if (!e.isSell) body.addChild(infBtn);
        body.addChild(qtyField);
        body.addChild(priceField);
        body.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return errSig.get() != null ? 12 : 0; }
            @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
                String err = errSig.get();
                if (err != null) UiRender.text(g, f, err, x, y, theme().colors().danger());
            }
        });
        HStack actions = new HStack().gap(4);
        actions.addChild(Ui.button(t("ui.economy.action.save"), () -> {
            errSig.set(null);
            String pr = priceSig.get().trim();
            BigDecimal price;
            try {
                price = new BigDecimal(pr);
                if (price.compareTo(BigDecimal.ZERO) <= 0) { errSig.set(t("ui.economy.error.price_zero")); return; }
            } catch (Exception ex) { errSig.set(t("ui.economy.error.price_invalid")); return; }
            int newQty = e.quantity > 0 ? e.quantity : 1;
            boolean inf = infSig.get();
            if (!inf) {
                try {
                    newQty = Integer.parseInt(qtySig.get().trim());
                    if (newQty <= 0) { errSig.set(t("ui.economy.error.qty_zero")); return; }
                } catch (Exception ex) { errSig.set(t("ui.economy.error.qty_invalid")); return; }
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
                drawStatBox(g, f, x, y, boxW, height, t("ui.economy.portfolio.net_worth"), formatCompact(nw), c.primary(), c);
                drawStatBox(g, f, x + boxW + 4, y, boxW, height, t("ui.economy.portfolio.liquid_cash"), formatCompact(bal), c.success(), c);
                drawStatBox(g, f, x + 2 * (boxW + 4), y, boxW, height, t("ui.economy.portfolio.vault_assets"), formatCompact(ass), c.primary(), c);
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
                String val = fitText(f, formatCompact(h.totalValue), Math.max(0, width / 3));
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
                drawStatBox(g, f, x, y, boxW, height, t("ui.economy.containers.vaults"), formatCompact(vaults), c.primary(), c);
                drawStatBox(g, f, x + boxW + 4, y, boxW, height, t("ui.economy.containers.tanks"), formatCompact(tanks), c.primary(), c);
                drawStatBox(g, f, x + 2 * (boxW + 4), y, boxW, height, t("ui.economy.containers.items"), Component.translatable("ui.economy.containers.items_unit", formatCompact(items)).getString(), c.success(), c);
                drawStatBox(g, f, x + 3 * (boxW + 4), y, boxW, height, t("ui.economy.containers.fluid"), Component.translatable("ui.economy.containers.fluid_unit", formatCompact(fluid)).getString(), c.success(), c);
            }
        });
        UIComponent containersList = Ui.switcher(containersEmpty)
                .when(false, () -> Ui.list(visibleContainers, this::buildContainerRow)
                        .key(e -> e.dimension + ":" + e.x + "," + e.y + "," + e.z)
                        .itemHeight(40)
                        .flex())
                .when(true, () -> Ui.emptyState(Component.translatable("ui.economy.empty.no_containers")));
        containersList.flex();
        v.addChild(containersList);
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
                boolean full = e.usedSlots >= e.totalSlots;
                String badge = full ? t("ui.economy.container.full") : t("ui.economy.container.active");
                int statusWidth = EconomyUiComponents.badgeWidth(f, badge);
                int statusX = x + width - 4 - statusWidth;
                boolean statusHovered = mx >= statusX && mx < statusX + statusWidth
                        && my >= y + 2 && my < y + 2 + EconomyUiComponents.BADGE_HEIGHT;
                EconomyUiComponents.drawBadge(g, f, badge, x + width - 4, y + 2,
                        full ? Badge.Variant.DANGER : Badge.Variant.SUCCESS, statusHovered, c);
                String modeBadge = switch (e.mode) {
                    case 1 -> t("ui.economy.container.mode_input"); case 2 -> t("ui.economy.container.mode_output"); default -> t("ui.economy.container.mode_both");
                };
                Badge.Variant modeVariant = e.mode == 1 ? Badge.Variant.WARNING : Badge.Variant.PRIMARY;
                int modeWidth = EconomyUiComponents.badgeWidth(f, modeBadge);
                int modeX = statusX - 4 - modeWidth;
                boolean modeHovered = mx >= modeX && mx < modeX + modeWidth
                        && my >= y + 2 && my < y + 2 + EconomyUiComponents.BADGE_HEIGHT;
                EconomyUiComponents.drawBadge(g, f, modeBadge, statusX - 4, y + 2,
                        modeVariant, modeHovered, c);
                String title = fitText(f,
                        (e.tank ? t("ui.economy.container.tank_prefix") : t("ui.economy.container.vault_prefix")) + idx,
                        Math.max(0, modeX - (x + 4) - 6));
                UiRender.text(g, f, title, x + 4, y + 3, c.primary());
                String loc = e.dimension.replace("minecraft:", "") + " (" + e.x + ", " + e.y + ", " + e.z + ")";
                String cap = e.tank ? formatFluidAmount(e.usedSlots) + "/" + formatFluidAmount(e.totalSlots)
                        : formatCompact(e.usedSlots) + "/" + formatCompact(e.totalSlots) + " " + t("ui.economy.containers.slots");
                cap = fitText(f, cap, Math.max(0, width / 3));
                int capX = x + width - f.width(cap) - 4;
                UiRender.text(g, f, cap, capX, y + 17, c.onSurface());
                loc = fitText(f, loc, Math.max(0, capX - (x + 4) - 6));
                UiRender.text(g, f, loc, x + 4, y + 17, c.onSurfaceMuted());
                if (statusHovered) {
                    deferredTooltip = Component.translatable(full
                            ? "ui.economy.container.tooltip.full"
                            : "ui.economy.container.tooltip.active");
                } else if (modeHovered) {
                    String tooltipKey = switch (e.mode) {
                        case 1 -> "ui.economy.container.tooltip.mode_input";
                        case 2 -> "ui.economy.container.tooltip.mode_output";
                        default -> "ui.economy.container.tooltip.mode_both";
                    };
                    deferredTooltip = Component.translatable(tooltipKey);
                }
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
                double rawRange = max - min;
                double padding = rawRange <= 0.0
                        ? Math.max(1.0, Math.abs(max) * 0.05)
                        : Math.max(1.0, rawRange * 0.08);
                graphMin = min >= 0 ? Math.max(0.0, min - padding) : min - padding;
                double graphMax = max + padding;
                graphRange = Math.max(1.0, graphMax - graphMin);

                currentText = formatCompact(points.get(points.size() - 1).value);
                badgeW = f.width(currentText) + 8;
                badgeH = 12;
                badgeX = x + width - badgeW - 4;
                liveText = off == 0 ? t("ui.economy.chart.live") : t("ui.economy.chart.live_scroll");
                liveW = f.width(liveText) + 8;
                liveH = 12;
                liveX = badgeX - liveW - 4;
                liveY = y + 2;
                int axisWidth = Math.max(f.width(formatCompact(max)), f.width(formatCompact(min))) + 8;
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
            UiRender.text(g, f, formatCompact(layout.max), x + 3, y + 3, c.onSurfaceMuted());
            UiRender.text(g, f, formatCompact(layout.min), x + 3, y + height - 11, c.onSurfaceMuted());
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
            EconomyUiComponents.drawBadge(g, f, layout.currentText,
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
