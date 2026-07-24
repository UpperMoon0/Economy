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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 250;

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
    private static final int SIDEBAR_W = 68;

    private static List<MarketNetwork.ItemCardData> cachedCards = new ArrayList<>();
    private static String cachedBalance = "0.00";
    private static boolean cachedHasVault;
    private static MarketNetwork.SyncItemDetailPacket cachedDetail;
    private static final Map<String, ItemStack> itemIconCache = new HashMap<>();
    private static final ItemStack COIN_ICON = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "gold_nugget")));

    private UIComponent root;
    private TextWidget vaultWidget;
    private UIComponent balanceWidget;
    private ButtonWidget browseBtn, newOrderBtn;
    private EditBoxWrapper searchField;
    private ScrollList cardGrid;
    private ScrollList askList, bidList;
    private UIComponent browser, detail, createOffer;
    private EditBoxWrapper qtyField, priceField, itemIdField;

    private String searchQuery = "";
    private int viewMode = 0;
    private int createOrderSourceMode = 0;
    private String selectedItemId;
    private boolean createSellMode = true;

    public MarketScreen(MarketMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SCREEN_W;
        this.imageHeight = SCREEN_H;
    }

    public static void handleSyncItemList(MarketNetwork.SyncItemListPacket pkt) {
        cachedCards = pkt.cards;
        cachedBalance = pkt.balance;
        cachedHasVault = pkt.hasVault;
    }

    public static void handleSyncItemDetail(MarketNetwork.SyncItemDetailPacket pkt) {
        cachedDetail = pkt;
    }

    @Override
    protected void init() {
        super.init();
        buildTree();
        if (searchField != null) this.addRenderableWidget(searchField.getEditBox());
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

        // Sidebar
        VStack sidebar = new VStack().gap(4);
        sidebar.addChild(TextWidget.centered("Market", ACCENT));
        balanceWidget = new UIComponent() {
            @Override public int preferredWidth(Font f) { return SIDEBAR_W; }
            @Override public int preferredHeight(Font f) { return 14; }
            @Override
            public void render(GuiGraphics g, Font fnt, int mx, int my, float pt) {
                g.renderItem(COIN_ICON, x + 2, y - 2);
                g.drawString(fnt, cachedBalance, x + 18, y + 2, TEXT_PRIMARY);
            }
        };
        sidebar.addChild(balanceWidget);
        vaultWidget = TextWidget.centered(cachedHasVault ? "Vault OK" : "No Vault!", cachedHasVault ? GREEN : RED);
        sidebar.addChild(vaultWidget);
        sidebar.addChild(new Divider(PANEL_BORDER));

        browseBtn = btn("Browse", PANEL, ACCENT).onPress(() -> {
            selectedItemId = null;
            switchView(0);
        });
        sidebar.addChild(browseBtn);

        newOrderBtn = btn("New Order", PANEL, ACCENT).onPress(() -> {
            createOrderSourceMode = 0;
            selectedItemId = null;
            createSellMode = true;
            if (itemIdField != null) itemIdField.setValue("");
            if (qtyField != null) qtyField.setValue("");
            if (priceField != null) priceField.setValue("");
            switchView(2);
        });
        sidebar.addChild(newOrderBtn);

        sidebar.addChild(new Spacer());
        SizedBox sidebarBox = new SizedBox(SIDEBAR_W, SCREEN_H);
        sidebarBox.addChild(sidebar);
        main.addChild(sidebarBox);

        // Content Container with Margin & Padding
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

        main.addChild(contentArea);
    }

    private ButtonWidget btn(String label, int normal, int hover) {
        return new ButtonWidget(label, normal, hover, TEXT_PRIMARY);
    }

    private UIComponent buildBrowser(Font font) {
        VStack v = new VStack().gap(6);
        searchField = new EditBoxWrapper(60, TEXT_PRIMARY, PANEL, font).setPlaceholder("Search products...");
        v.addChild(searchField);

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
                g.renderItem(icon, cx + 8, cy + (cardH - 16) / 2);

                String nameText = fnt.plainSubstrByWidth(card.displayName, cardW - 80);
                g.drawString(fnt, nameText, cx + 30, cy + 6, TEXT_PRIMARY);

                if (card.globalPrice != null && !card.globalPrice.isEmpty()) {
                    if (!card.globalPrice.equals("--")) {
                        g.renderItem(COIN_ICON, cx + 28, cy + 18);
                        g.drawString(fnt, card.globalPrice, cx + 46, cy + 22, ACCENT);
                    } else {
                        g.drawString(fnt, card.globalPrice, cx + 30, cy + 22, TEXT_MUTED);
                    }
                }

                if (card.offerCount > 0) {
                    String countText = card.offerCount + (card.offerCount == 1 ? " order" : " orders");
                    int countW = fnt.width(countText);
                    g.drawString(fnt, countText, cx + cardW - countW - 8, cy + 16, TEXT_MUTED);
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
            @Override public int preferredHeight(Font f) { return 48; }
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

                    // Draw min/max price text labels
                    g.drawString(fnt, String.valueOf(maxP), x + 3, y + 2, TEXT_MUTED);
                    g.drawString(fnt, String.valueOf(minP), x + 3, y + height - 10, TEXT_MUTED);

                    for (int i = 1; i < pts.size(); i++) {
                        int x0 = x + 30 + (i - 1) * (width - 36) / Math.max(1, pts.size() - 1);
                        int x1 = x + 30 + i * (width - 36) / Math.max(1, pts.size() - 1);
                        int y0 = y + height - 5 - (int)((pts.get(i - 1).price - minP) / range * (height - 10));
                        int y1 = y + height - 5 - (int)((pts.get(i).price - minP) / range * (height - 10));
                        drawLine(g, x0, y0, x1, y1, CHART_LINE);
                    }
                } else {
                    g.drawString(fnt, "No trade history available yet", x + (width - fnt.width("No trade history available yet")) / 2, y + 18, TEXT_MUTED);
                }
            }
        });

        HStack cols = new HStack().gap(4);
        cols.flex();
        cols.addChild(buildOrderColumn(font, true));
        cols.addChild(buildOrderColumn(font, false));
        v.addChild(cols);

        // My Orders Section
        v.addChild(TextWidget.centered("MY ORDERS", ACCENT));
        ScrollList myOrderList = new ScrollList(
            () -> Math.max(1, getMyOrders().size()),
            14,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<MarketNetwork.OrderEntry> myOrders = getMyOrders();
                if (myOrders.isEmpty()) {
                    String emptyText = "No active orders for this item";
                    g.drawString(fnt, emptyText, rx + (rw - fnt.width(emptyText)) / 2, ry + 2, TEXT_MUTED);
                    return;
                }
                if (idx >= myOrders.size()) return;
                MarketNetwork.OrderEntry e = myOrders.get(idx);
                boolean isSell = cachedDetail != null && cachedDetail.asks.contains(e);
                String typePrefix = isSell ? "[SELL] " : "[BUY] ";
                int clr = isSell ? RED : GREEN;
                if (hover) g.fill(rx, ry, rx + rw, ry + 13, CARD_HOVER);

                g.drawString(fnt, typePrefix, rx + 2, ry + 2, clr);
                int px = rx + 2 + fnt.width(typePrefix);
                g.renderItem(COIN_ICON, px - 2, ry - 2);
                String line = e.price + " x" + e.quantity;
                g.drawString(fnt, line, px + 15, ry + 2, clr);

                String cancelText = "[Cancel]";
                int cancelW = fnt.width(cancelText);
                g.drawString(fnt, cancelText, rx + rw - cancelW - 2, ry + 2, RED);
            },
            (idx, btn) -> {
                List<MarketNetwork.OrderEntry> myOrders = getMyOrders();
                if (idx >= 0 && idx < myOrders.size()) {
                    MarketNetwork.OrderEntry e = myOrders.get(idx);
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CancelOfferPacket(e.offerId));
                }
            },
            PANEL, ACCENT_DIM);
        v.addChild(myOrderList);

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
            14,
            (g, fnt, idx, rx, ry, rw, mx, my, hover) -> {
                List<MarketNetwork.OrderEntry> entries = getOtherOrders(isAsks);
                if (entries.isEmpty()) {
                    String emptyText = isAsks ? "No sell orders" : "No buy orders";
                    g.drawString(fnt, emptyText, rx + (rw - fnt.width(emptyText)) / 2, ry + 2, TEXT_MUTED);
                    return;
                }
                if (idx >= entries.size()) return;
                MarketNetwork.OrderEntry e = entries.get(idx);
                int clr = e.isServerOrder ? ACCENT : (isAsks ? RED : GREEN);
                if (hover) g.fill(rx, ry, rx + rw, ry + 13, CARD_HOVER);

                int textX = rx + 2;
                if (e.isServerOrder) {
                    String badge = "SERVER";
                    int badgeW = fnt.width(badge) + 4;
                    int badgeH = 10;
                    int badgeX = rx + 2;
                    int badgeY = ry + 2;

                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF00382B);
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, ACCENT);
                    g.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, ACCENT);
                    g.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, ACCENT);
                    g.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, ACCENT);

                    g.drawString(fnt, badge, badgeX + 2, badgeY + 1, ACCENT);
                    textX += badgeW + 4;

                    if (mx >= badgeX && mx < badgeX + badgeW && my >= badgeY && my < badgeY + badgeH) {
                        pendingTooltip = "Server Order: Generated by the server to provide market liquidity.";
                    }
                }

                g.renderItem(COIN_ICON, textX - 2, ry - 2);
                String line = e.price + " x" + e.quantity;
                g.drawString(fnt, line, textX + 15, ry + 2, clr);
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
        v.addChild(btn("< Back", PANEL, CARD_HOVER).onPress(() -> switchView(createOrderSourceMode)));

        createOfferTitleLabel = TextWidget.label(createSellMode ? "Create Sell Order" : "Create Buy Order", TEXT_PRIMARY);
        v.addChild(createOfferTitleLabel);

        itemIdField = new EditBoxWrapper(128, TEXT_PRIMARY, PANEL, font).setPlaceholder("Item ID (e.g. minecraft:diamond)");
        if (selectedItemId != null) {
            itemIdField.setValue(selectedItemId);
        }
        v.addChild(itemIdField);

        qtyField = new EditBoxWrapper(10, TEXT_PRIMARY, PANEL, font).setPlaceholder("Quantity (e.g. 10)");
        v.addChild(qtyField);

        priceField = new EditBoxWrapper(20, TEXT_PRIMARY, PANEL, font).setPlaceholder("Price per unit (e.g. 150.00)");
        v.addChild(priceField);

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
        try {
            int qty = Integer.parseInt(qtyField.getValue().trim());
            String price = priceField.getValue().trim();
            if (qty <= 0) { showCreateError("Quantity must be greater than 0."); return; }
            if (price.isEmpty()) { showCreateError("Price is required."); return; }
            // Vault check for sell orders
            if (createSellMode && cachedDetail != null && cachedDetail.vaultCount >= 0) {
                if (qty > cachedDetail.vaultCount) {
                    showCreateError("Not enough in vault. You have " + cachedDetail.vaultCount + ".");
                    return;
                }
            }
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.CreateOfferPacket(itemId, qty, price, createSellMode));
            selectedItemId = itemId;
            cachedDetail = null;
            switchView(1);
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(itemId));
        } catch (NumberFormatException ignored) {
            showCreateError("Quantity must be a valid number.");
        }
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
        browser.setVisible(mode == 0);
        detail.setVisible(mode == 1);
        createOffer.setVisible(mode == 2);
        if (mode == 2) updateCreateOfferLabels();

        if (browseBtn != null) {
            browseBtn.setVisible(true);
            browseBtn.setActive(mode == 0);
        }
        if (newOrderBtn != null) {
            newOrderBtn.setVisible(mode == 0);
            newOrderBtn.setActive(mode == 2);
        }
        if (searchField != null) {
            searchField.setVisible(mode == 0);
            if (mode != 0) searchField.getEditBox().setFocused(false);
        }
        if (itemIdField != null) {
            com.nstut.Economy.LOGGER.info("[MarketScreen] switchView itemIdField.getValue()='{}' BEFORE setVisible({})", itemIdField.getValue(), mode == 2);
            itemIdField.setVisible(mode == 2);
            itemIdField.getEditBox().setFocused(mode == 2);
            com.nstut.Economy.LOGGER.info("[MarketScreen] switchView itemIdField.getValue()='{}' AFTER setVisible", itemIdField.getValue());
        } else {
            com.nstut.Economy.LOGGER.warn("[MarketScreen] switchView itemIdField is NULL!");
        }
        if (qtyField != null) {
            com.nstut.Economy.LOGGER.info("[MarketScreen] switchView qtyField.getValue()='{}' BEFORE setVisible({})", qtyField.getValue(), mode == 2);
            qtyField.setVisible(mode == 2);
            if (mode != 2) qtyField.getEditBox().setFocused(false);
            com.nstut.Economy.LOGGER.info("[MarketScreen] switchView qtyField.getValue()='{}' AFTER setVisible", qtyField.getValue());
        } else {
            com.nstut.Economy.LOGGER.warn("[MarketScreen] switchView qtyField is NULL!");
        }
        if (priceField != null) {
            com.nstut.Economy.LOGGER.info("[MarketScreen] switchView priceField.getValue()='{}' BEFORE setVisible({})", priceField.getValue(), mode == 2);
            priceField.setVisible(mode == 2);
            if (mode != 2) priceField.getEditBox().setFocused(false);
            com.nstut.Economy.LOGGER.info("[MarketScreen] switchView priceField.getValue()='{}' AFTER setVisible", priceField.getValue());
        } else {
            com.nstut.Economy.LOGGER.warn("[MarketScreen] switchView priceField is NULL!");
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
            if (q.isEmpty() || c.displayName.toLowerCase().contains(q)) f.add(c);
        }
        return f;
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
        if (pendingTooltip != null) {
            g.renderTooltip(this.font, Component.literal(pendingTooltip), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        com.nstut.Economy.LOGGER.info("[MarketScreen] mouseClicked mx={}, my={}, btn={}, viewMode={}", mx, my, btn, viewMode);
        if (root != null && root.mouseClicked(mx, my, btn)) {
            com.nstut.Economy.LOGGER.info("[MarketScreen] Click handled by root UI component");
            return true;
        }
        boolean superResult = super.mouseClicked(mx, my, btn);
        com.nstut.Economy.LOGGER.info("[MarketScreen] super.mouseClicked returned {}", superResult);
        return superResult;
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
            if (itemIdField != null && itemIdField.isFocused() && itemIdField.keyPressed(key, scan, mod)) return true;
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
}

