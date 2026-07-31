package com.nstut.forge.client;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.forge.network.MarketNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

public class TankScreen extends AbstractContainerScreen<TankMenu> {

    private static final int BG_COLOR = 0xFF141423;
    private static final int PANEL_BORDER = 0xFF1E1E34;
    private static final int SLOT_BG = 0xFF0C0C17;
    private static final int SLOT_BORDER = 0xFF2A2A48;
    private static final int ACCENT = 0xFF00D4AA;
    private static final int TEXT_MUTED = 0xFF9E9E9E;
    private static final int TEXT_PRIMARY = 0xFFCCCCCC;
    private static final int TANK_OUTLINE = 0xFF2A2A48;
    private static final int TANK_BG = 0xFF0C0C17;

    public TankScreen(TankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 280;
        this.imageHeight = 186;
        this.inventoryLabelY = 90;
        this.titleLabelY = 13;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);

        int x = leftPos;
        int y = topPos;

        // Main background
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_COLOR);

        // Border
        g.fill(x, y, x + imageWidth, y + 1, PANEL_BORDER);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_BORDER);
        g.fill(x, y, x + 1, y + imageHeight, PANEL_BORDER);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_BORDER);

        // Section separators
        g.fill(x + 8, y + 30, x + imageWidth - 8, y + 31, PANEL_BORDER);
        g.fill(x + 8, y + 84, x + imageWidth - 8, y + 85, PANEL_BORDER);

        TankBlockEntity tank = menu.getTankBlockEntity();
        if (tank != null) {
            FluidStack fluid = tank.getFluid();
            int cap = tank.getCapacity();
            int amount = fluid.getAmount();

            // Tank outline - compact, centered
            int tankX = x + (imageWidth - 44) / 2;
            int tankY = y + 41;
            int tankW = 44;
            int tankH = 34;

            // Tank outer border
            g.fill(tankX - 2, tankY - 2, tankX + tankW + 2, tankY + tankH + 2, TANK_OUTLINE);
            // Tank inner background
            g.fill(tankX, tankY, tankX + tankW, tankY + tankH, TANK_BG);

            // Render fluid inside tank
            if (!fluid.isEmpty() && amount > 0) {
                int innerX = tankX + 2;
                int innerY = tankY + 2;
                int innerW = tankW - 4;
                int innerH = tankH - 4;
                int fluidH = Math.max(1, (int) ((long) amount * innerH / cap));
                fluidH = Math.min(innerH, fluidH);
                int fluidY = innerY + innerH - fluidH;

                // Get fluid tint color
                int tintColor = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor();
                float r = ((tintColor >> 16) & 0xFF) / 255f;
                float gCol = ((tintColor >> 8) & 0xFF) / 255f;
                float b = (tintColor & 0xFF) / 255f;
                float a = ((tintColor >> 24) & 0xFF) / 255f;
                if (a == 0) a = 1f;

                // Render fluid with sprite + tint
                ResourceLocation still = IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture();
                TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);

                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, gCol, b, a);
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                renderTiledFluid(g, sprite, innerX, fluidY, innerW, fluidH, innerY + innerH);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            // Keep status text in the open area left of the tank so it cannot
            // collide with the inventory heading below.
            int statusCenterX = x + 59;
            int statusMaxWidth = 108;
            String fluidText = fluid.isEmpty() ? "Empty" : fluid.getDisplayName().getString();
            fluidText = font.plainSubstrByWidth(fluidText, statusMaxWidth);
            g.drawString(font, fluidText, statusCenterX - font.width(fluidText) / 2,
                    y + 47, TEXT_PRIMARY, false);

            String amountText = com.nstut.economy.util.EconomyFormatUtil.formatFluidAmount(amount)
                    + " / " + com.nstut.economy.util.EconomyFormatUtil.formatFluidAmount(cap);
            amountText = font.plainSubstrByWidth(amountText, statusMaxWidth);
            g.drawString(font, amountText, statusCenterX - font.width(amountText) / 2,
                    y + 60, TEXT_MUTED, false);
        }

        // Render slots
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
            g.fill(sx - 1, sy - 1, sx + 17, sy, SLOT_BORDER);
            g.fill(sx - 1, sy + 16, sx + 17, sy + 17, SLOT_BORDER);
            g.fill(sx - 1, sy - 1, sx, sy + 17, SLOT_BORDER);
            g.fill(sx + 16, sy - 1, sx + 17, sy + 17, SLOT_BORDER);
        }

        // Mode button
        TankBlockEntity.TankMode currentMode = menu.getMode();
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

        int fillBg = currentMode == TankBlockEntity.TankMode.INPUT ? (hovered ? 0xFF991B1B : 0x80991B1B) :
                    (currentMode == TankBlockEntity.TankMode.OUTPUT ? (hovered ? 0xFF065F46 : 0x80065F46) :
                    (hovered ? 0xFF003024 : 0x80003024));

        int borderClr = currentMode == TankBlockEntity.TankMode.INPUT ? 0xFFDC2626 :
                       (currentMode == TankBlockEntity.TankMode.OUTPUT ? 0xFF059669 : ACCENT);

        int textClr = currentMode == TankBlockEntity.TankMode.INPUT ? 0xFFFF6666 :
                     (currentMode == TankBlockEntity.TankMode.OUTPUT ? 0xFF66FF66 : ACCENT);

        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, fillBg);
        g.fill(btnX, btnY, btnX + btnW, btnY + 1, borderClr);
        g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, borderClr);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, borderClr);
        g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, borderClr);

        int lw = font.width(label);
        g.drawString(font, label, btnX + (btnW - lw) / 2, btnY + 3, textClr, false);

        if (hovered) {
            String tooltip = switch (currentMode) {
                case BOTH -> "Both Mode: Used for Sell Orders (Input) AND receiving bought fluids (Output).";
                case INPUT -> "Input Only Mode: Used ONLY for creating Sell Orders. Bought fluids avoid this Tank.";
                case OUTPUT -> "Output Only Mode: Used ONLY for receiving bought fluids. Sell Orders ignore fluids here.";
            };
            var lines = font.split(Component.literal(tooltip), 200);
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private static void renderTiledFluid(GuiGraphics g, TextureAtlasSprite sprite,
                                         int x, int y, int width, int height, int bottom) {
        int right = x + width;
        g.enableScissor(x, y, right, bottom);
        for (int tileX = x; tileX < right; tileX += 16) {
            for (int tileBottom = bottom; tileBottom > y; tileBottom -= 16) {
                g.blit(tileX, tileBottom - 16, 0, 16, 16, sprite);
            }
        }
        g.disableScissor();
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "FLUID TANK", 12, 13, ACCENT, false);
        int invX = (imageWidth - 162) / 2;
        g.drawString(font, "INVENTORY", invX, 90, TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            TankBlockEntity.TankMode currentMode = menu.getMode();
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
                if (menu.getTankBlockEntity() != null) {
                    targetPos = menu.getTankBlockEntity().getBlockPos();
                } else if (minecraft != null && minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                    targetPos = bhr.getBlockPos();
                }
                if (targetPos != null) {
                    MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleTankModePacket(targetPos));
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
