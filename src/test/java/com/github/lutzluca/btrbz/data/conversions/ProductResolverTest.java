package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductResolverTest {

    @Nested
    @DisplayName("raw id resolution")
    class RawIdResolution {

        @Test
        void keepsRealProductIdAuthoritative() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("REDSTONE", "Growth 6-7");

            assertEquals("REDSTONE", product.resolvedProduct().orElseThrow().productId());
        }

        @Test
        void resolvesGenericBookIdFromDisplayName() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "SELL Quick Bite V");

            assertEquals("ENCHANTMENT_QUICK_BITE_5", product.resolvedProduct().orElseThrow().productId());
        }

        @Test
        void resolvesNeuStyleBookId() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("COUNTER_STRIKE;5", "Enchanted Book");

            assertEquals("ENCHANTMENT_COUNTER_STRIKE_5", product.resolvedProduct().orElseThrow().productId());
        }

        @Test
        void rejectsAmbiguousGroupTitle() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "Growth 6-7");

            assertTrue(product.resolvedProduct().isEmpty());
        }
    }

    private static ConversionIndexService serviceWithProducts() {
        var products = new LinkedHashMap<String, ConversionProductEntry>();
        products.put("REDSTONE", new ConversionProductEntry("Redstone", new ProductNameSource.HypixelItem()));
        products.put(
            "ENCHANTMENT_QUICK_BITE_5",
            new ConversionProductEntry("Quick Bite V", new ProductNameSource.Neu("ENCHANTMENT_QUICK_BITE_5"))
        );
        products.put(
            "ENCHANTMENT_COUNTER_STRIKE_5",
            new ConversionProductEntry("Counter-Strike V", new ProductNameSource.Neu("ENCHANTMENT_COUNTER_STRIKE_5"))
        );
        return new ConversionIndexService(new ConversionIndex(1, "now", null, products));
    }
}
