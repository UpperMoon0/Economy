package com.nstut.economy.client;

import com.nstut.economy.blocks.TankBlockEntity;
import com.nstut.economy.blocks.VaultBlockEntity;
import com.nstut.economy.compat.Compat;
import com.nstut.economy.config.StorageRoutingSettings;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.economy.platform.Services;
import com.nstut.economy.util.CommodityUtil;
import com.nstut.openui.api.*;
import com.nstut.openui.controls.Popover;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.overlay.OverlayManager;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.state.Subscription;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Searchable purchase filter uses the market's icon component and OpenUI virtual-list controls. */
final class StorageRoutingDialog {
    private StorageRoutingDialog() { }

    static List<StorageFilterPickerModel.Entry> catalog(boolean tank) {
        List<StorageFilterPickerModel.Entry> entries = new ArrayList<>();
        if (tank) {
            for (var fluid : BuiltInRegistries.FLUID) {
                if (!CommodityUtil.isCanonicalFluid(fluid)) continue;
                entries.add(new StorageFilterPickerModel.Entry(BuiltInRegistries.FLUID.getKey(fluid).toString(),
                        Services.FLUID.displayName(fluid).getString()));
            }
        } else {
            for (var item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                entries.add(new StorageFilterPickerModel.Entry(BuiltInRegistries.ITEM.getKey(item).toString(),
                        new ItemStack(item).getHoverName().getString()));
            }
        }
        return entries;
    }

    static void show(OverlayManager overlays, BlockEntity storage, String dimension) {
        boolean tank = storage instanceof TankBlockEntity;
        var settings = tank ? ((TankBlockEntity) storage).getRoutingSettings()
                : ((VaultBlockEntity) storage).getRoutingSettings();
        var model = new StorageFilterPickerModel(catalog(tank), settings);
        Signal<String> query = Signals.of("");
        Signal<String> priority = Signals.of(Integer.toString(settings.priority()));
        Signal<String> error = Signals.of("");
        Signal<Boolean> blacklist = Signals.of(settings.blacklist());
        Signal<List<StorageFilterPickerModel.Entry>> selected = Signals.of(model.search(""));
        Signal<List<StorageFilterPickerModel.Entry>> results = Signals.of(List.of());
        Signal<Boolean> empty = Signals.of(selected.get().isEmpty());
        Runnable refresh = () -> {
            selected.set(model.search(""));
            empty.set(selected.get().isEmpty());
        };
        var body = new VStack().gap(2);
        body.width(280); body.height(tank ? 166 : 188);
        body.addChild(Ui.text(Component.translatable("ui.economy.routing.title")).style(TextStyle.LABEL));
        body.addChild(Ui.text((Supplier<Component>) () -> Component.translatable(blacklist.get()
                ? "ui.economy.routing.blacklist_help" : "ui.economy.routing.whitelist_help")).style(TextStyle.CAPTION));
        var search = Ui.textField(query);
        search.placeholder(Component.translatable(tank ? "ui.economy.routing.search_fluids" : "ui.economy.routing.search_items").getString());
        search.maxLength(128); search.fillWidth(); search.height(18);
        body.addChild(search);
        Popover[] popover = new Popover[1];
        OverlayHandle[] dropdown = new OverlayHandle[1];
        Runnable addSelected = () -> { refresh.run(); query.set(""); search.requestFocus(); };
        var suggestions = Ui.list(results, entry -> searchRow(entry, model, addSelected, error))
                .key(StorageFilterPickerModel.Entry::id).itemHeight(24).gap(2);
        suggestions.height(76);
        popover[0] = Ui.popover(search, suggestions).matchAnchorWidth().placement(Popover.Placement.BOTTOM).offset(1);
        Subscription searchSubscription = query.subscribe(value -> {
            results.set(value.isBlank() ? List.of() : model.search(value).stream()
                    .filter(entry -> !model.isSelected(entry.id())).toList());
            if (results.get().isEmpty()) popover[0].close();
            else if (dropdown[0] == null || !dropdown[0].isOpen()) dropdown[0] = popover[0].show(overlays);
        });
        HStack summary = new HStack().gap(4).align(Alignment.CENTER);
        summary.height(18);
        summary.addChild(Ui.text((Supplier<Component>) () -> model.size() == 0
                ? Component.translatable(tank ? "ui.economy.routing.all_fluids" : "ui.economy.routing.all_items")
                : Component.translatable("ui.economy.routing.count", model.size(), StorageRoutingSettings.MAX_ENTRIES))
                .style(TextStyle.CAPTION).nowrap().ellipsis().flex());
        summary.addChild(Ui.button((Supplier<Component>) () -> Component.translatable(blacklist.get()
                ? "ui.economy.routing.blacklist" : "ui.economy.routing.whitelist"),
                () -> blacklist.set(!blacklist.get())).outline().small()
                .tooltip(Component.translatable("ui.economy.routing.mode_help")));
        body.addChild(summary);
        UIComponent list = Ui.switcher(empty)
                .when(true, () -> Ui.text(Component.translatable("ui.economy.routing.search_hint")).style(TextStyle.CAPTION))
                .when(false, () -> Ui.list(selected, entry -> entryRow(entry, model, refresh, error))
                        .key(StorageFilterPickerModel.Entry::id).itemHeight(22).gap(2));
        list.flex(); body.addChild(list);
        HStack priorityRow = new HStack().gap(4).align(Alignment.CENTER);
        priorityRow.height(18);
        priorityRow.addChild(Ui.text(Component.translatable("ui.economy.routing.priority")).style(TextStyle.CAPTION).flex());
        var priorityField = Ui.textField(priority);
        priorityField.maxLength(6); priorityField.width(54); priorityField.height(18);
        priorityField.tooltip(Component.translatable("ui.economy.routing.priority_help"));
        priorityRow.addChild(priorityField); body.addChild(priorityRow);
        body.addChild(Ui.text((Supplier<Component>) () -> Component.literal(error.get())).style(TextStyle.CAPTION).nowrap().ellipsis());
        var actions = new HStack().gap(6);
        actions.height(18);
        OverlayHandle[] handle = new OverlayHandle[1];
        actions.addChild(Ui.button(Component.translatable("ui.economy.action.save"), () -> {
            StorageRoutingSettings parsed;
            try {
                parsed = model.settings(Integer.parseInt(priority.get().trim())).withBlacklist(blacklist.get());
                for (String id : parsed.allowedIds()) {
                    var key = Compat.rl(id);
                    if (!(tank ? BuiltInRegistries.FLUID.containsKey(key) : BuiltInRegistries.ITEM.containsKey(key)))
                        throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                error.set(Component.translatable("ui.economy.routing.invalid").getString()); return;
            }
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.SetStorageRoutingPacket(
                    dimension, storage.getBlockPos(), tank, parsed.filterText(), parsed.priority(), parsed.blacklist()));
            handle[0].close();
        }).primary().small());
        actions.addChild(Ui.button(Component.translatable("ui.economy.action.cancel"), () -> handle[0].close()).small());
        body.addChild(actions);
        handle[0] = Dialog.show(overlays, EconomyUiComponents.storageRoutingCard(body).padding(6).outlined(true).elevated(true),
                true, true, () -> { searchSubscription.close(); popover[0].close(); });
    }

