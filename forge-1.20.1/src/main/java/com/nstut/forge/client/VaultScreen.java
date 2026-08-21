package com.nstut.forge.client;

import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.blocks.VaultMenu;
import com.nstut.forge.network.MarketNetwork;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.api.VStack;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.layout.Insets;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Vault surface migrated to the shared Economy OpenUI container screen.
 * Follows a clean 9x6 double chest layout with theme-native colors and surfaces.
 */
public class VaultScreen extends EconomyUiContainerScreen<VaultMenu> {

    private ButtonWidget modeBtn;
    private VaultBlockEntity.VaultMode currentMode;

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = VaultMenu.IMAGE_WIDTH;
        this.imageHeight = VaultMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = VaultMenu.PLAYER_INV_Y - 10;
        this.titleLabelY = 6;
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
        renderBaseShell(g);
        int x = leftPos;
        int y = topPos;
        ColorScheme c = colors();

        // Background panel for vault slots (6 rows of 9)
        UiRender.surface(g, x + 11, y + 30, 174, 114, radii().control(),
                c.input(), c.borderSubtle(), c);

        // Background panel for player inventory & hotbar (4 rows of 9)
        UiRender.surface(g, x + 11, y + 146, 174, 80, radii().control(),
                c.input(), c.borderSubtle(), c);

        // Render all slot frames
        for (Slot slot : menu.slots) {
            UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18, c);
        }
    }

    @Override
    protected UIComponent buildUI() {
        VStack titles = new VStack().gap(1);
        titles.addChild(Ui.text(Component.translatable("ui.economy.vault.title")).style(TextStyle.TITLE));
        titles.addChild(Ui.text(Component.translatable("ui.economy.vault.subtitle")).style(TextStyle.CAPTION));

        HStack header = new HStack().gap(4).align(Alignment.CENTER);
        header.addChild(titles);
        header.addChild(Ui.spacer().flex());
        modeBtn = Ui.button(modeLabel(currentMode), this::cycleMode).ghost();
        header.addChild(modeBtn);
        header.addChild(buildThemeToggle());

        return Ui.padding(Insets.of(6, 10, 6, 10), header);
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
