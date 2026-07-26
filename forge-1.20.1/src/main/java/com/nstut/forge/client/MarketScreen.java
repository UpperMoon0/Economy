package com.nstut.forge.client;

import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.ui.framework.*;
import com.nstut.forge.network.HistoryEntry;
import com.nstut.forge.network.MarketNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 240;

    private static final int BG_DARK = 0xFF141424;
    private static final int PANEL = 0xFF1E1E30;
    private static final int PANEL_BORDER = 0xFF2F2F48;
    private static final int CARD_BG = 0xFF232338;
    private static final int CARD_HOVER = 0xFF2F2F4A;
    private static final int ACCENT = 0xFF00D4AA;
    private static final int ACCENT_DIM = 0xFF008866;
    private static final int TEXT_PRIMARY = 0xFFE5E5F0;
    private static final int TEXT_MUTED = 0xFF8A8A9E;
    private static final int GREEN = 0xFF22CC66;
    private static final int RED = 0xFFEE4444;
    private static final int CHART_BG = 0xFF18182A;
    private static final int CHART_LINE = 0xFF00D4AA;
    private static final int SIDEBAR_W = 74;

    private static class PaddingBox extends UIComponent {
        private final int top, right, bottom, left;

        public PaddingBox(int top, int right, int bottom, int left, UIComponent child) {
            this.top = top; this.right = right; this.bottom = bottom; this.left = left;
            if (child != null) addChild(child);
        }

        @Override
        public int preferredWidth(Font font) {
            int max = 0;
            for (UIComponent c : children) max = Math.max(max, c.preferredWidth(font));
            return max + left + right;
        }

        @Override
        public int preferredHeight(Font font) {
            int total = 0;
            for (UIComponent c : children) total += c.preferredHeight(font);
            return total + top + bottom;
        }

        @Override
        public void layout(int x, int y, int availableWidth, int availableHeight) {
            setBounds(x, y, availableWidth, availableHeight);
            int cw = Math.max(0, availableWidth - left - right);
            int ch = Math.max(0, availableHeight - top - bottom);
            for (UIComponent c : children) {
                c.layout(x + left, y + top, cw, ch);
            }
        }

        @Override
        public void render(GuiGraphics g, Font font, int mx, int my, float pt) {
            if (!visible) return;
            for (UIComponent c : children) if (c.isVisible()) c.render(g, font, mx, my, pt);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!visible) return false;
            for (int i = children.size() - 1; i >= 0; i--) {
                UIComponent c = children.get(i);
                if (c.isVisible() && c.mouseClicked(mx, my, button)) return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (!visible) return false;
            for (UIComponent c : children) {
                if (c.isVisible() && c.mouseScrolled(mx, my, delta)) return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
            if (!visible) return false;
            for (UIComponent c : children) {
                if (c.isVisible() && c.mouseDragged(mx, my, button, dragX, dragY)) return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (!visible) return false;
            for (UIComponent c : children) {
                if (c.isVisible() && c.mouseReleased(mx, my, button)) return true;
            }
            return false;
        }
    }

    private static void renderSmallCoin(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 0.5f);
        g.renderItem(COIN_ICON, 0, 0);
        g.pose().popPose();
    }

    public static String formatCompact(double val) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(val); }
    public static String formatCompact(BigDecimal val) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(val); }
    public static String formatCompact(long val) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(val); }
    public static String formatCompact(String str) { return com.nstut.economy.util.EconomyFormatUtil.formatCompact(str); }
    public static String formatPriceChange(double percent) { return com.nstut.economy.util.EconomyFormatUtil.formatPriceChange(percent); }
    public static int getPriceChangeColor(double percent) { return com.nstut.economy.util.EconomyFormatUtil.getPriceChangeColor(percent); }

    private static List<MarketNetwork.ItemCardData> cachedCards = new ArrayList<>();
    private static String cachedBalance = "0";
    private static int cachedVaultCount;
    private static MarketNetwork.SyncItemDetailPacket cachedDetail;
    private static List<HistoryEntry> cachedHistory = new ArrayList<>();
    private static final Map<String, ItemStack> itemIconCache = new HashMap<>();
    private static final ItemStack COIN_ICON = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(com.nstut.Economy.MOD_ID, "coin")));

    private UIComponent root;
    private TextWidget vaultWidget;
    private UIComponent balanceWidget;
    private ButtonWidget browseBtn, vaultsBtn, portfolioBtn, newOrderBtn, orderHistoryBtn;
    private EditBoxWrapper searchField;
    private ScrollList cardGrid;
    private ScrollList askList, bidList;
    private UIComponent browser, detail, createOffer;
    private EditBoxWrapper qtyField, priceField, itemIdField;

    private String selectedItemId = null;
    private int createOrderSourceMode = 0;
    private boolean createSellMode = false;
    private UIComponent historyView;
    private UIComponent vaultsView;
    private UIComponent portfolioView;

    private static String savedSearchQuery = "";
    private static String savedHistorySearchQuery = "";
    private static int savedBrowseFilterMode = 0; // 0 = All, 1 = Active Only
    private static int savedBrowseSortMode = 0;   // 0 = Price ▲, 1 = Price ▼, 2 = Name A-Z, 3 = Most Active
    private static int savedHistoryFilterMode = 0; // 0 = All Trades, 1 = Sales Only, 2 = Purchases Only
    private static int savedHistorySortMode = 0;   // 0 = Newest, 1 = Oldest, 2 = Highest Total
    private static int savedViewMode = 0;

    private String searchQuery = savedSearchQuery;
    private String historySearchQuery = savedHistorySearchQuery;
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

    private static List<MarketNetwork.VaultDetailEntry> cachedVaultEntries = new ArrayList<>();
    private static List<MarketNetwork.PortfolioPointData> cachedPortfolioPoints = new ArrayList<>();
    private static List<MarketNetwork.AssetHoldingData> cachedAssetHoldings = new ArrayList<>();
    private static List<MarketNetwork.ActiveOrderEntry> cachedActiveOrders = new ArrayList<>();

    public static void handleSyncVaultInfo(MarketNetwork.SyncVaultInfoPacket pkt) {
        cachedVaultEntries = pkt.entries;
    }

    public static void handleSyncPortfolio(MarketNetwork.SyncPortfolioPacket pkt) {
        cachedPortfolioPoints = pkt.points;
        cachedAssetHoldings = pkt.holdings;
    }

    public static void handleSyncActiveOrders(MarketNetwork.SyncActiveOrdersPacket pkt) {
        cachedActiveOrders = pkt.entries;
    }

    private EditBoxWrapper historySearchField;

    private int browseFilterMode = savedBrowseFilterMode;
    private int browseSortMode = savedBrowseSortMode;
    private ButtonWidget browseFilterBtn, browseSortBtn;

    private int historyFilterMode = savedHistoryFilterMode;
    private int historySortMode = savedHistorySortMode;
    private ButtonWidget historyFilterBtn, historySortBtn;

    private String getBrowseFilterLabel() {
        return browseFilterMode == 1 ? "Active Only" : "All";
    }

    private String getBrowseSortLabel() {
        switch (browseSortMode) {
            case 1: return "Price \u25BC";
            case 2: return "Name A-Z";
            case 3: return "Most Active";
            default: return "Price \u25B2";
        }
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

        PendingOrderExecution(String itemId, int quantity, String priceStr, boolean isSell, boolean isInfinite, String action, String itemName, String totalPrice) {
            this.itemId = itemId; this.quantity = quantity; this.priceStr = priceStr;
            this.isSell = isSell; this.isInfinite = isInfinite; this.action = action; this.itemName = itemName;
            this.totalPrice = totalPrice;
        }

        PendingOrderExecution(String itemId, int quantity, String priceStr, boolean isSell, String action, String itemName, String totalPrice) {
            this(itemId, quantity, priceStr, isSell, false, action, itemName, totalPrice);
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
        editQtyField = new EditBoxWrapper(10, TEXT_PRIMARY, PANEL, this.font).setPlaceholder("Qty");
        editPriceField = new EditBoxWrapper(20, TEXT_PRIMARY, PANEL, this.font).setPlaceholder("Price");
        editQtyField.setVisible(false);
        editPriceField.setVisible(false);

        buildTree();
        if (searchField != null) this.addRenderableWidget(searchField.getEditBox());
        if (historySearchField != null) this.addRenderableWidget(historySearchField.getEditBox());
        if (itemIdField != null) this.addRenderableWidget(itemIdField.getEditBox());
        if (qtyField != null) this.addRenderableWidget(qtyField.getEditBox());
        if (priceField != null) this.addRenderableWidget(priceField.getEditBox());
        this.addRenderableWidget(editQtyField.getEditBox());
        this.addRenderableWidget(editPriceField.getEditBox());

        switchView(savedViewMode == 1 || savedViewMode == 2 ? 0 : savedViewMode);
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestRefreshPacket());
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

        // Sidebar with Padding & Padding Boxes around buttons
        VStack sidebar = new VStack().gap(5);
        sidebar.addChild(new SizedBox(0, 4)); // top margin
        sidebar.addChild(TextWidget.centered("Market", ACCENT));
        balanceWidget = new UIComponent() {
            @Override public int preferredWidth(Font f) { return SIDEBAR_W; }
            @Override public int preferredHeight(Font f) { return 16; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                String balDisp = formatCompact(new BigDecimal(cachedBalance));
                int textW = fnt.width(balDisp);
                int totalW = 8 + 3 + textW;
                int startX = x + (width - totalW) / 2;
                renderSmallCoin(g, startX, y + 4);
                g.drawString(fnt, balDisp, startX + 11, y + 3, TEXT_PRIMARY);
            }
        };
        sidebar.addChild(balanceWidget);
        sidebar.addChild(new SizedBox(0, 2));
        sidebar.addChild(new Divider(PANEL_BORDER));
        sidebar.addChild(new SizedBox(0, 2));

        browseBtn = btn("Browse", PANEL, CARD_HOVER).onPress(() -> {
            selectedItemId = null;
            switchView(0);
        });
        sidebar.addChild(new PaddingBox(0, 4, 0, 4, browseBtn));

        vaultsBtn = btn("Vaults", PANEL, CARD_HOVER).onPress(() -> {
            cachedVaultEntries = new ArrayList<>();
            switchView(4);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestVaultInfoPacket());
        });
        sidebar.addChild(new PaddingBox(0, 4, 0, 4, vaultsBtn));

        portfolioBtn = btn("Portfolio", PANEL, CARD_HOVER).onPress(() -> {
            cachedPortfolioPoints = new ArrayList<>();
            cachedAssetHoldings = new ArrayList<>();
            switchView(5);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestPortfolioPacket());
        });
        sidebar.addChild(new PaddingBox(0, 4, 0, 4, portfolioBtn));

        newOrderBtn = btn("New Order", PANEL, CARD_HOVER).onPress(() -> {
            createOrderSourceMode = 0;
            selectedItemId = null;
            createSellMode = true;
            if (itemIdField != null) itemIdField.setValue("");
            if (qtyField != null) qtyField.setValue("");
            if (priceField != null) priceField.setValue("");
            switchView(2);
        });
        sidebar.addChild(new PaddingBox(0, 4, 0, 4, newOrderBtn));

        orderHistoryBtn = btn("Orders", PANEL, CARD_HOVER).onPress(() -> {
            cachedHistory = new ArrayList<>();
            cachedActiveOrders = new ArrayList<>();
            switchView(3);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestActiveOrdersPacket());
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestOrderHistoryPacket());
        });
        sidebar.addChild(new PaddingBox(0, 4, 0, 4, orderHistoryBtn));

        sidebar.addChild(new Spacer());
        SizedBox sidebarBox = new SizedBox(SIDEBAR_W, SCREEN_H);
        sidebarBox.addChild(sidebar);
        main.addChild(sidebarBox);

        // Content Container wrapped with 8px Margin & Padding
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

        UIComponent vaults = buildVaults(font);
        vaults.flex();
        vaults.setVisible(false);
        this.vaultsView = vaults;
        contentArea.addChild(vaults);

        UIComponent portfolio = buildPortfolio(font);
        portfolio.flex();
        portfolio.setVisible(false);
        this.portfolioView = portfolio;
        contentArea.addChild(portfolio);

        PaddingBox contentPadding = new PaddingBox(8, 8, 8, 8, contentArea);
        contentPadding.flex();
        main.addChild(contentPadding);
    }

    private ButtonWidget btn(String label, int normal, int hover) {
        return new ButtonWidget(label, normal, hover, TEXT_PRIMARY);
    }

    private UIComponent buildBrowser(Font font) {
        VStack v = new VStack().gap(4);
        searchQuery = savedSearchQuery;
        searchField = new EditBoxWrapper(60, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search products...");
        if (savedSearchQuery != null && !savedSearchQuery.isEmpty()) {
            searchField.setValue(savedSearchQuery);
        }
        v.addChild(searchField);

        HStack bar = new HStack().gap(4);
        browseFilterBtn = btn("Filter: " + getBrowseFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            browseFilterMode = (browseFilterMode + 1) % 2;
            savedBrowseFilterMode = browseFilterMode;
            browseFilterBtn.setLabel("Filter: " + getBrowseFilterLabel());
        });
        browseFilterBtn.flex();
        bar.addChild(browseFilterBtn);

        browseSortBtn = btn("Sort: " + getBrowseSortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            browseSortMode = (browseSortMode + 1) % 4;
            savedBrowseSortMode = browseSortMode;
            browseSortBtn.setLabel("Sort: " + getBrowseSortLabel());
        });
        browseSortBtn.flex();
        bar.addChild(browseSortBtn);
        v.addChild(bar);

        cardGrid = new ScrollList(
            () -> filterCards().size(),
            44,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<MarketNetwork.ItemCardData> f = filterCards();
                if (idx >= f.size()) return;
                MarketNetwork.ItemCardData card = f.get(idx);

                int cx = rx;
                int cy = ry + 2;
                int cardW = rw;
                int cardH = 40;

                boolean ch = mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
                int fillCol = ch ? CARD_HOVER : CARD_BG;
                int borderCol = ch ? ACCENT : PANEL_BORDER;

                g.fill(cx, cy, cx + cardW, cy + cardH, fillCol);
                g.fill(cx, cy, cx + cardW, cy + 1, borderCol);
                g.fill(cx, cy + cardH - 1, cx + cardW, cy + cardH, borderCol);
                g.fill(cx, cy, cx + 1, cy + cardH, borderCol);
                g.fill(cx + cardW - 1, cy, cx + cardW, cy + cardH, borderCol);

                ItemStack icon = itemIconCache.computeIfAbsent(card.itemId, id -> {
                    Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                    return new ItemStack(it);
                });
                g.renderItem(icon, cx + 6, cy + (cardH - 16) / 2);

                String nameText = fnt.plainSubstrByWidth(card.displayName, cardW - 80);
                g.drawString(fnt, nameText, cx + 28, cy + 6, TEXT_PRIMARY);

                if (card.globalPrice != null && !card.globalPrice.isEmpty()) {
                    if (!card.globalPrice.equals("--")) {
                        renderSmallCoin(g, cx + 26, cy + 22);
                        String compactPrice = formatCompact(parsePrice(card.globalPrice));
                        g.drawString(fnt, compactPrice, cx + 37, cy + 22, ACCENT);

                        int priceW = fnt.width(compactPrice);
                        String changeText = formatPriceChange(card.priceChangePercent);
                        int changeColor = getPriceChangeColor(card.priceChangePercent);
                        g.drawString(fnt, changeText, cx + 37 + priceW + 6, cy + 22, changeColor);
                    } else {
                        g.drawString(fnt, card.globalPrice, cx + 28, cy + 22, TEXT_MUTED);
                    }
                }

                if (card.offerCount > 0) {
                    String countText = card.offerCount + (card.offerCount == 1 ? " order" : " orders");
                    int countW = fnt.width(countText);
                    g.drawString(fnt, countText, cx + cardW - countW - 6, cy + 16, TEXT_MUTED);
                }
            },
            (idx, btn, mx, my) -> {
                List<MarketNetwork.ItemCardData> f = filterCards();
                com.nstut.Economy.LOGGER.info("[MarketScreen] Card clicked idx={}, totalFiltered={}, btn={}", idx, f.size(), btn);
                if (idx >= 0 && idx < f.size()) {
                    MarketNetwork.ItemCardData card = f.get(idx);
                    com.nstut.Economy.LOGGER.info("[MarketScreen] Selected item={}, switching to detail view", card.itemId);
                    selectedItemId = card.itemId;
                    cachedDetail = null;
                    switchView(1);
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(card.itemId));
                }
            },
            PANEL, ACCENT_DIM);
        v.addChild(cardGrid);
        return v;
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
                    String titleText = cachedDetail.displayName;
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

                    String stockText = "In Vault: " + cachedDetail.vaultCount;
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
                g.fill(x, y, x + width, y + height, CHART_BG);
                g.fill(x, y, x + width, y + 1, PANEL_BORDER);
                g.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
                g.fill(x, y, x + 1, y + height, PANEL_BORDER);
                g.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);

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
                    int liveBg = detailChartOffset > 0 ? (liveHover ? 0xFF047857 : 0xFF065F46) : (liveHover ? CARD_HOVER : 0xFF003024);
                    int liveBorder = detailChartOffset > 0 ? GREEN : (liveHover ? ACCENT : PANEL_BORDER);
                    g.fill(liveX, liveY, liveX + liveW, liveY + liveH, liveBg);
                    g.fill(liveX, liveY, liveX + liveW, liveY + 1, liveBorder);
                    g.fill(liveX, liveY + liveH - 1, liveX + liveW, liveY + liveH, liveBorder);
                    g.fill(liveX, liveY, liveX + 1, liveY + liveH, liveBorder);
                    g.fill(liveX + liveW - 1, liveY, liveX + liveW, liveY + liveH, liveBorder);
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
                            pendingTooltip = "Time: " + timeStr + "\nPrice: $" + cp.price + "\nVolume: " + cp.quantity;
                        }
                    }

                    // Draw right current price badge pill container
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF003024);
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, ACCENT);
                    g.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, ACCENT);
                    g.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, ACCENT);
                    g.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, ACCENT);
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

                if (hover) g.fill(rx, ry, rx + rw, ry + 16, CARD_HOVER);

                // 1. Order Type Badge (SELL or BUY)
                String typeText = isSell ? "SELL" : "BUY";
                int typeW = fnt.width(typeText);
                int badgeW = typeW + 6;
                int badgeH = 11;
                int badgeX = rx + 3;
                int badgeY = ry + 2;

                int bgClr = isSell ? 0x40801818 : 0x40105028;
                int borderClr = isSell ? 0xFFC03030 : 0xFF20A050;
                int textClr = isSell ? 0xFFFF6666 : 0xFF66FF66;

                g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, bgClr);
                g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, borderClr);
                g.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, borderClr);
                g.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, borderClr);
                g.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, borderClr);
                g.drawString(fnt, typeText, badgeX + 3, badgeY + 2, textClr);

                // 2. Coin Icon & Price x Quantity (with fulfillment progress if partially filled)
                int px = badgeX + badgeW + 5;
                renderSmallCoin(g, px, ry + 4);

                // 3. Cancel Button Widget
                String cancelText = "Cancel";
                int btnW = fnt.width(cancelText) + 8;
                int btnH = 12;
                int btnX = rx + rw - btnW - 3;
                int btnY = ry + 2;

                String line;
                if (e.initialQuantity > e.quantity) {
                    int fulfilled = e.initialQuantity - e.quantity;
                    int pct = (fulfilled * 100) / e.initialQuantity;
                    line = formatCompact(parsePrice(e.price)) + " x" + formatCompact(e.quantity) + " (" + pct + "% filled)";
                } else {
                    line = formatCompact(parsePrice(e.price)) + " x" + formatCompact(e.quantity);
                }

                int maxTextW = btnX - (px + 11) - 4;
                String truncatedLine = fnt.plainSubstrByWidth(line, maxTextW);
                int clr = isSell ? RED : GREEN;
                g.drawString(fnt, truncatedLine, px + 10, ry + 3, clr);

                if (hover && mx >= px + 10 && mx < btnX) {
                    pendingTooltip = e.initialQuantity > e.quantity ? 
                        "Order Progress: " + (e.initialQuantity - e.quantity) + "/" + e.initialQuantity + " items filled (" + ((e.initialQuantity - e.quantity) * 100 / e.initialQuantity) + "%)" :
                        "Order Quantity: " + e.quantity;
                }

                boolean isCancelHover = (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH);
                int btnBg = isCancelHover ? 0xFFC02020 : 0x60901818;
                int btnBorder = 0xFFFF4444;

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                g.fill(btnX, btnY, btnX + btnW, btnY + 1, btnBorder);
                g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, btnBorder);
                g.fill(btnX, btnY, btnX + 1, btnY + btnH, btnBorder);
                g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, btnBorder);
                g.drawString(fnt, cancelText, btnX + 4, badgeY + 2, 0xFFFFFFFF);
            },
            (idx, button, mx, my) -> {
                List<MarketNetwork.OrderEntry> myOrders = getMyOrders();
                if (idx >= 0 && idx < myOrders.size()) {
                    Font fnt = net.minecraft.client.Minecraft.getInstance().font;
                    String cancelText = "Cancel";
                    int btnW = fnt.width(cancelText) + 8;
                    if (myOrderListHolder[0] != null) {
                        int listX = myOrderListHolder[0].getX();
                        int listW = myOrderListHolder[0].getWidth() - 4;
                        int btnX = listX + listW - btnW - 3;

                        // Only process cancellation if click occurred inside Cancel button bounds
                        if (mx >= btnX && mx <= btnX + btnW) {
                            MarketNetwork.OrderEntry e = myOrders.get(idx);
                            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOrderPacket(e.orderId));
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
            if (itemIdField != null && selectedItemId != null) itemIdField.setValue(selectedItemId);
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
                if (hover) g.fill(rx, ry, rx + rw, ry + 17, CARD_HOVER);

                int textX = rx + 3;
                if (e.isServerOrder) {
                    String badge = "SERVER";
                    int badgeW = fnt.width(badge) + 8;
                    int badgeH = 12;
                    int badgeX = rx + 2;
                    int badgeY = ry + 3;

                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF003024);
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, ACCENT);
                    g.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, ACCENT);
                    g.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, ACCENT);
                    g.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, ACCENT);

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

                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF232338);
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, PANEL_BORDER);
                    g.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, PANEL_BORDER);
                    g.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, PANEL_BORDER);
                    g.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, PANEL_BORDER);

                    g.drawString(fnt, dispName, badgeX + 3, badgeY + 2, TEXT_PRIMARY);
                    textX += badgeW + 4;

                    if (mx >= badgeX && mx < badgeX + badgeW && my >= badgeY && my < badgeY + badgeH) {
                        pendingTooltip = (isAsks ? "Seller: " : "Buyer: ") + rawName;
                    }
                }

                renderSmallCoin(g, textX - 1, ry + 4);
                String line = e.price + " x" + e.quantity;
                g.drawString(fnt, line, textX + 10, ry + 4, clr);
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

    private int getVaultStockForItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) return 0;
        if (cachedDetail != null && cachedDetail.itemId.equalsIgnoreCase(itemId)) {
            return cachedDetail.vaultCount;
        }
        for (var h : cachedAssetHoldings) {
            if (h.itemId.equalsIgnoreCase(itemId)) {
                return h.quantity;
            }
        }
        return 0;
    }

    private UIComponent buildCreateOffer(Font font) {
        VStack v = new VStack().gap(4);

        HStack header = new HStack().gap(6);
        ButtonWidget backBtn = btn("< Back", PANEL, CARD_HOVER).onPress(() -> switchView(createOrderSourceMode));
        header.addChild(backBtn);
        createOfferTitleLabel = TextWidget.label("CREATE ORDER", TEXT_PRIMARY);
        header.addChild(createOfferTitleLabel);
        v.addChild(header);

        HStack modeSelector = new HStack().gap(4);
        sellModeBtn = btn("SELL ORDER", createSellMode ? 0xFF991B1B : PANEL, 0xFFDC2626).onPress(() -> {
            createSellMode = true;
            updateCreateOfferLabels();
        });
        sellModeBtn.flex();
        buyModeBtn = btn("BUY ORDER", !createSellMode ? 0xFF065F46 : PANEL, 0xFF059669).onPress(() -> {
            createSellMode = false;
            updateCreateOfferLabels();
        });
        buyModeBtn.flex();
        modeSelector.addChild(sellModeBtn);
        modeSelector.addChild(buyModeBtn);
        v.addChild(modeSelector);

        v.addChild(new SizedBox(0, 2));

        itemIdField = new EditBoxWrapper(128, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search item name or ID...");
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
                String stockMsg = "Vault Stock Available: " + stock + " items";
                int color = stock > 0 ? GREEN : RED;
                g.drawString(fnt, stockMsg, x + 2, y + 3, color);
            }
        };
        v.addChild(vaultStockBadge);

        HStack qtyRow = new HStack().gap(4);
        qtyField = new EditBoxWrapper(10, TEXT_PRIMARY, PANEL, font).setPlaceholder("Quantity (e.g. 10)");
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
            infiniteBuyBtn.setColors(isCreateInfinite ? 0xFF047857 : PANEL, isCreateInfinite ? 0xFF059669 : CARD_HOVER);
            if (isCreateInfinite && qtyField != null) {
                qtyField.setValue("∞");
            } else if (!isCreateInfinite && qtyField != null && qtyField.getValue().equals("∞")) {
                qtyField.setValue("");
            }
        });
        qtyRow.addChild(infiniteBuyBtn);
        v.addChild(qtyRow);

        priceField = new EditBoxWrapper(20, TEXT_PRIMARY, PANEL, font).setPlaceholder("Price per unit (e.g. 150)");
        v.addChild(priceField);

        v.addChild(new SizedBox(0, 4));

        createBtn = btn(createSellMode ? "SUBMIT SELL ORDER" : "SUBMIT BUY ORDER",
                createSellMode ? 0xFFB91C1C : 0xFF047857,
                createSellMode ? 0xFFDC2626 : 0xFF059669).onPress(this::submitOffer);
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
            createBtn.setColors(createSellMode ? 0xFFB91C1C : 0xFF047857,
                                createSellMode ? 0xFFDC2626 : 0xFF059669);
        }
        if (maxQtyBtn != null) {
            maxQtyBtn.setVisible(createSellMode);
        }
        if (infiniteBuyBtn != null) {
            infiniteBuyBtn.setVisible(!createSellMode);
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
        int qty = 1;
        boolean inf = !createSellMode && isCreateInfinite;
        if (!inf) {
            try {
                qty = Integer.parseInt(qtyField.getValue().trim());
                if (qty <= 0) { showCreateError("Quantity must be greater than 0."); return; }
            } catch (NumberFormatException ignored) {
                showCreateError("Quantity must be a valid number.");
                return;
            }
        }
        if (createSellMode) {
            int stock = getVaultStockForItem(itemId);
            if (stock > 0 && qty > stock) {
                showCreateError("Not enough in vault. You have " + stock + ".");
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
        if (cachedDetail != null && cachedDetail.itemId.equalsIgnoreCase(itemId)) {
            dispName = cachedDetail.displayName;
        } else {
            net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
            if (it != net.minecraft.world.item.Items.AIR) {
                dispName = new ItemStack(it).getHoverName().getString();
            }
        }
        pendingConfirmation = new PendingOrderExecution(itemId, qty, price.toPlainString(), createSellMode, inf, actionStr, dispName, totStr);
        switchView(viewMode);
    }

    private void showCreateError(String msg) {
        if (createOfferErrorLabel != null) {
            createOfferErrorLabel.setText(msg);
            createOfferErrorLabel.setVisible(true);
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
        if (vaultsView != null) vaultsView.setVisible(mode == 4);
        if (portfolioView != null) portfolioView.setVisible(mode == 5);
        if (mode == 2) {
            updateCreateOfferLabels();
            // Reset dropdown guard whenever we (re-)enter the form
            itemSearchAutoFilled = selectedItemId; // pre-filled IDs shouldn't auto-open dropdown
            pendingDropdown = null;
        } else {
            pendingDropdown = null;
        }

        if (browseBtn != null) {
            browseBtn.setVisible(true);
            browseBtn.setActive(mode == 0);
        }
        if (vaultsBtn != null) {
            vaultsBtn.setVisible(true);
            vaultsBtn.setActive(mode == 4);
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
        if (historySearchField != null) {
            historySearchField.setVisible(mode == 3 && pendingConfirmation == null);
            if (mode != 3 || pendingConfirmation != null) historySearchField.getEditBox().setFocused(false);
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
            root.layout(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
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

    /** Searches the item registry for entries whose display name or registry id
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
        return results;
    }

    private UIComponent buildOrdersView(Font font) {
        VStack v = new VStack().gap(4);

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
        if (historySearchField != null) {
            historySearchField.setVisible(viewMode == 3 && ordersSubTab == 1 && pendingConfirmation == null && editingOrder == null);
            if (viewMode != 3 || ordersSubTab != 1 || pendingConfirmation != null || editingOrder != null) {
                historySearchField.getEditBox().setFocused(false);
            }
        }
    }

    private UIComponent buildActiveOrdersList(Font font) {
        ScrollList list = new ScrollList(
            () -> Math.max(1, cachedActiveOrders.size()),
            36,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                if (cachedActiveOrders.isEmpty()) {
                    String msg = "No active open orders";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 12, TEXT_MUTED);
                    return;
                }
                if (idx >= cachedActiveOrders.size()) return;
                MarketNetwork.ActiveOrderEntry e = cachedActiveOrders.get(idx);

                if (hover) g.fill(rx, ry, rx + rw, ry + 35, CARD_HOVER);
                g.fill(rx, ry + 35, rx + rw, ry + 36, PANEL_BORDER);

                // Icon
                ItemStack icon = itemIconCache.computeIfAbsent(e.itemId, id -> {
                    Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                    return new ItemStack(it);
                });
                g.renderItem(icon, rx + 4, ry + 10);

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
                String nameText = fnt.plainSubstrByWidth(e.displayName, maxNameW);
                g.drawString(fnt, nameText, nameX, ry + 4, TEXT_PRIMARY);

                // Price & Quantity
                renderSmallCoin(g, rx + 24, ry + 19);
                String qtyText = e.isInfinite ? "Qty: \u221E" : ("Qty: " + e.quantity + " / " + e.initialQuantity);
                String priceQty = e.price + " | " + qtyText;
                g.drawString(fnt, priceQty, rx + 35, ry + 18, ACCENT);

                // Action buttons on right side
                int btnY = ry + 8, btnH = 20;

                boolean editHover = mx >= editX && mx < editX + editW && my >= btnY && my < btnY + btnH;
                boolean cancelHover = mx >= cancelX && mx < cancelX + cancelW && my >= btnY && my < btnY + btnH;

                int editBg = editHover ? CARD_HOVER : PANEL;
                g.fill(editX, btnY, editX + editW, btnY + btnH, editBg);
                g.fill(editX, btnY, editX + editW, btnY + 1, ACCENT);
                g.fill(editX, btnY + btnH - 1, editX + editW, btnY + btnH, ACCENT);
                g.fill(editX, btnY, editX + 1, btnY + btnH, ACCENT);
                g.fill(editX + editW - 1, btnY, editX + editW, btnY + btnH, ACCENT);
                g.drawString(fnt, "Edit", editX + (editW - fnt.width("Edit")) / 2, btnY + 6, ACCENT);

                int cancelBg = cancelHover ? 0xFF991B1B : 0xFF7F1D1D;
                g.fill(cancelX, btnY, cancelX + cancelW, btnY + btnH, cancelBg);
                g.fill(cancelX, btnY, cancelX + cancelW, btnY + 1, RED);
                g.fill(cancelX, btnY + btnH - 1, cancelX + cancelW, btnY + btnH, RED);
                g.fill(cancelX, btnY, cancelX + 1, btnY + btnH, RED);
                g.fill(cancelX + cancelW - 1, btnY, cancelX + cancelW, btnY + btnH, RED);
                g.drawString(fnt, "Cancel", cancelX + (cancelW - fnt.width("Cancel")) / 2, btnY + 6, TEXT_PRIMARY);
            },
            (idx, btn, mx, my) -> {
                if (cachedActiveOrders.isEmpty() || idx >= cachedActiveOrders.size()) return;
                MarketNetwork.ActiveOrderEntry e = cachedActiveOrders.get(idx);
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
        return list;
    }

    private void openEditOrderModal(MarketNetwork.ActiveOrderEntry e) {
        this.editingOrder = e;
        this.editIsInfinite = e.isInfinite;
        this.editErrorMsg = null;
        if (editQtyField != null) {
            editQtyField.setValue(e.isInfinite ? "\u221E" : String.valueOf(e.quantity));
            editQtyField.getEditBox().setFocused(true);
            this.setFocused(editQtyField.getEditBox());
        }
        if (editPriceField != null) {
            editPriceField.setValue(e.price);
            editPriceField.getEditBox().setFocused(false);
        }
        if (historySearchField != null) {
            historySearchField.setVisible(false);
            historySearchField.getEditBox().setFocused(false);
        }
    }

    private void renderEditOrderModal(GuiGraphics g, int mx, int my) {
        if (editingOrder == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        int modalW = 220;
        int modalH = 140;
        int modalX = left() + (SCREEN_W - modalW) / 2;
        int modalY = top() + (SCREEN_H - modalH) / 2;

        // Dark dim backdrop over whole screen
        g.fill(left(), top(), left() + SCREEN_W, top() + SCREEN_H, 0xEE000000);

        // Modal container background & border
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0F1524);
        g.fill(modalX, modalY, modalX + modalW, modalY + 1, ACCENT);
        g.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, ACCENT);
        g.fill(modalX, modalY, modalX + 1, modalY + modalH, ACCENT);
        g.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, ACCENT);

        // Title
        String title = "EDIT ORDER";
        int titleW = font.width(title);
        g.drawString(font, title, modalX + (modalW - titleW) / 2, modalY + 8, ACCENT);
        g.fill(modalX + 10, modalY + 20, modalX + modalW - 10, modalY + 21, PANEL_BORDER);

        String typeTag = editingOrder.isSell ? "[SELL ORDER]" : "[BUY ORDER]";
        int typeColor = editingOrder.isSell ? RED : GREEN;
        g.drawString(font, typeTag, modalX + 12, modalY + 26, typeColor);

        String itemStr = font.plainSubstrByWidth(editingOrder.displayName, modalW - 100);
        g.drawString(font, itemStr, modalX + 12 + font.width(typeTag) + 6, modalY + 26, TEXT_PRIMARY);

        if (!editingOrder.isSell) {
            // Infinite toggle button
            int infX = modalX + 184;
            int infY = modalY + 42;
            int infW = 24;
            int infH = 18;
            boolean infHover = mx >= infX && mx < infX + infW && my >= infY && my < infY + infH;
            int infBg = editIsInfinite ? 0xFF047857 : (infHover ? CARD_HOVER : PANEL);
            g.fill(infX, infY, infX + infW, infY + infH, infBg);
            g.fill(infX, infY, infX + infW, infY + 1, ACCENT);
            g.fill(infX, infY + infH - 1, infX + infW, infY + infH, ACCENT);
            g.fill(infX, infY, infX + 1, infY + infH, ACCENT);
            g.fill(infX + infW - 1, infY, infX + infW, infY + infH, ACCENT);
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
        int saveBg = saveHover ? 0xFF004030 : 0xFF003024;
        g.fill(saveX, saveY, saveX + btnW, saveY + btnH, saveBg);
        g.fill(saveX, saveY, saveX + btnW, saveY + 1, ACCENT);
        g.fill(saveX, saveY + btnH - 1, saveX + btnW, saveY + btnH, ACCENT);
        g.fill(saveX, saveY, saveX + 1, saveY + btnH, ACCENT);
        g.fill(saveX + btnW - 1, saveY, saveX + btnW, saveY + btnH, ACCENT);
        g.drawString(font, "Save", saveX + (btnW - font.width("Save")) / 2, saveY + 4, ACCENT);

        // Cancel Button
        int cancelX = modalX + modalW - btnW - 18;
        int cancelY = modalY + 105;
        boolean cancelHover = mx >= cancelX && mx <= cancelX + btnW && my >= cancelY && my <= cancelY + btnH;
        int cancelBg = cancelHover ? 0xFFC02020 : 0x80901818;
        g.fill(cancelX, cancelY, cancelX + btnW, cancelY + btnH, cancelBg);
        g.fill(cancelX, cancelY, cancelX + btnW, cancelY + 1, RED);
        g.fill(cancelX, cancelY + btnH - 1, cancelX + btnW, cancelY + btnH, RED);
        g.fill(cancelX, cancelY, cancelX + 1, cancelY + btnH, RED);
        g.fill(cancelX + btnW - 1, cancelY, cancelX + btnW, cancelY + btnH, RED);
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

        int newQty = editingOrder.quantity;
        if (!editIsInfinite) {
            String qtyStr = editQtyField != null ? editQtyField.getValue().trim() : "";
            try {
                newQty = Integer.parseInt(qtyStr);
                if (newQty <= 0) { editErrorMsg = "Qty must be > 0"; return; }
            } catch (Exception e) {
                editErrorMsg = "Invalid quantity";
                return;
            }
        }

        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.EditOrderPacket(editingOrder.orderId, newQty, price.toPlainString(), editIsInfinite));
        closeEditModal();
    }

    private void closeEditModal() {
        editingOrder = null;
        editErrorMsg = null;
        if (editQtyField != null) editQtyField.setVisible(false);
        if (editPriceField != null) editPriceField.setVisible(false);
        if (historySearchField != null) {
            historySearchField.setVisible(viewMode == 3 && ordersSubTab == 1 && pendingConfirmation == null);
        }
    }

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MM/dd HH:mm");

    private UIComponent buildHistory(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(TextWidget.centered("ORDER HISTORY", ACCENT));

        historySearchQuery = savedHistorySearchQuery;
        historySearchField = new EditBoxWrapper(60, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search item or player...");
        if (savedHistorySearchQuery != null && !savedHistorySearchQuery.isEmpty()) {
            historySearchField.setValue(savedHistorySearchQuery);
        }
        v.addChild(historySearchField);

        HStack bar = new HStack().gap(4);
        historyFilterBtn = btn("Filter: " + getHistoryFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            historyFilterMode = (historyFilterMode + 1) % 3;
            savedHistoryFilterMode = historyFilterMode;
            historyFilterBtn.setLabel("Filter: " + getHistoryFilterLabel());
        });
        historyFilterBtn.flex();
        bar.addChild(historyFilterBtn);

        historySortBtn = btn("Sort: " + getHistorySortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            historySortMode = (historySortMode + 1) % 3;
            savedHistorySortMode = historySortMode;
            historySortBtn.setLabel("Sort: " + getHistorySortLabel());
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

                if (hover) g.fill(rx, ry, rx + rw, ry + 27, CARD_HOVER);
                g.fill(rx, ry + 27, rx + rw, ry + 28, PANEL_BORDER);

                // Item icon centered vertically
                ItemStack icon = itemIconCache.computeIfAbsent(e.itemId, id -> {
                    Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                    return new ItemStack(it);
                });
                g.renderItem(icon, rx + 4, ry + 6);

                // Top row (y + 4): Type badge + Item name + Timestamp
                String typeTag = e.wasSell ? "SELL" : "BUY";
                int typeColor = e.wasSell ? RED : GREEN;
                g.drawString(fnt, typeTag, rx + 24, ry + 4, typeColor);

                String dateStr = DATE_FMT.format(new Date(e.timestamp));
                int dateW = fnt.width(dateStr);

                int nameX = rx + 24 + fnt.width(typeTag) + 6;
                int maxNameW = Math.max(30, (rx + rw - dateW - 8) - nameX);
                String nameText = fnt.plainSubstrByWidth(e.displayName, maxNameW);
                g.drawString(fnt, nameText, nameX, ry + 4, TEXT_PRIMARY);

                g.drawString(fnt, dateStr, rx + rw - dateW - 4, ry + 4, TEXT_MUTED);

                // Bottom row (y + 16): Coin icon + Price x qty + Counterparty
                renderSmallCoin(g, rx + 24, ry + 17);
                String pqText = e.price + " x" + e.quantity;
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

    private UIComponent buildVaults(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(TextWidget.centered("VAULT OVERVIEW", ACCENT));
        v.addChild(new Divider(PANEL_BORDER));

        // Summary Stats Row (fixed 24px height, full width)
        UIComponent statsRow = new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 24; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                int totalV = cachedVaultEntries.size();
                int totalSlots = 0;
                int usedSlots = 0;
                int totalItems = 0;
                for (MarketNetwork.VaultDetailEntry e : cachedVaultEntries) {
                    totalSlots += e.totalSlots;
                    usedSlots += e.usedSlots;
                    totalItems += e.totalItems;
                }

                int boxW = (width - 8) / 3;

                // Stat Box 1: Vault Count
                int b1X = x;
                g.fill(b1X, y, b1X + boxW, y + height, CARD_BG);
                g.fill(b1X, y, b1X + boxW, y + 1, PANEL_BORDER);
                g.fill(b1X, y + height - 1, b1X + boxW, y + height, PANEL_BORDER);
                g.fill(b1X, y, b1X + 1, y + height, PANEL_BORDER);
                g.fill(b1X + boxW - 1, y, b1X + boxW, y + height, PANEL_BORDER);
                g.drawString(fnt, "VAULTS", b1X + (boxW - fnt.width("VAULTS")) / 2, y + 3, TEXT_MUTED);
                String vVal = String.valueOf(totalV);
                g.drawString(fnt, vVal, b1X + (boxW - fnt.width(vVal)) / 2, y + 13, ACCENT);

                // Stat Box 2: Storage Slots
                int b2X = b1X + boxW + 4;
                g.fill(b2X, y, b2X + boxW, y + height, CARD_BG);
                g.fill(b2X, y, b2X + boxW, y + 1, PANEL_BORDER);
                g.fill(b2X, y + height - 1, b2X + boxW, y + height, PANEL_BORDER);
                g.fill(b2X, y, b2X + 1, y + height, PANEL_BORDER);
                g.fill(b2X + boxW - 1, y, b2X + boxW, y + height, PANEL_BORDER);
                g.drawString(fnt, "SLOTS USED", b2X + (boxW - fnt.width("SLOTS USED")) / 2, y + 3, TEXT_MUTED);
                String sVal = usedSlots + "/" + totalSlots;
                g.drawString(fnt, sVal, b2X + (boxW - fnt.width(sVal)) / 2, y + 13, GREEN);

                // Stat Box 3: Total Items
                int b3X = b2X + boxW + 4;
                g.fill(b3X, y, b3X + boxW, y + height, CARD_BG);
                g.fill(b3X, y, b3X + boxW, y + 1, PANEL_BORDER);
                g.fill(b3X, y + height - 1, b3X + boxW, y + height, PANEL_BORDER);
                g.fill(b3X, y, b3X + 1, y + height, PANEL_BORDER);
                g.fill(b3X + boxW - 1, y, b3X + boxW, y + height, PANEL_BORDER);
                g.drawString(fnt, "TOTAL ITEMS", b3X + (boxW - fnt.width("TOTAL ITEMS")) / 2, y + 3, TEXT_MUTED);
                String iVal = String.valueOf(totalItems);
                g.drawString(fnt, iVal, b3X + (boxW - fnt.width(iVal)) / 2, y + 13, ACCENT);

                // Hover tooltips for Vault Overview Stat Boxes
                if (my >= y && my < y + height) {
                    if (mx >= b1X && mx < b1X + boxW) {
                        pendingTooltip = "Vaults: Total number of physical Vault blocks placed in world";
                    } else if (mx >= b2X && mx < b2X + boxW) {
                        pendingTooltip = "Slots Used: Occupied storage slots out of total capacity across all Vaults";
                    } else if (mx >= b3X && mx < b3X + boxW) {
                        pendingTooltip = "Total Items: Count of individual items stored inside your Vault network";
                    }
                }
            }
        };
        v.addChild(statsRow);
        v.addChild(new Divider(PANEL_BORDER));

        ScrollList list = new ScrollList(
            () -> Math.max(1, cachedVaultEntries.size()),
            40,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                if (cachedVaultEntries.isEmpty()) {
                    String msg = "No Vault blocks registered yet";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 15, TEXT_MUTED);
                    return;
                }
                if (idx >= cachedVaultEntries.size()) return;
                MarketNetwork.VaultDetailEntry e = cachedVaultEntries.get(idx);

                if (hover) g.fill(rx, ry, rx + rw, ry + 39, CARD_HOVER);
                g.fill(rx, ry + 39, rx + rw, ry + 40, PANEL_BORDER);

                // Vault Index Title (Line 1)
                String vTitle = "Vault #" + (idx + 1);
                g.drawString(fnt, vTitle, rx + 4, ry + 3, ACCENT);

                // Status Badge (ACTIVE or FULL) (Line 1 right)
                boolean isFull = e.usedSlots >= e.totalSlots;
                String badge = isFull ? "FULL" : "ACTIVE";
                int badgeW = fnt.width(badge) + 6;
                int badgeX = rx + rw - badgeW - 4;
                int badgeBg = isFull ? 0x40801818 : 0x40105028;
                int badgeBorder = isFull ? 0xFFFF4444 : 0xFF20A050;
                int badgeText = isFull ? 0xFFFF6666 : 0xFF66FF66;

                g.fill(badgeX, ry + 2, badgeX + badgeW, ry + 13, badgeBg);
                g.fill(badgeX, ry + 2, badgeX + badgeW, ry + 3, badgeBorder);
                g.fill(badgeX, ry + 12, badgeX + badgeW, ry + 13, badgeBorder);
                g.fill(badgeX, ry + 2, badgeX + 1, ry + 13, badgeBorder);
                g.fill(badgeX + badgeW - 1, ry + 2, badgeX + badgeW, ry + 13, badgeBorder);
                g.drawString(fnt, badge, badgeX + 3, ry + 3, badgeText);

                // Mode Badge (BOTH, INPUT ONLY, OUTPUT ONLY)
                String modeBadge = switch (e.mode) {
                    case 1 -> "INPUT ONLY";
                    case 2 -> "OUTPUT ONLY";
                    default -> "BOTH";
                };
                int modeW = fnt.width(modeBadge) + 6;
                int modeX = badgeX - modeW - 4;
                int modeBg = e.mode == 1 ? 0x40801818 : (e.mode == 2 ? 0x40105028 : 0x40004050);
                int modeBorder = e.mode == 1 ? 0xFFFF4444 : (e.mode == 2 ? 0xFF20A050 : 0xFF00D4AA);
                int modeText = e.mode == 1 ? 0xFFFF6666 : (e.mode == 2 ? 0xFF66FF66 : 0xFF00D4AA);

                g.fill(modeX, ry + 2, modeX + modeW, ry + 13, modeBg);
                g.fill(modeX, ry + 2, modeX + modeW, ry + 3, modeBorder);
                g.fill(modeX, ry + 12, modeX + modeW, ry + 13, modeBorder);
                g.fill(modeX, ry + 2, modeX + 1, ry + 13, modeBorder);
                g.fill(modeX + modeW - 1, ry + 2, modeX + modeW, ry + 13, modeBorder);
                g.drawString(fnt, modeBadge, modeX + 3, ry + 3, modeText);

                // Hover tooltip for Mode Badge
                if (mx >= modeX && mx < modeX + modeW && my >= ry + 2 && my < ry + 13) {
                    pendingTooltip = switch (e.mode) {
                        case 1 -> "Input Only Mode: Used ONLY for creating Sell Orders. Bought items avoid this Vault.";
                        case 2 -> "Output Only Mode: Used ONLY for receiving bought items. Sell Orders ignore items here.";
                        default -> "Both Mode (Default): Used for Sell Orders (Input) AND receiving bought items (Output).";
                    };
                }

                // Location String (Left, Line 2 - ry + 17)
                String dimClean = e.dimension.replace("minecraft:", "");
                String locStr = dimClean + " (" + e.x + ", " + e.y + ", " + e.z + ")";
                g.drawString(fnt, locStr, rx + 4, ry + 17, TEXT_MUTED);

                // Slots Used String (Right, Line 2 - ry + 17)
                String slotsStr = e.usedSlots + "/" + e.totalSlots + " Slots";
                int slotsW = fnt.width(slotsStr);
                g.drawString(fnt, slotsStr, rx + rw - slotsW - 4, ry + 17, TEXT_PRIMARY);

                // Storage Progress Bar (Left, Line 3 - ry + 29)
                int barX = rx + 4;
                int barY = ry + 29;
                int barW = rw - 90;
                int barH = 5;
                g.fill(barX, barY, barX + barW, barY + barH, 0xFF1A1A2E);
                int pct = e.totalSlots > 0 ? (e.usedSlots * barW) / e.totalSlots : 0;
                int fillClr = isFull ? RED : GREEN;
                g.fill(barX, barY, barX + pct, barY + barH, fillClr);

                // Items Count String (Right, Line 3 - ry + 28)
                String itemsStr = "(" + e.totalItems + " items)";
                int itemsW = fnt.width(itemsStr);
                g.drawString(fnt, itemsStr, rx + rw - itemsW - 4, ry + 28, TEXT_MUTED);
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
                g.fill(b1X, y, b1X + boxW, y + height, CARD_BG);
                g.fill(b1X, y, b1X + boxW, y + 1, PANEL_BORDER);
                g.fill(b1X, y + height - 1, b1X + boxW, y + height, PANEL_BORDER);
                g.fill(b1X, y, b1X + 1, y + height, PANEL_BORDER);
                g.fill(b1X + boxW - 1, y, b1X + boxW, y + height, PANEL_BORDER);
                g.drawString(fnt, "NET WORTH", b1X + (boxW - fnt.width("NET WORTH")) / 2, y + 3, TEXT_MUTED);
                String nwStr = formatCompact(latestNW);
                int nwW = 10 + fnt.width(nwStr);
                int nwX = b1X + (boxW - nwW) / 2;
                renderSmallCoin(g, nwX - 1, y + 13);
                g.drawString(fnt, nwStr, nwX + 10, y + 13, ACCENT);

                // Box 2: CASH
                int b2X = b1X + boxW + 4;
                g.fill(b2X, y, b2X + boxW, y + height, CARD_BG);
                g.fill(b2X, y, b2X + boxW, y + 1, PANEL_BORDER);
                g.fill(b2X, y + height - 1, b2X + boxW, y + height, PANEL_BORDER);
                g.fill(b2X, y, b2X + 1, y + height, PANEL_BORDER);
                g.fill(b2X + boxW - 1, y, b2X + boxW, y + height, PANEL_BORDER);
                g.drawString(fnt, "LIQUID CASH", b2X + (boxW - fnt.width("LIQUID CASH")) / 2, y + 3, TEXT_MUTED);
                String balStr = formatCompact(latestBal);
                int balW = 10 + fnt.width(balStr);
                int balX = b2X + (boxW - balW) / 2;
                renderSmallCoin(g, balX - 1, y + 13);
                g.drawString(fnt, balStr, balX + 10, y + 13, GREEN);

                // Box 3: ASSETS
                int b3X = b2X + boxW + 4;
                g.fill(b3X, y, b3X + boxW, y + height, CARD_BG);
                g.fill(b3X, y, b3X + boxW, y + 1, PANEL_BORDER);
                g.fill(b3X, y + height - 1, b3X + boxW, y + height, PANEL_BORDER);
                g.fill(b3X, y, b3X + 1, y + height, PANEL_BORDER);
                g.fill(b3X + boxW - 1, y, b3X + boxW, y + height, PANEL_BORDER);
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
                g.fill(x, y, x + width, y + height, CARD_BG);
                g.fill(x, y, x + width, y + 1, PANEL_BORDER);
                g.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
                g.fill(x, y, x + 1, y + height, PANEL_BORDER);
                g.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);

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
                    int liveBg = portfolioChartOffset > 0 ? (liveHover ? 0xFF047857 : 0xFF065F46) : (liveHover ? CARD_HOVER : 0xFF003024);
                    int liveBorder = portfolioChartOffset > 0 ? GREEN : (liveHover ? ACCENT : PANEL_BORDER);
                    g.fill(liveX, liveY, liveX + liveW, liveY + liveH, liveBg);
                    g.fill(liveX, liveY, liveX + liveW, liveY + 1, liveBorder);
                    g.fill(liveX, liveY + liveH - 1, liveX + liveW, liveY + liveH, liveBorder);
                    g.fill(liveX, liveY, liveX + 1, liveY + liveH, liveBorder);
                    g.fill(liveX + liveW - 1, liveY, liveX + liveW, liveY + liveH, liveBorder);
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
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF003024);
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, ACCENT);
                    g.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, ACCENT);
                    g.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, ACCENT);
                    g.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, ACCENT);
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

                if (hover) g.fill(rx, ry, rx + rw, ry + 25, CARD_HOVER);
                g.fill(rx, ry + 25, rx + rw, ry + 26, PANEL_BORDER);

                ItemStack icon = itemIconCache.computeIfAbsent(h.itemId, id -> {
                    Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                    return new ItemStack(it);
                });
                g.renderItem(icon, rx + 4, ry + 5);

                String nameStr = fnt.plainSubstrByWidth(h.displayName, rw - 110);
                g.drawString(fnt, nameStr, rx + 24, ry + 8, TEXT_PRIMARY);

                String qtyStr = "x" + formatCompact(h.quantity);
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
        g.fill(x, y, x + SCREEN_W, y + SCREEN_H, BG_DARK);
        g.fill(x, y, x + SIDEBAR_W, y + SCREEN_H, PANEL);
        g.fill(x + SIDEBAR_W - 1, y, x + SIDEBAR_W, y + SCREEN_H, PANEL_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        pendingTooltip = null;
        if (viewMode != 2) {
            pendingDropdown = null;
        }
        this.renderBackground(g);
        int sx = left(), sy = top();
        if (root != null) {
            root.layout(sx, sy, SCREEN_W, SCREEN_H);
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
        if (pendingDropdown != null && viewMode == 2) {
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
    private static final int DROP_BG     = 0xFF1A1A2E;
    private static final int DROP_BORDER = 0xFF00D4AA;
    private static final int DROP_HOVER  = 0xFF252540;

    private void renderItemDropdown(GuiGraphics g, int mx, int my, ItemDropdownData d) {
        int totalResults = d.results.size();
        int visibleRows = Math.min(totalResults, 6);
        int totalH = visibleRows * DROP_ROW_H;
        int maxScroll = Math.max(0, totalResults - visibleRows);
        d.scrollOffset = Math.max(0, Math.min(d.scrollOffset, maxScroll));

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        // 100% Solid Opaque Dark Background + Border
        g.fill(d.x, d.y, d.x + d.w, d.y + totalH, 0xFF0E0E1A);
        g.fill(d.x, d.y, d.x + d.w, d.y + 1, DROP_BORDER);
        g.fill(d.x, d.y + totalH - 1, d.x + d.w, d.y + totalH, DROP_BORDER);
        g.fill(d.x, d.y, d.x + 1, d.y + totalH, DROP_BORDER);
        g.fill(d.x + d.w - 1, d.y, d.x + d.w, d.y + totalH, DROP_BORDER);

        int itemWidth = maxScroll > 0 ? d.w - 6 : d.w;

        for (int i = 0; i < visibleRows; i++) {
            int idx = d.scrollOffset + i;
            if (idx >= totalResults) break;
            ItemSearchResult r = d.results.get(idx);
            int ry = d.y + i * DROP_ROW_H;
            boolean rowHover = mx >= d.x && mx < d.x + itemWidth && my >= ry && my < ry + DROP_ROW_H;
            if (rowHover) g.fill(d.x + 1, ry, d.x + itemWidth, ry + DROP_ROW_H, DROP_HOVER);

            // Row divider (skip first)
            if (i > 0) g.fill(d.x + 1, ry, d.x + itemWidth, ry + 1, PANEL_BORDER);

            // Item icon
            ItemStack icon = itemIconCache.computeIfAbsent(r.itemId, id -> {
                Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                return new ItemStack(it);
            });
            g.renderItem(icon, d.x + 2, ry);

            // Display name
            int nameX = d.x + 20;
            int nameMaxW = itemWidth - 22;
            String nameStr = this.font.plainSubstrByWidth(r.displayName, nameMaxW);
            g.drawString(this.font, nameStr, nameX, ry + 4, TEXT_PRIMARY, false);
        }

        if (maxScroll > 0) {
            int trackX = d.x + d.w - 6;
            int trackH = totalH;
            int thumbH = Math.max(10, trackH * visibleRows / totalResults);
            int thumbY = d.scrollOffset * (trackH - thumbH) / maxScroll;
            g.fill(trackX, d.y, trackX + 5, d.y + trackH, PANEL);
            g.fill(trackX, d.y + thumbY, trackX + 5, d.y + thumbY + thumbH, ACCENT);
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

        // Dark dim backdrop over whole screen
        g.fill(left(), top(), left() + SCREEN_W, top() + SCREEN_H, 0xEE000000);

        // Modal container background & border
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0F1524);
        g.fill(modalX, modalY, modalX + modalW, modalY + 1, ACCENT);
        g.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, ACCENT);
        g.fill(modalX, modalY, modalX + 1, modalY + modalH, ACCENT);
        g.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, ACCENT);

        // Title
        String title = "CONFIRM TRANSACTION";
        int titleW = font.width(title);
        g.drawString(font, title, modalX + (modalW - titleW) / 2, modalY + 8, ACCENT);
        g.fill(modalX + 10, modalY + 20, modalX + modalW - 10, modalY + 21, PANEL_BORDER);

        // Body message
        String msg1 = pendingConfirmation.action + " " + pendingConfirmation.quantity + "x " + pendingConfirmation.itemName;
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
        int confirmBg = confirmHover ? 0xFF004030 : 0xFF003024;
        g.fill(confirmX, confirmY, confirmX + btnW, confirmY + btnH, confirmBg);
        g.fill(confirmX, confirmY, confirmX + btnW, confirmY + 1, ACCENT);
        g.fill(confirmX, confirmY + btnH - 1, confirmX + btnW, confirmY + btnH, ACCENT);
        g.fill(confirmX, confirmY, confirmX + 1, confirmY + btnH, ACCENT);
        g.fill(confirmX + btnW - 1, confirmY, confirmX + btnW, confirmY + btnH, ACCENT);
        g.drawString(font, "Confirm", confirmX + (btnW - font.width("Confirm")) / 2, confirmY + 4, ACCENT);

        // Cancel Button
        int cancelX = modalX + modalW - btnW - 18;
        int cancelY = modalY + 66;
        boolean cancelHover = mx >= cancelX && mx <= cancelX + btnW && my >= cancelY && my <= cancelY + btnH;
        int cancelBg = cancelHover ? 0xFFC02020 : 0x80901818;
        g.fill(cancelX, cancelY, cancelX + btnW, cancelY + btnH, cancelBg);
        g.fill(cancelX, cancelY, cancelX + btnW, cancelY + 1, RED);
        g.fill(cancelX, cancelY + btnH - 1, cancelX + btnW, cancelY + btnH, RED);
        g.fill(cancelX, cancelY, cancelX + 1, cancelY + btnH, RED);
        g.fill(cancelX + btnW - 1, cancelY, cancelX + btnW, cancelY + btnH, RED);
        g.drawString(font, "Cancel", cancelX + (btnW - font.width("Cancel")) / 2, cancelY + 4, 0xFFFFFFFF);

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        com.nstut.Economy.LOGGER.info("[MarketScreen] mouseClicked mx={}, my={}, btn={}, viewMode={}", mx, my, btn, viewMode);
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
                    if (editIsInfinite && editQtyField != null) editQtyField.setValue("\u221E");
                    else if (!editIsInfinite && editQtyField != null && editQtyField.getValue().equals("\u221E")) editQtyField.setValue(String.valueOf(editingOrder.quantity));
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
                    pendingConfirmation.itemId, pendingConfirmation.quantity, pendingConfirmation.priceStr, pendingConfirmation.isSell, pendingConfirmation.isInfinite));
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
                    itemSearchAutoFilled = chosen;
                    if (itemIdField != null) {
                        itemIdField.setValue(chosen);
                        itemIdField.getEditBox().setFocused(false);
                    }
                    pendingDropdown = null;
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
        if (viewMode == 3 && historySearchField != null && historySearchField.isFocused()) {
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
        if (viewMode == 0 && searchField != null && searchField.isFocused()) {
            if (searchField.getEditBox().charTyped(codePoint, modifiers)) {
                searchQuery = searchField.getValue();
                savedSearchQuery = searchQuery;
                return true;
            }
        }
        if (viewMode == 3 && historySearchField != null && historySearchField.isFocused()) {
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
        if (historySearchField != null) {
            historySearchQuery = historySearchField.getValue();
            savedHistorySearchQuery = historySearchQuery;
        }
    }
}

