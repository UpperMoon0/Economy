package com.nstut.economy.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Browse matches select products, while each product retains its entire picker catalog. */
public final class BrowseGrouping {
    private BrowseGrouping() {}

    public static <T, K> Map<K, List<T>> visibleCatalogGroups(List<T> visible, List<T> catalog,
                                                            Function<T, K> groupKey) {
        Map<K, List<T>> groups = new LinkedHashMap<>();
        for (T card : visible) groups.computeIfAbsent(groupKey.apply(card), ignored -> new ArrayList<>());
        for (T card : catalog) {
            List<T> variants = groups.get(groupKey.apply(card));
            if (variants != null) variants.add(card);
        }
        groups.replaceAll((key, variants) -> List.copyOf(variants));
        return groups;
    }
}
