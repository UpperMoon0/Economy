package com.nstut.forge.client;

import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultMenu;
import com.nstut.forge.network.MarketNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    private static final int BG_COLOR = 0xFF141423;
    private static final int PANEL_BORDER = 0xFF1E1E34;
    private static final int SLOT_BG = 0xFF0C0C17;
    private static final int SLOT_BORDER = 0xFF2A2A48;
    private static final int ACCENT = 0xFF00D4AA;
    private static final int TEXT_MUTED = 0xFF9E9E9E;

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 348;
        this.imageHeight = 200;
        this.inventoryLabelY = 102;
        this.titleLabelY = 13;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);

        // Main Sleek Dark Container Frame
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_COLOR);

        // Outer Borders
        g.fill(x, y, x + imageWidth, y + 1, PANEL_BORDER);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_BORDER);
        g.fill(x, y, x + 1, y + imageHeight, PANEL_BORDER);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_BORDER);

        // Section Dividers
        g.fill(x + 8, y + 30, x + imageWidth - 8, y + 31, PANEL_BORDER);
        g.fill(x + 8, y + 96, x + imageWidth - 8, y + 97, PANEL_BORDER);

        // Render Framed Dark Item Slots
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
            g.fill(sx - 1, sy - 1, sx + 17, sy, SLOT_BORDER);
            g.fill(sx - 1, sy + 16, sx + 17, sy + 17, SLOT_BORDER);
            g.fill(sx - 1, sy - 1, sx, sy + 17, SLOT_BORDER);
            g.fill(sx + 16, sy - 1, sx + 17, sy + 17, SLOT_BORDER);
        }

        // Custom Sleek Mode Button Rendering (Right aligned in top header)
        VaultBlockEntity.VaultMode currentMode = menu.getMode();
        String label = switch (currentMode) {
            case BOTH -> "MODE: BOTH";
            case INPUT -> "MODE: INPUT";
            case OUTPUT -> "MODE: OUTPUT";
        };

        int btnW = font.width(label) + 10;
        int btnH = 14;
        int btnX = x + imageWidth - btnW - 12;
        int btnY = y + 10;

        boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

        int fillBg = currentMode == VaultBlockEntity.VaultMode.INPUT ? (hovered ? 0xFF991B1B : 0x80991B1B) :
                    (currentMode == VaultBlockEntity.VaultMode.OUTPUT ? (hovered ? 0xFF065F46 : 0x80065F46) :
                    (hovered ? 0xFF003024 : 0x80003024));

        int borderClr = currentMode == VaultBlockEntity.VaultMode.INPUT ? 0xFFDC2626 :
                       (currentMode == VaultBlockEntity.VaultMode.OUTPUT ? 0xFF059669 : ACCENT);

        int textClr = currentMode == VaultBlockEntity.VaultMode.INPUT ? 0xFFFF6666 :
                     (currentMode == VaultBlockEntity.VaultMode.OUTPUT ? 0xFF66FF66 : ACCENT);

        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, fillBg);
        g.fill(btnX, btnY, btnX + btnW, btnY + 1, borderClr);
        g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, borderClr);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, borderClr);
        g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, borderClr);

        int tw = font.width(label);
        g.drawString(font, label, btnX + (btnW - tw) / 2, btnY + 3, textClr, false);

        if (hovered) {
            String tooltip = switch (currentMode) {
                case BOTH -> "Both Mode: Used for Sell Orders (Input) AND receiving bought items (Output).";
                case INPUT -> "Input Only Mode: Used ONLY for creating Sell Orders. Bought items avoid this Vault.";
                case OUTPUT -> "Output Only Mode: Used ONLY for receiving bought items. Sell Orders ignore items here.";
            };
            var lines = font.split(Component.literal(tooltip), 180);
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "VAULT STORAGE", 12, 13, ACCENT, false);
        g.drawString(font, "INVENTORY", 93, 102, TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            VaultBlockEntity.VaultMode currentMode = menu.getMode();
            String label = switch (currentMode) {
                case BOTH -> "MODE: BOTH";
                case INPUT -> "MODE: INPUT";
                case OUTPUT -> "MODE: OUTPUT";
            };
            int btnW = font.width(label) + 10;
            int btnH = 14;
            int btnX = leftPos + imageWidth - btnW - 12;
            int btnY = topPos + 10;

            if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                net.minecraft.core.BlockPos targetPos = null;
                if (menu.getVaultBlockEntity() != null) {
                    targetPos = menu.getVaultBlockEntity().getBlockPos();
                } else if (minecraft != null && minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                    targetPos = bhr.getBlockPos();
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
