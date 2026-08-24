package com.nstut.forge.client;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.forge.network.MarketNetwork;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;

/** Fixed-geometry vanilla container with OpenUI chrome layered around real slots. */
public class TankScreen extends EconomyUiContainerScreen<TankMenu> {
    static final int FLUID_PANEL_Y = 31;
    static final int FLUID_PANEL_HEIGHT = 54;
    static final int INVENTORY_LABEL_Y = 87;
    static final int PLAYER_PANEL_X = TankMenu.PLAYER_INV_X - 8;
    static final int PLAYER_PANEL_WIDTH = 9 * 18 + 16;
    static final int PLAYER_PANEL_Y = 99;
    static final int PLAYER_PANEL_HEIGHT = 79;
    static final int TRANSFER_TITLE_X = TankMenu.TRANSFER_SLOT_X;
    static final int TRANSFER_TITLE_Y = TankMenu.TRANSFER_SLOT_Y - 12;
    static final int TRANSFER_HINT_X = TankMenu.TRANSFER_SLOT_X + 23;
    static final int TRANSFER_HINT_Y = TankMenu.TRANSFER_SLOT_Y + 5;
    private static final int TRANSFER_SECTION_WIDTH = 90;

    private ButtonWidget modeBtn;
    private FluidTankComponent tankComponent;
    private TankBlockEntity.TankMode currentMode;

    public TankScreen(TankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = TankMenu.IMAGE_WIDTH;
        imageHeight = TankMenu.IMAGE_HEIGHT;
        inventoryLabelY = INVENTORY_LABEL_Y;
        titleLabelY = 6;
        currentMode = menu.getMode();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        TankBlockEntity tank = menu.getTankBlockEntity();
        if (tankComponent != null && tank != null) {
            tankComponent.setFluid(tank.getFluid());
            tankComponent.setCapacity(tank.getCapacity());
        }
        TankBlockEntity.TankMode mode = menu.getMode();
        if (modeBtn != null && mode != currentMode) {
            currentMode = mode;
            modeBtn.setLabel(modeLabel(mode));
            modeBtn.tooltip(modeTooltip(mode));
        }
    }

    @Override
    protected void renderBackgroundLayer(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderBaseShell(g);
        ColorScheme c = colors();
        int x = leftPos, y = topPos;
        int panelRadius = radii().medium();

        UiRender.surface(g, x + 8, y + FLUID_PANEL_Y, imageWidth - 16, FLUID_PANEL_HEIGHT,
                panelRadius, c.surface(), c.borderSubtle(), false, c);
        UiRender.surface(g, x + PLAYER_PANEL_X, y + PLAYER_PANEL_Y, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT,
                panelRadius, c.surface(), c.borderSubtle(), false, c);
        for (Slot slot : menu.slots) {
            UiRender.slot(g, x + slot.x - 1, y + slot.y - 1, 18, 18, c);
        }
        int slotX = x + TankMenu.TRANSFER_SLOT_X;
        UiRender.text(g, font, Component.translatable("ui.economy.tank.transfer"),
                slotX, y + TRANSFER_TITLE_Y, c.onSurface());
        String hint = Component.translatable("ui.economy.tank.transfer_hint").getString();
        int hintX = x + TRANSFER_HINT_X;
        int hintWidth = Math.max(1, x + imageWidth - 10 - hintX);
        hint = MarketScreen.fitText(font, hint, hintWidth);
        UiRender.text(g, font, hint, hintX, y + TRANSFER_HINT_Y, c.onSurface());
    }

    @Override
    protected boolean showInventoryLabel() { return true; }

    @Override
    protected int economyInventoryLabelX() { return TankMenu.PLAYER_INV_X; }

