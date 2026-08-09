package com.nstut.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.ui.framework.UiRender;
import com.nstut.economy.ui.framework.UiTheme;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.forge.network.MarketNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/** Modern tank dashboard using the same surfaces and controls as the market. */
public class TankScreen extends AbstractContainerScreen<TankMenu> {

    public TankScreen(TankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 280;
        this.imageHeight = 186;
        this.inventoryLabelY = 90;
        this.titleLabelY = 8;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBackground(g);
        int x = leftPos;
        int y = topPos;

        UiRender.surface(g, x, y, imageWidth, imageHeight, UiTheme.RADIUS_LG,
                UiTheme.SHELL, UiTheme.BORDER, true);
        UiRender.roundedRect(g, x + 83, y + 3, 98, 2, 1,
                UiRender.alpha(UiTheme.AMBIENT_WARM, 95));
        UiRender.surface(g, x + 8, y + 31, imageWidth - 16, 54, UiTheme.RADIUS_MD,
                UiTheme.SURFACE, UiTheme.BORDER_SUBTLE, false);
        UiRender.surface(g, x + 51, y + 94, 178, 89, UiTheme.RADIUS_MD,
                UiTheme.SURFACE, UiTheme.BORDER_SUBTLE, false);

        renderTankDashboard(g, x, y);

        for (Slot slot : menu.slots) {
            UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
        renderModeButton(g, mouseX, mouseY);
    }

    private void renderTankDashboard(GuiGraphics g, int x, int y) {
        TankBlockEntity tank = menu.getTankBlockEntity();
        if (tank == null) return;

        FluidStack fluid = tank.getFluid();
        int capacity = Math.max(1, tank.getCapacity());
        int amount = Math.max(0, fluid.getAmount());
        float fillRatio = Math.min(1.0F, amount / (float) capacity);

        int tankX = x + 119;
        int tankY = y + 39;
        int tankWidth = 43;
        int tankHeight = 38;
        UiRender.roundedOutline(g, tankX, tankY, tankWidth, tankHeight, UiTheme.RADIUS_SM,
                UiTheme.INPUT, UiTheme.BORDER_STRONG);

        if (!fluid.isEmpty() && amount > 0) {
            int innerX = tankX + 3;
            int innerY = tankY + 3;
            int innerWidth = tankWidth - 6;
            int innerHeight = tankHeight - 6;
            int fluidHeight = Math.max(1, Math.round(innerHeight * fillRatio));
            int fluidY = innerY + innerHeight - fluidHeight;

            int tintColor = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor();
            float red = ((tintColor >> 16) & 0xFF) / 255F;
            float green = ((tintColor >> 8) & 0xFF) / 255F;
            float blue = (tintColor & 0xFF) / 255F;
            float alpha = ((tintColor >> 24) & 0xFF) / 255F;
            if (alpha == 0F) alpha = 1F;

            ResourceLocation still = IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture();
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
            RenderSystem.setShaderColor(red, green, blue, alpha);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            renderTiledFluid(g, sprite, innerX, fluidY, innerWidth, fluidHeight, innerY + innerHeight);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            g.fill(innerX + 2, fluidY + 1, innerX + innerWidth - 2, fluidY + 2, 0x55FFFFFF);
        }

        String fluidName = fluid.isEmpty() ? "Empty tank" : fluid.getDisplayName().getString();
        fluidName = font.plainSubstrByWidth(fluidName, 91);
        g.drawString(font, fluidName, x + 18, y + 43, UiTheme.TEXT_PRIMARY, false);
        String amountText = EconomyFormatUtil.formatFluidAmount(amount)
                + " / " + EconomyFormatUtil.formatFluidAmount(capacity);
        amountText = font.plainSubstrByWidth(amountText, 91);
        g.drawString(font, amountText, x + 18, y + 56, UiTheme.TEXT_SECONDARY, false);
        UiRender.progressTrack(g, x + 18, y + 72, 91, 4, fillRatio, UiTheme.ACCENT);

        g.drawString(font, "TRANSFER", x + 173, y + 38, UiTheme.TEXT_MUTED, false);
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
        TankBlockEntity.TankMode mode = menu.getMode();
        String label = "MODE  " + modeLabel();
        int buttonWidth = modeButtonWidth();
        int buttonX = leftPos + imageWidth - buttonWidth - 10;
        int buttonY = topPos + 7;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= buttonY && mouseY < buttonY + 17;

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

        UiRender.pill(g, buttonX, buttonY, buttonWidth, 17, background, accent);
        UiRender.roundedRect(g, buttonX + 6, buttonY + 7, 3, 3, 2, accent);
        g.drawString(font, label, buttonX + 13, buttonY + 4, accent, false);

        if (hovered) {
            String tooltip = switch (mode) {
                case BOTH -> "Both way: supplies sell orders and receives purchased fluids.";
                case INPUT -> "Input only: supplies stored fluid to sell orders.";
                case OUTPUT -> "Output only: receives purchased fluid.";
            };
            g.renderTooltip(font, font.split(Component.literal(tooltip), 200), mouseX, mouseY);
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
        g.drawString(font, "FLUID TANK", 12, 7, UiTheme.TEXT_PRIMARY, false);
        g.drawString(font, "LIQUID RESERVE", 12, 18, UiTheme.TEXT_MUTED, false);
        int inventoryX = (imageWidth - 162) / 2;
        g.drawString(font, "PLAYER INVENTORY", inventoryX, 90, UiTheme.TEXT_MUTED, false);
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
                if (menu.getTankBlockEntity() != null) {
                    targetPos = menu.getTankBlockEntity().getBlockPos();
                } else if (minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) {
                    targetPos = hit.getBlockPos();
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
