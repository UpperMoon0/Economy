package com.nstut.economy.blocks;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class VaultMenu extends AbstractContainerMenu {

    public static final int CONTAINER_SIZE = 54;
    public static final int IMAGE_WIDTH = 356;
    public static final int IMAGE_HEIGHT = 208;
    public static final int VAULT_SLOT_START_X = 16;
    public static final int VAULT_SLOT_START_Y = 42;
    public static final int PLAYER_INV_X = 97;
    public static final int PLAYER_INV_Y = 126;
    public static final int HOTBAR_Y = 184;

    private final Container container;
    private final ContainerData data;
    private final VaultBlockEntity vaultBlockEntity;

    public VaultMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE), new SimpleContainerData(1), null);
    }

    public VaultMenu(int containerId, Inventory playerInventory, Container container, ContainerData data, VaultBlockEntity vault) {
        super(BlockRegistries.VAULT_MENU.get(), containerId);
        checkContainerSize(container, CONTAINER_SIZE);
        checkContainerDataCount(data, 1);

        this.container = container;
        this.data = data;
        this.vaultBlockEntity = vault;
        container.startOpen(playerInventory.player);

        // Vault Container Slots (3 rows of 18 = 54 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 18; col++) {
                this.addSlot(new Slot(container, col + row * 18, VAULT_SLOT_START_X + col * 18, VAULT_SLOT_START_Y + row * 18));
            }
        }

        // Player Inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Player Hotbar (1 row of 9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }

        this.addDataSlots(data);
    }

    public VaultBlockEntity.VaultMode getMode() {
        return VaultBlockEntity.VaultMode.byId(data.get(0));
    }

    public VaultBlockEntity getVaultBlockEntity() {
        return vaultBlockEntity;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();
            if (index < CONTAINER_SIZE) {
                if (!this.moveItemStackTo(stackInSlot, CONTAINER_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stackInSlot, 0, CONTAINER_SIZE, false)) {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}
