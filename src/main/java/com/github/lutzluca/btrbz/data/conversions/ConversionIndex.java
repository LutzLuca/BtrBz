package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ProductRef;
import com.github.lutzluca.btrbz.utils.Utils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConversionIndex {

    public static final int SCHEMA_VERSION = 1;

    private static final ConversionIndex EMPTY = new ConversionIndex(
        SCHEMA_VERSION,
        Instant.EPOCH.toString(),
        null,
        Map.of()
    );

    private final int schemaVersion;
    private final String generatedAt;
    private final String neuCommit;
    private final Map<String, ConversionProductEntry> products;
    private final Map<String, List<ProductRef>> normalizedNameIndex;

    public ConversionIndex(
        int schemaVersion,
        String generatedAt,
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported conversion index schema version: " + schemaVersion);
        }

        this.schemaVersion = schemaVersion;
        this.generatedAt = generatedAt == null || generatedAt.isBlank()
            ? Instant.now().toString()
            : generatedAt;
        this.neuCommit = neuCommit == null || neuCommit.isBlank() ? null : neuCommit;
        this.products = Collections.unmodifiableMap(new LinkedHashMap<>(
            products == null ? Map.of() : products
        ));
        this.normalizedNameIndex = buildNameIndex(this.products);
    }

    public static ConversionIndex empty() {
        return EMPTY;
    }

    public int schemaVersion() {
        return this.schemaVersion;
    }

    public String generatedAt() {
        return this.generatedAt;
    }

    public Optional<String> neuCommit() {
        return Optional.ofNullable(this.neuCommit);
    }

    public Map<String, ConversionProductEntry> products() {
        return this.products;
    }

    public int size() {
        return this.products.size();
    }

    public boolean isEmpty() {
        return this.products.isEmpty();
    }

    public Optional<ProductRef> product(String productId) {
        return Optional
            .ofNullable(this.products.get(productId))
            .map(entry -> new ProductRef(productId, entry.displayName()));
    }

    public Optional<ProductRef> uniqueProductByName(String displayName) {
        var normalized = Utils.normalizeDisplayName(displayName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        var matches = this.normalizedNameIndex.get(normalized);
        return matches != null && matches.size() == 1
            ? Optional.of(matches.getFirst())
            : Optional.empty();
    }

    public boolean hasAmbiguousName(String displayName) {
        var matches = this.normalizedNameIndex.get(Utils.normalizeDisplayName(displayName));
        return matches != null && matches.size() > 1;
    }

    public Set<String> ambiguousNames() {
        var ambiguous = new HashSet<String>();
        this.normalizedNameIndex.forEach((name, refs) -> {
            if (refs.size() > 1) {
                ambiguous.add(name);
            }
        });
        return ambiguous;
    }

    public ConversionSourceCounts sourceCounts() {
        var hypixelItem = 0;
        var neu = 0;
        var derived = 0;

        for (var entry : this.products.values()) {
            switch (entry.source()) {
                case ProductNameSource.HypixelItem ignored -> hypixelItem++;
                case ProductNameSource.Neu ignored -> neu++;
                case ProductNameSource.Derived ignored -> derived++;
            }
        }

        return new ConversionSourceCounts(hypixelItem, neu, derived);
    }

    private static Map<String, List<ProductRef>> buildNameIndex(
        Map<String, ConversionProductEntry> products
    ) {
        var index = new HashMap<String, List<ProductRef>>();
        products.forEach((productId, entry) -> {
            var normalized = Utils.normalizeDisplayName(entry.displayName());
            if (normalized.isEmpty()) {
                return;
            }
            index.computeIfAbsent(normalized, ignored -> new ArrayList<>())
                .add(new ProductRef(productId, entry.displayName()));
        });
        index.replaceAll((ignored, refs) -> Collections.unmodifiableList(refs));
        return Collections.unmodifiableMap(index);
    }
}
