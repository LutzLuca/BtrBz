package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RemoteConversionIndexBuilderTest {

    @Nested
    @DisplayName("NEU entry reuse")
    class NeuEntryReuse {

        @Test
        void reusesWhenCommitMatchesAndNonHypixelProductsAreCovered() {
            var current = indexWithNeuCommit(
                "abc",
                Map.of("NEU_PRODUCT", new ConversionProductEntry(
                    "Neu Product",
                    new ProductNameSource.Neu("NEU_PRODUCT")
                ))
            );

            var reusable = RemoteConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "abc",
                Set.of("NEU_PRODUCT", "HYPIXEL_PRODUCT"),
                Map.of("HYPIXEL_PRODUCT", "Hypixel Product")
            );

            assertTrue(reusable);
        }

        @Test
        void fetchesWhenActiveNonHypixelProductIsNewDespiteMatchingCommit() {
            var current = indexWithNeuCommit(
                "abc",
                Map.of("NEU_PRODUCT", new ConversionProductEntry(
                    "Neu Product",
                    new ProductNameSource.Neu("NEU_PRODUCT")
                ))
            );

            var reusable = RemoteConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "abc",
                Set.of("NEU_PRODUCT", "NEW_NEU_PRODUCT"),
                Map.of()
            );

            assertFalse(reusable);
        }

        @Test
        void fetchesWhenCommitChanged() {
            var current = indexWithNeuCommit(
                "old",
                Map.of("NEU_PRODUCT", new ConversionProductEntry(
                    "Neu Product",
                    new ProductNameSource.Neu("NEU_PRODUCT")
                ))
            );

            var reusable = RemoteConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "new",
                Set.of("NEU_PRODUCT"),
                Map.of()
            );

            assertFalse(reusable);
        }
    }

    private static ConversionIndex indexWithNeuCommit(
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {
        return new ConversionIndex(ConversionIndex.SCHEMA_VERSION, "now", neuCommit, products);
    }
}