    private static UIComponent searchRow(StorageFilterPickerModel.Entry entry, StorageFilterPickerModel model,
                                         Runnable onAdded, Signal<String> error) {
        HStack content = new HStack().gap(4).align(Alignment.CENTER);
        var icon = new CommodityIconComponent(entry.id());
        icon.width(16); icon.height(16); content.addChild(icon);
        content.addChild(Ui.text(Component.literal(entry.name())).nowrap().ellipsis().flex());
        var button = Ui.button(Component.literal(entry.name()), () -> {
            if (model.toggle(entry.id())) { error.set(""); onAdded.run(); }
            else error.set(Component.translatable("ui.economy.routing.limit", StorageRoutingSettings.MAX_ENTRIES).getString());
        }).content(content).ghost();
        button.fillWidth(); button.height(24);
        button.tooltip(Component.literal(entry.name() + "\n" + entry.id()));
        return button;
    }

    private static UIComponent entryRow(StorageFilterPickerModel.Entry entry, StorageFilterPickerModel model,
                                        Runnable refresh, Signal<String> error) {
        HStack row = new HStack().gap(4).align(Alignment.CENTER);
        row.fillWidth();
        var icon = new CommodityIconComponent(entry.id());
        icon.width(16); icon.height(16); row.addChild(icon);
        row.addChild(Ui.text(Component.literal(entry.name())).nowrap().ellipsis().flex()
                .tooltip(Component.literal(entry.name() + "\n" + entry.id())));
        var toggle = Ui.button((Supplier<Component>) () -> Component.translatable(model.isSelected(entry.id())
                ? "ui.economy.routing.remove" : "ui.economy.routing.add"), () -> {
            if (model.toggle(entry.id())) { error.set(""); refresh.run(); }
            else error.set(Component.translatable("ui.economy.routing.limit", StorageRoutingSettings.MAX_ENTRIES).getString());
        }).outline().small();
        toggle.width(50); toggle.height(16); row.addChild(toggle);
        return row;
    }
}
