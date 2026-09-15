package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public final class ConversionIndex {

    public static final int SCHEMA_VERSION = 2;

    private static final ConversionIndex EMPTY = new ConversionIndex(
        SCHEMA_VERSION,
        0,
        Instant.EPOCH.toString(),
        null,
        Map.of(),
        Set.of());

    @Getter
    private final int schemaVersion;

    @Getter
    private final int builderVersion;

    @Getter
    private final String generatedAt;
    private final String neuCommit;

    @Getter
    private final Map<String, ConversionProductEntry> products;

    @Getter
    private final Set<String> missingProductIds;
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
        this(schemaVersion, builderVersion, generatedAt, neuCommit, products, Set.of());
    }

    public ConversionIndex(
        int schemaVersion,
        int builderVersion,
        String generatedAt,
        String neuCommit,
        Map<String, ConversionProductEntry> products,
        Set<String> missingProductIds
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
            products == null ? Map.of() : products));
        this.missingProductIds = Set.copyOf(
            missingProductIds == null ? Set.of() : missingProductIds);
        this.normalizedNameIndex = buildNameIndex(this.products);
    }

    public static ConversionIndex empty() {
        return EMPTY;
    }

    public Optional<String> neuCommit() {
        return Optional.ofNullable(this.neuCommit);
    }

    public boolean isComplete() {
        return this.missingProductIds.isEmpty();
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

    public Optional<ProductStackData> productStackData(String productId) {
        return Optional
            .ofNullable(this.products.get(productId))
            .map(ConversionProductEntry::itemStack);
    }

    public Optional<LegacyProductStackData> legacyProductStackData(String productId) {
        return Optional
            .ofNullable(this.products.get(productId))
            .map(ConversionProductEntry::legacyItemStack);
    }

    public List<IndexedProduct> allProducts() {
        return this.products
            .entrySet()
            .stream()
            .map(entry -> toIndexedProduct(entry.getKey(), entry.getValue()))
            .toList();
    }

    /** Searches display names, with an exact product id accepted as a secondary convenience. */
    public List<IndexedProduct> searchProducts(String query) {
        var normalizedQuery = Utils.normalizeDisplayName(query);
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        return this.allProducts()
            .stream()
            .map(product -> ProductSearchMatch.match(product, normalizedQuery))
            .flatMap(Optional::stream)
            .sorted(ProductSearchMatch.ORDER)
            .map(ProductSearchMatch::product)
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
        var normalized = Utils.normalizeDisplayName(displayName);
        if (normalized.isEmpty()) {
            return false;
        }

        var matches = this.normalizedNameIndex.get(normalized);
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
            index.computeIfAbsent(normalized, _ -> new ArrayList<>())
                .add(toIndexedProduct(productId, entry));
        });
        index.replaceAll((_, refs) -> Collections.unmodifiableList(refs));
        return Collections.unmodifiableMap(index);
    }

    private static IndexedProduct toIndexedProduct(String productId, ConversionProductEntry entry) {
        return new IndexedProduct(productId, entry.formattedName());
    }

    private record ProductSearchMatch(IndexedProduct product, int kind, int distance, String normalizedName) {

        private static final Comparator<ProductSearchMatch> ORDER = Comparator
            .comparingInt(ProductSearchMatch::kind)
            .thenComparingInt(ProductSearchMatch::distance)
            .thenComparing(ProductSearchMatch::normalizedName)
            .thenComparing(match -> match.product().productId());

        private static Optional<ProductSearchMatch> match(IndexedProduct product, String query) {
            var name = Utils.normalizeDisplayName(product.strippedName());
            if (name.equals(query)) {
                return Optional.of(new ProductSearchMatch(product, 0, 0, name));
            }
            if (name.startsWith(query)) {
                return Optional.of(new ProductSearchMatch(product, 1, 0, name));
            }
            if (name.contains(query)) {
                return Optional.of(new ProductSearchMatch(product, 2, 0, name));
            }
            if (product.productId().equalsIgnoreCase(query)) {
                return Optional.of(new ProductSearchMatch(product, 3, 0, name));
            }

            int distance = tokenDistance(query, name);
            return distance < 0
                ? Optional.empty()
                : Optional.of(new ProductSearchMatch(product, 4, distance, name));
        }

        private static int tokenDistance(String query, String name) {
            var queryTokens = query.split("\\s+");
            var nameTokens = name.split("\\s+");
            int total = 0;
            for (var queryToken : queryTokens) {
                int limit = Math.max(1, queryToken.length() / 3);
                int closest = limit + 1;
                for (var nameToken : nameTokens) {
                    closest = Math.min(closest, editDistance(queryToken, nameToken, limit));
                }
                if (closest > limit) {
                    return -1;
                }
                total += closest;
            }
            return total;
        }

        /** Optimal-string-alignment distance, bounded so searches cannot become unexpectedly expensive. */
        private static int editDistance(String left, String right, int limit) {
            if (Math.abs(left.length() - right.length()) > limit) {
                return limit + 1;
            }

            var previousPrevious = new int[right.length() + 1];
            var previous = new int[right.length() + 1];
            for (int column = 0; column <= right.length(); column++) {
                previous[column] = column;
            }

            for (int row = 1; row <= left.length(); row++) {
                var current = new int[right.length() + 1];
                current[0] = row;
                int rowMinimum = current[0];
                for (int column = 1; column <= right.length(); column++) {
                    int substitution = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                    current[column] = Math.min(
                        Math.min(previous[column] + 1, current[column - 1] + 1),
                        previous[column - 1] + substitution);
                    if (row > 1
                        && column > 1
                        && left.charAt(row - 1) == right.charAt(column - 2)
                        && left.charAt(row - 2) == right.charAt(column - 1)) {
                        current[column] = Math.min(current[column], previousPrevious[column - 2] + 1);
                    }
                    rowMinimum = Math.min(rowMinimum, current[column]);
                }
                if (rowMinimum > limit) {
                    return limit + 1;
                }
                previousPrevious = previous;
                previous = current;
            }
            return previous[right.length()] <= limit ? previous[right.length()] : limit + 1;
        }
    }
}
