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
        void normalizesDerivedEntriesWhenIndexIsApplied() {
            var rawIndex = new ConversionIndex(
                ConversionIndex.SCHEMA_VERSION,
                "now",
                null,
                Map.of(
                    "ESSENCE_WITHER", new ConversionProductEntry(
                        "Old Name",
                        new ProductNameSource.Derived()
                    ),
                    "BAZAAR_COOKIE", new ConversionProductEntry(
                        "Old Name",
                        new ProductNameSource.Derived()
                    )
                )
            );

            var service = new ConversionIndexService(rawIndex);

            var index = service.currentIndex();
            assertEquals("Wither Essence", index.product("ESSENCE_WITHER").orElseThrow().displayName());
            assertEquals("Booster Cookie", index.product("BAZAAR_COOKIE").orElseThrow().displayName());
        }
    }
}
