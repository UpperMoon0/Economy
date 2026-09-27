package com.nstut.economy.blocks;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import org.jetbrains.annotations.NotNull;

public class TankMenu extends AbstractContainerMenu {

    public static final int CONTAINER_SIZE = 2;
    public static final int DATA_MODE = 0;
    public static final int DATA_TEAM_OWNED = 1;
    public static final int DATA_TEAM_AVAILABLE = 2;
    public static final int DATA_COUNT = 3;
    public static final int IMAGE_WIDTH = 280;
    public static final int IMAGE_HEIGHT = 186;
    public static final int INPUT_SLOT_X = 170;
    public static final int OUTPUT_SLOT_X = 206;
    public static final int TRANSFER_SLOT_X = INPUT_SLOT_X;
    public static final int TRANSFER_SLOT_Y = 50;
    public static final int PLAYER_INV_X = (IMAGE_WIDTH - 162) / 2; // 59
    public static final int PLAYER_INV_Y = 102;
    public static final int HOTBAR_Y = 160;

    private final net.minecraft.world.Container container;
    private final ContainerData data;
    private final TankBlockEntity tankBlockEntity;

    public TankMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE), new SimpleContainerData(DATA_COUNT), null);
    }

    public TankMenu(int containerId, Inventory playerInventory, TankBlockEntity tank, ContainerData data) {
        this(containerId, playerInventory, tank, data, tank);
    }

    public TankMenu(int containerId, Inventory playerInventory, net.minecraft.world.Container container, ContainerData data, TankBlockEntity tank) {
        super(BlockRegistries.TANK_MENU.get(), containerId);
        checkContainerSize(container, CONTAINER_SIZE);
        checkContainerDataCount(data, DATA_COUNT);

        this.container = container;
        this.data = data;
        this.tankBlockEntity = tank;
        container.startOpen(playerInventory.player);

        this.addSlot(new Slot(container, TankBlockEntity.INPUT_SLOT, INPUT_SLOT_X, TRANSFER_SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return com.nstut.economy.platform.Services.FLUID.isFluidContainer(stack);
            }
        });
        this.addSlot(new Slot(container, TankBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, TRANSFER_SLOT_Y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }

        this.addDataSlots(data);
    }

    public TankBlockEntity.TankMode getMode() {
        return TankBlockEntity.TankMode.byId(data.get(DATA_MODE));
    }

    public TankBlockEntity getTankBlockEntity() {
        return tankBlockEntity;
    }

    /** Server-synchronized ownership state for reactive screen labels. */
    public boolean isTeamOwned() {
        return data.get(DATA_TEAM_OWNED) != 0;
    }

    /** Whether the viewing player currently belongs to a team/party. */
    public boolean hasTeam() {
        return data.get(DATA_TEAM_AVAILABLE) != 0;
    }

    public void setMode(int modeId) {
        data.set(DATA_MODE, modeId);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();
            if (index < CONTAINER_SIZE) {
                if (!this.moveItemStackTo(stackInSlot, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (com.nstut.economy.platform.Services.FLUID.isFluidContainer(stackInSlot)) {
                if (!this.moveItemStackTo(stackInSlot, TankBlockEntity.INPUT_SLOT, TankBlockEntity.INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.container.stillValid(player);
    }
}

