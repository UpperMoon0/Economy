#!/usr/bin/env python3
from pathlib import Path
import re

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


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 regex match, found {count}")
    return out


def patch_market(path: Path, flavor: str) -> None:
    text = path.read_text(encoding="utf-8")
    is26 = flavor == "26"
    is120 = flavor == "120"
    platform_type = "Identifier" if is26 else "ResourceLocation"
    parse_expr = "Identifier.parse({value})" if is26 else ("new ResourceLocation({value})" if is120 else "ResourceLocation.parse({value})")
    item_get = "BuiltInRegistries.ITEM.getValue" if is26 else "BuiltInRegistries.ITEM.get"
    fluid_get = "BuiltInRegistries.FLUID.getValue" if is26 else "BuiltInRegistries.FLUID.get"
    player_level = "player.level()" if is26 else "player.serverLevel()"

    if is26:
        text = replace_at_least(text,
            "net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ic.getItem()).toString()",
            "ic.getId().toString()", f"{path}: canonical item order ids", 3)
    else:
        text = replace_at_least(text,
            "ic.getItem().builtInRegistryHolder().key().location().toString()",
            "ic.getId().toString()", f"{path}: canonical item order ids", 3)

    # Register one compact descriptor packet used by every UI surface. It carries
    # canonical variant stack data keyed by the stable commodity id.
    register_anchor = "        " + ("CHANNEL.registerS2C" if "CHANNEL.registerS2C" in text else "CHANNEL.register") + "(SyncItemListPacket.class, SyncItemListPacket::encode, SyncItemListPacket::decode, SyncItemListPacket::handle);\n"
    register_new = register_anchor + "        " + ("CHANNEL.registerS2C" if "CHANNEL.registerS2C" in text else "CHANNEL.register") + "(SyncItemVariantDataPacket.class, SyncItemVariantDataPacket::encode, SyncItemVariantDataPacket::decode, SyncItemVariantDataPacket::handle);\n"
    text = replace_exact(text, register_anchor, register_new, f"{path}: variant packet registration")

    packet_anchor = "    public static class RequestItemDetailPacket {"
    packet = '''    public static class SyncItemVariantDataPacket {
        private static final int MAX_VARIANTS = 4096;
        public final Map<String, String> variants;

        public SyncItemVariantDataPacket(Map<String, String> variants) {
            this.variants = Map.copyOf(variants);
        }

        public static void encode(SyncItemVariantDataPacket pkt, FriendlyByteBuf buf) {
            if (pkt.variants.size() > MAX_VARIANTS) throw new IllegalArgumentException("Too many item variants");
            buf.writeInt(pkt.variants.size());
            for (var entry : pkt.variants.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }

        public static SyncItemVariantDataPacket decode(FriendlyByteBuf buf) {
            int count = buf.readInt();
            if (count < 0 || count > MAX_VARIANTS) throw new io.netty.handler.codec.DecoderException("Invalid item variant count: " + count);
            Map<String, String> variants = new HashMap<>();
            for (int i = 0; i < count; i++) variants.put(buf.readUtf(), buf.readUtf());
            return new SyncItemVariantDataPacket(variants);
        }

        public static void handle(SyncItemVariantDataPacket pkt, Supplier<NetworkManager.PacketContext> ctx) {
            ctx.get().queue(() -> com.nstut.economy.client.CommodityIconComponent.replaceVariantData(pkt.variants));
        }
    }

''' + packet_anchor
    text = replace_exact(text, packet_anchor, packet, f"{path}: variant packet class")

    helper_anchor = "    public static BigDecimal getGlobalPrice(OrderManager orderManager, String itemId) {"
    base_parse = parse_expr.format(value="baseItemId(commodityId)")
    helper = f'''    private static String baseItemId(String commodityId) {{
        return com.nstut.economy.trading.ItemVariant.baseItemId(
                com.nstut.economy.api.EconomyId.parse(commodityId)).toString();
    }}

    private static Item resolveItem(String commodityId) {{
        return {item_get}({base_parse});
    }}

    private static ItemCommodity findItemCommodity(OrderManager orderManager, String commodityId) {{
        for (Order order : orderManager.getAllOrders()) {{
            if (order.getCommodity() instanceof ItemCommodity item
                    && item.getId().toString().equals(commodityId)) return item;
        }}
        return null;
    }}

    public static ItemCommodity resolveItemCommodityForOrder(OrderManager orderManager, ServerLevel level,
                                                              UUID ownerId, String commodityId) {{
        ItemCommodity existing = findItemCommodity(orderManager, commodityId);
        if (existing != null) return existing;
        Item item = resolveItem(commodityId);
        if (item == net.minecraft.world.item.Items.AIR) return null;
        String baseId = baseItemId(commodityId);
        if (baseId.equals(commodityId)) {{
            return new ItemCommodity({parse_expr.format(value='baseId')}, item, BigDecimal.ZERO);
        }}
        if (level == null || ownerId == null) return null;
        for (var vault : VaultManager.getVaults(level, ownerId)) {{
            for (int slot = 0; slot < vault.getContainerSize(); slot++) {{
                ItemStack stack = vault.getItem(slot);
                if (stack.isEmpty() || !stack.is(item)) continue;
                ItemCommodity candidate = ItemCommodity.exactFromItemStack(level.registryAccess(), stack, BigDecimal.ZERO);
                if (candidate.getId().toString().equals(commodityId)) return candidate;
            }}
        }}
        return null;
    }}

    private static ItemCommodity commodityForStack(ServerLevel level, ItemStack stack) {{
        var baseId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (baseId == null) return null;
        if (com.nstut.economy.compat.Compat.stacksEqual(new ItemStack(stack.getItem()), stack)) {{
            return new ItemCommodity(baseId, stack.getItem(), BigDecimal.ZERO);
        }}
        return ItemCommodity.exactFromItemStack(level.registryAccess(), stack, BigDecimal.ZERO);
    }}

    private static String variantData(ItemCommodity commodity) {{
        return commodity != null && commodity.getMatchPolicy() != com.nstut.economy.trading.ItemMatchPolicy.ITEM_ONLY
                ? commodity.getCanonicalVariantData() : "";
    }}

    private static void sendItemVariantData(ServerPlayer player) {{
        Map<String, String> variants = new HashMap<>();
        OrderManager orderManager = Economy.getOrderManager();
        for (Order order : orderManager.getAllOrders()) {{
            if (order.getCommodity() instanceof ItemCommodity item) {{
                String data = variantData(item);
                if (!data.isEmpty()) variants.put(item.getId().toString(), data);
            }}
        }}
        ServerLevel level = {player_level};
        for (var vault : VaultManager.getVaults(level, player.getUUID())) {{
            for (int slot = 0; slot < vault.getContainerSize(); slot++) {{
                ItemStack stack = vault.getItem(slot);
                if (stack.isEmpty()) continue;
                ItemCommodity item = commodityForStack(level, stack);
                if (item == null) continue;
                String data = variantData(item);
                if (!data.isEmpty()) variants.put(item.getId().toString(), data);
            }}
        }}
        for (var trade : TradeLedger.getAllTrades()) {{
            if (trade.variantData != null && !trade.variantData.isBlank()) variants.put(trade.itemId, trade.variantData);
        }}
        CHANNEL.sendToPlayer(player, new SyncItemVariantDataPacket(variants));
    }}

''' + helper_anchor
    text = replace_exact(text, helper_anchor, helper, f"{path}: helper insertion")

    # Create-order resolution must be able to bootstrap a variant from the seller's
    # Vault, not only from an already-existing active order.
    getter_call = f"{item_get}(commodityId)"
    old_create = f'''                    }} else {{
                        Item item = {getter_call};
                        if (item == net.minecraft.world.item.Items.AIR) {{
                            sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                            sendItemList(player);
                            return;
                        }}
                        ItemCommodity commodity = new ItemCommodity(commodityId, item, BigDecimal.ZERO);
'''
    new_create = f'''                    }} else {{
                        ItemCommodity commodity = resolveItemCommodityForOrder(orderManager, level, player.getUUID(), pkt.itemId);
                        if (commodity == null) {{
                            sendActionResult(player, Action.CREATE_ORDER, Result.ERROR, "ui.economy.error.commodity_invalid");
                            sendItemList(player);
                            return;
                        }}
                        Item item = commodity.getItem();
'''
    text = replace_exact(text, old_create, new_create, f"{path}: create-order variant bootstrap")
    text = replace_exact(text,
        "VaultManager.countItemInVaults(level, player.getUUID(), item) < pkt.quantity",
        "VaultManager.countItemInVaults(level, player.getUUID(), commodity) < pkt.quantity",
        f"{path}: exact create stock count")
    text = replace_exact(text,
        "VaultManager.extractItemFromVaults(level, player.getUUID(), item, pkt.quantity, reserved)",
        "VaultManager.extractItemFromVaults(level, player.getUUID(), commodity, pkt.quantity, reserved)",
        f"{path}: exact create extraction")

    # Active-order names must come from the representative stack.
    level_registry = f"{player_level}.registryAccess()"
    text = replace_exact(text,
        "displayName = new ItemStack(ic.getItem()).getHoverName().getString();",
        f"displayName = ic.getDisplayName({level_registry}).getString();",
        f"{path}: active order representative name")

    # Browse and detail must resolve variant ids back to their base item and exact commodity.
    rl_line = f"            {platform_type} rl = {parse_expr.format(value='commodityId')};\n\n            Fluid fluid = {fluid_get}(rl);\n            Item item = {item_get}(rl);"
    rl_new = f"            {platform_type} rl = {parse_expr.format(value='commodityId')};\n\n            Fluid fluid = {fluid_get}(rl);\n            ItemCommodity resolvedCommodity = findItemCommodity(orderManager, commodityId);\n            Item item = resolveItem(commodityId);"
    text = replace_exact(text, rl_line, rl_new, f"{path}: browse variant resolution")

    detail_old = f"        {platform_type} rl = {parse_expr.format(value='itemId')};\n        String displayName;\n        int vaultCount;\n\n        Fluid fluid = {fluid_get}(rl);\n        Item item = {item_get}(rl);"
    detail_new = f"        {platform_type} rl = {parse_expr.format(value='itemId')};\n        String displayName;\n        int vaultCount;\n\n        Fluid fluid = {fluid_get}(rl);\n        ItemCommodity resolvedCommodity = resolveItemCommodityForOrder(orderManager, {player_level}, playerId, itemId);\n        Item item = resolveItem(itemId);"
    text = replace_exact(text, detail_old, detail_new, f"{path}: detail variant resolution")

    display_old = "displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();"
    display_new = f"displayName = resolvedCommodity != null\n                        ? resolvedCommodity.getDisplayName({level_registry}).getString()\n                        : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();"
    text = replace_at_least(text, display_old, display_new, f"{path}: representative item names", 2)

    text = replace_exact(text,
        f"vaultCount = VaultManager.countItemInVaults({player_level}, playerId, item);",
        f"vaultCount = resolvedCommodity != null\n                    ? VaultManager.countItemInVaults({player_level}, playerId, resolvedCommodity)\n                    : VaultManager.countItemInVaults({player_level}, playerId, item);",
        f"{path}: exact detail vault count")

    # History paths cannot parse /variant/<sha> as a registered item. New trades
    # persist their display name and canonical variant descriptor, so history no
    # longer depends on an unrelated active order still existing.
    if is26:
        hist_pattern = r'''            net\.minecraft\.resources\.Identifier rl = net\.minecraft\.resources\.Identifier\.parse\(t\.itemId\);\n            net\.minecraft\.world\.item\.Item item = net\.minecraft\.core\.registries\.BuiltInRegistries\.ITEM\.getValue\(rl\);'''
        hist_repl = '''            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.parse(baseItemId(t.itemId));
            ItemCommodity resolvedCommodity = findItemCommodity(Economy.getOrderManager(), t.itemId);
            net.minecraft.world.item.Item item = resolveItem(t.itemId);'''
    else:
        hist_pattern = r'''            net\.minecraft\.resources\.ResourceLocation rl = (?:new net\.minecraft\.resources\.ResourceLocation\(t\.itemId\)|ResourceLocation\.parse\(t\.itemId\));\n            net\.minecraft\.world\.item\.Item item = net\.minecraft\.core\.registries\.BuiltInRegistries\.ITEM\.get\(rl\);'''
        hist_repl = f'''            net.minecraft.resources.ResourceLocation rl = {parse_expr.format(value='baseItemId(t.itemId)')};
            ItemCommodity resolvedCommodity = findItemCommodity(Economy.getOrderManager(), t.itemId);
            net.minecraft.world.item.Item item = resolveItem(t.itemId);'''
    text = regex_once(text, hist_pattern, hist_repl, f"{path}: history base resolution")
    text = text.replace(display_new,
        f"displayName = t.displayName != null && !t.displayName.isBlank()\n                        ? t.displayName\n                        : resolvedCommodity != null\n                        ? resolvedCommodity.getDisplayName({level_registry}).getString()\n                        : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();",
        1 if text.count("t.displayName") == 0 else 0)

    # Player holdings materialize exact ids from non-default stacks, making the
    # first exact sell order discoverable without a pre-existing market order.
    if is26:
        holding_id_old = "String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();\n                    itemCounts.put(id, itemCounts.getOrDefault(id, 0) + stack.getCount());"
    else:
        holding_id_old = "String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();\n                    itemCounts.put(id, itemCounts.getOrDefault(id, 0) + stack.getCount());"
    holding_id_new = f"ItemCommodity holdingCommodity = commodityForStack({player_level}, stack);\n                    if (holdingCommodity == null) continue;\n                    String id = holdingCommodity.getId().toString();\n                    itemCounts.put(id, itemCounts.getOrDefault(id, 0) + stack.getCount());"
    text = replace_exact(text, holding_id_old, holding_id_new, f"{path}: variant portfolio ids")

    # Resolve portfolio display names through the same exact commodity path.
    if is26:
        portfolio_old = '''                net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                name = new ItemStack(it).getHoverName().getString();'''
    else:
        portfolio_old = f'''                net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get({parse_expr.format(value='id')});
                name = new ItemStack(it).getHoverName().getString();'''
    portfolio_new = f'''                ItemCommodity holdingCommodity = resolveItemCommodityForOrder(Economy.getOrderManager(), {player_level}, player.getUUID(), id);
                net.minecraft.world.item.Item it = resolveItem(id);
                name = holdingCommodity != null ? holdingCommodity.getDisplayName({level_registry}).getString()
                        : new ItemStack(it).getHoverName().getString();'''
    text = replace_exact(text, portfolio_old, portfolio_new, f"{path}: variant portfolio names")

    # Every normal market refresh also refreshes the icon descriptor cache.
    sync_anchor = "        CHANNEL.sendToPlayer(player, new SyncItemListPacket(balance, vaultCount, cards));"
    text = replace_exact(text, sync_anchor,
        "        sendItemVariantData(player);\n" + sync_anchor,
        f"{path}: send variant descriptor sync")

    path.write_text(text, encoding="utf-8")


