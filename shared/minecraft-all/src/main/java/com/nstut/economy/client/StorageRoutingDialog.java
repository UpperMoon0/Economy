package com.nstut.economy.client;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.config.StorageRoutingSettings;
import com.nstut.openui.api.VStack;
import com.nstut.openui.api.HStack;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.Card;
import com.nstut.economy.compat.Compat;
import net.minecraft.core.registries.BuiltInRegistries;
import com.nstut.openui.api.Ui;
import com.nstut.openui.overlay.OverlayManager;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.Supplier;

/** Shared modal keeps purchase routing controls identical across supported game versions. */
final class StorageRoutingDialog {
    private StorageRoutingDialog() { }

    static void show(OverlayManager overlays, BlockEntity storage, String dimension) {
        boolean tank = storage instanceof TankBlockEntity;
        var settings = tank ? ((TankBlockEntity) storage).getRoutingSettings()
                : ((VaultBlockEntity) storage).getRoutingSettings();
        Signal<String> filter = Signals.of(settings.filterText());
        Signal<String> priority = Signals.of(Integer.toString(settings.priority()));
        Signal<String> error = Signals.of("");
        var body = new VStack().gap(4);
        body.width(280);
        body.addChild(Ui.text(Component.translatable("ui.economy.routing.title")));
        body.addChild(Ui.text(Component.translatable("ui.economy.routing.help")));
        body.addChild(Ui.text(Component.translatable(tank ? "ui.economy.routing.fluids" : "ui.economy.routing.items")));
        var filterField = Ui.textField(filter);
        filterField.placeholder(tank ? "minecraft:water, minecraft:lava" : "minecraft:iron_ingot, minecraft:gold_ingot");
        filterField.maxLength(StorageRoutingSettings.MAX_TEXT_LENGTH);
        filterField.setValue(filter.get());
        filterField.fillWidth();
        body.addChild(filterField);
        body.addChild(Ui.text(Component.translatable("ui.economy.routing.priority")));
        var priorityField = Ui.textField(priority);
        priorityField.tooltip(Component.translatable("ui.economy.routing.priority_help"));
        priorityField.fillWidth(); body.addChild(priorityField);
        body.addChild(Ui.text((Supplier<Component>) () -> Component.literal(error.get())));
        var actions = new HStack().gap(6);
        OverlayHandle[] handle = new OverlayHandle[1];
        actions.addChild(Ui.button(Component.translatable("ui.economy.action.save"), () -> {
            StorageRoutingSettings parsed;
            try {
                parsed = StorageRoutingSettings.parse(filter.get(), Integer.parseInt(priority.get().trim()));
                for (String id : parsed.allowedIds()) {
                    var key = Compat.rl(id);
                    if (!(tank ? BuiltInRegistries.FLUID.containsKey(key)
                            : BuiltInRegistries.ITEM.containsKey(key))
                            || id.equals(tank ? "minecraft:empty" : "minecraft:air"))
                        throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                error.set(Component.translatable("ui.economy.routing.invalid").getString()); return;
            }
            MarketNetwork.CHANNEL.sendToServer(
                    new MarketNetwork.SetStorageRoutingPacket(
                            dimension, storage.getBlockPos(), tank,
                            parsed.filterText(), parsed.priority()));
            handle[0].close();
        }).primary());
        actions.addChild(Ui.button(Component.translatable("ui.economy.action.cancel"), () -> handle[0].close()));
        body.addChild(actions);
        handle[0] = Dialog.show(overlays, new Card(body).padding(10).outlined(true).elevated(true));
    }
}
