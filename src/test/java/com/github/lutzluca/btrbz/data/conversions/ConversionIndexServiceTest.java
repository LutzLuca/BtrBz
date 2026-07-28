package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversionIndexServiceTest {

    @Nested
    @DisplayName("apply boundary")
    class ApplyBoundary {

        @Test
        void appliesDerivedEntriesWithoutRewritingThem() {
            var rawIndex = new ConversionIndex(
                ConversionIndex.SCHEMA_VERSION,
                "now",
                null,
                Map.of(
                    "ENCHANTMENT_HECATOMB_10", new ConversionProductEntry(
                        "Custom Fallback",
                        new ProductNameSource.Derived()
                    )
                )
            );

            var service = new ConversionIndexService(rawIndex);

            var index = service.currentIndex();
            assertEquals("Custom Fallback", index.product("ENCHANTMENT_HECATOMB_10").orElseThrow().strippedName());
        }
    }

    @Nested
    @DisplayName("product stack compatibility")
    class ProductStackCompatibility {

        @Test
        void acceptsStacksFromTheCurrentOrAnOlderDataVersion() {
            var stackData = new ProductStackData(4671, "{count:1,id:\"minecraft:diamond\"}");

            assertTrue(ProductStackResolver.isCompatible(stackData, 4671));
            assertTrue(ProductStackResolver.isCompatible(stackData, 4790));
        }

        @Test
        void rejectsStacksFromANewerDataVersion() {
            var stackData = new ProductStackData(4790, "{count:1,id:\"minecraft:diamond\"}");

            assertFalse(ProductStackResolver.isCompatible(stackData, 4786));
        }
    }
}
