package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RemoteNeuConversionIndexBuilderTest {

    @Nested
    @DisplayName("NEU entry reuse")
    class NeuEntryReuse {

        @Test
        void reusesWhenCommitMatchesAndProductsAreCovered() {
            var current = indexWithNeuCommit(
                "abc",
                Map.of("NEU_PRODUCT", new ConversionProductEntry(
                    "Neu Product",
                    new ProductNameSource.Neu("NEU_PRODUCT")
                ))
            );

            var reusable = RemoteNeuConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "abc",
                Set.of("NEU_PRODUCT")
            );

            assertTrue(reusable);
        }

        @Test
        void fetchesWhenProductIsNewDespiteMatchingCommit() {
            var current = indexWithNeuCommit(
                "abc",
                Map.of("NEU_PRODUCT", new ConversionProductEntry(
                    "Neu Product",
                    new ProductNameSource.Neu("NEU_PRODUCT")
                ))
            );

            var reusable = RemoteNeuConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "abc",
                Set.of("NEU_PRODUCT", "NEW_NEU_PRODUCT")
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

            var reusable = RemoteNeuConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "new",
                Set.of("NEU_PRODUCT")
            );

            assertFalse(reusable);
        }

        @Test
        void fetchesWhenBuilderVersionChanged() {
            var current = new ConversionIndex(
                ConversionIndex.SCHEMA_VERSION,
                0,
                "now",
                "abc",
                Map.of("NEU_PRODUCT", new ConversionProductEntry(
                    "Neu Product",
                    new ProductNameSource.Neu("NEU_PRODUCT")
                ))
            );

            var reusable = RemoteNeuConversionIndexBuilder.shouldReuseNeuEntries(
                current,
                "abc",
                Set.of("NEU_PRODUCT")
            );

            assertFalse(reusable);
        }
    }

    @Nested
    @DisplayName("enchanted book formatting")
    class EnchantedBookFormatting {

        @Test
        void usesGenericBookRarityWhenLoreNameIsBlue() {
            assertEquals(
                "§fQuick Bite I",
                RemoteNeuConversionIndexBuilder.formatEnchantedBookName("§fEnchanted Book", "§9Quick Bite I")
            );
            assertEquals(
                "§aQuick Bite V",
                RemoteNeuConversionIndexBuilder.formatEnchantedBookName("§aEnchanted Book", "§9Quick Bite V")
            );
        }

        @Test
        void keepsSpecialLoreFormatting() {
            assertEquals(
                "§d§lBank III",
                RemoteNeuConversionIndexBuilder.formatEnchantedBookName("§fEnchanted Book", "§d§lBank III")
            );
        }
    }

    @Nested
    @DisplayName("derived enchantment fallback")
    class DerivedEnchantmentFallback {

        @Test
        void derivesPositiveEnchantmentLevelsFromProductId() {
            var entry = RemoteNeuConversionIndexBuilder
                .derivedEnchantmentEntry("ENCHANTMENT_CHAMPION_10")
                .orElseThrow();

            assertEquals("Champion X", entry.strippedName());
            assertTrue(entry.source() instanceof ProductNameSource.Derived);
        }

        @Test
        void keepsZeroLevelAsArabic() {
            var name = RemoteNeuConversionIndexBuilder
                .deriveEnchantmentDisplayName("ENCHANTMENT_WITHER_HUNTER_0")
                .orElseThrow();

            assertEquals("Wither Hunter 0", name);
        }

        @Test
        void rejectsNonEnchantmentFallback() {
            assertTrue(RemoteNeuConversionIndexBuilder.derivedEnchantmentEntry("ESSENCE_WITHER").isEmpty());
        }

        @Test
        void rejectsNonNumericEnchantmentLevel() {
            assertTrue(RemoteNeuConversionIndexBuilder
                .deriveEnchantmentDisplayName("ENCHANTMENT_COUNTER_STRIKE_X")
                .isEmpty());
        }
    }

    private static ConversionIndex indexWithNeuCommit(
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {
        return new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION,
            RemoteNeuConversionIndexBuilder.BUILDER_VERSION,
            "now",
            neuCommit,
            products
        );
    }
}