def patch_market_screen(path: Path, flavor: str) -> None:
    text = path.read_text(encoding="utf-8")
    is26 = flavor == "26"
    id_type = "Identifier" if is26 else "ResourceLocation"
    parse = "Identifier.tryParse(query.trim())" if is26 else "ResourceLocation.tryParse(query.trim())"
    base_parse = "Identifier.parse(base)" if is26 else ("new ResourceLocation(base)" if flavor == "120" else "ResourceLocation.parse(base)")
    item_get = "BuiltInRegistries.ITEM.getValue" if is26 else "BuiltInRegistries.ITEM.get"
    fluid_get = "BuiltInRegistries.FLUID.getValue" if is26 else "BuiltInRegistries.FLUID.get"

    method_pattern = r'''    private void requestCommodityDetailIfExact\(String query\) \{.*?\n    \}\n\n    private void openCreateOrderWithPrefill'''
    method_repl = f'''    private void requestCommodityDetailIfExact(String query) {{
        if (query == null || query.isBlank()) return;
        {id_type} id = {parse};
        if (id == null) return;
        String base = com.nstut.economy.trading.ItemVariant.baseItemId(
                com.nstut.economy.api.EconomyId.parse(id.toString())).toString();
        var baseId = {base_parse};
        boolean knownItem = BuiltInRegistries.ITEM.containsKey(baseId);
        boolean knownFluid = base.equals(id.toString()) && BuiltInRegistries.FLUID.containsKey(id)
                && CommodityUtil.isCanonicalFluid({fluid_get}(id));
        if (knownItem || knownFluid) {{
            MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.RequestItemDetailPacket(id.toString(),
                    knownItem && knownFluid ? null : knownFluid ? "FLUID" : "ITEM"));
        }}
    }}

    private void openCreateOrderWithPrefill'''
    text = regex_once(text, method_pattern, method_repl, f"{path}: variant detail lookup")

    search_pattern = r'''    private List<ItemSearchResult> getItemSearchResults\(String query\) \{.*?\n    \}\n\n    private static class ItemSearchResult'''
    search_repl = f'''    private List<ItemSearchResult> getItemSearchResults(String query) {{
        if (query == null || query.length() < 2) return List.of();
        String q = query.toLowerCase(Locale.ROOT);
        List<ItemSearchResult> results = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var holding : MarketClientStore.assetHoldings.get()) {{
            String id = holding.itemId;
            String name = holding.displayName;
            if ((name.toLowerCase(Locale.ROOT).contains(q) || id.toLowerCase(Locale.ROOT).contains(q)) && seen.add(id)) {{
                results.add(new ItemSearchResult(id, name));
                if (results.size() >= 50) return results;
            }}
        }}
        for ({id_type} rl : BuiltInRegistries.ITEM.keySet()) {{
            Item item = {item_get}(rl);
            String name = new ItemStack(item).getHoverName().getString();
            String rlStr = rl.toString();
            if ((name.toLowerCase(Locale.ROOT).contains(q) || rlStr.contains(q)) && seen.add(rlStr)) {{
                results.add(new ItemSearchResult(rlStr, name));
                if (results.size() >= 50) return results;
            }}
        }}
        for ({id_type} rl : BuiltInRegistries.FLUID.keySet()) {{
            Fluid fluid = {fluid_get}(rl);
            if (!CommodityUtil.isCanonicalFluid(fluid)) continue;
            String name = com.nstut.economy.platform.Services.FLUID.displayName(fluid).getString();
            String rlStr = rl.toString();
            if ((name.toLowerCase(Locale.ROOT).contains(q) || rlStr.contains(q)) && seen.add(rlStr)) {{
                results.add(new ItemSearchResult(rlStr, name));
                if (results.size() >= 50) return results;
            }}
        }}
        return results;
    }}

    private static class ItemSearchResult'''
    text = regex_once(text, search_pattern, search_repl, f"{path}: variant search results")
    path.write_text(text, encoding="utf-8")


