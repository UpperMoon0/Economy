#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_exact(text: str, old: str, new: str, label: str, expected: int = 1) -> str:
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} occurrence(s), found {count}")
    return text.replace(old, new)


def replace_at_least(text: str, old: str, new: str, label: str, minimum: int) -> str:
    count = text.count(old)
    if count < minimum:
        raise RuntimeError(f"{label}: expected at least {minimum} occurrence(s), found {count}")
    return text.replace(old, new)


def patch_common(path: Path, rl_expr: str) -> None:
    text = path.read_text(encoding="utf-8")

    text = replace_at_least(
        text,
        "ic.getItem().builtInRegistryHolder().key().location().toString()",
        "ic.getId().toString()",
        f"{path}: canonical item order ids",
        3,
    )

    helper_anchor = "    public static BigDecimal getGlobalPrice(OrderManager orderManager, String itemId) {"
    helper = """    private static String baseItemId(String commodityId) {
        return com.nstut.economy.trading.ItemVariant.baseItemId(
                com.nstut.economy.api.EconomyId.parse(commodityId)).toString();
    }

    private static Item resolveItem(String commodityId) {
        return BuiltInRegistries.ITEM.get(com.nstut.economy.compat.Compat.rl(baseItemId(commodityId)));
    }

    private static ItemCommodity findItemCommodity(OrderManager orderManager, String commodityId) {
        for (Order order : orderManager.getAllOrders()) {
            if (order.getCommodity() instanceof ItemCommodity item
                    && item.getId().toString().equals(commodityId)) return item;
        }
        return null;
    }

""" + helper_anchor
    text = replace_exact(text, helper_anchor, helper, f"{path}: helper insertion")

    text = replace_exact(
        text,
        """                    } else {
                        Item item = BuiltInRegistries.ITEM.get(commodityId);
                        if (item == net.minecraft.world.item.Items.AIR) {""",
        """                    } else {
                        ItemCommodity existingCommodity = findItemCommodity(orderManager, pkt.itemId);
                        var platformItemId = com.nstut.economy.compat.Compat.rl(baseItemId(pkt.itemId));
                        Item item = resolveItem(pkt.itemId);
                        if (item == net.minecraft.world.item.Items.AIR) {""",
        f"{path}: create-order base item resolution",
    )
    text = replace_exact(
        text,
        """                        ItemCommodity commodity = new ItemCommodity(commodityId, item, BigDecimal.ZERO);

                        if (pkt.isSell) {""",
        """                        if (existingCommodity == null && !platformItemId.toString().equals(commodityId.toString())) {
                            sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                            sendItemList(player);
                            return;
                        }
                        ItemCommodity commodity = existingCommodity != null
                                ? existingCommodity : new ItemCommodity(platformItemId, item, BigDecimal.ZERO);

                        if (pkt.isSell) {""",
        f"{path}: exact commodity reuse",
    )
    text = replace_exact(
        text,
        "VaultManager.countItemInVaults(level, player.getUUID(), item) < pkt.quantity",
        "VaultManager.countItemInVaults(level, player.getUUID(), commodity) < pkt.quantity",
        f"{path}: create-order exact stock count",
    )
    text = replace_exact(
        text,
        "VaultManager.extractItemFromVaults(level, player.getUUID(), item, pkt.quantity, reserved)",
        "VaultManager.extractItemFromVaults(level, player.getUUID(), commodity, pkt.quantity, reserved)",
        f"{path}: create-order exact stock extraction",
    )

    text = replace_exact(
        text,
        "displayName = new ItemStack(ic.getItem()).getHoverName().getString();",
        "displayName = ic.getDisplayName(player.serverLevel().registryAccess()).getString();",
        f"{path}: active-order representative name",
    )

    list_block = f"""            ResourceLocation rl = {rl_expr.format(value='commodityId')};

            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            Item item = BuiltInRegistries.ITEM.get(rl);"""
    list_new = f"""            ResourceLocation rl = {rl_expr.format(value='commodityId')};

            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            ItemCommodity resolvedCommodity = findItemCommodity(orderManager, commodityId);
            Item item = resolveItem(commodityId);"""
    text = replace_exact(text, list_block, list_new, f"{path}: browse variant resolution")

    detail_block = f"""        ResourceLocation rl = {rl_expr.format(value='itemId')};
        String displayName;
        int vaultCount;

        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        Item item = BuiltInRegistries.ITEM.get(rl);"""
    detail_new = f"""        ResourceLocation rl = {rl_expr.format(value='itemId')};
        String displayName;
        int vaultCount;

        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        ItemCommodity resolvedCommodity = findItemCommodity(orderManager, itemId);
        Item item = resolveItem(itemId);"""
    text = replace_exact(text, detail_block, detail_new, f"{path}: detail variant resolution")

    history_block = """            net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(t.itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);"""
    if "ResourceLocation.parse" in rl_expr:
        history_block = """            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(t.itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);"""
    history_new = history_block.split("\n")[0] + "\n" + \
        "            ItemCommodity resolvedCommodity = findItemCommodity(Economy.getOrderManager(), t.itemId);\n" + \
        "            net.minecraft.world.item.Item item = resolveItem(t.itemId);"
    text = replace_exact(text, history_block, history_new, f"{path}: history variant resolution")

    text = replace_at_least(
        text,
        "displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();",
        "displayName = resolvedCommodity != null\n                        ? resolvedCommodity.getDisplayName(player.serverLevel().registryAccess()).getString()\n                        : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();",
        f"{path}: representative item names",
        3,
    )

    text = replace_exact(
        text,
        "vaultCount = VaultManager.countItemInVaults(player.serverLevel(), playerId, item);",
        "vaultCount = resolvedCommodity != null\n                    ? VaultManager.countItemInVaults(player.serverLevel(), playerId, resolvedCommodity)\n                    : VaultManager.countItemInVaults(player.serverLevel(), playerId, item);",
        f"{path}: exact detail vault count",
    )

    path.write_text(text, encoding="utf-8")


