package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversionIndexTest {

    @Nested
    @DisplayName("lookups")
    class Lookups {

        @Test
        void resolvesProductByIdAndUniqueName() {
            var index = indexWith(
                "ENCHANTED_DIAMOND",
                new ConversionProductEntry("Enchanted Diamond", new ProductNameSource.HypixelItem())
            );

            var byId = index.product("ENCHANTED_DIAMOND");
            var byName = index.uniqueProductByName("enchanted diamond");

            assertTrue(byId.isPresent());
            assertTrue(byName.isPresent());
            assertEquals("ENCHANTED_DIAMOND", byId.get().productId());
            assertEquals(byId, byName);
        }

        @Test
        void doesNotResolveAmbiguousNamesAsUnique() {
            var products = new LinkedHashMap<String, ConversionProductEntry>();
            products.put("ONE", new ConversionProductEntry("Duplicate", new ProductNameSource.HypixelItem()));
            products.put("TWO", new ConversionProductEntry("duplicate", new ProductNameSource.Neu("TWO")));

            var index = new ConversionIndex(1, "now", null, products);

            assertTrue(index.uniqueProductByName("Duplicate").isEmpty());
            assertTrue(index.hasAmbiguousName("duplicate"));
        }
    }

    @Nested
    @DisplayName("sources")
    class Sources {

        @Test
        void countsTypedSources() {
            var products = new LinkedHashMap<String, ConversionProductEntry>();
            products.put("ITEM", new ConversionProductEntry("Item", new ProductNameSource.HypixelItem()));
            products.put("NEU_ITEM", new ConversionProductEntry("Neu Item", new ProductNameSource.Neu("NEU_ITEM")));
            products.put("DERIVED_ITEM", new ConversionProductEntry("Derived Item", new ProductNameSource.Derived()));

            var counts = new ConversionIndex(1, "now", null, products).sourceCounts();

            assertEquals(1, counts.hypixelItem());
            assertEquals(1, counts.neu());
            assertEquals(1, counts.derived());
        }

        @Test
        void recomputesDerivedDisplayNames() {
            var index = indexWith(
                "ESSENCE_WITHER",
                new ConversionProductEntry("Old Name", new ProductNameSource.Derived())
            );

            var normalized = ConversionIndexNormalizer.normalizeDerivedEntries(index);

            assertEquals("Wither Essence", normalized.product("ESSENCE_WITHER").orElseThrow().displayName());
        }

        @Test
        void derivesBoosterCookieDisplayName() {
            var index = indexWith(
                "BAZAAR_COOKIE",
                new ConversionProductEntry("Old Name", new ProductNameSource.Derived())
            );

            var normalized = ConversionIndexNormalizer.normalizeDerivedEntries(index);

            assertEquals("Booster Cookie", normalized.product("BAZAAR_COOKIE").orElseThrow().displayName());
        }
    }

    @Nested
    @DisplayName("json")
    class Json {

        @Test
        void roundTripsTypedSourceSchema() throws Exception {
            var index = indexWith(
                "ENCHANTMENT_SHARPNESS_5",
                new ConversionProductEntry("Sharpness V", new ProductNameSource.Neu("SHARPNESS;5"))
            );

            var json = ConversionLoader.GSON.toJson(ConversionIndexSnapshot.fromIndex(index));
            var parsed = ConversionLoader.GSON.fromJson(json, ConversionIndexSnapshot.class).toIndex();
            var source = parsed.products().get("ENCHANTMENT_SHARPNESS_5").source();

            assertEquals("Sharpness V", parsed.product("ENCHANTMENT_SHARPNESS_5").orElseThrow().displayName());
            var neu = assertInstanceOf(ProductNameSource.Neu.class, source);
            assertEquals("SHARPNESS;5", neu.neuId());
        }
    }

    private static ConversionIndex indexWith(String productId, ConversionProductEntry entry) {
        return new ConversionIndex(1, "now", null, java.util.Map.of(productId, entry));
    }
}
