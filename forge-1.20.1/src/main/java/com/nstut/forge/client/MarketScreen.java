package com.nstut.forge.client;

import com.nstut.Economy;
import com.nstut.economy.api.IAccountManager;
import com.nstut.economy.api.IBankAccount;
import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.config.EconomyConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(Economy.MOD_ID + ":textures/gui/market.png");

    public MarketScreen(MarketMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            IAccountManager accounts = IAccountManager.getInstance();
            IBankAccount account = accounts.getOrCreatePlayerAccount(player.getUUID());
            EconomyConfig config = EconomyConfig.getInstance();

            String balanceStr = config.getCurrencySymbol() + account.getBalance().toPlainString();

            graphics.drawString(this.font, Component.translatable("block.economy.market"), x + 8, y + 6, 0x404040, false);
            graphics.drawString(this.font, Component.literal("Balance: " + balanceStr), x + 8, y + 20, 0x404040, false);
            graphics.drawString(this.font, Component.literal("Use /offers to browse the market"), x + 8, y + 36, 0x808080, false);
            graphics.drawString(this.font, Component.literal("Use /sell <item> <qty> <price> to sell"), x + 8, y + 50, 0x808080, false);
            graphics.drawString(this.font, Component.literal("Use /buy <item> <qty> <price> to buy"), x + 8, y + 64, 0x808080, false);
            graphics.drawString(this.font, Component.literal("Use /vault to locate your vault"), x + 8, y + 78, 0x808080, false);
        }
    }
}
