package com.nstut.forge.client;

import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultMenu;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.api.UiTheme;
import com.nstut.forge.network.MarketNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/** Premium, texture-free vault surface built from the shared Economy UI theme. */
public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 348;
        this.imageHeight = 200;
        this.inventoryLabelY = 102;
        this.titleLabelY = 8;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);
        int x = leftPos;
        int y = topPos;

        UiRender.surface(g, x, y, imageWidth, imageHeight, UiTheme.RADIUS_LG,
                UiTheme.SHELL, UiTheme.BORDER, true);

        // A restrained warm highlight echoes the reference UI without
        // competing with Economy's mint interaction accent.
        UiRender.roundedRect(g, x + 108, y + 3, 126, 2, 1,
                UiRender.alpha(UiTheme.AMBIENT_WARM, 95));

        UiRender.surface(g, x + 7, y + 31, imageWidth - 14, 65, UiTheme.RADIUS_MD,
                UiTheme.SURFACE, UiTheme.BORDER_SUBTLE, false);
        UiRender.surface(g, x + 85, y + 99, 178, 97, UiTheme.RADIUS_MD,
                UiTheme.SURFACE, UiTheme.BORDER_SUBTLE, false);

        for (Slot slot : menu.slots) {
            UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }

        renderModeButton(g, mouseX, mouseY);
    }

    private String modeLabel() {
        return switch (menu.getMode()) {
            case BOTH -> "BOTH";
            case INPUT -> "INPUT";
            case OUTPUT -> "OUTPUT";
        };
    }

    private int modeButtonWidth() {
        return font.width("MODE  " + modeLabel()) + 18;
    }

    private void renderModeButton(GuiGraphics g, int mouseX, int mouseY) {
        VaultBlockEntity.VaultMode mode = menu.getMode();
        String label = "MODE  " + modeLabel();
        int buttonWidth = modeButtonWidth();
        int buttonHeight = 17;
        int buttonX = leftPos + imageWidth - buttonWidth - 10;
        int buttonY = topPos + 7;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= buttonY && mouseY < buttonY + buttonHeight;

        int accent = switch (mode) {
            case BOTH -> UiTheme.ACCENT;
            case INPUT -> UiTheme.DANGER;
            case OUTPUT -> UiTheme.SUCCESS;
        };
        int background = switch (mode) {
            case BOTH -> UiTheme.ACCENT_DEEP;
            case INPUT -> UiTheme.DANGER_DEEP;
            case OUTPUT -> UiTheme.SUCCESS_DEEP;
        };
        if (hovered) background = UiRender.mix(background, accent, 0.18F);

        UiRender.pill(g, buttonX, buttonY, buttonWidth, buttonHeight, background, accent);
        UiRender.roundedRect(g, buttonX + 6, buttonY + 7, 3, 3, 2, accent);
        g.drawString(font, label, buttonX + 13, buttonY + 4, accent, false);

        if (hovered) {
            String tooltip = switch (mode) {
                case BOTH -> "Both way: contributes to sell orders and receives purchased items.";
                case INPUT -> "Input only: contributes items to sell orders.";
                case OUTPUT -> "Output only: receives purchased items.";
            };
            g.renderTooltip(font, font.split(Component.literal(tooltip), 190), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "VAULT", 12, 7, UiTheme.TEXT_PRIMARY, false);
        g.drawString(font, "SECURE ITEM STORAGE", 12, 18, UiTheme.TEXT_MUTED, false);
        g.drawString(font, "PLAYER INVENTORY", 93, 102, UiTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int buttonWidth = modeButtonWidth();
            int buttonX = leftPos + imageWidth - buttonWidth - 10;
            int buttonY = topPos + 7;
            if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                    && mouseY >= buttonY && mouseY < buttonY + 17) {
                BlockPos targetPos = null;
                if (menu.getVaultBlockEntity() != null) {
                    targetPos = menu.getVaultBlockEntity().getBlockPos();
                } else if (minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) {
                    targetPos = hit.getBlockPos();
                }
                if (targetPos != null) {
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleVaultModePacket(targetPos));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