def patch_26(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_at_least(
        text,
        "net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ic.getItem()).toString()",
        "ic.getId().toString()",
        f"{path}: canonical item order ids",
        3,
    )

    anchor = "    public static BigDecimal getGlobalPrice(OrderManager orderManager, String itemId) {"
    helper = """    private static String baseItemId(String commodityId) {
        return com.nstut.economy.trading.ItemVariant.baseItemId(
                com.nstut.economy.api.EconomyId.parse(commodityId)).toString();
    }

    private static Item resolveItem(String commodityId) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(baseItemId(commodityId)));
    }

    private static ItemCommodity findItemCommodity(OrderManager orderManager, String commodityId) {
        for (Order order : orderManager.getAllOrders()) {
            if (order.getCommodity() instanceof ItemCommodity item
                    && item.getId().toString().equals(commodityId)) return item;
        }
        return null;
    }

""" + anchor
    text = replace_exact(text, anchor, helper, f"{path}: helper insertion")

    text = replace_exact(
        text,
        """                    } else {
                        Item item = BuiltInRegistries.ITEM.getValue(commodityId);
                        if (item == net.minecraft.world.item.Items.AIR) {""",
        """                    } else {
                        ItemCommodity existingCommodity = findItemCommodity(orderManager, pkt.itemId);
                        Identifier platformItemId = Identifier.parse(baseItemId(pkt.itemId));
                        Item item = resolveItem(pkt.itemId);
                        if (item == net.minecraft.world.item.Items.AIR) {""",
        f"{path}: create-order base item resolution",
    )
    text = replace_exact(
        text,
        """                        ItemCommodity commodity = new ItemCommodity(commodityId, item, BigDecimal.ZERO);

                        if (pkt.isSell) {""",
        """                        if (existingCommodity == null && !platformItemId.toString().equals(commodityId.toString())) {
                            sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                            sendItemList(player);
                            return;
                        }
                        ItemCommodity commodity = existingCommodity != null
                                ? existingCommodity : new ItemCommodity(platformItemId, item, BigDecimal.ZERO);

                        if (pkt.isSell) {""",
        f"{path}: exact commodity reuse",
    )
    text = replace_exact(text,
        "VaultManager.countItemInVaults(level, player.getUUID(), item) < pkt.quantity",
        "VaultManager.countItemInVaults(level, player.getUUID(), commodity) < pkt.quantity",
        f"{path}: create-order exact stock count")
    text = replace_exact(text,
        "VaultManager.extractItemFromVaults(level, player.getUUID(), item, pkt.quantity, reserved)",
        "VaultManager.extractItemFromVaults(level, player.getUUID(), commodity, pkt.quantity, reserved)",
        f"{path}: create-order exact stock extraction")
    text = replace_exact(text,
        "displayName = new ItemStack(ic.getItem()).getHoverName().getString();",
        "displayName = ic.getDisplayName(player.level().registryAccess()).getString();",
        f"{path}: active-order representative name")

    text = replace_exact(text,
        """            Identifier rl = Identifier.parse(commodityId);

            Fluid fluid = BuiltInRegistries.FLUID.getValue(rl);
            Item item = BuiltInRegistries.ITEM.getValue(rl);""",
        """            Identifier rl = Identifier.parse(commodityId);

            Fluid fluid = BuiltInRegistries.FLUID.getValue(rl);
            ItemCommodity resolvedCommodity = findItemCommodity(orderManager, commodityId);
            Item item = resolveItem(commodityId);""",
        f"{path}: browse variant resolution")
    text = replace_exact(text,
        """        Identifier rl = Identifier.parse(itemId);
        String displayName;
        int vaultCount;

        Fluid fluid = BuiltInRegistries.FLUID.getValue(rl);
        Item item = BuiltInRegistries.ITEM.getValue(rl);""",
        """        Identifier rl = Identifier.parse(itemId);
        String displayName;
        int vaultCount;

        Fluid fluid = BuiltInRegistries.FLUID.getValue(rl);
        ItemCommodity resolvedCommodity = findItemCommodity(orderManager, itemId);
        Item item = resolveItem(itemId);""",
        f"{path}: detail variant resolution")
    text = replace_exact(text,
        """            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(t.itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(rl);""",
        """            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(t.itemId);
            ItemCommodity resolvedCommodity = findItemCommodity(Economy.getOrderManager(), t.itemId);
            net.minecraft.world.item.Item item = resolveItem(t.itemId);""",
        f"{path}: history variant resolution")
    text = replace_at_least(text,
        "displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();",
        "displayName = resolvedCommodity != null\n                        ? resolvedCommodity.getDisplayName(player.level().registryAccess()).getString()\n                        : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();",
        f"{path}: representative item names", 3)
    text = replace_exact(text,
        "vaultCount = VaultManager.countItemInVaults(player.level(), playerId, item);",
        "vaultCount = resolvedCommodity != null\n                    ? VaultManager.countItemInVaults(player.level(), playerId, resolvedCommodity)\n                    : VaultManager.countItemInVaults(player.level(), playerId, item);",
        f"{path}: exact detail vault count")

    path.write_text(text, encoding="utf-8")


patch_common(ROOT / "common-1.20.1/src/main/java/com/nstut/economy/network/MarketNetwork.java",
             "new ResourceLocation({value})")
patch_common(ROOT / "common-1.21.1/src/main/java/com/nstut/economy/network/MarketNetwork.java",
             "ResourceLocation.parse({value})")
patch_26(ROOT / "neoforge-26.1.2/src/main/java/com/nstut/economy/network/MarketNetwork.java")
print("Applied issue #23 canonical variant network patch")
