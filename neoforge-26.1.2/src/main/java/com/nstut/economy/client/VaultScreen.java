package com.nstut.economy.client;

import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultMenu;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.api.VStack;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.layout.Insets;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;

/** Wide 18x3 vault using fixed vanilla slot geometry and theme-native OpenUI chrome. */
public class VaultScreen extends EconomyUiContainerScreen<VaultMenu> {
    static final int STORAGE_PANEL_Y = 36;
    static final int STORAGE_PANEL_HEIGHT = 66;
    static final int INVENTORY_LABEL_Y = 108;
    static final int PLAYER_PANEL_X = VaultMenu.PLAYER_INV_X - 9;
    static final int PLAYER_PANEL_WIDTH = 9 * 18 + 18;
    static final int PLAYER_PANEL_Y = 122;
    static final int PLAYER_PANEL_HEIGHT = 82;
    private ButtonWidget modeBtn;
    private ButtonWidget ownerBtn;
    private VaultBlockEntity.VaultMode currentMode;

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, VaultMenu.IMAGE_WIDTH, VaultMenu.IMAGE_HEIGHT);
        inventoryLabelY = INVENTORY_LABEL_Y;
        titleLabelY = 6;
        currentMode = menu.getMode();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        VaultBlockEntity.VaultMode mode = menu.getMode();
        if (modeBtn != null && mode != currentMode) {
            com.nstut.Economy.LOGGER.info("[vault-mode] client label {} -> {}", currentMode, mode);
            currentMode = mode;
            modeBtn.setLabel(modeLabel(mode));
            modeBtn.tooltip(modeTooltipWithPermission(mode));
        }
        if (ownerBtn != null) {
            ownerBtn.setLabel(storageOwnerLabel());
            ownerBtn.enabled(menu.getVaultBlockEntity() != null);
        }
        if (modeBtn != null) modeBtn.tooltip(modeTooltipWithPermission(currentMode));
    }

    @Override
    protected void renderBackgroundLayer(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        renderBaseShell(g);
        ColorScheme c = colors();
        int x = leftPos, y = topPos;
        int panelRadius = radii().medium();
        UiRender.surface(g, x + 10, y + STORAGE_PANEL_Y, imageWidth - 20, STORAGE_PANEL_HEIGHT, panelRadius, c.surface(), c.borderSubtle(), false, c);
        UiRender.surface(g, x + PLAYER_PANEL_X, y + PLAYER_PANEL_Y, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT, panelRadius, c.surface(), c.borderSubtle(), false, c);
        for (Slot slot : menu.slots) UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18, c);
    }

    @Override
    protected boolean showInventoryLabel() { return true; }

    @Override
    protected int economyInventoryLabelX() { return VaultMenu.PLAYER_INV_X; }

    @Override
    protected UIComponent buildUI() {
        HStack header = new HStack().gap(4).align(Alignment.CENTER);
        header.addChild(Ui.text(Component.translatable("ui.economy.vault.title")).style(TextStyle.TITLE));
        header.addChild(Ui.spacer().flex());
        ownerBtn = Ui.button(storageOwnerLabel(), this::toggleStorageOwner).ghost().small();
        ownerBtn.tooltip(Component.translatable("ui.economy.container.owner_transfer_tooltip"));
        header.addChild(ownerBtn);
        modeBtn = Ui.button(modeLabel(currentMode), this::cycleMode).ghost().small();
        modeBtn.tooltip(modeTooltipWithPermission(currentMode));
        header.addChild(modeBtn);
        header.addChild(buildCompactThemeToggle());

        VStack root = new VStack().gap(1);
        root.addChild(header);
        root.addChild(Ui.text(Component.translatable("ui.economy.vault.subtitle")).style(TextStyle.CAPTION));
        return Ui.padding(Insets.only(5, 10, 5, 10), root);
    }

    private Component modeLabel(VaultBlockEntity.VaultMode mode) {
        return Component.translatable(switch (mode) {
            case BOTH -> "ui.economy.mode.both";
            case INPUT -> "ui.economy.mode.input";
            case OUTPUT -> "ui.economy.mode.output";
        });
    }

    private Component modeTooltip(VaultBlockEntity.VaultMode mode) {
        return Component.translatable(switch (mode) {
            case BOTH -> "ui.economy.container.tooltip.mode_both";
            case INPUT -> "ui.economy.container.tooltip.mode_input";
            case OUTPUT -> "ui.economy.container.tooltip.mode_output";
        });
    }

    private Component modeTooltipWithPermission(VaultBlockEntity.VaultMode mode) {
        return modeTooltip(mode).copy()
                .append("\n")
                .append(Component.translatable("ui.economy.container.mode_admin_hint"));
    }

    private boolean isTeamStorage() {
        VaultBlockEntity storage = menu.getVaultBlockEntity();
        return storage != null && storage.getOwnerRef() != null
                && "TEAM".equals(storage.getOwnerRef().kind().name());
    }

    private Component storageOwnerLabel() {
        Component principal = Component.translatable(isTeamStorage()
                ? "ui.economy.principal.team"
                : "ui.economy.principal.personal");
        return Component.translatable("ui.economy.container.owner", principal);
    }

    private void toggleStorageOwner() {
        VaultBlockEntity storage = menu.getVaultBlockEntity();
        if (storage == null || minecraft == null || minecraft.level == null) return;
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.SetStorageOwnerPacket(
                minecraft.level.dimension().identifier().toString(),
                storage.getBlockPos(), false, !isTeamStorage()));
    }

    private void cycleMode() {
        BlockPos targetPos = menu.getVaultBlockEntity() != null ? menu.getVaultBlockEntity().getBlockPos() : null;
        if (targetPos == null && minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) targetPos = hit.getBlockPos();
        if (targetPos != null) {
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleVaultModePacket(targetPos));
        }
    }
}

