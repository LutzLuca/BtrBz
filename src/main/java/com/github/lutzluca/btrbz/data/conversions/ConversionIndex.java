package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConversionIndex {

    public static final int SCHEMA_VERSION = 1;

    private static final ConversionIndex EMPTY = new ConversionIndex(
        SCHEMA_VERSION,
        0,
        Instant.EPOCH.toString(),
        null,
        Map.of()
    );

    private final int schemaVersion;
    private final int builderVersion;
    private final String generatedAt;
    private final String neuCommit;
    private final Map<String, ConversionProductEntry> products;
    private final Map<String, List<IndexedProduct>> normalizedNameIndex;

    public ConversionIndex(
        int schemaVersion,
        String generatedAt,
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {
        this(schemaVersion, 0, generatedAt, neuCommit, products);
    }

    public ConversionIndex(
        int schemaVersion,
        int builderVersion,
        String generatedAt,
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported conversion index schema version: " + schemaVersion);
        }
        if (builderVersion < 0) {
            throw new IllegalArgumentException("builderVersion must not be negative");
        }

        this.schemaVersion = schemaVersion;
        this.builderVersion = builderVersion;
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

    public int builderVersion() {
        return this.builderVersion;
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

    public Optional<IndexedProduct> product(String productId) {
        return Optional
            .ofNullable(this.products.get(productId))
            .map(entry -> toIndexedProduct(productId, entry));
    }

    public List<IndexedProduct> allProducts() {
        return this.products
            .entrySet()
            .stream()
            .map(entry -> toIndexedProduct(entry.getKey(), entry.getValue()))
            .toList();
    }

    public Optional<IndexedProduct> uniqueProductByName(String displayName) {
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

    public ConversionSourceCounts sourceCounts() {
        var neu = 0;
        var derived = 0;

        for (var entry : this.products.values()) {
            switch (entry.source()) {
                case ProductNameSource.Neu _ -> neu++;
                case ProductNameSource.Derived _ -> derived++;
            }
        }

        return new ConversionSourceCounts(neu, derived);
    }

    private static Map<String, List<IndexedProduct>> buildNameIndex(
        Map<String, ConversionProductEntry> products
    ) {
        var index = new HashMap<String, List<IndexedProduct>>();
        products.forEach((productId, entry) -> {
            var normalized = Utils.normalizeDisplayName(entry.strippedName());
            if (normalized.isEmpty()) {
                return;
            }
            index.computeIfAbsent(normalized, ignored -> new ArrayList<>())
                .add(toIndexedProduct(productId, entry));
        });
        index.replaceAll((ignored, refs) -> Collections.unmodifiableList(refs));
        return Collections.unmodifiableMap(index);
    }

    private static IndexedProduct toIndexedProduct(String productId, ConversionProductEntry entry) {
        return new IndexedProduct(productId, entry.formattedName());
    }
}
