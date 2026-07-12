package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductResolverTest {

    @Nested
    @DisplayName("essence id parsing")
    class EssenceIdParsing {

        @Test
        void derivesEssenceProductIdFromDisplayName() {
            assertEquals(
                "ESSENCE_WITHER",
                ProductResolver.essenceProductId("Wither Essence").orElseThrow()
            );
        }

        @Test
        void rejectsNonEssenceDisplayName() {
            assertTrue(ProductResolver.essenceProductId("Suspicious Scrap").isEmpty());
        }
    }

    @Nested
    @DisplayName("shard resolution")
    class ShardResolution {

        @Test
        void acceptsIdlessMenuShard() {
            assertTrue(ProductResolver.isPossibleShardStack(null, "Lapis Zombie Shard"));
        }

        @Test
        void rejectsIdlessNonShard() {
            assertFalse(ProductResolver.isPossibleShardStack(null, "Suspicious Scrap"));
        }

        @Test
        void resolvesShardByIndexedDisplayName() {
            var resolver = new ProductResolver(serviceWithProducts());
            var product = resolver.resolveShardIdentity("Phanpyre Shard", null, "ATTRIBUTE_SHARD");

            assertEquals("SHARD_PHANPYRE", product.bazaarProductId().orElseThrow());
            assertEquals("Phanpyre Shard", product.visualName());
        }

        @Test
        void keepsUnknownShardNameOnly() {
            var resolver = new ProductResolver(serviceWithProducts());
            var product = resolver.resolveShardIdentity("Unknown Shard", null, "ATTRIBUTE_SHARD");

            assertTrue(product.bazaarProductId().isEmpty());
            assertEquals("Unknown Shard", product.visualName());
        }
    }

    @Nested
    @DisplayName("raw id resolution")
    class RawIdResolution {

        @Test
        void keepsRealProductIdAuthoritative() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("REDSTONE", "Growth 6-7");

            assertEquals("REDSTONE", product.bazaarProductId().orElseThrow());
            assertEquals("Redstone", product.visualName());
        }

        @Test
        void unknownNonBookRawIdReturnsRuntimeIdentityWithBazaarProductId() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("TROUBLED_BUBBLE", "Troubled Bubble");

            assertEquals("TROUBLED_BUBBLE", product.bazaarProductId().orElseThrow());
            assertEquals("Troubled Bubble", product.visualName());
        }

        @Test
        void resolvesGenericBookIdFromDisplayName() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "SELL Quick Bite V");

            assertEquals("ENCHANTMENT_QUICK_BITE_5", product.bazaarProductId().orElseThrow());
            assertEquals("Quick Bite V", product.visualName());
        }

        @Test
        void displayNameDerivedBookIdMissingFromIndexReturnsNameOnlyIdentity() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "Habanero Tactics V");

            assertTrue(product.bazaarProductId().isEmpty());
            assertEquals("Habanero Tactics V", product.visualName());
        }

        @Test
        void rejectsAmbiguousGroupTitle() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "Growth 6-7");

            assertTrue(product.bazaarProductId().isEmpty());
        }

        @Test
        void genericBookWithoutDerivedIdReturnsNameOnlyIdentity() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "Enchanted Book");

            assertTrue(product.bazaarProductId().isEmpty());
            assertEquals("Enchanted Book", product.visualName());
        }

        @Test
        void resolvesCanonicalDisplayNameBeforeParsedEnchantmentId() {
            var service = serviceWithProducts();
            var product = service.resolveProduct("ENCHANTED_BOOK", "Turbo-Cacti 5");

            assertEquals("ENCHANTMENT_TURBO_CACTUS_5", product.bazaarProductId().orElseThrow());
            assertEquals("Turbo-Cacti V", product.visualName());
        }
    }

    private static ConversionIndexService serviceWithProducts() {
        var products = new LinkedHashMap<String, ConversionProductEntry>();
        products.put("REDSTONE", new ConversionProductEntry("Redstone", new ProductNameSource.Neu("REDSTONE")));
        products.put(
            "ENCHANTMENT_QUICK_BITE_5",
            new ConversionProductEntry("Quick Bite V", new ProductNameSource.Neu("ENCHANTMENT_QUICK_BITE_5"))
        );
        products.put(
            "ENCHANTMENT_TURBO_CACTUS_5",
            new ConversionProductEntry("Turbo-Cacti V", new ProductNameSource.Neu("TURBO_CACTUS;5"))
        );
        products.put(
            "SHARD_PHANPYRE",
            new ConversionProductEntry(
                "Phanpyre Shard",
                new ProductNameSource.Neu("ATTRIBUTE_SHARD_NOCTURNAL_ANIMAL;1")
            )
        );
        return new ConversionIndexService(new ConversionIndex(1, "now", null, products));
    }
}
