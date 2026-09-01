package com.nstut.economy.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Namespaced registry for built-in and addon-defined commodity types/codecs. */
public final class CommodityTypeRegistry {
    private final Map<EconomyId, ICommodityTypeHandler> handlers = new ConcurrentHashMap<>();

    public void register(ICommodityTypeHandler handler) {
        ICommodityTypeHandler previous = handlers.putIfAbsent(handler.id(), handler);
        if (previous != null && previous != handler) {
            throw new IllegalStateException("Commodity type already registered: " + handler.id());
        }
    }

    public boolean unregister(EconomyId id) {
        return handlers.remove(id) != null;
    }

    public Optional<ICommodityTypeHandler> handler(EconomyId id) {
        return Optional.ofNullable(handlers.get(id));
    }

    public ICommodityTypeHandler require(EconomyId id) {
        ICommodityTypeHandler handler = handlers.get(id);
        if (handler == null) throw new IllegalStateException("No commodity type handler registered for " + id);
        return handler;
    }

    public ICommodityTypeHandler handlerFor(ICommodity commodity) {
        ICommodityTypeHandler direct = handlers.get(commodity.getTypeId());
        if (direct != null && direct.supports(commodity)) return direct;
        for (ICommodityTypeHandler handler : handlers.values()) {
            if (handler.supports(commodity)) return handler;
        }
        throw new IllegalStateException("No registered commodity handler supports " + commodity.getId());
    }

    public CommodityPayload encode(ICommodity commodity) {
        return handlerFor(commodity).encode(commodity);
    }

    public ICommodity decode(EconomyId typeId, EconomyId commodityId, int version, Map<String, String> values) {
        return require(typeId).decode(commodityId, new CommodityPayload(version, values));
    }

    public boolean fluidLike(ICommodity commodity) {
        return handlerFor(commodity).fluidLike();
    }

    public List<ICommodityTypeHandler> handlers() {
        ArrayList<ICommodityTypeHandler> result = new ArrayList<>(handlers.values());
        result.sort(Comparator.comparing(h -> h.id().toString()));
        return List.copyOf(result);
    }
}
