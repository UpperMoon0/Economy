package com.nstut.economy.client;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.util.EconomyFormatUtil;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.TextWidget;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.api.VStack;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.layout.Insets;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import com.nstut.economy.trading.EconomyFluidStack;

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
    static final int TRANSFER_HINT_X = TankMenu.INPUT_SLOT_X - 8;
    static final int TRANSFER_HINT_Y = TankMenu.TRANSFER_SLOT_Y + 26;
    private static final int TRANSFER_SECTION_WIDTH = 90;

    private final TextWidget transferHint = Ui.text(Component.translatable("ui.economy.tank.transfer_hint"))
            .nowrap().marquee();

    private ButtonWidget modeBtn;
    private ButtonWidget ownerBtn;
    private FluidTankComponent tankComponent;
    private TankBlockEntity.TankMode currentMode;

    public TankScreen(TankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, TankMenu.IMAGE_WIDTH, TankMenu.IMAGE_HEIGHT);
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
            modeBtn.tooltip(modeTooltipWithPermission(mode));
        }
        if (ownerBtn != null) {
            ownerBtn.setVisible(menu.hasTeam());
            ownerBtn.setLabel(storageOwnerLabel());
            ownerBtn.enabled(menu.hasTeam() && menu.getTankBlockEntity() != null);
        }
        if (modeBtn != null) modeBtn.tooltip(modeTooltipWithPermission(currentMode));
    }

    @Override
    protected void renderBackgroundLayer(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
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
        UiRender.text(g, font, Component.translatable("ui.economy.tank.input"),
                x + TankMenu.INPUT_SLOT_X - 4, y + TRANSFER_TITLE_Y, c.onSurface());
        UiRender.text(g, font, Component.translatable("ui.economy.tank.output"),
                x + TankMenu.OUTPUT_SLOT_X - 6, y + TRANSFER_TITLE_Y, c.onSurface());
        renderMarquee(transferHint, g, font, x + TRANSFER_HINT_X, y + TRANSFER_HINT_Y,
                imageWidth - 10 - TRANSFER_HINT_X, c.onSurfaceMuted(), mouseX, mouseY, partialTick);
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
        ownerBtn = EconomyUiComponents.configStateButton(storageOwnerLabel(), this::toggleStorageOwner);
        ownerBtn.tooltip(Component.translatable("ui.economy.container.owner_transfer_tooltip"));
        ownerBtn.setVisible(menu.hasTeam());
        header.addChild(ownerBtn);
        modeBtn = EconomyUiComponents.configStateButton(modeLabel(currentMode), this::cycleMode);
        modeBtn.tooltip(modeTooltipWithPermission(currentMode));
        header.addChild(modeBtn);
        header.addChild(buildCompactThemeToggle());

        HStack body = new HStack().gap(8).align(Alignment.CENTER);
        TankBlockEntity tank = menu.getTankBlockEntity();
        EconomyFluidStack initial = tank != null ? tank.getFluid() : EconomyFluidStack.EMPTY;
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
        root.addChild(Ui.text(Component.translatable("ui.economy.tank.subtitle")).style(TextStyle.CAPTION).nowrap().marquee());
        root.addChild(body);
        return Ui.padding(Insets.only(5, 10, 6, 10), root);
    }

    /** Uses OpenUI's tested, hard-clipped ping-pong animation; fitting text stays still. */
    private static void renderMarquee(TextWidget text, GuiGraphicsExtractor g, Font font,
                                      int x, int y, int width, int color, int mx, int my, float pt) {
        text.color(color);
        text.layout(x, y, Math.max(1, width), font.lineHeight);
        text.render(g, font, mx, my, pt);
    }

    private final class TankInfoText extends UIComponent {
        private final TextWidget nameLabel = Ui.text(Component.empty()).nowrap().marquee();
        private final TextWidget amountLabel = Ui.text(Component.empty()).nowrap().marquee();
        private final TextWidget percentLabel = Ui.text(Component.empty()).nowrap().marquee();

        private void updateLabel(TextWidget label, String value) {
            if (!label.getText().getString().equals(value)) label.setText(value);
        }
        @Override public int preferredWidth(Font f) { return 72; }
        @Override public int preferredHeight(Font f) { return 48; }
        @Override public void render(GuiGraphicsExtractor g, Font f, int mx, int my, float pt) {
            TankBlockEntity tank = menu.getTankBlockEntity();
            if (tank == null) return;
            ColorScheme c = colors();
            EconomyFluidStack fluid = tank.getFluid();
            int capacity = Math.max(1, tank.getCapacity());
            int amount = Math.max(0, fluid.getAmount());
            float fill = Math.min(1f, amount / (float) capacity);
            String name = fluid.isEmpty() ? Component.translatable("ui.economy.tank.empty").getString()
                    : com.nstut.economy.platform.Services.FLUID.displayName(fluid.getFluid()).getString();
            updateLabel(nameLabel, name);
            renderMarquee(nameLabel, g, f, x, y + 4, width, c.onSurface(), mx, my, pt);
            String amountText = EconomyFormatUtil.formatFluidAmount(amount) + " / " + EconomyFormatUtil.formatFluidAmount(capacity);
            updateLabel(amountLabel, amountText);
            renderMarquee(amountLabel, g, f, x, y + 18, width, c.onSurfaceMuted(), mx, my, pt);
            String percentText = Component.translatable("ui.economy.tank.percent_full",
                    String.format(java.util.Locale.ROOT, "%.1f%%", fill * 100.0F)).getString();
            updateLabel(percentLabel, percentText);
            renderMarquee(percentLabel, g, f, x, y + 32, width, c.onSurfaceMuted(), mx, my, pt);
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

    private Component modeTooltipWithPermission(TankBlockEntity.TankMode mode) {
        return modeTooltip(mode).copy()
                .append("\n")
                .append(Component.translatable("ui.economy.container.mode_admin_hint"));
    }

    private boolean isTeamStorage() {
        return menu.isTeamOwned();
    }

    private Component storageOwnerLabel() {
        Component principal = Component.translatable(isTeamStorage()
                ? "ui.economy.principal.team"
                : "ui.economy.principal.personal");
        return principal;
    }

    private void toggleStorageOwner() {
        TankBlockEntity storage = menu.getTankBlockEntity();
        if (storage == null || minecraft == null || minecraft.level == null) return;
        MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.SetStorageOwnerPacket(
                minecraft.level.dimension().identifier().toString(),
                storage.getBlockPos(), true, !menu.isTeamOwned()));
    }

    private void cycleMode() {
        BlockPos targetPos = menu.getTankBlockEntity() != null ? menu.getTankBlockEntity().getBlockPos() : null;
        if (targetPos == null && minecraft != null && minecraft.hitResult instanceof BlockHitResult hit) targetPos = hit.getBlockPos();
        if (targetPos != null) {
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.ToggleTankModePacket(targetPos));
        }
    }
}