    @Override
    protected UIComponent buildUI() {
        HStack header = new HStack().gap(4).align(Alignment.CENTER);
        header.addChild(Ui.text(Component.translatable("ui.economy.tank.title")).style(TextStyle.TITLE));
        header.addChild(Ui.spacer().flex());
        modeBtn = Ui.button(modeLabel(currentMode), this::cycleMode).ghost().small();
        modeBtn.tooltip(modeTooltip(currentMode));
        header.addChild(modeBtn);
        header.addChild(buildCompactThemeToggle());

        HStack body = new HStack().gap(8).align(Alignment.CENTER);
        TankBlockEntity tank = menu.getTankBlockEntity();
        FluidStack initial = tank != null ? tank.getFluid() : FluidStack.EMPTY;
        int capacity = tank != null ? tank.getCapacity() : TankBlockEntity.DEFAULT_CAPACITY;
        tankComponent = new FluidTankComponent(initial, capacity);
        tankComponent.width(32).height(48);
        body.addChild(tankComponent);

        UIComponent info = new TankInfoText();
        info.flex();
        body.addChild(info);

        // Native slot annotations are rendered from TankMenu coordinates.
        body.addChild(Ui.spacer().width(TRANSFER_SECTION_WIDTH));

        VStack root = new VStack().gap(2);
        root.addChild(header);
        root.addChild(Ui.text(Component.translatable("ui.economy.tank.subtitle")).style(TextStyle.CAPTION));
        root.addChild(body);
        return Ui.padding(Insets.only(5, 10, 6, 10), root);
    }

    private final class TankInfoText extends UIComponent {
        @Override public int preferredWidth(Font f) { return 72; }
        @Override public int preferredHeight(Font f) { return 48; }
        @Override public void render(GuiGraphics g, Font f, int mx, int my, float pt) {
            TankBlockEntity tank = menu.getTankBlockEntity();
            if (tank == null) return;
            ColorScheme c = colors();
            FluidStack fluid = tank.getFluid();
            int capacity = Math.max(1, tank.getCapacity());
            int amount = Math.max(0, fluid.getAmount());
            float fill = Math.min(1f, amount / (float) capacity);
            String name = fluid.isEmpty() ? Component.translatable("ui.economy.tank.empty").getString()
                    : fluid.getDisplayName().getString();
            UiRender.text(g, f, f.plainSubstrByWidth(name, Math.max(1, width)), x, y + 4, c.onSurface());
            String amountText = EconomyFormatUtil.formatFluidAmount(amount) + " / " + EconomyFormatUtil.formatFluidAmount(capacity);
            UiRender.text(g, f, f.plainSubstrByWidth(amountText, Math.max(1, width)), x, y + 18, c.onSurfaceMuted());
            String percentText = Component.translatable("ui.economy.tank.percent_full",
                    String.format(java.util.Locale.ROOT, "%.1f%%", fill * 100.0F)).getString();
            UiRender.text(g, f, percentText, x, y + 32, c.onSurfaceMuted());
        }
    }

    private Component modeLabel(TankBlockEntity.TankMode mode) {
        return Component.translatable(switch (mode) {
            case BOTH -> "ui.economy.mode.both";
            case INPUT -> "ui.economy.mode.input";
            case OUTPUT -> "ui.economy.mode.output";
        });
    }

    private Component modeTooltip(TankBlockEntity.TankMode mode) {
        return Component.translatable(switch (mode) {
            case BOTH -> "ui.economy.container.tooltip.mode_both";
            case INPUT -> "ui.economy.container.tooltip.mode_input";
            case OUTPUT -> "ui.economy.container.tooltip.mode_output";
        });
    }

    private void cycleMode() {
        TankBlockEntity.TankMode next = TankBlockEntity.TankMode.byId((menu.getMode().id + 1) % 3);
        BlockPos targetPos = menu.getTankBlockEntity() != null ? menu.getTankBlockEntity().getBlockPos() : null;
        if (targetPos == null && minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) targetPos = hit.getBlockPos();
        if (targetPos != null) {
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleTankModePacket(targetPos));
            currentMode = next;
            if (modeBtn != null) {
                modeBtn.setLabel(modeLabel(next));
                modeBtn.tooltip(modeTooltip(next));
            }
        }
    }
}
