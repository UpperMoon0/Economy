package com.nstut.forge.client;

import com.nstut.economy.blocks.TankMenu;
import com.nstut.economy.blocks.VaultMenu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ContainerScreenGeometryTest {
    private static final int LABEL_HEIGHT = 10;
    private static final int SLOT_SIZE = 18;

    @Test
    void tankLabelsAndPanelsDoNotOverlapNativeSlots() {
        Rect fluidPanel = new Rect(8, TankScreen.FLUID_PANEL_Y,
                TankMenu.IMAGE_WIDTH - 16, TankScreen.FLUID_PANEL_HEIGHT);
        Rect inventoryLabel = new Rect(TankMenu.PLAYER_INV_X,
                TankScreen.INVENTORY_LABEL_Y, 60, LABEL_HEIGHT);
        Rect playerPanel = new Rect(TankScreen.PLAYER_PANEL_X, TankScreen.PLAYER_PANEL_Y,
                TankScreen.PLAYER_PANEL_WIDTH, TankScreen.PLAYER_PANEL_HEIGHT);
        Rect firstPlayerSlot = new Rect(TankMenu.PLAYER_INV_X - 1,
                TankMenu.PLAYER_INV_Y - 1, SLOT_SIZE, SLOT_SIZE);
        Rect transferSlot = new Rect(TankMenu.TRANSFER_SLOT_X,
                TankMenu.TRANSFER_SLOT_Y, SLOT_SIZE, SLOT_SIZE);
        Rect transferTitle = new Rect(TankScreen.TRANSFER_TITLE_X,
                TankScreen.TRANSFER_TITLE_Y, 60, LABEL_HEIGHT);
        Rect transferHint = new Rect(TankScreen.TRANSFER_HINT_X,
                TankScreen.TRANSFER_HINT_Y,
                TankMenu.IMAGE_WIDTH - 10 - TankScreen.TRANSFER_HINT_X,
                LABEL_HEIGHT);

        assertFalse(fluidPanel.intersects(inventoryLabel));
        assertFalse(inventoryLabel.intersects(playerPanel));
        assertFalse(inventoryLabel.intersects(firstPlayerSlot));
        assertFalse(transferTitle.intersects(transferSlot));
        assertFalse(transferHint.intersects(transferSlot));
    }

    @Test
    void vaultInventoryLabelSeparatesStorageAndPlayerRegions() {
        Rect storagePanel = new Rect(10, VaultScreen.STORAGE_PANEL_Y,
                VaultMenu.IMAGE_WIDTH - 20, VaultScreen.STORAGE_PANEL_HEIGHT);
        Rect inventoryLabel = new Rect(VaultMenu.PLAYER_INV_X,
                VaultScreen.INVENTORY_LABEL_Y, 60, LABEL_HEIGHT);
        Rect playerPanel = new Rect(VaultScreen.PLAYER_PANEL_X, VaultScreen.PLAYER_PANEL_Y,
                VaultScreen.PLAYER_PANEL_WIDTH, VaultScreen.PLAYER_PANEL_HEIGHT);
        Rect firstPlayerSlot = new Rect(VaultMenu.PLAYER_INV_X - 1,
                VaultMenu.PLAYER_INV_Y - 1, SLOT_SIZE, SLOT_SIZE);

        assertFalse(storagePanel.intersects(inventoryLabel));
        assertFalse(inventoryLabel.intersects(playerPanel));
        assertFalse(inventoryLabel.intersects(firstPlayerSlot));
    }

    private record Rect(int x, int y, int width, int height) {
        boolean intersects(Rect other) {
            return x < other.x + other.width
                    && x + width > other.x
                    && y < other.y + other.height
                    && y + height > other.y;
        }
    }
}
