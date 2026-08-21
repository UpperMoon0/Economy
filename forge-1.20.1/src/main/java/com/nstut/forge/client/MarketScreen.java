package com.nstut.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.economy.blocks.MarketMenu;
import com.nstut.openui.api.*;
import com.nstut.economy.util.CommodityUtil;
import com.nstut.forge.network.HistoryEntry;
import com.nstut.forge.network.MarketNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final int SCREEN_W = 356;
    private static final int SCREEN_H = 248;

    private static final int BG_DARK = UiTheme.SHELL;
    private static final int PANEL = UiTheme.SURFACE;
    private static final int PANEL_BORDER = UiTheme.BORDER_SUBTLE;
    private static final int CARD_BG = UiTheme.SURFACE_RAISED;
    private static final int CARD_HOVER = UiTheme.SURFACE_HOVER;
    private static final int ACCENT = UiTheme.ACCENT;
    private static final int ACCENT_DIM = UiTheme.ACCENT_DIM;
    private static final int TEXT_PRIMARY = UiTheme.TEXT_PRIMARY;
    private static final int TEXT_MUTED = UiTheme.TEXT_MUTED;
    private static final int GREEN = UiTheme.SUCCESS;
    private static final int RED = UiTheme.DANGER;
    private static final int CHART_BG = UiTheme.INPUT;
    private static final int CHART_LINE = UiTheme.ACCENT;
    private static final int SIDEBAR_W = 84;

    private static void renderSmallCoin(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 0.5f);
        g.renderItem(COIN_ICON, 0, 0);
        g.pose().popPose();
    }

    private static void drawStatBox(GuiGraphics g, Font font, int x, int y, int width, int height,
                                    String label, String value, int valueColor) {
        UiRender.surface(g, x, y, width, height, UiTheme.RADIUS_SM,
                CARD_BG, PANEL_BORDER, false);
        g.drawString(font, label, x + (width - font.width(label)) / 2, y + 3, TEXT_MUTED);
        g.drawString(font, value, x + (width - font.width(value)) / 2, y + 13, valueColor);
    }

    private static void drawCoinTextPingPongMarquee(GuiGraphics g, Font font, String text,
                                                     int x, int y, int viewportWidth, int color) {
        if (text == null || text.isEmpty() || viewportWidth <= 0) return;

        final int coinAndGapWidth = 10;
        int contentWidth = coinAndGapWidth + font.width(text);
        int offset = UiAnimationUtil.pingPongOffset(
                contentWidth, viewportWidth, net.minecraft.Util.getMillis());

        g.enableScissor(x, y, x + viewportWidth, y + font.lineHeight);
        renderSmallCoin(g, x - offset, y + 1);
        g.drawString(font, text, x + coinAndGapWidth - offset, y, color);
        g.disableScissor();
    }

    private static void renderCommodityIcon(GuiGraphics g, String commodityId, int x, int y) {
        Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(commodityId));
        if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && !fluid.getFluidType().isAir()) {
            TextureAtlasSprite sprite = fluidSpriteCache.computeIfAbsent(commodityId, id -> {
                ResourceLocation still = IClientFluidTypeExtensions.of(fluid).getStillTexture();
                return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
            });
            int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
            float r = ((tint >> 16) & 0xFF) / 255f;
            float green = ((tint >> 8) & 0xFF) / 255f;
            float b = (tint & 0xFF) / 255f;
            float a = ((tint >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1f;
            RenderSystem.setShaderColor(r, green, b, a);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            g.blit(x, y, 0, 16, 16, sprite);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }

        ItemStack icon = itemIconCache.computeIfAbsent(commodityId, id -> {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            return new ItemStack(item);
        });
        g.renderItem(icon, x, y);
    }

    public static String formatCompact(double val) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(val); }
    public static String formatCompact(BigDecimal val) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(val); }
    public static String formatCompact(long val) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(val); }
    public static String formatCompact(String str) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(str); }
    public static String formatPriceChange(double percent) { return com.nstut.economy.util.EconomyFormatUtil.formatPriceChange(percent); }
    public static int getPriceChangeColor(double percent) { return com.nstut.economy.util.EconomyFormatUtil.getPriceChangeColor(percent); }
    private static String formatFluidAmount(int amount) { return com.nstut.economy.util.EconomyFormatUtil.formatFluidAmount(amount); }
    private static String formatFluidAmountDetailed(int amount) { return com.nstut.economy.util.EconomyFormatUtil.formatFluidAmountDetailed(amount); }
    private static String formatItemAmount(int amount) { return com.nstut.economy.util.EconomyFormatUtil.formatItemAmount(amount); }

    private static List<MarketNetwork.ItemCardData> cachedCards = new ArrayList<>();
    private static String cachedBalance = "0";
    private static int cachedVaultCount;
    private static MarketNetwork.SyncItemDetailPacket cachedDetail;
    private static List<HistoryEntry> cachedHistory = new ArrayList<>();
    private static final Map<String, ItemStack> itemIconCache = new HashMap<>();
    private static final Map<String, TextureAtlasSprite> fluidSpriteCache = new HashMap<>();
    private static final ItemStack COIN_ICON = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(com.nstut.Economy.MOD_ID, "coin")));

    private UIComponent root;
    private TextWidget vaultWidget;
    private UIComponent balanceWidget;
    private ButtonWidget browseBtn, containersBtn, portfolioBtn, newOrderBtn, orderHistoryBtn;
    private EditBoxWrapper searchField;
    private ScrollList cardList;
    private ScrollGrid cardGrid;
    private ScrollList askList, bidList;
    private UIComponent browser, detail, createOffer;
    private EditBoxWrapper qtyField, priceField, itemIdField;

    private String selectedItemId = null;
    private int createOrderSourceMode = 0;
    private boolean createSellMode = false;
    private UIComponent historyView;
    private UIComponent containersView;
    private UIComponent portfolioView;

    private static String savedSearchQuery = "";
    private static String savedHistorySearchQuery = "";
    private static String savedActiveOrdersSearchQuery = "";
    private static int savedBrowseFilterMode = 0; // 0 = All, 1 = Active Only
    private static int savedBrowseCommodityTypeMode = 0; // 0 = All, 1 = Items, 2 = Fluids
    private static int savedBrowseSortMode = 0;   // 0 = Price ▲, 1 = Price ▼, 2 = Name A-Z, 3 = Most Active
    private static boolean savedBrowseGridView = MarketClientPreferences.isBrowseGridView();
    private static int savedHistoryFilterMode = 0; // 0 = All Trades, 1 = Sales Only, 2 = Purchases Only
    private static int savedHistoryCommodityTypeMode = 0; // 0 = All, 1 = Items, 2 = Fluids
    private static int savedHistorySortMode = 0;   // 0 = Newest, 1 = Oldest, 2 = Highest Total
    private static int savedActiveOrdersFilterMode = 0; // 0 = All Orders, 1 = Sell Only, 2 = Buy Only, 3 = Infinite Only
    private static int savedActiveOrdersCommodityTypeMode = 0; // 0 = All, 1 = Items, 2 = Fluids
    private static int savedActiveOrdersSortMode = 0;   // 0 = Newest, 1 = Oldest, 2 = Price ▲, 3 = Price ▼
    private static int savedViewMode = 0;

    private String searchQuery = savedSearchQuery;
    private String historySearchQuery = savedHistorySearchQuery;
    private String activeOrdersSearchQuery = savedActiveOrdersSearchQuery;
    private int viewMode = savedViewMode;
    private static int savedOrdersSubTab = 0; // 0 = Active Orders, 1 = History
    private int ordersSubTab = savedOrdersSubTab;
    private UIComponent ordersView;
    private UIComponent activeOrdersContainer;
    private ButtonWidget ordersActiveTabBtn;
    private ButtonWidget ordersHistoryTabBtn;

    private boolean isCreateInfinite = false;
    private ButtonWidget infiniteBuyBtn;

    private MarketNetwork.ActiveOrderEntry editingOrder = null;
    private EditBoxWrapper editQtyField = null;
    private EditBoxWrapper editPriceField = null;
    private boolean editIsInfinite = false;
    private String editErrorMsg = null;

    private static final int MAX_VISIBLE_CHART_STEPS = 15;
    private static final java.text.SimpleDateFormat CHART_TIME_FMT = new java.text.SimpleDateFormat("MM/dd HH:mm:ss");
    private int detailChartOffset = 0;
    private int portfolioChartOffset = 0;
    private int detailLiveBtnX, detailLiveBtnY, detailLiveBtnW, detailLiveBtnH;
    private int portfolioLiveBtnX, portfolioLiveBtnY, portfolioLiveBtnW, portfolioLiveBtnH;

    private static List<MarketNetwork.VaultDetailEntry> cachedContainerEntries = new ArrayList<>();
    private static List<MarketNetwork.PortfolioPointData> cachedPortfolioPoints = new ArrayList<>();
    private static List<MarketNetwork.AssetHoldingData> cachedAssetHoldings = new ArrayList<>();
    private static List<MarketNetwork.ActiveOrderEntry> cachedActiveOrders = new ArrayList<>();

    public static void handleSyncVaultInfo(MarketNetwork.SyncVaultInfoPacket pkt) {
        cachedContainerEntries = pkt.entries;
    }

    public static void handleSyncPortfolio(MarketNetwork.SyncPortfolioPacket pkt) {
        cachedPortfolioPoints = pkt.points;
        cachedAssetHoldings = pkt.holdings;
    }

    public static void handleSyncActiveOrders(MarketNetwork.SyncActiveOrdersPacket pkt) {
        cachedActiveOrders = pkt.entries;
    }

    private EditBoxWrapper historySearchField;
    private EditBoxWrapper activeOrdersSearchField;

    private int browseFilterMode = savedBrowseFilterMode;
    private int browseCommodityTypeMode = savedBrowseCommodityTypeMode;
    private int browseSortMode = savedBrowseSortMode;
    private boolean browseGridView = savedBrowseGridView;
    private ButtonWidget browseFilterBtn, browseCommodityTypeBtn, browseSortBtn, browseLayoutBtn;

    private int historyFilterMode = savedHistoryFilterMode;
    private int historyCommodityTypeMode = savedHistoryCommodityTypeMode;
    private int historySortMode = savedHistorySortMode;
    private ButtonWidget historyFilterBtn, historyCommodityTypeBtn, historySortBtn;

    private int activeOrdersFilterMode = savedActiveOrdersFilterMode;
    private int activeOrdersCommodityTypeMode = savedActiveOrdersCommodityTypeMode;
    private int activeOrdersSortMode = savedActiveOrdersSortMode;
    private ButtonWidget activeOrdersFilterBtn, activeOrdersCommodityTypeBtn, activeOrdersSortBtn;

    private String getBrowseFilterLabel() {
        return browseFilterMode == 1 ? "Active" : "All";
    }

    private String getBrowseSortLabel() {
        switch (browseSortMode) {
            case 1: return "Price \u25BC";
            case 2: return "Name A-Z";
            case 3: return "Most Active";
            default: return "Price \u25B2";
        }
    }

    private static String getCommodityTypeFilterLabel(int mode) {
        return switch (mode) {
            case 1 -> "Items";
            case 2 -> "Fluids";
            default -> "All";
        };
    }

    private static boolean matchesCommodityTypeFilter(String itemId, String commodityType, int mode) {
        if (mode == 0) return true;
        boolean fluid = commodityType != null
                ? "FLUID".equalsIgnoreCase(commodityType)
                : isFluidCommodity(itemId);
        return CommodityUtil.matchesTypeFilter(fluid, mode);
    }

    private String getHistoryFilterLabel() {
        switch (historyFilterMode) {
            case 1: return "Sales";
            case 2: return "Purchases";
            default: return "All";
        }
    }

    private String getHistorySortLabel() {
        switch (historySortMode) {
            case 1: return "Oldest";
            case 2: return "Highest $";
            default: return "Newest";
        }
    }

    private String getActiveOrdersFilterLabel() {
        switch (activeOrdersFilterMode) {
            case 1: return "Sell";
            case 2: return "Buy";
            case 3: return "Infinite";
            default: return "All";
        }
    }

    private String getActiveOrdersSortLabel() {
        switch (activeOrdersSortMode) {
            case 1: return "Oldest";
            case 2: return "Price \u25B2";
            case 3: return "Price \u25BC";
            default: return "Newest";
        }
    }

    private List<MarketNetwork.ActiveOrderEntry> filterActiveOrders() {
        List<MarketNetwork.ActiveOrderEntry> f = new ArrayList<>();
        if (cachedActiveOrders == null) return f;
        String q = activeOrdersSearchQuery != null ? activeOrdersSearchQuery.toLowerCase().trim() : "";
        for (MarketNetwork.ActiveOrderEntry e : cachedActiveOrders) {
            if (e == null) continue;
            if (!q.isEmpty()) {
                boolean matchName = e.displayName != null && e.displayName.toLowerCase().contains(q);
                boolean matchId = e.itemId != null && e.itemId.toLowerCase().contains(q);
                if (!matchName && !matchId) continue;
            }
            if (activeOrdersFilterMode == 1 && !e.isSell) continue;
            if (activeOrdersFilterMode == 2 && e.isSell) continue;
            if (activeOrdersFilterMode == 3 && (!e.isInfinite || e.isSell)) continue;
            if (!matchesCommodityTypeFilter(e.itemId, null, activeOrdersCommodityTypeMode)) continue;
            f.add(e);
        }

        f.sort((a, b) -> {
            if (activeOrdersSortMode == 0) { // Newest First
                return Long.compare(b.createdAt, a.createdAt);
            } else if (activeOrdersSortMode == 1) { // Oldest First
                return Long.compare(a.createdAt, b.createdAt);
            } else if (activeOrdersSortMode == 2) { // Price Low to High
                BigDecimal pa = parsePrice(a.price);
                BigDecimal pb = parsePrice(b.price);
                return pa.compareTo(pb);
            } else if (activeOrdersSortMode == 3) { // Price High to Low
                BigDecimal pa = parsePrice(a.price);
                BigDecimal pb = parsePrice(b.price);
                return pb.compareTo(pa);
            }
            return 0;
        });

        return f;
    }
    /** Set when the player clicked a search result; cleared when they type again. */
    private String itemSearchAutoFilled = null;
    /** Per-frame data for the late-rendered item search dropdown. */
    private ItemDropdownData pendingDropdown = null;

    /** Lightweight struct used only for the late-render dropdown pass. */
    private static class PendingOrderExecution {
        final String itemId;
        final int quantity;
        final String priceStr;
        final boolean isSell;
        final boolean isInfinite;
        final String action;
        final String itemName;
        final String totalPrice;
        final String commodityType;

        PendingOrderExecution(String itemId, int quantity, String priceStr, boolean isSell, boolean isInfinite, String action, String itemName, String totalPrice, String commodityType) {
            this.itemId = itemId; this.quantity = quantity; this.priceStr = priceStr;
            this.isSell = isSell; this.isInfinite = isInfinite; this.action = action; this.itemName = itemName;
            this.totalPrice = totalPrice; this.commodityType = commodityType;
        }

        PendingOrderExecution(String itemId, int quantity, String priceStr, boolean isSell, boolean isInfinite, String action, String itemName, String totalPrice) {
            this(itemId, quantity, priceStr, isSell, isInfinite, action, itemName, totalPrice, "ITEM");
        }

        PendingOrderExecution(String itemId, int quantity, String priceStr, boolean isSell, String action, String itemName, String totalPrice) {
            this(itemId, quantity, priceStr, isSell, false, action, itemName, totalPrice, "ITEM");
        }
    }
    private PendingOrderExecution pendingConfirmation = null;

    /** Lightweight struct used only for the late-render dropdown pass. */
    private static class ItemDropdownData {
        int x, y, w;
        String query;
        List<ItemSearchResult> results;
        int scrollOffset = 0;
        boolean isDraggingScrollbar = false;
        ItemDropdownData(int x, int y, int w, String query, List<ItemSearchResult> results) {
            this.x = x; this.y = y; this.w = w; this.query = query; this.results = results;
        }
        void update(int x, int y, int w, List<ItemSearchResult> newResults) {
            this.x = x; this.y = y; this.w = w; this.results = newResults;
            int visibleRows = Math.min(results.size(), 6);
            int maxScroll = Math.max(0, results.size() - visibleRows);
            this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    private static class ItemSearchResult {
        final String itemId;
        final String displayName;
        ItemSearchResult(String itemId, String displayName) {
            this.itemId = itemId; this.displayName = displayName;
        }
    }

    public MarketScreen(MarketMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SCREEN_W;
        this.imageHeight = SCREEN_H;
    }

    public static void handleSyncItemList(MarketNetwork.SyncItemListPacket pkt) {
        cachedCards = pkt.cards;
        cachedBalance = pkt.balance;
        cachedVaultCount = pkt.vaultCount;
        com.nstut.Economy.LOGGER.info("[handleSyncItemList] Received {} cards", pkt.cards.size());
        for (var card : pkt.cards) {
            if ("FLUID".equals(card.commodityType)) {
                com.nstut.Economy.LOGGER.info("[handleSyncItemList] FLUID card: id={} name={} type={}", card.itemId, card.displayName, card.commodityType);
            }
        }
    }

    public static void handleSyncItemDetail(MarketNetwork.SyncItemDetailPacket pkt) {
        cachedDetail = pkt;
    }

    public static void handleSyncOrderHistory(MarketNetwork.SyncOrderHistoryPacket pkt) {
        cachedHistory = pkt.entries;
    }

    @Override
    protected void init() {
        super.init();
        editQtyField = new EditBoxWrapper(10, TEXT_PRIMARY, UiTheme.INPUT, this.font).setPlaceholder("Qty");
        editPriceField = new EditBoxWrapper(20, TEXT_PRIMARY, UiTheme.INPUT, this.font).setPlaceholder("Price");
        editQtyField.setVisible(false);
        editPriceField.setVisible(false);

        buildTree();
        if (searchField != null) this.addRenderableWidget(searchField.getEditBox());
        if (historySearchField != null) this.addRenderableWidget(historySearchField.getEditBox());
        if (activeOrdersSearchField != null) this.addRenderableWidget(activeOrdersSearchField.getEditBox());
        if (itemIdField != null) this.addRenderableWidget(itemIdField.getEditBox());
        if (qtyField != null) this.addRenderableWidget(qtyField.getEditBox());
        if (priceField != null) this.addRenderableWidget(priceField.getEditBox());
        this.addRenderableWidget(editQtyField.getEditBox());
        this.addRenderableWidget(editPriceField.getEditBox());

        switchView(savedViewMode == 1 || savedViewMode == 2 ? 0 : savedViewMode);
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
    }

    private void syncEditBoxes() {
        if (searchField != null && searchField.isVisible()) {
            searchField.getEditBox().setX(searchField.getX() + 4);
            searchField.getEditBox().setY(searchField.getY() + 3);
            searchField.getEditBox().setWidth(Math.max(10, searchField.getWidth() - 8));
            searchField.getEditBox().setHeight(12);
        }
        if (historySearchField != null && historySearchField.isVisible()) {
            historySearchField.getEditBox().setX(historySearchField.getX() + 4);
            historySearchField.getEditBox().setY(historySearchField.getY() + 3);
            historySearchField.getEditBox().setWidth(Math.max(10, historySearchField.getWidth() - 8));
            historySearchField.getEditBox().setHeight(12);
        }
        if (activeOrdersSearchField != null && activeOrdersSearchField.isVisible()) {
            activeOrdersSearchField.getEditBox().setX(activeOrdersSearchField.getX() + 4);
            activeOrdersSearchField.getEditBox().setY(activeOrdersSearchField.getY() + 3);
            activeOrdersSearchField.getEditBox().setWidth(Math.max(10, activeOrdersSearchField.getWidth() - 8));
            activeOrdersSearchField.getEditBox().setHeight(12);
        }
        if (itemIdField != null && itemIdField.isVisible()) {
            itemIdField.getEditBox().setX(itemIdField.getX() + 4);
            itemIdField.getEditBox().setY(itemIdField.getY() + 3);
            itemIdField.getEditBox().setWidth(Math.max(10, itemIdField.getWidth() - 8));
            itemIdField.getEditBox().setHeight(12);
        }
        if (qtyField != null && qtyField.isVisible()) {
            qtyField.getEditBox().setX(qtyField.getX() + 4);
            qtyField.getEditBox().setY(qtyField.getY() + 3);
            qtyField.getEditBox().setWidth(Math.max(10, qtyField.getWidth() - 8));
            qtyField.getEditBox().setHeight(12);
        }
        if (priceField != null && priceField.isVisible()) {
            priceField.getEditBox().setX(priceField.getX() + 4);
            priceField.getEditBox().setY(priceField.getY() + 3);
            priceField.getEditBox().setWidth(Math.max(10, priceField.getWidth() - 8));
            priceField.getEditBox().setHeight(12);
        }
        if (editingOrder != null && editQtyField != null && editPriceField != null) {
            int modalW = 220;
            int modalH = 140;
            int modalX = left() + (SCREEN_W - modalW) / 2;
            int modalY = top() + (SCREEN_H - modalH) / 2;
            int qtyW = editingOrder.isSell ? 196 : 166;

            editQtyField.getEditBox().setX(modalX + 16);
            editQtyField.getEditBox().setY(modalY + 46);
            editQtyField.getEditBox().setWidth(qtyW - 8);
            editQtyField.getEditBox().setHeight(10);
            editQtyField.getEditBox().visible = true;

            editPriceField.getEditBox().setX(modalX + 16);
            editPriceField.getEditBox().setY(modalY + 68);
            editPriceField.getEditBox().setWidth(188);
            editPriceField.getEditBox().setHeight(10);
            editPriceField.getEditBox().visible = true;
        } else {
            if (editQtyField != null) editQtyField.getEditBox().visible = false;
            if (editPriceField != null) editPriceField.getEditBox().visible = false;
        }
    }

    private void buildTree() {
        if (root != null) root.dispose();
        root = new VStack();
        Font font = this.font;

        HStack main = new HStack().gap(0);
        root.addChild(main);

        // Persistent navigation rail, composed from reusable layout primitives.
        VStack sidebar = new VStack().gap(5);
        sidebar.addChild(new SizedBox(0, 7));
        sidebar.addChild(TextWidget.centered("ECONOMY", TEXT_PRIMARY));
        sidebar.addChild(TextWidget.centered("MARKET", ACCENT));
        balanceWidget = new UIComponent() {
            @Override public int preferredWidth(Font f) { return SIDEBAR_W; }
            @Override public int preferredHeight(Font f) { return 19; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                String balDisp = formatCompact(new BigDecimal(cachedBalance));
                int textW = fnt.width(balDisp);
                int totalW = 8 + 3 + textW;
                int startX = x + (width - totalW) / 2;
                UiRender.pill(g, x + 6, y + 1, width - 12, height - 2,
                        UiTheme.INPUT, UiTheme.BORDER_SUBTLE);
                renderSmallCoin(g, startX, y + 5);
                g.drawString(fnt, balDisp, startX + 11, y + 4, TEXT_PRIMARY);
            }
        };
        sidebar.addChild(balanceWidget);
        sidebar.addChild(new SizedBox(0, 2));
        sidebar.addChild(new Divider(PANEL_BORDER));
        sidebar.addChild(new SizedBox(0, 2));

        browseBtn = btn("Browse", PANEL, CARD_HOVER).alignLeft().activeIndicator().height(18).onPress(() -> {
            selectedItemId = null;
            switchView(0);
        });
        sidebar.addChild(new Padding(0, 4, 0, 4, browseBtn));

        newOrderBtn = btn("New Order", PANEL, CARD_HOVER).alignLeft().activeIndicator().height(18).onPress(() -> {
            createOrderSourceMode = 0;
            selectedItemId = null;
            createSellMode = true;
            isCreateInfinite = false;
            if (itemIdField != null) itemIdField.setValue("");
            if (qtyField != null) qtyField.setValue("");
            if (priceField != null) priceField.setValue("");
            updateCreateOfferLabels();
            switchView(2);
        });
        sidebar.addChild(new Padding(0, 4, 0, 4, newOrderBtn));

        orderHistoryBtn = btn("Orders", PANEL, CARD_HOVER).alignLeft().activeIndicator().height(18).onPress(() -> {
            cachedHistory = new ArrayList<>();
            cachedActiveOrders = new ArrayList<>();
            switchView(3);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestActiveOrdersPacket());
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestOrderHistoryPacket());
        });
        sidebar.addChild(new Padding(0, 4, 0, 4, orderHistoryBtn));

        portfolioBtn = btn("Portfolio", PANEL, CARD_HOVER).alignLeft().activeIndicator().height(18).onPress(() -> {
            cachedPortfolioPoints = new ArrayList<>();
            cachedAssetHoldings = new ArrayList<>();
            switchView(5);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
        });
        sidebar.addChild(new Padding(0, 4, 0, 4, portfolioBtn));

        containersBtn = btn("Containers", PANEL, CARD_HOVER).alignLeft().activeIndicator().height(18).onPress(() -> {
            cachedContainerEntries = new ArrayList<>();
            switchView(4);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestVaultInfoPacket());
        });
        sidebar.addChild(new Padding(0, 4, 0, 4, containersBtn));

        sidebar.addChild(new Spacer());
        SizedBox sidebarBox = new SizedBox(SIDEBAR_W, SCREEN_H);
        sidebarBox.addChild(sidebar);
        main.addChild(sidebarBox);

        // Content container follows the same predictable inset rhythm as cards.
        VStack contentArea = new VStack().gap(4);
        contentArea.flex();

        browser = buildBrowser(font);
        browser.flex();
        contentArea.addChild(browser);

        detail = buildDetail(font);
        detail.flex();
        detail.setVisible(false);
        contentArea.addChild(detail);

        createOffer = buildCreateOffer(font);
        createOffer.flex();
        createOffer.setVisible(false);
        contentArea.addChild(createOffer);

        UIComponent orders = buildOrdersView(font);
        orders.flex();
        orders.setVisible(false);
        this.ordersView = orders;
        contentArea.addChild(orders);

        UIComponent containers = buildContainers(font);
        containers.flex();
        containers.setVisible(false);
        this.containersView = containers;
        contentArea.addChild(containers);

        UIComponent portfolio = buildPortfolio(font);
        portfolio.flex();
        portfolio.setVisible(false);
        this.portfolioView = portfolio;
        contentArea.addChild(portfolio);

        Padding contentPadding = new Padding(10, 10, 10, 10, contentArea);
        contentPadding.flex();
        main.addChild(contentPadding);
    }

    private ButtonWidget btn(String label, int normal, int hover) {
        return new ButtonWidget(label, normal, hover, TEXT_PRIMARY);
    }

    private UIComponent buildBrowser(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 15; }
            @Override public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                g.drawString(fnt, "MARKETPLACE", x, y + 2, TEXT_PRIMARY);
                String listingCount = filterCards().size() + " live listings";
                g.drawString(fnt, listingCount, x + width - fnt.width(listingCount), y + 2, TEXT_MUTED);
            }
        });
        searchQuery = savedSearchQuery;
        searchField = new EditBoxWrapper(60, TEXT_PRIMARY, UiTheme.INPUT, font).setPlaceholder("Search products...");
        if (savedSearchQuery != null && !savedSearchQuery.isEmpty()) {
            searchField.setValue(savedSearchQuery);
        }

        HStack searchBar = new HStack().gap(4);
        searchField.flex();
        searchBar.addChild(searchField);
        browseLayoutBtn = btn(browseGridView ? "Grid" : "Rows", PANEL, CARD_HOVER).onPress(() -> {
            browseGridView = !browseGridView;
            savedBrowseGridView = browseGridView;
            MarketClientPreferences.setBrowseGridView(browseGridView);
            browseLayoutBtn.setLabel(browseGridView ? "Grid" : "Rows");
            if (cardGrid != null) cardGrid.resetScroll();
            if (cardList != null) cardList.resetScroll();
        });
        searchBar.addChild(browseLayoutBtn);
        v.addChild(searchBar);

        HStack bar = new HStack().gap(4);
        browseFilterBtn = btn("Activity\n" + getBrowseFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            browseFilterMode = (browseFilterMode + 1) % 2;
            savedBrowseFilterMode = browseFilterMode;
            browseFilterBtn.setLabel("Activity\n" + getBrowseFilterLabel());
        });
        browseFilterBtn.flex();
        bar.addChild(browseFilterBtn);

        browseCommodityTypeBtn = btn("Product\n" + getCommodityTypeFilterLabel(browseCommodityTypeMode), PANEL, CARD_HOVER).onPress(() -> {
            browseCommodityTypeMode = (browseCommodityTypeMode + 1) % 3;
            savedBrowseCommodityTypeMode = browseCommodityTypeMode;
            browseCommodityTypeBtn.setLabel("Product\n" + getCommodityTypeFilterLabel(browseCommodityTypeMode));
        });
        browseCommodityTypeBtn.flex();
        bar.addChild(browseCommodityTypeBtn);

        browseSortBtn = btn("Sort\n" + getBrowseSortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            browseSortMode = (browseSortMode + 1) % 4;
            savedBrowseSortMode = browseSortMode;
            browseSortBtn.setLabel("Sort\n" + getBrowseSortLabel());
        });
        browseSortBtn.flex();
        bar.addChild(browseSortBtn);
        v.addChild(bar);

        ScrollList.ItemRenderer cardRenderer = this::renderBrowseCard;
        ScrollList.ItemClickListener cardClick = this::openBrowseCard;

        cardList = new ScrollList(
            () -> filterCards().size(),
            44,
            cardRenderer,
            cardClick,
            PANEL, ACCENT_DIM);

        cardGrid = new ScrollGrid(
                () -> filterCards().size(),
                2,
                44,
                4,
                cardRenderer,
                cardClick,
                PANEL,
                ACCENT_DIM);

        UIComponent cardView = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 10; }
            @Override public int preferredHeight(Font f) { return 10; }

            @Override
            public void layout(int x, int y, int availableWidth, int availableHeight) {
                setBounds(x, y, availableWidth, availableHeight);
                cardList.layout(x, y, availableWidth, availableHeight);
                cardGrid.layout(x, y, availableWidth, availableHeight);
            }

            private UIComponent activeView() {
                return browseGridView ? cardGrid : cardList;
            }

            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                activeView().render(g, fnt, mx, my, pt);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                return activeView().mouseClicked(mx, my, button);
            }

            @Override
            public boolean mouseScrolled(double mx, double my, double delta) {
                return activeView().mouseScrolled(mx, my, delta);
            }

            @Override
            public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
                return activeView().mouseDragged(mx, my, button, dragX, dragY);
            }

            @Override
            public boolean mouseReleased(double mx, double my, int button) {
                return activeView().mouseReleased(mx, my, button);
            }
        };
        cardView.flex();
        v.addChild(cardView);
        return v;
    }

    private void renderBrowseCard(GuiGraphics g, Font font, int index, int rowX, int rowY,
                                  int cardWidth, int mouseX, int mouseY, boolean hovered) {
        List<MarketNetwork.ItemCardData> cards = filterCards();
        if (index < 0 || index >= cards.size()) return;
        MarketNetwork.ItemCardData card = cards.get(index);

        int cardX = rowX;
        int cardY = rowY + 2;
        int cardHeight = 40;
        boolean cardHovered = mouseX >= cardX && mouseX < cardX + cardWidth
                && mouseY >= cardY && mouseY < cardY + cardHeight;
        int fillColor = cardHovered ? CARD_HOVER : CARD_BG;
        int borderColor = cardHovered ? ACCENT : PANEL_BORDER;

        UiRender.surface(g, cardX, cardY, cardWidth, cardHeight, UiTheme.RADIUS_MD,
                fillColor, borderColor, cardHovered);
        if (cardHovered) {
            UiRender.roundedRect(g, cardX + 2, cardY + 6, 2, cardHeight - 12, 1, ACCENT);
        }
        UiRender.roundedOutline(g, cardX + 4, cardY + 9, 22, 22, UiTheme.RADIUS_SM,
                UiTheme.INPUT, PANEL_BORDER);
        renderCommodityIcon(g, card.itemId, cardX + 6, cardY + 12);

        int textX = cardX + 28;
        int textWidth = Math.max(1, cardWidth - 34);
        String name = font.plainSubstrByWidth(
                getItemDisplayName(card.itemId, card.displayName), textWidth);
        g.drawString(font, name, textX, cardY + 4, TEXT_PRIMARY);

        String countText = card.offerCount > 0
                ? com.nstut.economy.util.EconomyFormatUtil.formatCount(card.offerCount, "order", "orders")
                : "No active orders";
        int countWidth = font.width(countText);
        int priceClipRight = browseGridView
                ? cardX + cardWidth - 4
                : cardX + cardWidth - countWidth - 12;

        g.enableScissor(textX - 2, cardY + 14, priceClipRight, cardY + 27);
        if (card.globalPrice != null && !card.globalPrice.isEmpty() && !card.globalPrice.equals("--")) {
            renderSmallCoin(g, textX - 2, cardY + 17);
            String compactPrice = formatCompact(parsePrice(card.globalPrice));
            int priceX = textX + 9;
            g.drawString(font, compactPrice, priceX, cardY + 16, ACCENT);

            String changeText = formatPriceChange(card.priceChangePercent);
            int changeX = priceX + font.width(compactPrice) + 5;
            g.drawString(font, changeText, changeX, cardY + 16,
                    getPriceChangeColor(card.priceChangePercent));
        } else {
            g.drawString(font, "--", textX, cardY + 16, TEXT_MUTED);
        }
        g.disableScissor();

        if (browseGridView) {
            countText = font.plainSubstrByWidth(countText, textWidth);
            g.drawString(font, countText, textX, cardY + 28, TEXT_MUTED);
        } else {
            int pillWidth = countWidth + 10;
            int pillX = cardX + cardWidth - pillWidth - 5;
            UiRender.pill(g, pillX, cardY + 13, pillWidth, 15, UiTheme.INPUT, PANEL_BORDER);
            g.drawString(font, countText, pillX + 5, cardY + 16, TEXT_MUTED);
        }
    }

    private void openBrowseCard(int index, int button, int mouseX, int mouseY) {
        List<MarketNetwork.ItemCardData> cards = filterCards();
        if (index < 0 || index >= cards.size()) return;
        MarketNetwork.ItemCardData card = cards.get(index);
        selectedItemId = card.itemId;
        cachedDetail = null;
        switchView(1);
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(card.itemId));
    }

    private UIComponent buildDetail(Font font) {
        VStack v = new VStack().gap(4);

        HStack header = new HStack().gap(6);
        header.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 230; }
            @Override public int preferredHeight(Font f) { return 18; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                if (cachedDetail != null) {
                    String titleText = getItemDisplayName(cachedDetail.itemId, cachedDetail.displayName);
                    g.drawString(fnt, titleText, x, y + 4, TEXT_PRIMARY);

                    double detailChange = Double.NaN;
                    if (cachedDetail.chart != null && !cachedDetail.chart.isEmpty()) {
                        List<MarketNetwork.ChartPoint> pts = cachedDetail.chart;
                        int curP = pts.get(pts.size() - 1).price;
                        int prevP = curP;
                        boolean foundDiff = false;
                        for (int i = pts.size() - 2; i >= 0; i--) {
                            if (pts.get(i).price != curP) {
                                prevP = pts.get(i).price;
                                foundDiff = true;
                                break;
                            }
                        }
                        if (foundDiff && prevP > 0) {
                            detailChange = ((double)(curP - prevP) / prevP) * 100.0;
                        }
                    }

                    int titleW = fnt.width(titleText);
                    String changeText = formatPriceChange(detailChange);
                    int changeColor = getPriceChangeColor(detailChange);
                    g.drawString(fnt, changeText, x + titleW + 8, y + 4, changeColor);

                    boolean isItemFluid = false;
                    if (cachedDetail.itemId != null) {
                        Fluid f = BuiltInRegistries.FLUID.get(new ResourceLocation(cachedDetail.itemId));
                        isItemFluid = f != net.minecraft.world.level.material.Fluids.EMPTY;
                    }
                    String stockText = isItemFluid
                            ? "In Tank: " + formatFluidAmountDetailed(cachedDetail.vaultCount)
                            : "In Vault: " + formatItemAmount(cachedDetail.vaultCount);
                    int stockW = fnt.width(stockText);
                    g.drawString(fnt, stockText, x + width - stockW, y + 4, ACCENT);
                } else if (selectedItemId != null) {
                    g.drawString(fnt, selectedItemId, x, y + 4, TEXT_MUTED);
                }
            }
        });
        v.addChild(header);

        // Chart box
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 40; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                UiRender.surface(g, x, y, width, height, UiTheme.RADIUS_MD,
                        CHART_BG, PANEL_BORDER, false);

                if (cachedDetail != null && cachedDetail.chart != null && !cachedDetail.chart.isEmpty()) {
                    List<MarketNetwork.ChartPoint> pts = cachedDetail.chart;
                    int totalCount = pts.size();

                    int maxOffset = Math.max(0, totalCount - MAX_VISIBLE_CHART_STEPS);
                    if (detailChartOffset > maxOffset) detailChartOffset = maxOffset;
                    if (detailChartOffset < 0) detailChartOffset = 0;

                    int endIndex = Math.min(totalCount, Math.max(MAX_VISIBLE_CHART_STEPS, totalCount - detailChartOffset));
                    int startIndex = Math.max(0, endIndex - MAX_VISIBLE_CHART_STEPS);

                    List<MarketNetwork.ChartPoint> visiblePts = pts.subList(startIndex, endIndex);

                    int maxP = Integer.MIN_VALUE, minP = Integer.MAX_VALUE;
                    for (MarketNetwork.ChartPoint cp : visiblePts) {
                        if (cp.price > maxP) maxP = cp.price;
                        if (cp.price < minP) minP = cp.price;
                    }
                    if (maxP == minP) { maxP += 5; minP = Math.max(0, minP - 5); }
                    float range = maxP - minP;

                    // Draw min/max price text labels on the left
                    g.drawString(fnt, formatCompact((double)maxP), x + 3, y + 3, TEXT_MUTED);
                    g.drawString(fnt, formatCompact((double)minP), x + 3, y + height - 11, TEXT_MUTED);

                    // Right side Live / Snap button & Price badge
                    int currentPrice = visiblePts.get(visiblePts.size() - 1).price;
                    String currentPriceStr = formatCompact((double)currentPrice);
                    int currentPriceW = fnt.width(currentPriceStr);
                    int badgeW = currentPriceW + 8;
                    int badgeH = 12;
                    int badgeX = x + width - badgeW - 4;
                    int rawY = y + height - 6 - (int)((currentPrice - minP) / range * (height - 12));
                    int badgeY = Math.max(y + 2, Math.min(y + height - badgeH - 2, rawY - 5));

                    // LIVE Snap Button
                    String liveStr = detailChartOffset == 0 ? "LIVE" : "\u25B6 LIVE";
                    int liveW = fnt.width(liveStr) + 6;
                    int liveX = badgeX - liveW - 4;
                    int liveY = y + 2;
                    int liveH = 11;
                    detailLiveBtnX = liveX;
                    detailLiveBtnY = liveY;
                    detailLiveBtnW = liveW;
                    detailLiveBtnH = liveH;
                    boolean liveHover = mx >= liveX && mx < liveX + liveW && my >= liveY && my < liveY + liveH;
                    int liveBg = detailChartOffset > 0 ? (liveHover ? UiTheme.SUCCESS : UiTheme.SUCCESS_DEEP) : (liveHover ? CARD_HOVER : UiTheme.ACCENT_DEEP);
                    int liveBorder = detailChartOffset > 0 ? GREEN : (liveHover ? ACCENT : PANEL_BORDER);
                    UiRender.pill(g, liveX, liveY, liveW, liveH, liveBg, liveBorder);
                    g.drawString(fnt, liveStr, liveX + 3, liveY + 2, detailChartOffset > 0 ? 0xFFFFFFFF : ACCENT);
                    if (liveHover && detailChartOffset > 0) {
                        pendingTooltip = "Click to snap back to current live time";
                    }

                    // Dotted line stops before reaching the right price badge
                    int lineY = rawY;
                    int chartLeft = x + 26;
                    int chartRight = liveX - 4;
                    int dotStep = 4;
                    for (int lx = chartLeft; lx <= chartRight; lx += dotStep) {
                        g.fill(lx, lineY, Math.min(lx + 2, chartRight), lineY + 1, ACCENT_DIM);
                    }

                    // Line graph plot & interactive nodes
                    int ptsCount = visiblePts.size();
                    for (int i = 0; i < ptsCount; i++) {
                        MarketNetwork.ChartPoint cp = visiblePts.get(i);
                        int x0 = chartLeft + i * (chartRight - chartLeft) / Math.max(1, ptsCount - 1);
                        int y0 = y + height - 6 - (int)((cp.price - minP) / range * (height - 12));

                        if (i > 0) {
                            MarketNetwork.ChartPoint cpPrev = visiblePts.get(i - 1);
                            int xPrev = chartLeft + (i - 1) * (chartRight - chartLeft) / Math.max(1, ptsCount - 1);
                            int yPrev = y + height - 6 - (int)((cpPrev.price - minP) / range * (height - 12));
                            drawLine(g, xPrev, yPrev, x0, y0, CHART_LINE);
                        }

                        // Data node dot + tooltip hover detection
                        boolean nodeHover = (mx >= x0 - 3 && mx <= x0 + 3 && my >= y0 - 3 && my <= y0 + 3);
                        int dotClr = nodeHover ? 0xFFFFFFFF : ACCENT;
                        g.fill(x0 - 1, y0 - 1, x0 + 2, y0 + 2, dotClr);
                        if (nodeHover) {
                            String timeStr = cp.timestamp > 0 ? CHART_TIME_FMT.format(new java.util.Date(cp.timestamp)) : "Step " + (startIndex + i + 1);
                            boolean fluidProduct = cachedDetail != null && isFluidCommodity(cachedDetail.itemId);
                            String volume = fluidProduct
                                    ? formatFluidAmountDetailed(cp.quantity)
                                    : formatItemAmount(cp.quantity);
                            pendingTooltip = "Time: " + timeStr + "\nPrice: $" + cp.price + "\nVolume: " + volume;
                        }
                    }

                    // Draw right current price badge pill container
                    UiRender.pill(g, badgeX, badgeY, badgeW, badgeH,
                            UiTheme.ACCENT_DEEP, ACCENT);
                    g.drawString(fnt, currentPriceStr, badgeX + 4, badgeY + 2, ACCENT);
                } else {
                    g.drawString(fnt, "No trade history available yet", x + (width - fnt.width("No trade history available yet")) / 2, y + 18, TEXT_MUTED);
                }
            }
        });

        HStack cols = new HStack().gap(6);
        cols.flex();
        cols.addChild(buildOrderColumn(font, true));
        cols.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 1; }
            @Override public int preferredHeight(Font f) { return 1; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                g.fill(x, y + 2, x + 1, y + height - 2, PANEL_BORDER);
            }
        });
        cols.addChild(buildOrderColumn(font, false));
        v.addChild(cols);

        // My Orders Section
        v.addChild(new SizedBox(0, 2));
        v.addChild(TextWidget.centered("MY ORDERS", ACCENT));
        final ScrollList[] myOrderListHolder = new ScrollList[1];
        ScrollList myOrderList = new ScrollList(
            () -> Math.max(1, getMyOrders().size()),
            16,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<MarketNetwork.OrderEntry> myOrders = getMyOrders();
                if (myOrders.isEmpty()) {
                    String emptyText = "No active orders for this item";
                    g.drawString(fnt, emptyText, rx + (rw - fnt.width(emptyText)) / 2, ry + 3, TEXT_MUTED);
                    return;
                }
                if (idx >= myOrders.size()) return;
                MarketNetwork.OrderEntry e = myOrders.get(idx);
                boolean isSell = cachedDetail != null && cachedDetail.asks.contains(e);

                if (hover) UiRender.roundedRect(g, rx, ry + 1, rw, 15,
                        UiTheme.RADIUS_SM, CARD_HOVER);

                // 1. Order Type Badge (SELL or BUY)
                String typeText = isSell ? "SELL" : "BUY";
                int typeW = fnt.width(typeText);
                int badgeW = typeW + 6;
                int badgeH = 11;
                int badgeX = rx + 3;
                int badgeY = ry + 2;

                int bgClr = isSell ? 0x40801818 : 0x40105028;
                int borderClr = isSell ? UiTheme.DANGER : UiTheme.SUCCESS;
                int textClr = isSell ? UiTheme.DANGER : UiTheme.SUCCESS;

                UiRender.pill(g, badgeX, badgeY, badgeW, badgeH, bgClr, borderClr);
                g.drawString(fnt, typeText, badgeX + 3, badgeY + 2, textClr);

                // 2. Coin Icon & Price x Quantity (with fulfillment progress if partially filled)
                int px = badgeX + badgeW + 5;

                // 3. Edit & Cancel Buttons
                String cancelText = "Cancel";
                int cancelW = fnt.width(cancelText) + 6;
                int cancelX = rx + rw - cancelW - 3;

                String editText = "Edit";
                int editW = fnt.width(editText) + 6;
                int editX = cancelX - editW - 3;

                int btnH = 12;
                int btnY = ry + 2;

                boolean fluidOrder = cachedDetail != null && isFluidCommodity(cachedDetail.itemId);
                String line;
                if (e.isInfinite) {
                    line = formatCompact(parsePrice(e.price)) + " \u00d7 \u221e";
                } else if (e.initialQuantity > e.quantity) {
                    int fulfilled = e.initialQuantity - e.quantity;
                    int pct = (fulfilled * 100) / e.initialQuantity;
                    String quantity = fluidOrder ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity);
                    line = formatCompact(parsePrice(e.price)) + " \u00d7 " + quantity + " (" + pct + "% filled)";
                } else {
                    String quantity = fluidOrder ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity);
                    line = formatCompact(parsePrice(e.price)) + " \u00d7 " + quantity;
                }

                int marqueeWidth = editX - px - 4;
                int clr = isSell ? RED : GREEN;
                drawCoinTextPingPongMarquee(g, fnt, line, px, ry + 3, marqueeWidth, clr);

                if (hover && mx >= px && mx < editX) {
                    if (e.isInfinite) {
                        pendingTooltip = "Order Quantity: Continuous Infinite (\u221e)";
                    } else if (e.initialQuantity > e.quantity) {
                        String filled = fluidOrder
                                ? formatFluidAmountDetailed(e.initialQuantity - e.quantity)
                                : formatItemAmount(e.initialQuantity - e.quantity);
                        String total = fluidOrder
                                ? formatFluidAmountDetailed(e.initialQuantity)
                                : formatItemAmount(e.initialQuantity);
                        pendingTooltip = "Order Progress: " + filled + " / " + total
                                + " (" + ((e.initialQuantity - e.quantity) * 100 / e.initialQuantity) + "%)";
                    } else {
                        pendingTooltip = "Order Quantity: " + (fluidOrder
                                ? formatFluidAmountDetailed(e.quantity)
                                : formatItemAmount(e.quantity));
                    }
                }

                boolean isEditHover = (mx >= editX && mx < cancelX && my >= btnY && my <= btnY + btnH);
                int editBg = isEditHover ? CARD_HOVER : PANEL;
                UiRender.pill(g, editX, btnY, editW, btnH, editBg, ACCENT);
                g.drawString(fnt, editText, editX + 3, badgeY + 2, ACCENT);

                boolean isCancelHover = (mx >= cancelX && mx <= cancelX + cancelW && my >= btnY && my <= btnY + btnH);
                int cancelBg = isCancelHover ? UiRender.mix(UiTheme.DANGER_DEEP, UiTheme.DANGER, 0.28F) : UiTheme.DANGER_DEEP;
                int cancelBorder = UiTheme.DANGER;

                UiRender.pill(g, cancelX, btnY, cancelW, btnH, cancelBg, cancelBorder);
                g.drawString(fnt, cancelText, cancelX + 3, badgeY + 2, 0xFFFFFFFF);
            },
            (idx, button, mx, my) -> {
                List<MarketNetwork.OrderEntry> myOrders = getMyOrders();
                if (idx >= 0 && idx < myOrders.size()) {
                    Font fnt = net.minecraft.client.Minecraft.getInstance().font;
                    String cancelText = "Cancel";
                    int cancelW = fnt.width(cancelText) + 6;
                    String editText = "Edit";
                    int editW = fnt.width(editText) + 6;

                    if (myOrderListHolder[0] != null) {
                        int listX = myOrderListHolder[0].getX();
                        int listW = myOrderListHolder[0].getWidth() - 8;
                        int cancelX = listX + listW - cancelW - 3;
                        int editX = cancelX - editW - 3;

                        MarketNetwork.OrderEntry e = myOrders.get(idx);
                        if (mx >= cancelX && mx <= cancelX + cancelW) {
                            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId));
                        } else if (mx >= editX && mx < cancelX) {
                            openEditOrderModal(e);
                        }
                    }
                }
            },
            PANEL, ACCENT_DIM);
        myOrderListHolder[0] = myOrderList;
        v.addChild(myOrderList);

        v.addChild(new SizedBox(0, 2));
        v.addChild(btn("Create Order for this Item", ACCENT_DIM, ACCENT).onPress(() -> {
            createOrderSourceMode = 1;
            createSellMode = true;
            isCreateInfinite = false;
            if (itemIdField != null && selectedItemId != null) itemIdField.setValue(selectedItemId);
            updateCreateOfferLabels();
            switchView(2);
        }));
        return v;
    }

    private List<MarketNetwork.OrderEntry> getOtherOrders(boolean isAsks) {
        if (cachedDetail == null) return Collections.emptyList();
        List<MarketNetwork.OrderEntry> source = isAsks ? cachedDetail.asks : cachedDetail.bids;
        List<MarketNetwork.OrderEntry> res = new ArrayList<>();
        for (MarketNetwork.OrderEntry e : source) {
            if (!e.isPlayerOwned) res.add(e);
        }
        return res;
    }

    private List<MarketNetwork.OrderEntry> getMyOrders() {
        if (cachedDetail == null) return Collections.emptyList();
        List<MarketNetwork.OrderEntry> res = new ArrayList<>();
        for (MarketNetwork.OrderEntry e : cachedDetail.asks) {
            if (e.isPlayerOwned) res.add(e);
        }
        for (MarketNetwork.OrderEntry e : cachedDetail.bids) {
            if (e.isPlayerOwned) res.add(e);
        }
        return res;
    }

    private void openCreateOrderWithPrefill(boolean isSell, String rawPrice, int qty) {
        createOrderSourceMode = 1;
        createSellMode = isSell;
        isCreateInfinite = false;
        String targetItemId = selectedItemId;
        if ((targetItemId == null || targetItemId.isEmpty()) && cachedDetail != null) {
            targetItemId = cachedDetail.itemId;
        }
        if (itemIdField != null) itemIdField.setValue(targetItemId != null ? targetItemId : "");

        String cleanPrice = rawPrice != null ? rawPrice.replaceAll("[^0-9.]", "").trim() : "";
        try {
            if (!cleanPrice.isEmpty()) {
                double p = Double.parseDouble(cleanPrice);
                cleanPrice = String.format(java.util.Locale.ROOT, "%.2f", p);
            }
        } catch (Exception ignored) {}
        if (priceField != null) priceField.setValue(cleanPrice);

        // For sell orders: prefill min(orderQty, vaultCount) so player can't over-commit
        int prefillQty = qty;
        if (isSell && cachedDetail != null && cachedDetail.vaultCount >= 0) {
            prefillQty = Math.min(qty, cachedDetail.vaultCount);
        }
        if (qtyField != null) qtyField.setValue(prefillQty > 0 ? String.valueOf(prefillQty) : "");
        updateCreateOfferLabels();
        switchView(2);
    }


    private String pendingTooltip = null;

    private UIComponent buildOrderColumn(Font font, boolean isAsks) {
        VStack v = new VStack().gap(2);
        v.flex();

        String title = isAsks ? "SELL ORDERS" : "BUY ORDERS";
        int titleColor = isAsks ? RED : GREEN;
        v.addChild(TextWidget.centered(title, titleColor));

        ScrollList list = new ScrollList(
            () -> Math.max(1, getOtherOrders(isAsks).size()),
            18,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<MarketNetwork.OrderEntry> entries = getOtherOrders(isAsks);
                if (entries.isEmpty()) {
                    String emptyText = isAsks ? "No sell orders" : "No buy orders";
                    g.drawString(fnt, emptyText, rx + (rw - fnt.width(emptyText)) / 2, ry + 4, TEXT_MUTED);
                    return;
                }
                if (idx >= entries.size()) return;
                MarketNetwork.OrderEntry e = entries.get(idx);
                int clr = e.isServerOrder ? ACCENT : (isAsks ? RED : GREEN);
                if (hover) UiRender.roundedRect(g, rx, ry + 1, rw, 16,
                        UiTheme.RADIUS_SM, CARD_HOVER);

                int textX = rx + 3;
                if (e.isServerOrder) {
                    String badge = "SERVER";
                    int badgeW = fnt.width(badge) + 8;
                    int badgeH = 12;
                    int badgeX = rx + 2;
                    int badgeY = ry + 3;

                    UiRender.pill(g, badgeX, badgeY, badgeW, badgeH,
                            UiTheme.ACCENT_DEEP, ACCENT);

                    g.drawString(fnt, badge, badgeX + 4, badgeY + 2, ACCENT);
                    textX += badgeW + 4;

                    if (mx >= badgeX && mx < badgeX + badgeW && my >= badgeY && my < badgeY + badgeH) {
                        pendingTooltip = "Server Order: Generated by the server to provide market liquidity.";
                    }
                } else {
                    String rawName = e.sellerName != null && !e.sellerName.isEmpty() ? e.sellerName : "Player";
                    String dispName = fnt.plainSubstrByWidth(rawName, 44);
                    int badgeW = fnt.width(dispName) + 6;
                    int badgeH = 12;
                    int badgeX = rx + 2;
                    int badgeY = ry + 3;

                    UiRender.pill(g, badgeX, badgeY, badgeW, badgeH,
                            CARD_BG, PANEL_BORDER);

                    g.drawString(fnt, dispName, badgeX + 3, badgeY + 2, TEXT_PRIMARY);
                    textX += badgeW + 4;

                    if (mx >= badgeX && mx < badgeX + badgeW && my >= badgeY && my < badgeY + badgeH) {
                        pendingTooltip = (isAsks ? "Seller: " : "Buyer: ") + rawName;
                    }
                }

                boolean fluidOrder = cachedDetail != null && isFluidCommodity(cachedDetail.itemId);
                String quantity = e.isInfinite
                        ? "\u221e"
                        : (fluidOrder ? formatFluidAmount(e.quantity) : formatItemAmount(e.quantity));
                String line = e.price + " \u00d7 " + quantity;
                int marqueeX = textX - 1;
                int marqueeWidth = rx + rw - 3 - marqueeX;
                drawCoinTextPingPongMarquee(g, fnt, line, marqueeX, ry + 4, marqueeWidth, clr);
            },
            (idx, btn) -> {
                List<MarketNetwork.OrderEntry> entries = getOtherOrders(isAsks);
                if (idx >= 0 && idx < entries.size()) {
                    MarketNetwork.OrderEntry e = entries.get(idx);
                    if (isAsks) {
                        // Clicked a Sell order -> Create a Buy order matching price & qty
                        openCreateOrderWithPrefill(false, e.price, e.quantity);
                    } else {
                        // Clicked a Buy order -> Create a Sell order matching price & qty
                        openCreateOrderWithPrefill(true, e.price, e.quantity);
                    }
                }
            },
            PANEL, ACCENT_DIM);

        if (isAsks) askList = list; else bidList = list;
        v.addChild(list);
        return v;
    }

    private ButtonWidget sellModeBtn, buyModeBtn;
    private ButtonWidget createBtn;
    private ButtonWidget maxQtyBtn;
    private TextWidget createOfferTitleLabel;
    private UIComponent vaultStockBadge;
    private TextWidget createOfferErrorLabel;

    private int getVaultStockForItem(String query) {
        if (query == null || query.trim().isEmpty()) return 0;
        String q = query.trim();
        if (cachedDetail != null && (cachedDetail.itemId.equalsIgnoreCase(q) || cachedDetail.displayName.equalsIgnoreCase(q))) {
            return cachedDetail.vaultCount;
        }
        for (var h : cachedAssetHoldings) {
            if (h.itemId.equalsIgnoreCase(q) || h.displayName.equalsIgnoreCase(q)) {
                return h.quantity;
            }
        }
        return 0;
    }

    public static String getItemDisplayName(String itemId, String rawName) {
        if (itemId != null && !itemId.isEmpty()) {
            try {
                ResourceLocation rl = new ResourceLocation(itemId);
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != net.minecraft.world.item.Items.AIR) {
                    String name = new ItemStack(item).getHoverName().getString();
                    if (name != null && !name.isEmpty() && !name.startsWith("tagprefix.") && !name.startsWith("item.")) {
                        return name;
                    }
                }
                Fluid fluid = BuiltInRegistries.FLUID.get(rl);
                if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    String name = new FluidStack(fluid, 1000).getDisplayName().getString();
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (rawName != null && !rawName.isEmpty()) {
            try {
                String translated = Component.translatable(rawName).getString();
                if (translated != null && !translated.isEmpty() && !translated.equals(rawName)) {
                    return translated;
                }
            } catch (Exception ignored) {}
            return rawName;
        }
        return itemId != null ? itemId : "";
    }

    private UIComponent buildCreateOffer(Font font) {
        VStack v = new VStack().gap(4);

        HStack header = new HStack().gap(6);
        ButtonWidget backBtn = btn("< Back", PANEL, CARD_HOVER).onPress(() -> switchView(createOrderSourceMode));
        header.addChild(backBtn);
        createOfferTitleLabel = TextWidget.centered("CREATE ORDER", ACCENT);
        createOfferTitleLabel.flex();
        header.addChild(createOfferTitleLabel);
        header.addChild(new SizedBox(backBtn.preferredWidth(font), 0));
        v.addChild(header);
        v.addChild(new Divider(PANEL_BORDER));

        HStack modeSelector = new HStack().gap(4);
        sellModeBtn = btn("SELL ORDER", createSellMode ? UiTheme.DANGER_DEEP : PANEL, UiTheme.DANGER).onPress(() -> {
            createSellMode = true;
            updateCreateOfferLabels();
        });
        sellModeBtn.flex();
        buyModeBtn = btn("BUY ORDER", !createSellMode ? UiTheme.SUCCESS_DEEP : PANEL, UiTheme.SUCCESS).onPress(() -> {
            createSellMode = false;
            updateCreateOfferLabels();
        });
        buyModeBtn.flex();
        modeSelector.addChild(sellModeBtn);
        modeSelector.addChild(buyModeBtn);
        v.addChild(modeSelector);

        v.addChild(new SizedBox(0, 2));

        itemIdField = new EditBoxWrapper(128, TEXT_PRIMARY, UiTheme.INPUT, font).setPlaceholder("Search item name or ID...");
        if (selectedItemId != null) {
            itemIdField.setValue(selectedItemId);
            itemSearchAutoFilled = selectedItemId;
        }
        v.addChild(itemIdField);

        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 0; }
            @Override public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                if (!visible) return;
                String query = itemIdField != null ? itemIdField.getValue().trim() : "";
                boolean showDropdown = !query.isEmpty() && !query.equals(itemSearchAutoFilled);
                if (showDropdown) {
                    List<ItemSearchResult> results = getItemSearchResults(query);
                    if (!results.isEmpty()) {
                        int dx = itemIdField != null ? itemIdField.getX() : x;
                        int dy = itemIdField != null ? (itemIdField.getY() + itemIdField.getHeight() + 1) : y;
                        int dw = itemIdField != null ? itemIdField.getWidth() : width;
                        if (pendingDropdown == null || !query.equalsIgnoreCase(pendingDropdown.query)) {
                            pendingDropdown = new ItemDropdownData(dx, dy, dw, query, results);
                        } else {
                            pendingDropdown.update(dx, dy, dw, results);
                        }
                    } else {
                        pendingDropdown = null;
                    }
                } else {
                    pendingDropdown = null;
                }
            }
        });

        vaultStockBadge = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return createSellMode ? 14 : 0; }
            @Override public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                if (!createSellMode) return;
                String id = itemIdField != null && !itemIdField.getValue().trim().isEmpty() ? itemIdField.getValue().trim() : selectedItemId;
                if ((id == null || id.isEmpty()) && cachedDetail != null) id = cachedDetail.itemId;
                int stock = getVaultStockForItem(id);
                boolean fluidCommodity = isFluidCommodity(id);
                String stockMsg = fluidCommodity
                        ? "Tank Stock Available: " + formatFluidAmountDetailed(stock)
                        : "Vault Stock Available: " + formatItemAmount(stock);
                int color = stock > 0 ? GREEN : RED;
                g.drawString(fnt, stockMsg, x + 2, y + 3, color);
            }
        };
        v.addChild(vaultStockBadge);

        HStack qtyRow = new HStack().gap(4);
        qtyField = new EditBoxWrapper(10, TEXT_PRIMARY, UiTheme.INPUT, font).setPlaceholder("Quantity (e.g. 10)");
        qtyField.flex();
        qtyRow.addChild(qtyField);

        maxQtyBtn = btn("MAX", PANEL, CARD_HOVER).onPress(() -> {
            String id = itemIdField != null && !itemIdField.getValue().trim().isEmpty() ? itemIdField.getValue().trim() : selectedItemId;
            if ((id == null || id.isEmpty()) && cachedDetail != null) id = cachedDetail.itemId;
            int stock = getVaultStockForItem(id);
            if (stock > 0 && qtyField != null) {
                qtyField.setValue(String.valueOf(stock));
            }
        });
        qtyRow.addChild(maxQtyBtn);

        infiniteBuyBtn = btn("∞", PANEL, CARD_HOVER).onPress(() -> {
            isCreateInfinite = !isCreateInfinite;
            infiniteBuyBtn.setColors(isCreateInfinite ? UiTheme.SUCCESS_DEEP : PANEL, isCreateInfinite ? UiTheme.SUCCESS : CARD_HOVER);
            if (isCreateInfinite && qtyField != null) {
                qtyField.setValue("∞");
            } else if (!isCreateInfinite && qtyField != null && qtyField.getValue().equals("∞")) {
                qtyField.setValue("");
            }
        });
        qtyRow.addChild(infiniteBuyBtn);
        v.addChild(qtyRow);

        priceField = new EditBoxWrapper(20, TEXT_PRIMARY, UiTheme.INPUT, font).setPlaceholder("Price per unit (e.g. 150)");
        v.addChild(priceField);

        v.addChild(new SizedBox(0, 4));

        createBtn = btn(createSellMode ? "SUBMIT SELL ORDER" : "SUBMIT BUY ORDER",
                createSellMode ? UiTheme.DANGER_DEEP : UiTheme.SUCCESS_DEEP,
                createSellMode ? UiTheme.DANGER : UiTheme.SUCCESS).onPress(this::submitOffer);
        v.addChild(createBtn);

        createOfferErrorLabel = TextWidget.label("", RED);
        createOfferErrorLabel.setVisible(false);
        v.addChild(createOfferErrorLabel);

        updateCreateOfferLabels();
        return v;
    }

    private void updateCreateOfferLabels() {
        if (sellModeBtn != null) {
            sellModeBtn.setActive(createSellMode);
        }
        if (buyModeBtn != null) {
            buyModeBtn.setActive(!createSellMode);
        }
        if (createBtn != null) {
            createBtn.setLabel(createSellMode ? "SUBMIT SELL ORDER" : "SUBMIT BUY ORDER");
            createBtn.setColors(createSellMode ? UiTheme.DANGER_DEEP : UiTheme.SUCCESS_DEEP,
                                createSellMode ? UiTheme.DANGER : UiTheme.SUCCESS);
        }
        if (maxQtyBtn != null) {
            maxQtyBtn.setVisible(createSellMode);
        }
        if (infiniteBuyBtn != null) {
            infiniteBuyBtn.setVisible(!createSellMode);
            infiniteBuyBtn.setColors(isCreateInfinite ? UiTheme.SUCCESS_DEEP : PANEL, isCreateInfinite ? UiTheme.SUCCESS : CARD_HOVER);
        }
    }

    private void submitOffer() {
        if (createOfferErrorLabel != null) createOfferErrorLabel.setVisible(false);
        String itemId = itemIdField != null && !itemIdField.getValue().trim().isEmpty() ? itemIdField.getValue().trim() : selectedItemId;
        if ((itemId == null || itemId.isEmpty()) && cachedDetail != null) {
            itemId = cachedDetail.itemId;
        }
        if (itemId == null || itemId.isEmpty()) {
            showCreateError("Item ID is required.");
            return;
        }
        String priceStr = priceField.getValue().trim();
        if (priceStr.isEmpty()) { showCreateError("Price is required."); return; }
        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                showCreateError("Price must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            showCreateError("Price must be a valid number.");
            return;
        }

        String qtyStr = qtyField != null ? qtyField.getValue().trim() : "";
        if (qtyStr.equals("\u221E")) {
            isCreateInfinite = true;
        } else if (!qtyStr.isEmpty() && !qtyStr.contains("\u221E")) {
            isCreateInfinite = false;
        }

        int qty = 1;
        boolean inf = !createSellMode && isCreateInfinite;
        if (!inf) {
            try {
                qty = Integer.parseInt(qtyStr);
                if (qty <= 0) { showCreateError("Quantity must be greater than 0."); return; }
            } catch (NumberFormatException ignored) {
                showCreateError("Quantity must be a valid number.");
                return;
            }
        }
        if (createSellMode) {
            int stock = getVaultStockForItem(itemId);
            if (stock > 0 && qty > stock) {
                showCreateError(isFluidCommodity(itemId)
                        ? "Not enough fluid in tank. You have " + formatFluidAmountDetailed(stock) + "."
                        : "Not enough in vault. You have " + formatItemAmount(stock) + ".");
                return;
            }
        } else if (!inf) {
            BigDecimal totalCost = price.multiply(BigDecimal.valueOf(qty));
            try {
                BigDecimal balance = new BigDecimal(cachedBalance);
                if (totalCost.compareTo(balance) > 0) {
                    showCreateError("Insufficient funds. Balance: " + cachedBalance + ".");
                    return;
                }
            } catch (NumberFormatException ignored) {
                showCreateError("Cannot verify balance. Try again.");
                return;
            }
        }
        String actionStr = createSellMode ? "Sell" : "Buy";
        BigDecimal tot = price.multiply(BigDecimal.valueOf(qty));
        String totStr = inf ? "∞ (Per unit: " + price.toPlainString() + ")" : tot.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        String dispName = itemId;
        Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(itemId));
        String commodityType = fluid != net.minecraft.world.level.material.Fluids.EMPTY ? "FLUID" : "ITEM";
        if (cachedDetail != null && cachedDetail.itemId.equalsIgnoreCase(itemId)) {
            dispName = cachedDetail.displayName;
        } else {
            net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
            if (it != net.minecraft.world.item.Items.AIR) {
                dispName = new ItemStack(it).getHoverName().getString();
            } else if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                dispName = new FluidStack(fluid, 1000).getDisplayName().getString();
            }
        }
        pendingConfirmation = new PendingOrderExecution(itemId, qty, price.toPlainString(), createSellMode, inf, actionStr, dispName, totStr, commodityType);
        switchView(viewMode);
    }

    private void showCreateError(String msg) {
        if (createOfferErrorLabel != null) {
            createOfferErrorLabel.setText(msg);
            createOfferErrorLabel.setVisible(true);
        }
    }

    private static boolean isFluidCommodity(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        try {
            Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(itemId));
            return fluid != net.minecraft.world.level.material.Fluids.EMPTY
                    && !fluid.getFluidType().isAir();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void switchView(int mode) {
        com.nstut.Economy.LOGGER.info("[MarketScreen] switchView mode={}, current viewMode={}", mode, viewMode);
        com.nstut.Economy.LOGGER.info("[MarketScreen] switchView browser={}, detail={}, createOffer={}", browser, detail, createOffer);
        com.nstut.Economy.LOGGER.info("[MarketScreen] switchView itemIdField={}, qtyField={}, priceField={}", itemIdField, qtyField, priceField);
        viewMode = mode;
        savedViewMode = mode;
        if (mode == 0) selectedItemId = null;
        if (browser != null) browser.setVisible(mode == 0);
        if (detail != null) detail.setVisible(mode == 1);
        if (createOffer != null) createOffer.setVisible(mode == 2);
        if (ordersView != null) ordersView.setVisible(mode == 3);
        if (containersView != null) containersView.setVisible(mode == 4);
        if (portfolioView != null) portfolioView.setVisible(mode == 5);
        if (mode == 2) {
            updateCreateOfferLabels();
            // Reset dropdown guard whenever we (re-)enter the form
            itemSearchAutoFilled = selectedItemId; // pre-filled IDs shouldn't auto-open dropdown
            pendingDropdown = null;
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
            String idToFetch = itemIdField != null && !itemIdField.getValue().trim().isEmpty() ? itemIdField.getValue().trim() : selectedItemId;
            if (idToFetch != null && !idToFetch.isEmpty()) {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(idToFetch));
            }
        } else {
            pendingDropdown = null;
        }

        if (browseBtn != null) {
            browseBtn.setVisible(true);
            browseBtn.setActive(mode == 0);
        }
        if (containersBtn != null) {
            containersBtn.setVisible(true);
            containersBtn.setActive(mode == 4);
        }
        if (portfolioBtn != null) {
            portfolioBtn.setVisible(true);
            portfolioBtn.setActive(mode == 5);
        }
        if (newOrderBtn != null) {
            newOrderBtn.setVisible(true);
            newOrderBtn.setActive(mode == 2);
        }
        if (orderHistoryBtn != null) {
            orderHistoryBtn.setVisible(true);
            orderHistoryBtn.setActive(mode == 3);
        }
        if (searchField != null) {
            searchField.setVisible(mode == 0 && pendingConfirmation == null);
            if (mode != 0 || pendingConfirmation != null) searchField.getEditBox().setFocused(false);
        }
        if (activeOrdersSearchField != null) {
            activeOrdersSearchField.setVisible(mode == 3 && ordersSubTab == 0 && pendingConfirmation == null);
            if (mode != 3 || ordersSubTab != 0 || pendingConfirmation != null) activeOrdersSearchField.getEditBox().setFocused(false);
        }
        if (historySearchField != null) {
            historySearchField.setVisible(mode == 3 && ordersSubTab == 1 && pendingConfirmation == null);
            if (mode != 3 || ordersSubTab != 1 || pendingConfirmation != null) historySearchField.getEditBox().setFocused(false);
        }
        if (itemIdField != null) {
            itemIdField.setVisible(mode == 2 && pendingConfirmation == null);
            if (mode != 2 || pendingConfirmation != null) itemIdField.getEditBox().setFocused(false);
        }
        if (qtyField != null) {
            qtyField.setVisible(mode == 2 && pendingConfirmation == null);
            if (mode != 2 || pendingConfirmation != null) qtyField.getEditBox().setFocused(false);
        }
        if (priceField != null) {
            priceField.setVisible(mode == 2 && pendingConfirmation == null);
            if (mode != 2 || pendingConfirmation != null) priceField.getEditBox().setFocused(false);
        }

        if (root != null) {
            root.layoutTree(this.font, left(), top(), SCREEN_W, SCREEN_H);
        }
        syncEditBoxes();

        if (mode == 1 && cachedDetail != null) {
            if (askList != null) askList.resetScroll();
            if (bidList != null) bidList.resetScroll();
        }
        if (mode == 0) MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
        com.nstut.Economy.LOGGER.info("[MarketScreen] switchView done. viewMode={}", viewMode);
    }

    private List<MarketNetwork.ItemCardData> filterCards() {
        List<MarketNetwork.ItemCardData> f = new ArrayList<>();
        String q = searchQuery.toLowerCase().trim();
        for (MarketNetwork.ItemCardData c : cachedCards) {
            if (!q.isEmpty() && !c.displayName.toLowerCase().contains(q) && !c.itemId.toLowerCase().contains(q)) {
                continue;
            }
            if (browseFilterMode == 1 && c.offerCount <= 0) {
                continue; // Active Trades Only
            }
            if (!matchesCommodityTypeFilter(c.itemId, c.commodityType, browseCommodityTypeMode)) {
                continue;
            }
            f.add(c);
        }

        f.sort((a, b) -> {
            if (browseSortMode == 0) { // Price Low to High
                BigDecimal pa = parsePrice(a.globalPrice);
                BigDecimal pb = parsePrice(b.globalPrice);
                return pa.compareTo(pb);
            } else if (browseSortMode == 1) { // Price High to Low
                BigDecimal pa = parsePrice(a.globalPrice);
                BigDecimal pb = parsePrice(b.globalPrice);
                return pb.compareTo(pa);
            } else if (browseSortMode == 2) { // Name A-Z
                return a.displayName.compareToIgnoreCase(b.displayName);
            } else if (browseSortMode == 3) { // Orders: Most
                return Integer.compare(b.offerCount, a.offerCount);
            }
            return 0;
        });

        return f;
    }

    private List<HistoryEntry> filterHistory() {
        List<HistoryEntry> f = new ArrayList<>();
        if (cachedHistory == null) return f;
        String q = historySearchQuery.toLowerCase().trim();
        for (HistoryEntry e : cachedHistory) {
            if (e == null) continue;
            if (historyFilterMode == 1 && !e.wasSell) continue; // Sales Only
            if (historyFilterMode == 2 && e.wasSell) continue;  // Purchases Only
            if (!matchesCommodityTypeFilter(e.itemId, null, historyCommodityTypeMode)) continue;
            if (!q.isEmpty()) {
                boolean matchName = e.displayName.toLowerCase().contains(q);
                boolean matchId = e.itemId.toLowerCase().contains(q);
                boolean matchPlayer = e.counterparty.toLowerCase().contains(q);
                if (!matchName && !matchId && !matchPlayer) continue;
            }
            f.add(e);
        }

        f.sort((a, b) -> {
            if (historySortMode == 0) { // Newest First
                return Long.compare(b.timestamp, a.timestamp);
            } else if (historySortMode == 1) { // Oldest First
                return Long.compare(a.timestamp, b.timestamp);
            } else if (historySortMode == 2) { // Highest Total Price
                BigDecimal totalA = new BigDecimal(a.price).multiply(BigDecimal.valueOf(a.quantity));
                BigDecimal totalB = new BigDecimal(b.price).multiply(BigDecimal.valueOf(b.quantity));
                return totalB.compareTo(totalA);
            }
            return 0;
        });

        return f;
    }

    private BigDecimal parsePrice(String s) {
        if (s == null || s.equals("--") || s.isEmpty()) return BigDecimal.valueOf(999999999);
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.valueOf(999999999);
        }
    }

    /** Searches the item and fluid registries for entries whose display name or registry id
     *  contains the query (case-insensitive). Returns at most 50 results. */
    private List<ItemSearchResult> getItemSearchResults(String query) {
        if (query == null || query.length() < 2) return Collections.emptyList();
        String q = query.toLowerCase(java.util.Locale.ROOT);
        List<ItemSearchResult> results = new ArrayList<>();

        for (net.minecraft.resources.ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            String displayName = new ItemStack(item).getHoverName().getString();
            String rlStr = rl.toString();
            if (displayName.toLowerCase(java.util.Locale.ROOT).contains(q) || rlStr.contains(q)) {
                results.add(new ItemSearchResult(rlStr, displayName));
                if (results.size() >= 50) break;
            }
        }

        for (net.minecraft.resources.ResourceLocation rl : BuiltInRegistries.FLUID.keySet()) {
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            if (!CommodityUtil.isCanonicalFluid(fluid)) continue;
            String displayName = new FluidStack(fluid, 1000).getDisplayName().getString();
            String rlStr = rl.toString();
            if (displayName.toLowerCase(java.util.Locale.ROOT).contains(q) || rlStr.contains(q)) {
                results.add(new ItemSearchResult(rlStr, displayName));
                if (results.size() >= 50) break;
            }
        }

        return results;
    }

    private UIComponent buildOrdersView(Font font) {
        VStack v = new VStack().gap(4);

        v.addChild(TextWidget.centered("ORDERS", ACCENT));
        v.addChild(new Divider(PANEL_BORDER));

        HStack subTabs = new HStack().gap(4);
        ordersActiveTabBtn = btn("Active Orders (" + cachedActiveOrders.size() + ")", ordersSubTab == 0 ? ACCENT_DIM : PANEL, CARD_HOVER).onPress(() -> {
            ordersSubTab = 0;
            savedOrdersSubTab = 0;
            updateOrdersSubTabs();
        });
        ordersActiveTabBtn.flex();
        subTabs.addChild(ordersActiveTabBtn);

        ordersHistoryTabBtn = btn("Trade History (" + cachedHistory.size() + ")", ordersSubTab == 1 ? ACCENT_DIM : PANEL, CARD_HOVER).onPress(() -> {
            ordersSubTab = 1;
            savedOrdersSubTab = 1;
            updateOrdersSubTabs();
        });
        ordersHistoryTabBtn.flex();
        subTabs.addChild(ordersHistoryTabBtn);
        v.addChild(subTabs);

        v.addChild(new Divider(PANEL_BORDER));

        activeOrdersContainer = buildActiveOrdersList(font);
        activeOrdersContainer.flex();
        activeOrdersContainer.setVisible(ordersSubTab == 0);
        v.addChild(activeOrdersContainer);

        UIComponent history = buildHistory(font);
        history.flex();
        history.setVisible(ordersSubTab == 1);
        this.historyView = history;
        v.addChild(history);

        return v;
    }

    private void updateOrdersSubTabs() {
        if (ordersActiveTabBtn != null) {
            ordersActiveTabBtn.setColors(ordersSubTab == 0 ? ACCENT_DIM : PANEL, CARD_HOVER);
            ordersActiveTabBtn.setLabel("Active Orders (" + cachedActiveOrders.size() + ")");
        }
        if (ordersHistoryTabBtn != null) {
            ordersHistoryTabBtn.setColors(ordersSubTab == 1 ? ACCENT_DIM : PANEL, CARD_HOVER);
            ordersHistoryTabBtn.setLabel("Trade History (" + cachedHistory.size() + ")");
        }
        if (activeOrdersContainer != null) {
            activeOrdersContainer.setVisible(ordersSubTab == 0);
        }
        if (historyView != null) {
            historyView.setVisible(ordersSubTab == 1);
        }
        if (activeOrdersSearchField != null) {
            activeOrdersSearchField.setVisible(viewMode == 3 && ordersSubTab == 0 && pendingConfirmation == null && editingOrder == null);
            if (viewMode != 3 || ordersSubTab != 0 || pendingConfirmation != null || editingOrder != null) {
                activeOrdersSearchField.getEditBox().setFocused(false);
            }
        }
        if (historySearchField != null) {
            historySearchField.setVisible(viewMode == 3 && ordersSubTab == 1 && pendingConfirmation == null && editingOrder == null);
            if (viewMode != 3 || ordersSubTab != 1 || pendingConfirmation != null || editingOrder != null) {
                historySearchField.getEditBox().setFocused(false);
            }
        }
    }

    private UIComponent buildActiveOrdersList(Font font) {
        VStack v = new VStack().gap(4);

        activeOrdersSearchQuery = savedActiveOrdersSearchQuery;
        activeOrdersSearchField = new EditBoxWrapper(60, TEXT_PRIMARY, UiTheme.INPUT, font).setPlaceholder("Search item name or ID...");
        if (savedActiveOrdersSearchQuery != null && !savedActiveOrdersSearchQuery.isEmpty()) {
            activeOrdersSearchField.setValue(savedActiveOrdersSearchQuery);
        }
        v.addChild(activeOrdersSearchField);

        HStack bar = new HStack().gap(4);
        activeOrdersFilterBtn = btn("Order\n" + getActiveOrdersFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            activeOrdersFilterMode = (activeOrdersFilterMode + 1) % 4;
            savedActiveOrdersFilterMode = activeOrdersFilterMode;
            activeOrdersFilterBtn.setLabel("Order\n" + getActiveOrdersFilterLabel());
        });
        activeOrdersFilterBtn.flex();
        bar.addChild(activeOrdersFilterBtn);

        activeOrdersCommodityTypeBtn = btn("Product\n" + getCommodityTypeFilterLabel(activeOrdersCommodityTypeMode), PANEL, CARD_HOVER).onPress(() -> {
            activeOrdersCommodityTypeMode = (activeOrdersCommodityTypeMode + 1) % 3;
            savedActiveOrdersCommodityTypeMode = activeOrdersCommodityTypeMode;
            activeOrdersCommodityTypeBtn.setLabel("Product\n" + getCommodityTypeFilterLabel(activeOrdersCommodityTypeMode));
        });
        activeOrdersCommodityTypeBtn.flex();
        bar.addChild(activeOrdersCommodityTypeBtn);

        activeOrdersSortBtn = btn("Sort\n" + getActiveOrdersSortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            activeOrdersSortMode = (activeOrdersSortMode + 1) % 4;
            savedActiveOrdersSortMode = activeOrdersSortMode;
            activeOrdersSortBtn.setLabel("Sort\n" + getActiveOrdersSortLabel());
        });
        activeOrdersSortBtn.flex();
        bar.addChild(activeOrdersSortBtn);
        v.addChild(bar);

        v.addChild(new Divider(PANEL_BORDER));

        ScrollList list = new ScrollList(
            () -> Math.max(1, filterActiveOrders().size()),
            36,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<MarketNetwork.ActiveOrderEntry> entries = filterActiveOrders();
                if (entries.isEmpty()) {
                    String msg = cachedActiveOrders.isEmpty() ? "No active open orders" : "No orders match filter";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 12, TEXT_MUTED);
                    return;
                }
                if (idx >= entries.size()) return;
                MarketNetwork.ActiveOrderEntry e = entries.get(idx);

                UiRender.roundedOutline(g, rx, ry + 1, rw, 34, UiTheme.RADIUS_SM,
                        hover ? CARD_HOVER : CARD_BG, PANEL_BORDER);

                // Icon
                renderCommodityIcon(g, e.itemId, rx + 4, ry + 10);

                // Type & Name
                String typeTag = e.isSell ? "SELL" : "BUY";
                int typeColor = e.isSell ? RED : GREEN;
                g.drawString(fnt, typeTag, rx + 24, ry + 4, typeColor);

                int nameX = rx + 24 + fnt.width(typeTag) + 6;
                int cancelW = 38;
                int cancelX = rx + rw - cancelW - 4;
                int editW = 30;
                int editX = cancelX - editW - 4;
                int maxNameW = Math.max(30, editX - 4 - nameX);
                String nameText = fnt.plainSubstrByWidth(getItemDisplayName(e.itemId, e.displayName), maxNameW);
                g.drawString(fnt, nameText, nameX, ry + 4, TEXT_PRIMARY);

                // Price & Quantity
                renderSmallCoin(g, rx + 24, ry + 19);
                boolean fluidOrder = isFluidCommodity(e.itemId);
                String qtyText = e.isInfinite
                        ? "Qty: \u221e"
                        : (fluidOrder
                        ? "Qty: " + formatFluidAmount(e.quantity) + " / " + formatFluidAmount(e.initialQuantity)
                        : "Qty: " + formatItemAmount(e.quantity) + " / " + formatItemAmount(e.initialQuantity));
                String priceQty = e.price + " | " + qtyText;
                g.drawString(fnt, priceQty, rx + 35, ry + 18, ACCENT);

                // Action buttons on right side
                int btnY = ry + 8, btnH = 20;

                boolean editHover = mx >= editX && mx < editX + editW && my >= btnY && my < btnY + btnH;
                boolean cancelHover = mx >= cancelX && mx < cancelX + cancelW && my >= btnY && my < btnY + btnH;

                int editBg = editHover ? CARD_HOVER : PANEL;
                UiRender.pill(g, editX, btnY, editW, btnH, editBg, ACCENT);
                g.drawString(fnt, "Edit", editX + (editW - fnt.width("Edit")) / 2, btnY + 6, ACCENT);

                int cancelBg = cancelHover ? UiRender.mix(UiTheme.DANGER_DEEP, UiTheme.DANGER, 0.28F) : UiTheme.DANGER_DEEP;
                UiRender.pill(g, cancelX, btnY, cancelW, btnH, cancelBg, RED);
                g.drawString(fnt, "Cancel", cancelX + (cancelW - fnt.width("Cancel")) / 2, btnY + 6, TEXT_PRIMARY);
            },
            (idx, btn, mx, my) -> {
                List<MarketNetwork.ActiveOrderEntry> entries = filterActiveOrders();
                if (entries.isEmpty() || idx >= entries.size()) return;
                MarketNetwork.ActiveOrderEntry e = entries.get(idx);
                int listX = activeOrdersContainer != null ? activeOrdersContainer.getX() : left() + SIDEBAR_W + 16;
                int listW = activeOrdersContainer != null ? activeOrdersContainer.getWidth() - 8 : (SCREEN_W - SIDEBAR_W - 32);
                int rx = listX;
                int rw = listW;
                int cancelW = 38;
                int cancelX = rx + rw - cancelW - 4;
                int editW = 30;
                int editX = cancelX - editW - 4;

                if (mx >= cancelX && mx <= cancelX + cancelW) {
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId));
                } else if (mx >= editX && mx < cancelX) {
                    openEditOrderModal(e);
                }
            },
            PANEL, ACCENT_DIM
        );
        list.flex();
        v.addChild(list);
        return v;
    }

    private void openEditOrderModal(java.util.UUID orderId, String itemId, String displayName, String price, int quantity, boolean isSell, boolean isInfinite) {
        this.editingOrder = new MarketNetwork.ActiveOrderEntry(orderId, itemId, displayName, price, quantity, quantity, isSell, isInfinite, 0);
        this.editIsInfinite = isInfinite;
        this.editErrorMsg = null;
        if (editQtyField != null) {
            editQtyField.setValue(isInfinite ? "\u221E" : String.valueOf(quantity));
            editQtyField.getEditBox().setFocused(true);
            this.setFocused(editQtyField.getEditBox());
        }
        if (editPriceField != null) {
            editPriceField.setValue(price);
            editPriceField.getEditBox().setFocused(false);
        }
        if (activeOrdersSearchField != null) {
            activeOrdersSearchField.setVisible(false);
            activeOrdersSearchField.getEditBox().setFocused(false);
        }
        if (historySearchField != null) {
            historySearchField.setVisible(false);
            historySearchField.getEditBox().setFocused(false);
        }
    }

    private void openEditOrderModal(MarketNetwork.ActiveOrderEntry e) {
        openEditOrderModal(e.orderId, e.itemId, e.displayName, e.price, e.quantity, e.isSell, e.isInfinite);
    }

    private void openEditOrderModal(MarketNetwork.OrderEntry e) {
        boolean isSell = cachedDetail != null && cachedDetail.asks.contains(e);
        String itemId = cachedDetail != null ? cachedDetail.itemId : "";
        String displayName = cachedDetail != null ? cachedDetail.displayName : "";
        openEditOrderModal(e.orderId, itemId, displayName, e.price, e.quantity, isSell, e.isInfinite);
    }

    private void renderEditOrderModal(GuiGraphics g, int mx, int my) {
        if (editingOrder == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        int modalW = 220;
        int modalH = 140;
        int modalX = left() + (SCREEN_W - modalW) / 2;
        int modalY = top() + (SCREEN_H - modalH) / 2;

        UiRender.roundedRect(g, left(), top(), SCREEN_W, SCREEN_H,
                UiTheme.RADIUS_LG, UiTheme.BACKDROP);
        UiRender.surface(g, modalX, modalY, modalW, modalH, UiTheme.RADIUS_LG,
                UiTheme.SURFACE_RAISED, ACCENT, true);

        // Title
        String title = "EDIT ORDER";
        int titleW = font.width(title);
        g.drawString(font, title, modalX + (modalW - titleW) / 2, modalY + 8, ACCENT);
        g.fill(modalX + 10, modalY + 20, modalX + modalW - 10, modalY + 21, PANEL_BORDER);

        String typeTag = editingOrder.isSell ? "[SELL ORDER]" : "[BUY ORDER]";
        int typeColor = editingOrder.isSell ? RED : GREEN;
        g.drawString(font, typeTag, modalX + 12, modalY + 26, typeColor);

        String itemStr = font.plainSubstrByWidth(getItemDisplayName(editingOrder.itemId, editingOrder.displayName), modalW - 100);
        g.drawString(font, itemStr, modalX + 12 + font.width(typeTag) + 6, modalY + 26, TEXT_PRIMARY);

        if (!editingOrder.isSell) {
            // Infinite toggle button
            int infX = modalX + 184;
            int infY = modalY + 42;
            int infW = 24;
            int infH = 18;
            boolean infHover = mx >= infX && mx < infX + infW && my >= infY && my < infY + infH;
            int infBg = editIsInfinite ? UiTheme.SUCCESS_DEEP : (infHover ? CARD_HOVER : PANEL);
            UiRender.pill(g, infX, infY, infW, infH, infBg, ACCENT);
            g.drawString(font, "\u221E", infX + (infW - font.width("\u221E")) / 2, infY + 5, TEXT_PRIMARY);
            if (infHover) {
                pendingTooltip = "Infinite Order: Buy order stays open continuously";
            }
        }

        // Quantity field
        if (editQtyField != null) {
            int qtyW = editingOrder.isSell ? 196 : 166;
            editQtyField.layout(modalX + 12, modalY + 42, qtyW, 18);
            editQtyField.setVisible(true);
            editQtyField.getEditBox().visible = true;
            editQtyField.render(g, font, mx, my, 0);
            editQtyField.getEditBox().setX(modalX + 16);
            editQtyField.getEditBox().setY(modalY + 46);
            editQtyField.getEditBox().setWidth(qtyW - 8);
            editQtyField.getEditBox().setHeight(10);
            editQtyField.getEditBox().render(g, mx, my, 0);
        }

        // Price field
        if (editPriceField != null) {
            int priceW = 196;
            editPriceField.layout(modalX + 12, modalY + 64, priceW, 18);
            editPriceField.setVisible(true);
            editPriceField.getEditBox().visible = true;
            editPriceField.render(g, font, mx, my, 0);
            editPriceField.getEditBox().setX(modalX + 16);
            editPriceField.getEditBox().setY(modalY + 68);
            editPriceField.getEditBox().setWidth(188);
            editPriceField.getEditBox().setHeight(10);
            editPriceField.getEditBox().render(g, mx, my, 0);
        }

        // Save Button
        int btnW = 85;
        int btnH = 16;
        int saveX = modalX + 18;
        int saveY = modalY + 105;
        boolean saveHover = mx >= saveX && mx <= saveX + btnW && my >= saveY && my <= saveY + btnH;
        int saveBg = saveHover ? UiRender.mix(UiTheme.ACCENT_DEEP, UiTheme.ACCENT, 0.18F) : UiTheme.ACCENT_DEEP;
        UiRender.pill(g, saveX, saveY, btnW, btnH, saveBg, ACCENT);
        g.drawString(font, "Save", saveX + (btnW - font.width("Save")) / 2, saveY + 4, ACCENT);

        // Cancel Button
        int cancelX = modalX + modalW - btnW - 18;
        int cancelY = modalY + 105;
        boolean cancelHover = mx >= cancelX && mx <= cancelX + btnW && my >= cancelY && my <= cancelY + btnH;
        int cancelBg = cancelHover ? UiRender.mix(UiTheme.DANGER_DEEP, UiTheme.DANGER, 0.28F) : UiTheme.DANGER_DEEP;
        UiRender.pill(g, cancelX, cancelY, btnW, btnH, cancelBg, RED);
        g.drawString(font, "Cancel", cancelX + (btnW - font.width("Cancel")) / 2, cancelY + 4, 0xFFFFFFFF);

        if (editErrorMsg != null && !editErrorMsg.isEmpty()) {
            int errW = font.width(editErrorMsg);
            g.drawString(font, editErrorMsg, modalX + (modalW - errW) / 2, modalY + 86, RED);
        }

        g.pose().popPose();
    }

    private void submitEditOrder() {
        if (editingOrder == null) return;
        editErrorMsg = null;
        String priceStr = editPriceField != null ? editPriceField.getValue().trim() : "";
        if (priceStr.isEmpty()) { editErrorMsg = "Price required"; return; }
        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) <= 0) { editErrorMsg = "Price must be > 0"; return; }
        } catch (Exception e) {
            editErrorMsg = "Invalid price";
            return;
        }

        String qtyStr = editQtyField != null ? editQtyField.getValue().trim() : "";
        if (qtyStr.equals("\u221E")) {
            editIsInfinite = true;
        } else if (editIsInfinite && !qtyStr.isEmpty() && !qtyStr.contains("\u221E")) {
            editIsInfinite = false;
        }

        int newQty = editingOrder.quantity > 0 ? editingOrder.quantity : 1;
        if (!editIsInfinite) {
            try {
                newQty = Integer.parseInt(qtyStr);
                if (newQty <= 0) { editErrorMsg = "Qty must be > 0"; return; }
            } catch (Exception e) {
                editErrorMsg = "Invalid quantity";
                return;
            }
        } else {
            try {
                if (!qtyStr.equals("\u221E") && !qtyStr.isEmpty()) {
                    newQty = Integer.parseInt(qtyStr);
                }
            } catch (Exception ignored) {}
            if (newQty <= 0) newQty = 1;
        }

        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.EditOrderPacket(editingOrder.orderId, newQty, price.toPlainString(), editIsInfinite));
        closeEditModal();
    }

    private void closeEditModal() {
        editingOrder = null;
        editErrorMsg = null;
        if (editQtyField != null) editQtyField.setVisible(false);
        if (editPriceField != null) editPriceField.setVisible(false);
        if (activeOrdersSearchField != null) {
            activeOrdersSearchField.setVisible(viewMode == 3 && ordersSubTab == 0 && pendingConfirmation == null);
        }
        if (historySearchField != null) {
            historySearchField.setVisible(viewMode == 3 && ordersSubTab == 1 && pendingConfirmation == null);
        }
    }

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MM/dd HH:mm");

    private UIComponent buildHistory(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(TextWidget.centered("ORDER HISTORY", ACCENT));

        historySearchQuery = savedHistorySearchQuery;
        historySearchField = new EditBoxWrapper(60, TEXT_PRIMARY, UiTheme.INPUT, font).setPlaceholder("Search item or player...");
        if (savedHistorySearchQuery != null && !savedHistorySearchQuery.isEmpty()) {
            historySearchField.setValue(savedHistorySearchQuery);
        }

        v.addChild(historySearchField);

        HStack bar = new HStack().gap(4);
        historyFilterBtn = btn("Trade\n" + getHistoryFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            historyFilterMode = (historyFilterMode + 1) % 3;
            savedHistoryFilterMode = historyFilterMode;
            historyFilterBtn.setLabel("Trade\n" + getHistoryFilterLabel());
        });
        historyFilterBtn.flex();
        bar.addChild(historyFilterBtn);

        historyCommodityTypeBtn = btn("Product\n" + getCommodityTypeFilterLabel(historyCommodityTypeMode), PANEL, CARD_HOVER).onPress(() -> {
            historyCommodityTypeMode = (historyCommodityTypeMode + 1) % 3;
            savedHistoryCommodityTypeMode = historyCommodityTypeMode;
            historyCommodityTypeBtn.setLabel("Product\n" + getCommodityTypeFilterLabel(historyCommodityTypeMode));
        });
        historyCommodityTypeBtn.flex();
        bar.addChild(historyCommodityTypeBtn);

        historySortBtn = btn("Sort\n" + getHistorySortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            historySortMode = (historySortMode + 1) % 3;
            savedHistorySortMode = historySortMode;
            historySortBtn.setLabel("Sort\n" + getHistorySortLabel());
        });
        historySortBtn.flex();
        bar.addChild(historySortBtn);
        v.addChild(bar);

        v.addChild(new Divider(PANEL_BORDER));

        ScrollList list = new ScrollList(
            () -> Math.max(1, filterHistory().size()),
            28,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<HistoryEntry> entries = filterHistory();
                if (entries == null || entries.isEmpty()) {
                    String msg = "No trade history recorded yet";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 8, TEXT_MUTED);
                    return;
                }
                if (idx >= entries.size()) return;
                HistoryEntry e = entries.get(idx);

                UiRender.roundedOutline(g, rx, ry + 1, rw, 26, UiTheme.RADIUS_SM,
                        hover ? CARD_HOVER : CARD_BG, PANEL_BORDER);

                // Item icon centered vertically
                renderCommodityIcon(g, e.itemId, rx + 4, ry + 6);

                // Top row (y + 4): Type badge + Item name + Timestamp
                String typeTag = e.wasSell ? "SELL" : "BUY";
                int typeColor = e.wasSell ? RED : GREEN;
                g.drawString(fnt, typeTag, rx + 24, ry + 4, typeColor);

                String dateStr = DATE_FMT.format(new Date(e.timestamp));
                int dateW = fnt.width(dateStr);

                int nameX = rx + 24 + fnt.width(typeTag) + 6;
                int maxNameW = Math.max(30, (rx + rw - dateW - 8) - nameX);
                String nameText = fnt.plainSubstrByWidth(getItemDisplayName(e.itemId, e.displayName), maxNameW);
                g.drawString(fnt, nameText, nameX, ry + 4, TEXT_PRIMARY);

                g.drawString(fnt, dateStr, rx + rw - dateW - 4, ry + 4, TEXT_MUTED);

                // Bottom row (y + 16): Coin icon + Price x qty + Counterparty
                renderSmallCoin(g, rx + 24, ry + 17);
                String pqText = e.price + " \u00d7 " + (isFluidCommodity(e.itemId)
                        ? formatFluidAmount(e.quantity)
                        : formatItemAmount(e.quantity));
                g.drawString(fnt, pqText, rx + 35, ry + 16, ACCENT);

                String cpText = (e.wasSell ? "to " : "from ") + e.counterparty;
                int cpW = fnt.width(cpText);
                g.drawString(fnt, cpText, rx + rw - cpW - 4, ry + 16, TEXT_MUTED);
            },
            (idx, btn) -> { /* read-only list */ },
            PANEL, ACCENT_DIM);

        v.addChild(list);
        return v;
    }

    private UIComponent buildContainers(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(TextWidget.centered("CONTAINER OVERVIEW", ACCENT));
        v.addChild(new Divider(PANEL_BORDER));

        UIComponent statsRow = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 24; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                int vaultCount = 0;
                int tankCount = 0;
                long totalItems = 0;
                long totalFluid = 0;
                for (MarketNetwork.VaultDetailEntry e : cachedContainerEntries) {
                    if (e.tank) {
                        tankCount++;
                        totalFluid += e.totalItems;
                    } else {
                        vaultCount++;
                        totalItems += e.totalItems;
                    }
                }

                int boxW = (width - 12) / 4;
                int b1X = x;
                int b2X = b1X + boxW + 4;
                int b3X = b2X + boxW + 4;
                int b4X = b3X + boxW + 4;

                drawStatBox(g, fnt, b1X, y, boxW, height, "VAULTS", formatCompact(vaultCount), ACCENT);
                drawStatBox(g, fnt, b2X, y, boxW, height, "TANKS", formatCompact(tankCount), ACCENT);
                drawStatBox(g, fnt, b3X, y, boxW, height, "ITEMS", formatCompact(totalItems) + " items", GREEN);
                drawStatBox(g, fnt, b4X, y, boxW, height, "FLUID", formatCompact(totalFluid) + " mB", GREEN);

                if (my >= y && my < y + height) {
                    if (mx >= b1X && mx < b1X + boxW) {
                        pendingTooltip = "Vaults: Registered item-storage containers";
                    } else if (mx >= b2X && mx < b2X + boxW) {
                        pendingTooltip = "Tanks: Registered fluid-storage containers";
                    } else if (mx >= b3X && mx < b3X + boxW) {
                        pendingTooltip = "Items: Total item count stored across all Vaults";
                    } else if (mx >= b4X && mx < b4X + boxW) {
                        pendingTooltip = "Fluid: Total fluid stored across all Tanks";
                    }
                }
            }
        };
        v.addChild(statsRow);
        v.addChild(new Divider(PANEL_BORDER));

        ScrollList list = new ScrollList(
            () -> Math.max(1, cachedContainerEntries.size()),
            40,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                if (cachedContainerEntries.isEmpty()) {
                    String msg = "No Vault or Tank blocks registered yet";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 15, TEXT_MUTED);
                    return;
                }
                if (idx >= cachedContainerEntries.size()) return;
                MarketNetwork.VaultDetailEntry e = cachedContainerEntries.get(idx);

                UiRender.roundedOutline(g, rx, ry + 1, rw, 38, UiTheme.RADIUS_SM,
                        hover ? CARD_HOVER : CARD_BG, PANEL_BORDER);

                int typeIndex = 1;
                for (int i = 0; i < idx; i++) {
                    if (cachedContainerEntries.get(i).tank == e.tank) typeIndex++;
                }
                String containerTitle = (e.tank ? "Tank #" : "Vault #") + typeIndex;
                g.drawString(fnt, containerTitle, rx + 4, ry + 3, ACCENT);

                boolean isFull = e.usedSlots >= e.totalSlots;
                String badge = isFull ? "FULL" : "ACTIVE";
                int badgeW = fnt.width(badge) + 6;
                int badgeX = rx + rw - badgeW - 4;
                int badgeBg = isFull ? 0x40801818 : 0x40105028;
                int badgeBorder = isFull ? UiTheme.DANGER : UiTheme.SUCCESS;
                int badgeText = isFull ? UiTheme.DANGER : UiTheme.SUCCESS;

                UiRender.pill(g, badgeX, ry + 2, badgeW, 11, badgeBg, badgeBorder);
                g.drawString(fnt, badge, badgeX + 3, ry + 3, badgeText);

                String modeBadge = switch (e.mode) {
                    case 1 -> "INPUT ONLY";
                    case 2 -> "OUTPUT ONLY";
                    default -> "BOTH";
                };
                int modeW = fnt.width(modeBadge) + 6;
                int modeX = badgeX - modeW - 4;
                int modeBg = e.mode == 1 ? UiTheme.DANGER_DEEP : (e.mode == 2 ? UiTheme.SUCCESS_DEEP : UiTheme.ACCENT_DEEP);
                int modeBorder = e.mode == 1 ? UiTheme.DANGER : (e.mode == 2 ? UiTheme.SUCCESS : UiTheme.ACCENT);
                int modeText = modeBorder;

                UiRender.pill(g, modeX, ry + 2, modeW, 11, modeBg, modeBorder);
                g.drawString(fnt, modeBadge, modeX + 3, ry + 3, modeText);

                if (mx >= modeX && mx < modeX + modeW && my >= ry + 2 && my < ry + 13) {
                    String contents = e.tank ? "fluids" : "items";
                    pendingTooltip = switch (e.mode) {
                        case 1 -> "Input Only: Supplies " + contents + " to Sell Orders; purchases avoid this container.";
                        case 2 -> "Output Only: Receives bought " + contents + "; Sell Orders ignore this container.";
                        default -> "Both: Supplies Sell Orders and receives bought " + contents + ".";
                    };
                }

                String dimClean = e.dimension.replace("minecraft:", "");
                String locStr = dimClean + " (" + e.x + ", " + e.y + ", " + e.z + ")";
                g.drawString(fnt, locStr, rx + 4, ry + 17, TEXT_MUTED);

                String capacityStr = e.tank
                        ? formatFluidAmount(e.usedSlots) + "/" + formatFluidAmount(e.totalSlots)
                        : formatCompact(e.usedSlots) + "/" + formatCompact(e.totalSlots) + " Slots";
                int capacityW = fnt.width(capacityStr);
                g.drawString(fnt, capacityStr, rx + rw - capacityW - 4, ry + 17, TEXT_PRIMARY);

                int barX = rx + 4;
                int barY = ry + 29;
                String contentsStr = e.tank
                        ? (e.contentId.isEmpty() ? "Empty" : getItemDisplayName(e.contentId, e.contentId))
                        : formatItemAmount(e.totalItems);
                int contentsW = fnt.width(contentsStr);
                int barW = Math.max(20, rw - contentsW - 16);
                int barH = 5;
                int pct = e.totalSlots > 0 ? (int) ((long) e.usedSlots * barW / e.totalSlots) : 0;
                int fillClr = isFull ? RED : GREEN;
                UiRender.progressTrack(g, barX, barY, barW, barH,
                        barW <= 0 ? 0.0F : pct / (float) barW, fillClr);

                g.drawString(fnt, contentsStr, rx + rw - contentsW - 4, ry + 28, TEXT_MUTED);
            },
            (idx, btn) -> { /* read only */ },
            PANEL, ACCENT_DIM);

        list.flex();
        v.addChild(list);
        return v;
    }

    private UIComponent buildPortfolio(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(TextWidget.centered("PORTFOLIO PERFORMANCE", ACCENT));
        v.addChild(new Divider(PANEL_BORDER));

        // Summary Stats Row (fixed 24px height)
        UIComponent statsRow = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 24; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                BigDecimal latestNW = BigDecimal.ZERO;
                BigDecimal latestBal = BigDecimal.ZERO;
                BigDecimal latestAss = BigDecimal.ZERO;
                if (!cachedPortfolioPoints.isEmpty()) {
                    var last = cachedPortfolioPoints.get(cachedPortfolioPoints.size() - 1);
                    latestNW = new BigDecimal(last.netWorth);
                    latestBal = new BigDecimal(last.balance);
                    latestAss = new BigDecimal(last.assets);
                }

                int boxW = (width - 8) / 3;

                // Box 1: NET WORTH
                int b1X = x;
                UiRender.surface(g, b1X, y, boxW, height, UiTheme.RADIUS_SM,
                        CARD_BG, PANEL_BORDER, false);
                g.drawString(fnt, "NET WORTH", b1X + (boxW - fnt.width("NET WORTH")) / 2, y + 3, TEXT_MUTED);
                String nwStr = formatCompact(latestNW);
                int nwW = 10 + fnt.width(nwStr);
                int nwX = b1X + (boxW - nwW) / 2;
                renderSmallCoin(g, nwX - 1, y + 13);
                g.drawString(fnt, nwStr, nwX + 10, y + 13, ACCENT);

                // Box 2: CASH
                int b2X = b1X + boxW + 4;
                UiRender.surface(g, b2X, y, boxW, height, UiTheme.RADIUS_SM,
                        CARD_BG, PANEL_BORDER, false);
                g.drawString(fnt, "LIQUID CASH", b2X + (boxW - fnt.width("LIQUID CASH")) / 2, y + 3, TEXT_MUTED);
                String balStr = formatCompact(latestBal);
                int balW = 10 + fnt.width(balStr);
                int balX = b2X + (boxW - balW) / 2;
                renderSmallCoin(g, balX - 1, y + 13);
                g.drawString(fnt, balStr, balX + 10, y + 13, GREEN);

                // Box 3: ASSETS
                int b3X = b2X + boxW + 4;
                UiRender.surface(g, b3X, y, boxW, height, UiTheme.RADIUS_SM,
                        CARD_BG, PANEL_BORDER, false);
                g.drawString(fnt, "VAULT ASSETS", b3X + (boxW - fnt.width("VAULT ASSETS")) / 2, y + 3, TEXT_MUTED);
                String assStr = formatCompact(latestAss);
                int assW = 10 + fnt.width(assStr);
                int assX = b3X + (boxW - assW) / 2;
                renderSmallCoin(g, assX - 1, y + 13);
                g.drawString(fnt, assStr, assX + 10, y + 13, ACCENT);

                // Hover tooltips for Stat Boxes
                if (my >= y && my < y + height) {
                    if (mx >= b1X && mx < b1X + boxW) {
                        pendingTooltip = "Net Worth: Total financial value (Liquid Cash + Vault Assets)";
                    } else if (mx >= b2X && mx < b2X + boxW) {
                        pendingTooltip = "Liquid Cash: Available unspent wallet balance for trading & payments";
                    } else if (mx >= b3X && mx < b3X + boxW) {
                        pendingTooltip = "Vault Assets: Total estimated market valuation of all commodities in Vaults";
                    }
                }
            }
        };
        v.addChild(statsRow);
        v.addChild(new Divider(PANEL_BORDER));

        // Net Worth Trend Chart Component
        UIComponent chart = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 48; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                UiRender.surface(g, x, y, width, height, UiTheme.RADIUS_MD,
                        CHART_BG, PANEL_BORDER, false);

                List<MarketNetwork.PortfolioPointData> pts = cachedPortfolioPoints;
                if (pts.size() >= 2) {
                    int totalCount = pts.size();
                    int maxOffset = Math.max(0, totalCount - MAX_VISIBLE_CHART_STEPS);
                    if (portfolioChartOffset > maxOffset) portfolioChartOffset = maxOffset;
                    if (portfolioChartOffset < 0) portfolioChartOffset = 0;

                    int endIndex = Math.min(totalCount, Math.max(MAX_VISIBLE_CHART_STEPS, totalCount - portfolioChartOffset));
                    int startIndex = Math.max(0, endIndex - MAX_VISIBLE_CHART_STEPS);

                    List<MarketNetwork.PortfolioPointData> visiblePts = pts.subList(startIndex, endIndex);

                    double maxP = Double.MIN_VALUE, minP = Double.MAX_VALUE;
                    for (var cp : visiblePts) {
                        double nw = Double.parseDouble(cp.netWorth);
                        if (nw > maxP) maxP = nw;
                        if (nw < minP) minP = nw;
                    }
                    if (maxP == minP) { maxP += 10; minP = Math.max(0, minP - 10); }
                    double range = maxP - minP;

                    g.drawString(fnt, formatCompact(maxP), x + 3, y + 3, TEXT_MUTED);
                    g.drawString(fnt, formatCompact(minP), x + 3, y + height - 11, TEXT_MUTED);

                    double currentNW = Double.parseDouble(visiblePts.get(visiblePts.size() - 1).netWorth);
                    String currentPriceStr = formatCompact(currentNW);
                    int currentPriceW = fnt.width(currentPriceStr);
                    int badgeW = currentPriceW + 8;
                    int badgeH = 12;
                    int badgeX = x + width - badgeW - 4;
                    int rawY = y + height - 6 - (int)((currentNW - minP) / range * (height - 12));
                    int badgeY = Math.max(y + 2, Math.min(y + height - badgeH - 2, rawY - 5));

                    // LIVE Snap Button
                    String liveStr = portfolioChartOffset == 0 ? "LIVE" : "\u25B6 LIVE";
                    int liveW = fnt.width(liveStr) + 6;
                    int liveX = badgeX - liveW - 4;
                    int liveY = y + 2;
                    int liveH = 11;
                    portfolioLiveBtnX = liveX;
                    portfolioLiveBtnY = liveY;
                    portfolioLiveBtnW = liveW;
                    portfolioLiveBtnH = liveH;
                    boolean liveHover = mx >= liveX && mx < liveX + liveW && my >= liveY && my < liveY + liveH;
                    int liveBg = portfolioChartOffset > 0 ? (liveHover ? UiTheme.SUCCESS : UiTheme.SUCCESS_DEEP) : (liveHover ? CARD_HOVER : UiTheme.ACCENT_DEEP);
                    int liveBorder = portfolioChartOffset > 0 ? GREEN : (liveHover ? ACCENT : PANEL_BORDER);
                    UiRender.pill(g, liveX, liveY, liveW, liveH, liveBg, liveBorder);
                    g.drawString(fnt, liveStr, liveX + 3, liveY + 2, portfolioChartOffset > 0 ? 0xFFFFFFFF : ACCENT);
                    if (liveHover && portfolioChartOffset > 0) {
                        pendingTooltip = "Click to snap back to current live time";
                    }

                    int lineY = rawY;
                    int chartLeft = x + 26;
                    int chartRight = liveX - 4;
                    int dotStep = 4;
                    for (int lx = chartLeft; lx <= chartRight; lx += dotStep) {
                        g.fill(lx, lineY, Math.min(lx + 2, chartRight), lineY + 1, ACCENT_DIM);
                    }

                    // Render right side current Net Worth badge
                    UiRender.pill(g, badgeX, badgeY, badgeW, badgeH,
                            UiTheme.ACCENT_DEEP, ACCENT);
                    g.drawString(fnt, currentPriceStr, badgeX + 4, badgeY + 2, ACCENT);

                    int ptsCount = visiblePts.size();
                    for (int i = 0; i < ptsCount; i++) {
                        var cp = visiblePts.get(i);
                        double nw = Double.parseDouble(cp.netWorth);
                        int x0 = chartLeft + i * (chartRight - chartLeft) / Math.max(1, ptsCount - 1);
                        int y0 = y + height - 6 - (int)((nw - minP) / range * (height - 12));

                        if (i > 0) {
                            double prevNW = Double.parseDouble(visiblePts.get(i - 1).netWorth);
                            int xPrev = chartLeft + (i - 1) * (chartRight - chartLeft) / Math.max(1, ptsCount - 1);
                            int yPrev = y + height - 6 - (int)((prevNW - minP) / range * (height - 12));
                            drawLine(g, xPrev, yPrev, x0, y0, CHART_LINE);
                        }

                        boolean nodeHover = (mx >= x0 - 3 && mx <= x0 + 3 && my >= y0 - 3 && my <= y0 + 3);
                        int dotClr = nodeHover ? 0xFFFFFFFF : ACCENT;
                        g.fill(x0 - 1, y0 - 1, x0 + 2, y0 + 2, dotClr);
                        if (nodeHover) {
                            String timeStr = cp.timestamp > 0 ? CHART_TIME_FMT.format(new java.util.Date(cp.timestamp)) : "Step " + (startIndex + i + 1);
                            pendingTooltip = "Time: " + timeStr + "\nNet Worth: " + formatCompact(Double.parseDouble(cp.netWorth)) + "\nCash: " + formatCompact(Double.parseDouble(cp.balance)) + "  |  Assets: " + formatCompact(Double.parseDouble(cp.assets));
                        }
                    }
                } else {
                    String msg = "Not enough portfolio data points yet";
                    g.drawString(fnt, msg, x + (width - fnt.width(msg)) / 2, y + 18, TEXT_MUTED);
                }
            }
        };
        v.addChild(chart);
        v.addChild(new Divider(PANEL_BORDER));

        // Asset Allocation Holdings Breakdown List
        ScrollList holdingsList = new ScrollList(
            () -> Math.max(1, cachedAssetHoldings.size()),
            26,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                if (cachedAssetHoldings.isEmpty()) {
                    String msg = "No items currently stored in Vaults";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 8, TEXT_MUTED);
                    return;
                }
                if (idx >= cachedAssetHoldings.size()) return;
                var h = cachedAssetHoldings.get(idx);

                UiRender.roundedOutline(g, rx, ry + 1, rw, 24, UiTheme.RADIUS_SM,
                        hover ? CARD_HOVER : CARD_BG, PANEL_BORDER);

                renderCommodityIcon(g, h.itemId, rx + 4, ry + 5);

                String nameStr = fnt.plainSubstrByWidth(getItemDisplayName(h.itemId, h.displayName), rw - 110);
                g.drawString(fnt, nameStr, rx + 24, ry + 8, TEXT_PRIMARY);

                String qtyStr = isFluidCommodity(h.itemId)
                        ? formatFluidAmount(h.quantity)
                        : "x" + formatCompact(h.quantity);
                g.drawString(fnt, qtyStr, rx + rw - 110, ry + 8, TEXT_MUTED);

                String valStr = formatCompact(h.totalValue);
                int valW = 10 + fnt.width(valStr);
                renderSmallCoin(g, rx + rw - valW - 4, ry + 8);
                g.drawString(fnt, valStr, rx + rw - fnt.width(valStr) - 4, ry + 8, ACCENT);
            },
            (idx, btn) -> { /* read only */ },
            PANEL, ACCENT_DIM);

        holdingsList.flex();
        v.addChild(holdingsList);
        return v;
    }

    private void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private int left() { return (this.width - SCREEN_W) / 2; }
    private int top() { return (this.height - SCREEN_H) / 2; }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = left(), y = top();
        UiRender.surface(g, x, y, SCREEN_W, SCREEN_H, UiTheme.RADIUS_LG,
                BG_DARK, UiTheme.BORDER, true);
        UiRender.roundedRect(g, x + 2, y + 2, SIDEBAR_W - 3, SCREEN_H - 4,
                UiTheme.RADIUS_MD, UiTheme.SIDEBAR);
        g.fill(x + SIDEBAR_W - 1, y + 9, x + SIDEBAR_W, y + SCREEN_H - 9, PANEL_BORDER);
        UiRender.roundedRect(g, x + SIDEBAR_W + 48, y + 3, 124, 2, 1,
                UiRender.alpha(UiTheme.AMBIENT_WARM, 110));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        pendingTooltip = null;
        if (viewMode != 2 || pendingConfirmation != null || editingOrder != null) {
            pendingDropdown = null;
        }
        this.renderBackground(g);
        int sx = left(), sy = top();
        if (root != null) {
            root.layoutTree(this.font, sx, sy, SCREEN_W, SCREEN_H);
            syncEditBoxes();
        }
        super.render(g, mx, my, pt);
        if (root != null) {
            root.preRender(mx, my);
            root.render(g, this.font, mx, my, pt);
            if (viewMode == 2 && infiniteBuyBtn != null && infiniteBuyBtn.isVisible() && infiniteBuyBtn.isHovered()) {
                pendingTooltip = "Infinite Order: Buy order stays open continuously";
            }
        }
        // ── Late-pass: item search dropdown (drawn on top of everything) ──
        if (pendingDropdown != null && viewMode == 2 && pendingConfirmation == null && editingOrder == null) {
            renderItemDropdown(g, mx, my, pendingDropdown);
        }
        // ── Late-pass: Confirmation modal overlay ──
        if (pendingConfirmation != null) {
            renderConfirmationModal(g, mx, my);
        }
        // ── Late-pass: Edit Order modal overlay ──
        if (editingOrder != null) {
            renderEditOrderModal(g, mx, my);
        }
        if (pendingTooltip != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            for (String line : pendingTooltip.split("\n")) {
                lines.addAll(this.font.split(Component.literal(line), 150));
            }
            g.renderTooltip(this.font, lines, mx, my);
        }
    }

    private static final int DROP_ROW_H = 16;
    private static final int DROP_BG     = UiTheme.SURFACE_RAISED;
    private static final int DROP_BORDER = UiTheme.ACCENT;
    private static final int DROP_HOVER  = UiTheme.SURFACE_HOVER;

    private void renderItemDropdown(GuiGraphics g, int mx, int my, ItemDropdownData d) {
        if (pendingConfirmation != null || editingOrder != null) return;
        int totalResults = d.results.size();
        int visibleRows = Math.min(totalResults, 6);
        int totalH = visibleRows * DROP_ROW_H;
        int maxScroll = Math.max(0, totalResults - visibleRows);
        d.scrollOffset = Math.max(0, Math.min(d.scrollOffset, maxScroll));

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        UiRender.surface(g, d.x, d.y, d.w, totalH, UiTheme.RADIUS_MD,
                DROP_BG, DROP_BORDER, true);

        int itemWidth = maxScroll > 0 ? d.w - 6 : d.w;

        for (int i = 0; i < visibleRows; i++) {
            int idx = d.scrollOffset + i;
            if (idx >= totalResults) break;
            ItemSearchResult r = d.results.get(idx);
            int ry = d.y + i * DROP_ROW_H;
            boolean rowHover = mx >= d.x && mx < d.x + itemWidth && my >= ry && my < ry + DROP_ROW_H;
            if (rowHover) UiRender.roundedRect(g, d.x + 2, ry + 1,
                    itemWidth - 3, DROP_ROW_H - 2, UiTheme.RADIUS_SM, DROP_HOVER);

            // Row divider (skip first)
            if (i > 0) g.fill(d.x + 1, ry, d.x + itemWidth, ry + 1, PANEL_BORDER);

            // Item icon
            renderCommodityIcon(g, r.itemId, d.x + 2, ry);

            // Display name
            int nameX = d.x + 20;
            int nameMaxW = itemWidth - 22;
            String nameStr = this.font.plainSubstrByWidth(getItemDisplayName(r.itemId, r.displayName), nameMaxW);
            g.drawString(this.font, nameStr, nameX, ry + 4, TEXT_PRIMARY, false);
        }

        if (maxScroll > 0) {
            int trackX = d.x + d.w - 6;
            int trackH = totalH;
            int thumbH = Math.max(10, trackH * visibleRows / totalResults);
            int thumbY = d.scrollOffset * (trackH - thumbH) / maxScroll;
            UiRender.roundedRect(g, trackX + 1, d.y + 2, 3, trackH - 4, 2, PANEL);
            UiRender.roundedRect(g, trackX, d.y + thumbY, 5, thumbH, 3, ACCENT);
        }

        g.pose().popPose();
    }

    private void updateDropdownScrollFromMouseY(ItemDropdownData d, double my, int visibleRows, int maxScroll) {
        int trackH = visibleRows * DROP_ROW_H;
        int thumbH = Math.max(10, trackH * visibleRows / d.results.size());
        if (trackH - thumbH <= 0) return;
        double relY = Math.max(0, Math.min(trackH - thumbH, my - d.y - thumbH / 2.0));
        d.scrollOffset = (int) Math.round((relY / (double) (trackH - thumbH)) * maxScroll);
        d.scrollOffset = Math.max(0, Math.min(d.scrollOffset, maxScroll));
    }

    private void renderConfirmationModal(GuiGraphics g, int mx, int my) {
        if (pendingConfirmation == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        int modalW = 200;
        int modalH = 95;
        int modalX = left() + (SCREEN_W - modalW) / 2;
        int modalY = top() + (SCREEN_H - modalH) / 2;

        UiRender.roundedRect(g, left(), top(), SCREEN_W, SCREEN_H,
                UiTheme.RADIUS_LG, UiTheme.BACKDROP);
        UiRender.surface(g, modalX, modalY, modalW, modalH, UiTheme.RADIUS_LG,
                UiTheme.SURFACE_RAISED, ACCENT, true);

        // Title
        String title = "CONFIRM TRANSACTION";
        int titleW = font.width(title);
        g.drawString(font, title, modalX + (modalW - titleW) / 2, modalY + 8, ACCENT);
        g.fill(modalX + 10, modalY + 20, modalX + modalW - 10, modalY + 21, PANEL_BORDER);

        // Body message
        boolean fluidCommodity = isFluidCommodity(pendingConfirmation.itemId);
        String qtyStr = pendingConfirmation.isInfinite
                ? "\u221E"
                : com.nstut.economy.util.EconomyFormatUtil.formatCommodityQuantity(
                        pendingConfirmation.quantity, fluidCommodity);
        String msg1 = pendingConfirmation.action + " " + qtyStr + " of "
                + getItemDisplayName(pendingConfirmation.itemId, pendingConfirmation.itemName);
        int msg1W = font.width(msg1);
        g.drawString(font, msg1, modalX + (modalW - msg1W) / 2, modalY + 28, TEXT_PRIMARY);

        renderSmallCoin(g, modalX + 35, modalY + 44);
        String msg2 = "Total: " + pendingConfirmation.totalPrice;
        g.drawString(font, msg2, modalX + 47, modalY + 44, ACCENT);

        // Confirm Button
        int btnW = 75;
        int btnH = 16;
        int confirmX = modalX + 18;
        int confirmY = modalY + 66;
        boolean confirmHover = mx >= confirmX && mx <= confirmX + btnW && my >= confirmY && my <= confirmY + btnH;
        int confirmBg = confirmHover ? UiRender.mix(UiTheme.ACCENT_DEEP, UiTheme.ACCENT, 0.18F) : UiTheme.ACCENT_DEEP;
        UiRender.pill(g, confirmX, confirmY, btnW, btnH, confirmBg, ACCENT);
        g.drawString(font, "Confirm", confirmX + (btnW - font.width("Confirm")) / 2, confirmY + 4, ACCENT);

        // Cancel Button
        int cancelX = modalX + modalW - btnW - 18;
        int cancelY = modalY + 66;
        boolean cancelHover = mx >= cancelX && mx <= cancelX + btnW && my >= cancelY && my <= cancelY + btnH;
        int cancelBg = cancelHover ? UiRender.mix(UiTheme.DANGER_DEEP, UiTheme.DANGER, 0.28F) : UiTheme.DANGER_DEEP;
        UiRender.pill(g, cancelX, cancelY, btnW, btnH, cancelBg, RED);
        g.drawString(font, "Cancel", cancelX + (btnW - font.width("Cancel")) / 2, cancelY + 4, 0xFFFFFFFF);

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (editingOrder != null) {
            int modalW = 220;
            int modalH = 140;
            int modalX = left() + (SCREEN_W - modalW) / 2;
            int modalY = top() + (SCREEN_H - modalH) / 2;
            int btnW = 85;
            int btnH = 16;

            if (!editingOrder.isSell) {
                int infX = modalX + 184;
                int infY = modalY + 42;
                int infW = 24;
                int infH = 18;
                if (mx >= infX && mx < infX + infW && my >= infY && my < infY + infH) {
                    editIsInfinite = !editIsInfinite;
                    if (editQtyField != null) {
                        if (editIsInfinite) {
                            editQtyField.setValue("\u221E");
                        } else {
                            String val = editQtyField.getValue().trim();
                            if (val.equals("\u221E") || val.contains("\u221E") || val.isEmpty()) {
                                editQtyField.setValue(String.valueOf(editingOrder.quantity > 0 ? editingOrder.quantity : 1));
                            }
                        }
                    }
                    return true;
                }
            }

            int qtyW = editingOrder.isSell ? 196 : 166;
            int qtyX = modalX + 12, qtyY = modalY + 42, qtyH = 18;
            int priceX = modalX + 12, priceY = modalY + 64, priceW = 196, priceH = 18;

            if (mx >= qtyX && mx < qtyX + qtyW && my >= qtyY && my < qtyY + qtyH) {
                if (editQtyField != null) {
                    editQtyField.getEditBox().setFocused(true);
                    this.setFocused(editQtyField.getEditBox());
                }
                if (editPriceField != null) editPriceField.getEditBox().setFocused(false);
                return true;
            }
            if (mx >= priceX && mx < priceX + priceW && my >= priceY && my < priceY + priceH) {
                if (editPriceField != null) {
                    editPriceField.getEditBox().setFocused(true);
                    this.setFocused(editPriceField.getEditBox());
                }
                if (editQtyField != null) editQtyField.getEditBox().setFocused(false);
                return true;
            }

            int saveX = modalX + 18;
            int saveY = modalY + 105;
            if (mx >= saveX && mx <= saveX + btnW && my >= saveY && my <= saveY + btnH) {
                submitEditOrder();
                return true;
            }

            int cancelX = modalX + modalW - btnW - 18;
            int cancelY = modalY + 105;
            if (mx >= cancelX && mx <= cancelX + btnW && my >= cancelY && my <= cancelY + btnH) {
                closeEditModal();
                return true;
            }
            return true;
        }

        if (pendingConfirmation != null) {
            int modalW = 200;
            int modalH = 95;
            int modalX = left() + (SCREEN_W - modalW) / 2;
            int modalY = top() + (SCREEN_H - modalH) / 2;
            int btnW = 75;
            int btnH = 16;

            int confirmX = modalX + 18;
            int confirmY = modalY + 66;
            if (mx >= confirmX && mx <= confirmX + btnW && my >= confirmY && my <= confirmY + btnH) {
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CreateOrderPacket(
                    pendingConfirmation.itemId, pendingConfirmation.quantity, pendingConfirmation.priceStr, pendingConfirmation.isSell, pendingConfirmation.isInfinite, pendingConfirmation.commodityType));
                selectedItemId = pendingConfirmation.itemId;
                cachedDetail = null;
                pendingConfirmation = null;
                switchView(1);
                MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(selectedItemId));
                return true;
            }

            int cancelX = modalX + modalW - btnW - 18;
            int cancelY = modalY + 66;
            if (mx >= cancelX && mx <= cancelX + btnW && my >= cancelY && my <= cancelY + btnH) {
                pendingConfirmation = null;
                switchView(viewMode);
                return true;
            }

            pendingConfirmation = null;
            switchView(viewMode);
            return true;
        }
        // ── Item search dropdown click interception ──
        if (viewMode == 2 && pendingDropdown != null) {
            ItemDropdownData d = pendingDropdown;
            int visibleRows = Math.min(d.results.size(), 6);
            int totalH = visibleRows * DROP_ROW_H;
            if (mx >= d.x && mx < d.x + d.w && my >= d.y && my < d.y + totalH) {
                int maxScroll = Math.max(0, d.results.size() - visibleRows);
                // Scrollbar click (rightmost 12 pixels)
                if (maxScroll > 0 && mx >= d.x + d.w - 12) {
                    d.isDraggingScrollbar = true;
                    updateDropdownScrollFromMouseY(d, my, visibleRows, maxScroll);
                    return true;
                }
                // Row item click
                int relativeY = (int) my - d.y;
                int rowIdx = relativeY / DROP_ROW_H;
                int idx = d.scrollOffset + rowIdx;
                if (idx >= 0 && idx < d.results.size()) {
                    String chosen = d.results.get(idx).itemId;
                    selectedItemId = chosen;
                    itemSearchAutoFilled = chosen;
                    if (itemIdField != null) {
                        itemIdField.setValue(chosen);
                        itemIdField.getEditBox().setFocused(false);
                    }
                    pendingDropdown = null;
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(chosen));
                    return true;
                }
            }
        }
        // Product Detail chart LIVE snap button check
        if (viewMode == 1 && detailChartOffset > 0) {
            if (mx >= detailLiveBtnX && mx < detailLiveBtnX + detailLiveBtnW && my >= detailLiveBtnY && my < detailLiveBtnY + detailLiveBtnH) {
                com.nstut.Economy.LOGGER.info("[MarketScreen] Snap to Live clicked on Product Details chart!");
                detailChartOffset = 0;
                return true;
            }
        }

        // Portfolio chart LIVE snap button check
        if (viewMode == 5 && portfolioChartOffset > 0) {
            if (mx >= portfolioLiveBtnX && mx < portfolioLiveBtnX + portfolioLiveBtnW && my >= portfolioLiveBtnY && my < portfolioLiveBtnY + portfolioLiveBtnH) {
                com.nstut.Economy.LOGGER.info("[MarketScreen] Snap to Live clicked on Portfolio chart!");
                portfolioChartOffset = 0;
                return true;
            }
        }

        if (root != null && root.mouseClicked(mx, my, btn)) {
            com.nstut.Economy.LOGGER.info("[MarketScreen] Click handled by root UI component");
            return true;
        }
        boolean superResult = super.mouseClicked(mx, my, btn);
        if (viewMode == 2) {
            enforceCreateFormFocus();
        }
        com.nstut.Economy.LOGGER.info("[MarketScreen] super.mouseClicked returned {}", superResult);
        return superResult;
    }

    private void enforceCreateFormFocus() {
        if (itemIdField != null && itemIdField.getEditBox().isFocused()) {
            if (qtyField != null) qtyField.getEditBox().setFocused(false);
            if (priceField != null) priceField.getEditBox().setFocused(false);
        } else if (qtyField != null && qtyField.getEditBox().isFocused()) {
            if (itemIdField != null) itemIdField.getEditBox().setFocused(false);
            if (priceField != null) priceField.getEditBox().setFocused(false);
        } else if (priceField != null && priceField.getEditBox().isFocused()) {
            if (itemIdField != null) itemIdField.getEditBox().setFocused(false);
            if (qtyField != null) qtyField.getEditBox().setFocused(false);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (viewMode == 1 && cachedDetail != null && cachedDetail.chart != null && !cachedDetail.chart.isEmpty()) {
            int chartX = left() + SIDEBAR_W + 16;
            int chartW = SCREEN_W - SIDEBAR_W - 32;
            int chartY = top() + 24 + 18;
            int chartH = 40;
            if (mx >= chartX && mx < chartX + chartW && my >= chartY && my < chartY + chartH) {
                int maxOffset = Math.max(0, cachedDetail.chart.size() - MAX_VISIBLE_CHART_STEPS);
                if (delta < 0) detailChartOffset = Math.min(maxOffset, detailChartOffset + 1);
                else if (delta > 0) detailChartOffset = Math.max(0, detailChartOffset - 1);
                return true;
            }
        }
        if (viewMode == 5 && cachedPortfolioPoints.size() >= 2) {
            int chartX = left() + SIDEBAR_W + 16;
            int chartW = SCREEN_W - SIDEBAR_W - 32;
            int chartY = top() + 24 + 28 + 1;
            int chartH = 48;
            if (mx >= chartX && mx < chartX + chartW && my >= chartY && my < chartY + chartH) {
                int maxOffset = Math.max(0, cachedPortfolioPoints.size() - MAX_VISIBLE_CHART_STEPS);
                if (delta < 0) portfolioChartOffset = Math.min(maxOffset, portfolioChartOffset + 1);
                else if (delta > 0) portfolioChartOffset = Math.max(0, portfolioChartOffset - 1);
                return true;
            }
        }
        if (viewMode == 2 && pendingDropdown != null) {
            ItemDropdownData d = pendingDropdown;
            int visibleRows = Math.min(d.results.size(), 6);
            int totalH = visibleRows * DROP_ROW_H;
            if (mx >= d.x && mx < d.x + d.w && my >= d.y - 24 && my < d.y + totalH) {
                int maxScroll = Math.max(0, d.results.size() - visibleRows);
                if (maxScroll > 0) {
                    if (delta > 0) d.scrollOffset = Math.max(0, d.scrollOffset - 1);
                    else if (delta < 0) d.scrollOffset = Math.min(maxScroll, d.scrollOffset + 1);
                }
                return true;
            }
        }
        if (root != null && root.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dragX, double dragY) {
        if (viewMode == 2 && pendingDropdown != null && pendingDropdown.isDraggingScrollbar) {
            ItemDropdownData d = pendingDropdown;
            int visibleRows = Math.min(d.results.size(), 6);
            int maxScroll = Math.max(0, d.results.size() - visibleRows);
            if (maxScroll > 0) {
                updateDropdownScrollFromMouseY(d, my, visibleRows, maxScroll);
            }
            return true;
        }
        if (root != null && root.mouseDragged(mx, my, btn, dragX, dragY)) return true;
        return super.mouseDragged(mx, my, btn, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (pendingDropdown != null && pendingDropdown.isDraggingScrollbar) {
            pendingDropdown.isDraggingScrollbar = false;
            return true;
        }
        if (root != null && root.mouseReleased(mx, my, btn)) return true;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        com.nstut.Economy.LOGGER.info("[MarketScreen] keyPressed key={}, scan={}, mod={}, editingOrder={}",
            key, scan, mod, editingOrder != null ? editingOrder.orderId : "null");

        if (editingOrder != null) {
            if (editQtyField != null && editQtyField.isFocused() && editQtyField.keyPressed(key, scan, mod)) {
                com.nstut.Economy.LOGGER.info("[MarketScreen] editQtyField handled keyPressed key={}, val='{}'", key, editQtyField.getValue());
                return true;
            }
            if (editPriceField != null && editPriceField.isFocused() && editPriceField.keyPressed(key, scan, mod)) {
                com.nstut.Economy.LOGGER.info("[MarketScreen] editPriceField handled keyPressed key={}, val='{}'", key, editPriceField.getValue());
                return true;
            }
            if (key == 256) {
                closeEditModal();
                return true;
            }
            if (key >= 48 && key <= 57) {
                // Digit keys: allow charTyped to process digit input
                return false;
            }
            return true;
        }
        if (viewMode == 0 && searchField != null && searchField.isFocused()) {
            if (searchField.keyPressed(key, scan, mod)) {
                searchQuery = searchField.getValue();
                savedSearchQuery = searchQuery;
                return true;
            }
            searchQuery = searchField.getValue();
            savedSearchQuery = searchQuery;
        }
        if (viewMode == 3 && ordersSubTab == 0 && activeOrdersSearchField != null && activeOrdersSearchField.isFocused()) {
            if (activeOrdersSearchField.keyPressed(key, scan, mod)) {
                activeOrdersSearchQuery = activeOrdersSearchField.getValue();
                savedActiveOrdersSearchQuery = activeOrdersSearchQuery;
                return true;
            }
            activeOrdersSearchQuery = activeOrdersSearchField.getValue();
            savedActiveOrdersSearchQuery = activeOrdersSearchQuery;
        }
        if (viewMode == 3 && ordersSubTab == 1 && historySearchField != null && historySearchField.isFocused()) {
            if (historySearchField.keyPressed(key, scan, mod)) {
                historySearchQuery = historySearchField.getValue();
                savedHistorySearchQuery = historySearchQuery;
                return true;
            }
            historySearchQuery = historySearchField.getValue();
            savedHistorySearchQuery = historySearchQuery;
        }
        if (viewMode == 2) {
            if (itemIdField != null && itemIdField.isFocused() && itemIdField.keyPressed(key, scan, mod)) {
                itemSearchAutoFilled = null;
                return true;
            }
            if (qtyField != null && qtyField.isFocused() && qtyField.keyPressed(key, scan, mod)) return true;
            if (priceField != null && priceField.isFocused() && priceField.keyPressed(key, scan, mod)) return true;
        }

        if (key == 256) { // GLFW_KEY_ESCAPE
            this.onClose();
            return true;
        }

        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scan)) {
            return true; // Prevent inventory key 'E' from closing screen
        }

        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        com.nstut.Economy.LOGGER.info("[MarketScreen] charTyped codePoint='{}' ({}), editingOrder={}, qtyFocused={}, priceFocused={}",
            codePoint, (int)codePoint, editingOrder != null ? editingOrder.orderId : "null",
            editQtyField != null && editQtyField.isFocused(), editPriceField != null && editPriceField.isFocused());

        if (editingOrder != null) {
            if (editQtyField != null && editQtyField.isFocused()) {
                if (editIsInfinite && (editQtyField.getValue().equals("\u221E") || editQtyField.getValue().contains("\u221E")) && Character.isDigit(codePoint)) {
                    editQtyField.setValue("");
                    editIsInfinite = false;
                }
                boolean handled = editQtyField.getEditBox().charTyped(codePoint, modifiers);
                com.nstut.Economy.LOGGER.info("[MarketScreen] editQtyField.charTyped returned {}, val='{}'", handled, editQtyField.getValue());
                if (handled) return true;
            }
            if (editPriceField != null && editPriceField.isFocused()) {
                boolean handled = editPriceField.getEditBox().charTyped(codePoint, modifiers);
                com.nstut.Economy.LOGGER.info("[MarketScreen] editPriceField.charTyped returned {}, val='{}'", handled, editPriceField.getValue());
                if (handled) return true;
            }
            return true;
        }
        if (viewMode == 2 && qtyField != null && qtyField.isFocused()) {
            if (!createSellMode && isCreateInfinite && (qtyField.getValue().equals("\u221E") || qtyField.getValue().contains("\u221E")) && Character.isDigit(codePoint)) {
                qtyField.setValue("");
                isCreateInfinite = false;
                if (infiniteBuyBtn != null) {
                    infiniteBuyBtn.setColors(PANEL, CARD_HOVER);
                }
            }
        }
        if (viewMode == 0 && searchField != null && searchField.isFocused()) {
            if (searchField.getEditBox().charTyped(codePoint, modifiers)) {
                searchQuery = searchField.getValue();
                savedSearchQuery = searchQuery;
                return true;
            }
        }
        if (viewMode == 3 && ordersSubTab == 0 && activeOrdersSearchField != null && activeOrdersSearchField.isFocused()) {
            if (activeOrdersSearchField.getEditBox().charTyped(codePoint, modifiers)) {
                activeOrdersSearchQuery = activeOrdersSearchField.getValue();
                savedActiveOrdersSearchQuery = activeOrdersSearchQuery;
                return true;
            }
        }
        if (viewMode == 3 && ordersSubTab == 1 && historySearchField != null && historySearchField.isFocused()) {
            if (historySearchField.getEditBox().charTyped(codePoint, modifiers)) {
                historySearchQuery = historySearchField.getValue();
                savedHistorySearchQuery = historySearchQuery;
                return true;
            }
        }
        if (viewMode == 2) {
            if (itemIdField != null && itemIdField.isFocused()) {
                if (itemIdField.getEditBox().charTyped(codePoint, modifiers)) {
                    itemSearchAutoFilled = null;
                    return true;
                }
            }
            if (qtyField != null && qtyField.isFocused() && qtyField.getEditBox().charTyped(codePoint, modifiers)) return true;
            if (priceField != null && priceField.isFocused() && priceField.getEditBox().charTyped(codePoint, modifiers)) return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (searchField != null) {
            searchQuery = searchField.getValue();
            savedSearchQuery = searchQuery;
        }
        if (activeOrdersSearchField != null) {
            activeOrdersSearchQuery = activeOrdersSearchField.getValue();
            savedActiveOrdersSearchQuery = activeOrdersSearchQuery;
        }
        if (historySearchField != null) {
            historySearchQuery = historySearchField.getValue();
            savedHistorySearchQuery = historySearchQuery;
        }
    }
}
