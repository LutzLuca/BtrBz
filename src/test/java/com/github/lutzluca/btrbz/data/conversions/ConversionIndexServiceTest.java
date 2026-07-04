package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
