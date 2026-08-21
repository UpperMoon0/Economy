package com.nstut.forge.client;

import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultMenu;
import com.nstut.forge.network.MarketNetwork;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.api.UiTheme;
import com.nstut.openui.api.VStack;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** Vault surface migrated to the shared Economy OpenUI container screen. */
public class VaultScreen extends EconomyUiContainerScreen<VaultMenu> {

    private ButtonWidget modeBtn;
    private VaultBlockEntity.VaultMode currentMode;

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 348;
        this.imageHeight = 200;
        this.inventoryLabelY = 102;
        this.titleLabelY = 8;
        this.currentMode = menu.getMode();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        VaultBlockEntity.VaultMode m = menu.getMode();
        if (modeBtn != null && m != currentMode) {
            currentMode = m;
            modeBtn.setLabel(modeLabel(m));
        }
    }

    @Override
    protected void renderBackgroundLayer(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);
        int x = leftPos;
        int y = topPos;

        UiRender.surface(g, x, y, imageWidth, imageHeight, UiTheme.RADIUS_LG,
                UiTheme.SHELL, UiTheme.BORDER, true);

        UiRender.roundedRect(g, x + 108, y + 3, 126, 2, 1,
                UiRender.alpha(UiTheme.AMBIENT_WARM, 95));

        UiRender.surface(g, x + 7, y + 31, imageWidth - 14, 65, UiTheme.RADIUS_MD,
                UiTheme.SURFACE, UiTheme.BORDER_SUBTLE, false);
        UiRender.surface(g, x + 85, y + 99, 178, 97, UiTheme.RADIUS_MD,
                UiTheme.SURFACE, UiTheme.BORDER_SUBTLE, false);

        for (Slot slot : menu.slots) {
            UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        // Titles are drawn by OpenUI in buildUI to stay theme-consistent.
    }

    @Override
    protected UIComponent buildUI() {
        VStack root = new VStack().gap(6);

        HStack header = new HStack().gap(8);
        header.addChild(Ui.text(Component.translatable("ui.economy.vault.title")).style(TextStyle.TITLE));
        header.addChild(Ui.text(Component.translatable("ui.economy.vault.subtitle")).style(TextStyle.CAPTION));
        header.addChild(Ui.spacer().flex());
        modeBtn = Ui.button(modeLabel(currentMode), this::cycleMode).ghost();
        header.addChild(modeBtn);
        header.addChild(buildThemeToggle());
        root.addChild(header);

        return root;
    }

    private Component modeLabel(VaultBlockEntity.VaultMode m) {
        return Component.translatable(switch (m) {
            case BOTH -> "ui.economy.vault.mode_both";
            case INPUT -> "ui.economy.vault.mode_input";
            case OUTPUT -> "ui.economy.vault.mode_output";
        });
    }

    private void cycleMode() {
        VaultBlockEntity.VaultMode next =
                VaultBlockEntity.VaultMode.byId((menu.getMode().id + 1) % 3);
        BlockPos targetPos = null;
        if (menu.getVaultBlockEntity() != null) {
            targetPos = menu.getVaultBlockEntity().getBlockPos();
        } else if (minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) {
            targetPos = hit.getBlockPos();
        }
        if (targetPos != null) {
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleVaultModePacket(targetPos));
            currentMode = next;
            if (modeBtn != null) modeBtn.setLabel(modeLabel(next));
        }
    }
}
