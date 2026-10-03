package com.github.lutzluca.btrbz.data.conversions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RemoteNeuConversionIndexBuilderTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("reuseCases")
    void reusesOnlyCompleteCurrentEntriesAtTheSameCommit(
        String description,
        ConversionIndex current,
        String nextCommit,
        Set<String> products,
        boolean expected
    ) {
        Assertions.assertEquals(expected,
            RemoteNeuConversionIndexBuilder.shouldReuseNeuEntries(current, nextCommit, products));
    }

    private static Stream<Arguments> reuseCases() {
        var entries = Map.of("NEU_PRODUCT",
            new ConversionProductEntry("Neu Product", new ProductNameSource.Neu("NEU_PRODUCT")));
        var current = indexWithNeuCommit("abc", entries);
        return Stream.of(
            Arguments.of("matching commit with covered products",
                current, "abc", Set.of("NEU_PRODUCT"), true),
            Arguments.of("new product despite matching commit",
                current, "abc", Set.of("NEU_PRODUCT", "NEW_NEU_PRODUCT"), false),
            Arguments.of("changed commit",
                indexWithNeuCommit("old", entries), "new", Set.of("NEU_PRODUCT"), false),
            Arguments.of("changed builder version",
                new ConversionIndex(ConversionIndex.SCHEMA_VERSION, 0, "now", "abc", entries),
                "abc", Set.of("NEU_PRODUCT"), false),
            Arguments.of("incomplete current index",
                new ConversionIndex(ConversionIndex.SCHEMA_VERSION,
                    RemoteNeuConversionIndexBuilder.BUILDER_VERSION, "now", "abc",
                    entries, Set.of("MISSING_PRODUCT")),
                "abc", Set.of("NEU_PRODUCT"), false));
    }

    @ParameterizedTest(name = "partial index allowed={0}")
    @ValueSource(booleans = {false, true})
    void enforcesThePartialIndexPolicy(boolean allowPartial) throws Exception {
        var productIds = Set.of("KNOWN", "MISSING");
        var products = Map.of("KNOWN",
            new ConversionProductEntry("Known", new ProductNameSource.Neu("KNOWN")));

        if (allowPartial) {
            Assertions.assertEquals(Set.of("MISSING"),
                RemoteNeuConversionIndexBuilder.validateCompleteIndex(productIds, products, true));
        } else {
            Assertions.assertThrows(ConversionRefreshException.class,
                () -> RemoteNeuConversionIndexBuilder.validateCompleteIndex(productIds, products, false));
        }
    }

    @Test
    void carriesForwardOnlyMissingEntriesWithoutOverwritingFreshMappings() {
        var stale = new ConversionProductEntry("Stale", new ProductNameSource.Neu("STALE"));
        var current = indexWithNeuCommit("old", Map.of(
            "UPDATED", new ConversionProductEntry("Old Updated", new ProductNameSource.Neu("OLD_UPDATED")),
            "STALE", stale,
            "REMOVED", new ConversionProductEntry("Removed", new ProductNameSource.Neu("REMOVED"))));
        var freshUpdated = new ConversionProductEntry(
            "Fresh Updated", new ProductNameSource.Neu("FRESH_UPDATED"));
        var products = new LinkedHashMap<>(Map.of("UPDATED", freshUpdated));

        var carriedForward = RemoteNeuConversionIndexBuilder.carryForwardMissingEntries(
            current, products, Set.of("STALE"));

        Assertions.assertEquals(1, carriedForward);
        Assertions.assertEquals(Map.of("UPDATED", freshUpdated, "STALE", stale), products);
    }

    @ParameterizedTest(name = "{0}, {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
        "\u00a7fEnchanted Book | \u00a79Quick Bite I | \u00a7fQuick Bite I",
        "\u00a7aEnchanted Book | \u00a79Quick Bite V | \u00a7aQuick Bite V",
        "\u00a7fEnchanted Book | \u00a7d\u00a7lBank III | \u00a7d\u00a7lBank III"
    })
    void formatsEnchantedBookNames(String genericBookName, String loreName, String expected) {
        Assertions.assertEquals(expected,
            RemoteNeuConversionIndexBuilder.formatEnchantedBookName(genericBookName, loreName));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "ENCHANTMENT_CHAMPION_10, Champion X",
        "ENCHANTMENT_WITHER_HUNTER_0, Wither Hunter 0",
        "ESSENCE_WITHER,",
        "ENCHANTMENT_COUNTER_STRIKE_X,"
    })
    void derivesOnlyValidEnchantmentDisplayNames(String productId, String expected) {
        Assertions.assertEquals(Optional.ofNullable(expected),
            RemoteNeuConversionIndexBuilder.deriveEnchantmentDisplayName(productId));
    }

    private static ConversionIndex indexWithNeuCommit(
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {
        return new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION, RemoteNeuConversionIndexBuilder.BUILDER_VERSION,
            "now", neuCommit, products);
    }
}