def patch_icon(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    cache_anchor = "    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();"
    cache_new = cache_anchor + '''
    private static final Map<String, String> VARIANT_DATA = new HashMap<>();

    public static void replaceVariantData(Map<String, String> variants) {
        VARIANT_DATA.clear();
        if (variants != null) VARIANT_DATA.putAll(variants);
        ITEM_CACHE.clear();
    }'''
    text = replace_exact(text, cache_anchor, cache_new, f"{path}: variant icon cache")

    compute_anchor = "            ItemStack icon = ITEM_CACHE.computeIfAbsent(commodityId, id -> {\n"
    compute_new = compute_anchor + '''                String canonical = VARIANT_DATA.get(id);
                if (canonical != null && !canonical.isBlank() && Minecraft.getInstance().level != null) {
                    try {
                        ItemStack exact = com.nstut.economy.compat.Compat.deserializeCanonicalItemStack(
                                Minecraft.getInstance().level.registryAccess(), canonical);
                        if (exact != null && !exact.isEmpty()) return exact;
                    } catch (RuntimeException ignored) {
                        // Fall through to the registered base item. Server remains authoritative.
                    }
                }
'''
    text = replace_exact(text, compute_anchor, compute_new, f"{path}: render exact representative")
    path.write_text(text, encoding="utf-8")


def patch_item_variant(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    anchor = '''    public String fingerprint() {
        return fingerprint;
    }
'''
    new = anchor + '''
    public boolean hasCapturedRepresentative() {
        return !capturedRepresentative.isEmpty();
    }
'''
    text = replace_exact(text, anchor, new, f"{path}: captured representative state")
    path.write_text(text, encoding="utf-8")


def patch_item_commodity(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    old = '''    public boolean matches(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(item) && variant.matchesCaptured(stack);
    }
'''
    new = '''    public boolean matches(ItemStack stack) {
        if (variant.policy() != ItemMatchPolicy.ITEM_ONLY && !variant.hasCapturedRepresentative()) {
            throw new UnsupportedOperationException("Persisted variant matching requires registry access; use matches(Level, ItemStack) or matches(HolderLookup.Provider, ItemStack)");
        }
        return stack != null && !stack.isEmpty() && stack.is(item) && variant.matchesCaptured(stack);
    }
'''
    text = replace_exact(text, old, new, f"{path}: fail-loud legacy exact matching")
    path.write_text(text, encoding="utf-8")


def patch_trade_ledger(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    old = '''    public static void recordTrade(String itemId, String commodityType, BigDecimal price,
                                   int quantity, UUID buyer, UUID seller) {
        EconomyTradeData current = data;
        if (current == null) return;
        current.recordTrade(itemId, commodityType, price, quantity, buyer, seller);
        TradeView view = new TradeView(EconomyId.parse(itemId), typeId(commodityType), price, quantity,
                buyer, seller, Instant.now());
        EconomyEvents.post(new MarketEvents.TradeCompleted(view));
    }
'''
    new = '''    public static void recordTrade(String itemId, String commodityType, BigDecimal price,
                                   int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, commodityType, "", "", price, quantity, buyer, seller);
    }

    public static void recordTrade(String itemId, String commodityType, String variantData, String displayName,
                                   BigDecimal price, int quantity, UUID buyer, UUID seller) {
        EconomyTradeData current = data;
        if (current == null) return;
        current.recordTrade(itemId, commodityType, variantData, displayName, price, quantity, buyer, seller);
        TradeView view = new TradeView(EconomyId.parse(itemId), typeId(commodityType), price, quantity,
                buyer, seller, Instant.now());
        EconomyEvents.post(new MarketEvents.TradeCompleted(view));
    }
'''
    text = replace_exact(text, old, new, f"{path}: trade descriptor persistence")
    path.write_text(text, encoding="utf-8")


def patch_order(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_exact(text,
        "        recordTrade(pricePerUnit, delivered, buyer, seller);",
        "        recordTrade(level, pricePerUnit, delivered, buyer, seller);",
        f"{path}: pass registry context to trade record")
    old = '''    private void recordTrade(BigDecimal price, int amount, UUID buyer, UUID seller) {
        String typeValue = commodity.getType() == ICommodity.CommodityType.ITEM ? "ITEM"
                : commodity.getType() == ICommodity.CommodityType.FLUID ? "FLUID" : commodity.getTypeId().toString();
        TradeLedger.recordTrade(commodity.getId().toString(), typeValue, price, amount, buyer, seller);
    }
'''
    new = '''    private void recordTrade(ServerLevel level, BigDecimal price, int amount, UUID buyer, UUID seller) {
        String typeValue = commodity.getType() == ICommodity.CommodityType.ITEM ? "ITEM"
                : commodity.getType() == ICommodity.CommodityType.FLUID ? "FLUID" : commodity.getTypeId().toString();
        String variantData = "";
        String displayName = commodity.getDisplayName().getString();
        if (commodity instanceof ItemCommodity item && item.getMatchPolicy() != ItemMatchPolicy.ITEM_ONLY) {
            variantData = item.getCanonicalVariantData();
            if (level != null) displayName = item.getDisplayName(level.registryAccess()).getString();
        }
        TradeLedger.recordTrade(commodity.getId().toString(), typeValue, variantData, displayName,
                price, amount, buyer, seller);
    }
'''
    text = replace_exact(text, old, new, f"{path}: persist exact trade descriptor")
    path.write_text(text, encoding="utf-8")


def patch_trade_data_common(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_exact(text,
        "        public final String itemId; public final String commodityType; public final String price;\n        public final int quantity; public final UUID buyer; public final UUID seller; public final long timestamp;",
        "        public final String itemId; public final String commodityType; public final String variantData; public final String displayName; public final String price;\n        public final int quantity; public final UUID buyer; public final UUID seller; public final long timestamp;",
        f"{path}: trade snapshot fields")
    text = replace_exact(text,
        '''        public TradeSnapshot(String itemId, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, null, price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this.itemId = itemId; this.commodityType = commodityType; this.price = price; this.quantity = quantity;
            this.buyer = buyer; this.seller = seller; this.timestamp = timestamp;
        }''',
        '''        public TradeSnapshot(String itemId, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, null, "", "", price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, commodityType, "", "", price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String variantData, String displayName,
                             String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this.itemId = itemId; this.commodityType = commodityType; this.variantData = variantData; this.displayName = displayName;
            this.price = price; this.quantity = quantity; this.buyer = buyer; this.seller = seller; this.timestamp = timestamp;
        }''', f"{path}: trade snapshot constructors")
    text = replace_exact(text,
        '''    public void recordTrade(String itemId, String commodityType, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        trades.add(new TradeSnapshot(itemId, commodityType, price.toPlainString(), quantity, buyer, seller, System.currentTimeMillis()));''',
        '''    public void recordTrade(String itemId, String commodityType, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, commodityType, "", "", price, quantity, buyer, seller);
    }
    public void recordTrade(String itemId, String commodityType, String variantData, String displayName,
                            BigDecimal price, int quantity, UUID buyer, UUID seller) {
        trades.add(new TradeSnapshot(itemId, commodityType, variantData, displayName, price.toPlainString(), quantity, buyer, seller, System.currentTimeMillis()));''',
        f"{path}: trade record overload")
    text = replace_exact(text,
        '''                data.trades.add(new TradeSnapshot(t.getString("ItemId"), t.contains("CommodityType") ? t.getString("CommodityType") : null,
                        t.getString("Price"), t.getInt("Quantity"), t.getUUID("Buyer"), t.getUUID("Seller"), t.getLong("Timestamp")));''',
        '''                data.trades.add(new TradeSnapshot(t.getString("ItemId"), t.contains("CommodityType") ? t.getString("CommodityType") : null,
                        t.contains("VariantData") ? t.getString("VariantData") : "", t.contains("DisplayName") ? t.getString("DisplayName") : "",
                        t.getString("Price"), t.getInt("Quantity"), t.getUUID("Buyer"), t.getUUID("Seller"), t.getLong("Timestamp")));''',
        f"{path}: load trade descriptors")
    text = replace_exact(text,
        '''            if (t.commodityType != null) tTag.putString("CommodityType", t.commodityType);
            tTag.putUUID("Buyer", t.buyer);''',
        '''            if (t.commodityType != null) tTag.putString("CommodityType", t.commodityType);
            if (t.variantData != null && !t.variantData.isBlank()) tTag.putString("VariantData", t.variantData);
            if (t.displayName != null && !t.displayName.isBlank()) tTag.putString("DisplayName", t.displayName);
            tTag.putUUID("Buyer", t.buyer);''',
        f"{path}: save trade descriptors")
    path.write_text(text, encoding="utf-8")


def patch_trade_data_26(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_exact(text,
        "        public final String itemId; public final String commodityType; public final String price;\n        public final int quantity; public final UUID buyer; public final UUID seller; public final long timestamp;",
        "        public final String itemId; public final String commodityType; public final String variantData; public final String displayName; public final String price;\n        public final int quantity; public final UUID buyer; public final UUID seller; public final long timestamp;",
        f"{path}: trade snapshot fields")
    text = replace_exact(text,
        '''        public TradeSnapshot(String itemId, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, null, price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this.itemId = itemId; this.commodityType = commodityType; this.price = price; this.quantity = quantity;
            this.buyer = buyer; this.seller = seller; this.timestamp = timestamp;
        }''',
        '''        public TradeSnapshot(String itemId, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, null, "", "", price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this(itemId, commodityType, "", "", price, quantity, buyer, seller, timestamp);
        }
        public TradeSnapshot(String itemId, String commodityType, String variantData, String displayName,
                             String price, int quantity, UUID buyer, UUID seller, long timestamp) {
            this.itemId = itemId; this.commodityType = commodityType; this.variantData = variantData; this.displayName = displayName;
            this.price = price; this.quantity = quantity; this.buyer = buyer; this.seller = seller; this.timestamp = timestamp;
        }''', f"{path}: trade snapshot constructors")
    text = replace_exact(text,
        '''    public void recordTrade(String itemId, String commodityType, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        trades.add(new TradeSnapshot(itemId, commodityType, price.toPlainString(), quantity, buyer, seller, System.currentTimeMillis()));''',
        '''    public void recordTrade(String itemId, String commodityType, BigDecimal price, int quantity, UUID buyer, UUID seller) {
        recordTrade(itemId, commodityType, "", "", price, quantity, buyer, seller);
    }
    public void recordTrade(String itemId, String commodityType, String variantData, String displayName,
                            BigDecimal price, int quantity, UUID buyer, UUID seller) {
        trades.add(new TradeSnapshot(itemId, commodityType, variantData, displayName, price.toPlainString(), quantity, buyer, seller, System.currentTimeMillis()));''',
        f"{path}: trade record overload")
    text = replace_exact(text,
        '''                data.trades.add(new TradeSnapshot(t.getStringOr("ItemId", ""), t.contains("CommodityType") ? t.getStringOr("CommodityType", "") : null,
                        t.getStringOr("Price", ""), t.getIntOr("Quantity", 0), com.nstut.economy.util.NbtCompat.getUuid(t, "Buyer"),
                        com.nstut.economy.util.NbtCompat.getUuid(t, "Seller"), t.getLongOr("Timestamp", 0L)));''',
        '''                data.trades.add(new TradeSnapshot(t.getStringOr("ItemId", ""), t.contains("CommodityType") ? t.getStringOr("CommodityType", "") : null,
                        t.getStringOr("VariantData", ""), t.getStringOr("DisplayName", ""), t.getStringOr("Price", ""), t.getIntOr("Quantity", 0),
                        com.nstut.economy.util.NbtCompat.getUuid(t, "Buyer"), com.nstut.economy.util.NbtCompat.getUuid(t, "Seller"), t.getLongOr("Timestamp", 0L)));''',
        f"{path}: load trade descriptors")
    text = replace_exact(text,
        '''            if (t.commodityType != null) tTag.putString("CommodityType", t.commodityType);
            com.nstut.economy.util.NbtCompat.putUuid(tTag, "Buyer", t.buyer);''',
        '''            if (t.commodityType != null) tTag.putString("CommodityType", t.commodityType);
            if (t.variantData != null && !t.variantData.isBlank()) tTag.putString("VariantData", t.variantData);
            if (t.displayName != null && !t.displayName.isBlank()) tTag.putString("DisplayName", t.displayName);
            com.nstut.economy.util.NbtCompat.putUuid(tTag, "Buyer", t.buyer);''',
        f"{path}: save trade descriptors")
    path.write_text(text, encoding="utf-8")


def patch_gametest(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    anchor = '''        helper.assertTrue(restored.matches(helper.getLevel(), sharpness) && !restored.matches(helper.getLevel(), mending),
                "restored exact matcher must remain variant-specific");
'''
    new = anchor + '''
        ItemCommodity bootstrapped = com.nstut.economy.network.MarketNetwork.resolveItemCommodityForOrder(
                new OrderManager(), helper.getLevel(), seller, sharpnessCommodity.getId().toString());
        helper.assertTrue(bootstrapped != null && bootstrapped.getId().equals(sharpnessCommodity.getId()),
                "first exact sell order must bootstrap its commodity from matching Vault stock without an existing order");
        boolean noContextFailedLoudly = false;
        try {
            restored.matches(sharpness);
        } catch (UnsupportedOperationException expected) {
            noContextFailedLoudly = true;
        }
        helper.assertTrue(noContextFailedLoudly,
                "persisted exact commodities must reject registry-less matching instead of silently reporting no stock");
'''
    text = replace_exact(text, anchor, new, f"{path}: bootstrap/fail-loud regression")
    path.write_text(text, encoding="utf-8")


patch_market(ROOT / "common-1.20.1/src/main/java/com/nstut/economy/network/MarketNetwork.java", "120")
patch_market(ROOT / "common-1.21.1/src/main/java/com/nstut/economy/network/MarketNetwork.java", "121")
patch_market(ROOT / "neoforge-26.1.2/src/main/java/com/nstut/economy/network/MarketNetwork.java", "26")

patch_market_screen(ROOT / "common-1.20.1/src/main/java/com/nstut/economy/client/MarketScreen.java", "120")
patch_market_screen(ROOT / "common-1.21.1/src/main/java/com/nstut/economy/client/MarketScreen.java", "121")
patch_market_screen(ROOT / "neoforge-26.1.2/src/main/java/com/nstut/economy/client/MarketScreen.java", "26")

patch_icon(ROOT / "common-1.20.1/src/main/java/com/nstut/economy/client/CommodityIconComponent.java")
patch_icon(ROOT / "common-1.21.1/src/main/java/com/nstut/economy/client/CommodityIconComponent.java")
patch_icon(ROOT / "neoforge-26.1.2/src/main/java/com/nstut/economy/client/CommodityIconComponent.java")

patch_item_variant(ROOT / "shared/minecraft-all/src/main/java/com/nstut/economy/trading/ItemVariant.java")
patch_item_commodity(ROOT / "shared/minecraft-1.20plus/src/main/java/com/nstut/economy/trading/ItemCommodity.java")
patch_item_commodity(ROOT / "neoforge-26.1.2/src/main/java/com/nstut/economy/trading/ItemCommodity.java")
patch_trade_ledger(ROOT / "shared/minecraft-all/src/main/java/com/nstut/economy/data/TradeLedger.java")
patch_order(ROOT / "shared/minecraft-all/src/main/java/com/nstut/economy/trading/Order.java")
patch_trade_data_common(ROOT / "common-1.20.1/src/main/java/com/nstut/economy/data/EconomyTradeData.java")
patch_trade_data_common(ROOT / "common-1.21.1/src/main/java/com/nstut/economy/data/EconomyTradeData.java")
patch_trade_data_26(ROOT / "neoforge-26.1.2/src/main/java/com/nstut/economy/data/EconomyTradeData.java")
patch_gametest(ROOT / "forge-1.20.1/src/main/java/com/nstut/forge/gametest/EconomyGameTests.java")

print("Applied issue #23 variant boundary, bootstrap, UI descriptor, and persistence fixes")