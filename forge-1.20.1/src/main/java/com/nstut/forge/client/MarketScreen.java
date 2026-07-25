package com.nstut.forge.client;

import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.ui.framework.*;
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
    private static final int SCREEN_H = 220;

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
    }

    private static void renderSmallCoin(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 0.5f);
        g.renderItem(COIN_ICON, 0, 0);
        g.pose().popPose();
    }

    private static List<MarketNetwork.ItemCardData> cachedCards = new ArrayList<>();
    private static String cachedBalance = "0.00";
    private static int cachedVaultCount;
    private static MarketNetwork.SyncItemDetailPacket cachedDetail;
    private static List<MarketNetwork.HistoryEntry> cachedHistory = new ArrayList<>();
    private static final Map<String, ItemStack> itemIconCache = new HashMap<>();
    private static final ItemStack COIN_ICON = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(com.nstut.Economy.MOD_ID, "coin")));

    private UIComponent root;
    private TextWidget vaultWidget;
    private UIComponent balanceWidget;
    private ButtonWidget browseBtn, vaultsBtn, newOrderBtn, orderHistoryBtn;
    private EditBoxWrapper searchField;
    private ScrollList cardGrid;
    private ScrollList askList, bidList;
    private UIComponent browser, detail, createOffer;
    private EditBoxWrapper qtyField, priceField, itemIdField;

    private String searchQuery = "";
    private String historySearchQuery = "";
    private int viewMode = 0;
    private int createOrderSourceMode = 0;
    private String selectedItemId;
    private boolean createSellMode = true;
    private UIComponent historyView;
    private UIComponent vaultsView;

    private static List<MarketNetwork.VaultDetailEntry> cachedVaultEntries = new ArrayList<>();

    public static void handleSyncVaultInfo(MarketNetwork.SyncVaultInfoPacket pkt) {
        cachedVaultEntries = pkt.entries;
    }

    private EditBoxWrapper historySearchField;

    private int browseFilterMode = 0; // 0 = All, 1 = Active Only
    private int browseSortMode = 0;   // 0 = Price ▲, 1 = Price ▼, 2 = Name A-Z, 3 = Most Active
    private ButtonWidget browseFilterBtn, browseSortBtn;

    private int historyFilterMode = 0; // 0 = All Trades, 1 = Sales Only, 2 = Purchases Only
    private int historySortMode = 0;   // 0 = Newest, 1 = Oldest, 2 = Highest Total
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
        final String action;
        final String itemName;
        final String totalPrice;

        PendingOrderExecution(String itemId, int quantity, String priceStr, boolean isSell, String action, String itemName, String totalPrice) {
            this.itemId = itemId; this.quantity = quantity; this.priceStr = priceStr;
            this.isSell = isSell; this.action = action; this.itemName = itemName;
            this.totalPrice = totalPrice;
        }
    }
    private PendingOrderExecution pendingConfirmation = null;

    /** Lightweight struct used only for the late-render dropdown pass. */
    private static class ItemDropdownData {
        final int x, y, w;
        final List<ItemSearchResult> results;
        ItemDropdownData(int x, int y, int w, List<ItemSearchResult> results) {
            this.x = x; this.y = y; this.w = w; this.results = results;
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
        buildTree();
        if (searchField != null) this.addRenderableWidget(searchField.getEditBox());
        if (historySearchField != null) this.addRenderableWidget(historySearchField.getEditBox());
        if (itemIdField != null) this.addRenderableWidget(itemIdField.getEditBox());
        if (qtyField != null) this.addRenderableWidget(qtyField.getEditBox());
        if (priceField != null) this.addRenderableWidget(priceField.getEditBox());
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
                int textW = fnt.width(cachedBalance);
                int totalW = 8 + 3 + textW;
                int startX = x + (width - totalW) / 2;
                renderSmallCoin(g, startX, y + 4);
                g.drawString(fnt, cachedBalance, startX + 11, y + 3, TEXT_PRIMARY);
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

        orderHistoryBtn = btn("History", PANEL, CARD_HOVER).onPress(() -> {
            cachedHistory = new ArrayList<>();
            switchView(3);
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

        UIComponent history = buildHistory(font);
        history.flex();
        history.setVisible(false);
        this.historyView = history;
        contentArea.addChild(history);

        UIComponent vaults = buildVaults(font);
        vaults.flex();
        vaults.setVisible(false);
        this.vaultsView = vaults;
        contentArea.addChild(vaults);

        PaddingBox contentPadding = new PaddingBox(8, 8, 8, 8, contentArea);
        contentPadding.flex();
        main.addChild(contentPadding);
    }

    private ButtonWidget btn(String label, int normal, int hover) {
        return new ButtonWidget(label, normal, hover, TEXT_PRIMARY);
    }

    private UIComponent buildBrowser(Font font) {
        VStack v = new VStack().gap(4);
        searchField = new EditBoxWrapper(60, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search products...");
        v.addChild(searchField);

        HStack bar = new HStack().gap(4);
        browseFilterBtn = btn("Filter: " + getBrowseFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            browseFilterMode = (browseFilterMode + 1) % 2;
            browseFilterBtn.setLabel("Filter: " + getBrowseFilterLabel());
        });
        browseFilterBtn.flex();
        bar.addChild(browseFilterBtn);

        browseSortBtn = btn("Sort: " + getBrowseSortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            browseSortMode = (browseSortMode + 1) % 4;
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
                        g.drawString(fnt, card.globalPrice, cx + 37, cy + 22, ACCENT);
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
                    ItemStack icon = itemIconCache.computeIfAbsent(cachedDetail.itemId, id -> {
                        Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                        return new ItemStack(it);
                    });
                    g.renderItem(icon, x, y);
                    g.drawString(fnt, cachedDetail.displayName, x + 20, y + 4, TEXT_PRIMARY);

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
                    int maxP = Integer.MIN_VALUE, minP = Integer.MAX_VALUE;
                    for (MarketNetwork.ChartPoint cp : pts) {
                        if (cp.price > maxP) maxP = cp.price;
                        if (cp.price < minP) minP = cp.price;
                    }
                    if (maxP == minP) { maxP += 5; minP = Math.max(0, minP - 5); }
                    float range = maxP - minP;

                    // Draw min/max price text labels on the left
                    g.drawString(fnt, String.valueOf(maxP), x + 3, y + 3, TEXT_MUTED);
                    g.drawString(fnt, String.valueOf(minP), x + 3, y + height - 11, TEXT_MUTED);

                    // Right current price badge dimensions
                    int currentPrice = pts.get(pts.size() - 1).price;
                    String currentPriceStr = String.valueOf(currentPrice);
                    int currentPriceW = fnt.width(currentPriceStr);
                    int badgeW = currentPriceW + 8;
                    int badgeH = 12;
                    int badgeX = x + width - badgeW - 4;
                    int rawY = y + height - 6 - (int)((currentPrice - minP) / range * (height - 12));
                    int badgeY = Math.max(y + 2, Math.min(y + height - badgeH - 2, rawY - 5));

                    // Dotted line stops before reaching the right price badge
                    int lineY = rawY;
                    int chartLeft = x + 26;
                    int chartRight = badgeX - 4;
                    int dotStep = 4;
                    for (int lx = chartLeft; lx <= chartRight; lx += dotStep) {
                        g.fill(lx, lineY, Math.min(lx + 2, chartRight), lineY + 1, ACCENT_DIM);
                    }

                    // Line graph plot & interactive nodes
                    int ptsCount = pts.size();
                    for (int i = 0; i < ptsCount; i++) {
                        int x0 = chartLeft + i * (chartRight - chartLeft) / Math.max(1, ptsCount - 1);
                        int y0 = y + height - 6 - (int)((pts.get(i).price - minP) / range * (height - 12));

                        if (i > 0) {
                            int xPrev = chartLeft + (i - 1) * (chartRight - chartLeft) / Math.max(1, ptsCount - 1);
                            int yPrev = y + height - 6 - (int)((pts.get(i - 1).price - minP) / range * (height - 12));
                            drawLine(g, xPrev, yPrev, x0, y0, CHART_LINE);
                        }

                        // Data node dot + tooltip hover detection
                        boolean nodeHover = (mx >= x0 - 3 && mx <= x0 + 3 && my >= y0 - 3 && my <= y0 + 3);
                        int dotClr = nodeHover ? 0xFFFFFFFF : ACCENT;
                        g.fill(x0 - 1, y0 - 1, x0 + 2, y0 + 2, dotClr);
                        if (nodeHover) {
                            pendingTooltip = "Price: $" + pts.get(i).price + " | Volume: " + pts.get(i).quantity;
                        }
                    }

                    // Draw right current price badge pill container on top of chart background
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
                String line;
                if (e.initialQuantity > e.quantity) {
                    int fulfilled = e.initialQuantity - e.quantity;
                    int pct = (fulfilled * 100) / e.initialQuantity;
                    line = e.price + " x" + e.quantity + " (" + fulfilled + "/" + e.initialQuantity + " - " + pct + "% filled)";
                } else {
                    line = e.price + " x" + e.quantity;
                }
                int clr = isSell ? RED : GREEN;
                g.drawString(fnt, line, px + 10, ry + 3, clr);

                // 3. Cancel Button Widget
                String cancelText = "Cancel";
                int btnW = fnt.width(cancelText) + 8;
                int btnH = 12;
                int btnX = rx + rw - btnW - 3;
                int btnY = ry + 2;

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

    private TextWidget createOfferTitleLabel;
    private ButtonWidget switchModeBtn;
    private TextWidget createOfferErrorLabel;

    private UIComponent buildCreateOffer(Font font) {
        VStack v = new VStack().gap(6);

        HStack header = new HStack().gap(6);
        ButtonWidget backBtn = btn("< Back", PANEL, CARD_HOVER).onPress(() -> switchView(createOrderSourceMode));
        header.addChild(backBtn);
        createOfferTitleLabel = TextWidget.label(createSellMode ? "Create Sell Order" : "Create Buy Order", TEXT_PRIMARY);
        header.addChild(createOfferTitleLabel);
        v.addChild(header);

        itemIdField = new EditBoxWrapper(128, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search item name or ID...");
        if (selectedItemId != null) {
            itemIdField.setValue(selectedItemId);
            itemSearchAutoFilled = selectedItemId;
        }
        v.addChild(itemIdField);

        // ── Search hint label ──
        v.addChild(new UIComponent() {
            @Override public int preferredWidth(Font f) { return 0; }
            @Override public int preferredHeight(Font f) { return 0; }
            @Override public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                if (!visible) return;
                String query = itemIdField != null ? itemIdField.getValue().trim() : "";
                boolean showDropdown = !query.isEmpty()
                        && !query.equals(itemSearchAutoFilled);
                if (showDropdown) {
                    List<ItemSearchResult> results = getItemSearchResults(query);
                    if (!results.isEmpty()) {
                        int dx = itemIdField != null ? itemIdField.getX() : x;
                        int dy = itemIdField != null ? (itemIdField.getY() + itemIdField.getHeight() + 1) : y;
                        int dw = itemIdField != null ? itemIdField.getWidth() : width;
                        pendingDropdown = new ItemDropdownData(dx, dy, dw, results);
                    } else {
                        pendingDropdown = null;
                    }
                } else {
                    pendingDropdown = null;
                }
            }
        });

        qtyField = new EditBoxWrapper(10, TEXT_PRIMARY, PANEL, font).setPlaceholder("Quantity (e.g. 10)");
        v.addChild(qtyField);

        priceField = new EditBoxWrapper(20, TEXT_PRIMARY, PANEL, font).setPlaceholder("Price per unit (e.g. 150.00)");
        v.addChild(priceField);

        v.addChild(new SizedBox(0, 4)); // Spacing before buttons
        ButtonWidget createBtn = btn("Submit Order", ACCENT_DIM, ACCENT).onPress(this::submitOffer);
        v.addChild(createBtn);

        switchModeBtn = btn(createSellMode ? "Switch to Buy Mode" : "Switch to Sell Mode", PANEL, CARD_HOVER).onPress(() -> {
            createSellMode = !createSellMode;
            updateCreateOfferLabels();
        });
        v.addChild(switchModeBtn);

        createOfferErrorLabel = TextWidget.label("", RED);
        createOfferErrorLabel.setVisible(false);
        v.addChild(createOfferErrorLabel);
        return v;
    }

    private void updateCreateOfferLabels() {
        if (createOfferTitleLabel != null) {
            createOfferTitleLabel.setText(createSellMode ? "Create Sell Order" : "Create Buy Order");
        }
        if (switchModeBtn != null) {
            switchModeBtn.setLabel(createSellMode ? "Switch to Buy Mode" : "Switch to Sell Mode");
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
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getValue().trim());
            if (qty <= 0) { showCreateError("Quantity must be greater than 0."); return; }
        } catch (NumberFormatException ignored) {
            showCreateError("Quantity must be a valid number.");
            return;
        }
        if (createSellMode) {
            if (cachedDetail != null && cachedDetail.itemId.equalsIgnoreCase(itemId)) {
                if (qty > cachedDetail.vaultCount) {
                    showCreateError("Not enough in vault. You have " + cachedDetail.vaultCount + ".");
                    return;
                }
            }
        } else {
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
        String totStr = tot.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String dispName = itemId;
        if (cachedDetail != null && cachedDetail.itemId.equalsIgnoreCase(itemId)) {
            dispName = cachedDetail.displayName;
        } else {
            net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
            if (it != net.minecraft.world.item.Items.AIR) {
                dispName = new ItemStack(it).getHoverName().getString();
            }
        }
        pendingConfirmation = new PendingOrderExecution(itemId, qty, price.toPlainString(), createSellMode, actionStr, dispName, totStr);
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
        if (mode == 0) selectedItemId = null;
        if (browser != null) browser.setVisible(mode == 0);
        if (detail != null) detail.setVisible(mode == 1);
        if (createOffer != null) createOffer.setVisible(mode == 2);
        if (historyView != null) historyView.setVisible(mode == 3);
        if (vaultsView != null) vaultsView.setVisible(mode == 4);
        if (mode == 2) {
            updateCreateOfferLabels();
            // Reset dropdown guard whenever we (re-)enter the form
            itemSearchAutoFilled = selectedItemId; // pre-filled IDs shouldn't auto-open dropdown
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

    private List<MarketNetwork.HistoryEntry> filterHistory() {
        List<MarketNetwork.HistoryEntry> f = new ArrayList<>();
        String q = historySearchQuery.toLowerCase().trim();
        for (MarketNetwork.HistoryEntry e : cachedHistory) {
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
     *  contains the query (case-insensitive). Returns at most 6 results. */
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
                if (results.size() >= 6) break;
            }
        }
        return results;
    }

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MM/dd HH:mm");

    private UIComponent buildHistory(Font font) {
        VStack v = new VStack().gap(4);
        v.addChild(TextWidget.centered("ORDER HISTORY", ACCENT));

        historySearchField = new EditBoxWrapper(60, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search item or player...");
        v.addChild(historySearchField);

        HStack bar = new HStack().gap(4);
        historyFilterBtn = btn("Filter: " + getHistoryFilterLabel(), PANEL, CARD_HOVER).onPress(() -> {
            historyFilterMode = (historyFilterMode + 1) % 3;
            historyFilterBtn.setLabel("Filter: " + getHistoryFilterLabel());
        });
        historyFilterBtn.flex();
        bar.addChild(historyFilterBtn);

        historySortBtn = btn("Sort: " + getHistorySortLabel(), PANEL, CARD_HOVER).onPress(() -> {
            historySortMode = (historySortMode + 1) % 3;
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
                List<MarketNetwork.HistoryEntry> entries = filterHistory();
                if (entries.isEmpty()) {
                    String msg = "No trades matching filter";
                    g.drawString(fnt, msg, rx + (rw - fnt.width(msg)) / 2, ry + 8, TEXT_MUTED);
                    return;
                }
                if (idx >= entries.size()) return;
                MarketNetwork.HistoryEntry e = entries.get(idx);

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

                int nameX = rx + 24 + fnt.width(typeTag) + 6;
                String nameText = fnt.plainSubstrByWidth(e.displayName, rw - nameX - 65 - rx);
                g.drawString(fnt, nameText, nameX, ry + 4, TEXT_PRIMARY);

                String dateStr = DATE_FMT.format(new Date(e.timestamp));
                int dateW = fnt.width(dateStr);
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
        pendingDropdown = null;
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
        }
        // ── Late-pass: item search dropdown (drawn on top of everything) ──
        if (pendingDropdown != null && viewMode == 2) {
            renderItemDropdown(g, mx, my, pendingDropdown);
        }
        // ── Late-pass: Confirmation modal overlay ──
        if (pendingConfirmation != null) {
            renderConfirmationModal(g, mx, my);
        }
        if (pendingTooltip != null) {
            g.renderTooltip(this.font, Component.literal(pendingTooltip), mx, my);
        }
    }

    private static final int DROP_ROW_H = 16;
    private static final int DROP_BG     = 0xFF1A1A2E;
    private static final int DROP_BORDER = 0xFF00D4AA;
    private static final int DROP_HOVER  = 0xFF252540;

    private void renderItemDropdown(GuiGraphics g, int mx, int my, ItemDropdownData d) {
        int rows = d.results.size();
        int totalH = rows * DROP_ROW_H;

        // Background + border
        g.fill(d.x, d.y, d.x + d.w, d.y + totalH, DROP_BG);
        g.fill(d.x, d.y, d.x + d.w, d.y + 1, DROP_BORDER);
        g.fill(d.x, d.y + totalH - 1, d.x + d.w, d.y + totalH, DROP_BORDER);
        g.fill(d.x, d.y, d.x + 1, d.y + totalH, DROP_BORDER);
        g.fill(d.x + d.w - 1, d.y, d.x + d.w, d.y + totalH, DROP_BORDER);

        for (int i = 0; i < rows; i++) {
            ItemSearchResult r = d.results.get(i);
            int ry = d.y + i * DROP_ROW_H;
            boolean rowHover = mx >= d.x && mx < d.x + d.w && my >= ry && my < ry + DROP_ROW_H;
            if (rowHover) g.fill(d.x + 1, ry, d.x + d.w - 1, ry + DROP_ROW_H, DROP_HOVER);

            // Row divider (skip first)
            if (i > 0) g.fill(d.x + 1, ry, d.x + d.w - 1, ry + 1, PANEL_BORDER);

            // Item icon
            ItemStack icon = itemIconCache.computeIfAbsent(r.itemId, id -> {
                Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
                return new ItemStack(it);
            });
            g.renderItem(icon, d.x + 2, ry);

            // Display name
            int nameX = d.x + 20;
            int nameMaxW = d.w - 22;
            String nameStr = this.font.plainSubstrByWidth(r.displayName, nameMaxW);
            g.drawString(this.font, nameStr, nameX, ry + 4, TEXT_PRIMARY);
        }
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
                    pendingConfirmation.itemId, pendingConfirmation.quantity, pendingConfirmation.priceStr, pendingConfirmation.isSell));
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
            if (mx >= d.x && mx < d.x + d.w && my >= d.y && my < d.y + d.results.size() * DROP_ROW_H) {
                int idx = ((int) my - d.y) / DROP_ROW_H;
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
        if (root != null && root.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (viewMode == 0 && searchField != null && searchField.isFocused()) {
            if (searchField.keyPressed(key, scan, mod)) {
                searchQuery = searchField.getValue();
                return true;
            }
            searchQuery = searchField.getValue();
        }
        if (viewMode == 2) {
            if (itemIdField != null && itemIdField.isFocused() && itemIdField.keyPressed(key, scan, mod)) {
                // Any keystroke in the item id field resets the auto-fill guard
                // so the dropdown shows again when the text changes.
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
    public void containerTick() {
        super.containerTick();
        if (searchField != null) searchQuery = searchField.getValue();
        if (historySearchField != null) historySearchQuery = historySearchField.getValue();
    }
}

