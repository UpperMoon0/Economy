package com.nstut.forge.client;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.util.EconomyFormatUtil;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fluid tank dashboard migrated to the shared Economy OpenUI container screen.
 * Follows theme-native colors, explicit transfer slot positioning, and clean layout.
 */
public class TankScreen extends EconomyUiContainerScreen<TankMenu> {

    private ButtonWidget modeBtn;
    private FluidTankComponent tankComponent;
    private TankBlockEntity.TankMode currentMode;

    public TankScreen(TankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TankMenu.IMAGE_WIDTH;
        this.imageHeight = TankMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = TankMenu.PLAYER_INV_Y - 10;
        this.titleLabelY = 6;
        this.currentMode = menu.getMode();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        TankBlockEntity tank = menu.getTankBlockEntity();
        if (tankComponent != null && tank != null) {
            tankComponent.setFluid(tank.getFluid());
            tankComponent.setCapacity(tank.getCapacity());
        }
        TankBlockEntity.TankMode m = menu.getMode();
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

        // Upper panel for tank & transfer
        UiRender.surface(g, x + 8, y + 30, imageWidth - 16, 62, radii().control(),
                c.input(), c.borderSubtle(), c);

        // Lower panel for player inventory
        UiRender.surface(g, x + 51, y + 96, 178, 82, radii().control(),
                c.input(), c.borderSubtle(), c);

        // Slots
        for (Slot slot : menu.slots) {
            UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18, c);
        }
    }

    @Override
    protected UIComponent buildUI() {
        VStack titles = new VStack().gap(1);
        titles.addChild(Ui.text(Component.translatable("ui.economy.tank.title")).style(TextStyle.TITLE));
        titles.addChild(Ui.text(Component.translatable("ui.economy.tank.subtitle")).style(TextStyle.CAPTION));

        HStack header = new HStack().gap(4).align(Alignment.CENTER);
        header.addChild(titles);
        header.addChild(Ui.spacer().flex());
        modeBtn = Ui.button(modeLabel(currentMode), this::cycleMode).ghost();
        header.addChild(modeBtn);
        header.addChild(buildThemeToggle());

        HStack body = new HStack().gap(8).align(Alignment.CENTER);

        HStack tankSection = new HStack().gap(8).align(Alignment.CENTER);
        TankBlockEntity tank = menu.getTankBlockEntity();
        FluidStack initial = tank != null ? tank.getFluid() : FluidStack.EMPTY;
        int cap = tank != null ? tank.getCapacity() : TankBlockEntity.DEFAULT_CAPACITY;
        tankComponent = new FluidTankComponent(initial, cap);
        tankSection.addChild(tankComponent);
        tankSection.addChild(new TankInfoText().flex());

        body.addChild(tankSection.width(160));
        body.addChild(Ui.spacer().flex());

        VStack transfer = new VStack().gap(2);
        transfer.addChild(Ui.text(Component.translatable("ui.economy.tank.transfer")).style(TextStyle.CAPTION));
        transfer.addChild(Ui.text(Component.translatable("ui.economy.tank.transfer_hint")).style(TextStyle.CAPTION));
        body.addChild(transfer.width(80));

        VStack root = new VStack().gap(6);
        root.addChild(header);
        root.addChild(body);

        return Ui.padding(Insets.of(6, 10, 6, 10), root);
    }

    private class TankInfoText extends UIComponent {
        @Override public int preferredWidth(Font f) { return 0; }
        @Override public int preferredHeight(Font f) { return 48; }
        @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
            ColorScheme c = colors();
            TankBlockEntity t = menu.getTankBlockEntity();
            if (t == null) return;
            FluidStack fluid = t.getFluid();
            int capacity = Math.max(1, t.getCapacity());
            int amount = Math.max(0, fluid.getAmount());
            float fill = Math.min(1f, amount / (float) capacity);

            String name = fluid.isEmpty() ? Component.translatable("ui.economy.tank.empty").getString() : fluid.getDisplayName().getString();
            name = f.plainSubstrByWidth(name, width);
            g.drawString(f, name, x, y + 4, c.onSurface());

            String amt = EconomyFormatUtil.formatFluidAmount(amount)
                    + " / " + EconomyFormatUtil.formatFluidAmount(capacity);
            amt = f.plainSubstrByWidth(amt, width);
            g.drawString(f, amt, x, y + 18, c.onSurfaceMuted());

            UiRender.progressTrack(g, x, y + 36, width, 4, fill, c);
        }
    }

    private Component modeLabel(TankBlockEntity.TankMode m) {
        return Component.translatable(switch (m) {
            case BOTH -> "ui.economy.tank.mode_both";
            case INPUT -> "ui.economy.tank.mode_input";
            case OUTPUT -> "ui.economy.tank.mode_output";
        });
    }

    private void cycleMode() {
        TankBlockEntity.TankMode next =
                TankBlockEntity.TankMode.byId((menu.getMode().id + 1) % 3);
        BlockPos targetPos = null;
        if (menu.getTankBlockEntity() != null) {
            targetPos = menu.getTankBlockEntity().getBlockPos();
        } else if (minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) {
            targetPos = hit.getBlockPos();
        }
        if (targetPos != null) {
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleTankModePacket(targetPos));
            currentMode = next;
            if (modeBtn != null) modeBtn.setLabel(modeLabel(next));
        }
    }
}
