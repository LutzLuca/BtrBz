package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                new ConversionProductEntry("§aEnchanted Diamond", new ProductNameSource.Neu("ENCHANTED_DIAMOND"))
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
            products.put("ONE", new ConversionProductEntry("Duplicate", new ProductNameSource.Neu("ONE")));
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
            products.put("NEU_ITEM", new ConversionProductEntry("Neu Item", new ProductNameSource.Neu("NEU_ITEM")));
            products.put("DERIVED_ITEM", new ConversionProductEntry("Derived Item", new ProductNameSource.Derived()));

            var counts = new ConversionIndex(1, "now", null, products).sourceCounts();

            assertEquals(1, counts.neu());
            assertEquals(1, counts.derived());
        }
    }

    @Nested
    @DisplayName("json")
    class Json {

        @Test
        void roundTripsTypedSourceSchema() throws Exception {
            var index = new ConversionIndex(
                ConversionIndex.SCHEMA_VERSION,
                7,
                "now",
                null,
                java.util.Map.of(
                    "ENCHANTMENT_SHARPNESS_5",
                    new ConversionProductEntry("Sharpness V", new ProductNameSource.Neu("SHARPNESS;5"))
                )
            );

            var json = ConversionLoader.GSON.toJson(ConversionLoader.IndexSnapshot.fromIndex(index));
            var parsed = ConversionLoader.GSON.fromJson(json, ConversionLoader.IndexSnapshot.class).toIndex();
            var source = parsed.products().get("ENCHANTMENT_SHARPNESS_5").source();

            assertFalse(json.contains("\"strippedName\""));
            assertEquals(7, parsed.builderVersion());
            assertEquals("Sharpness V", parsed.product("ENCHANTMENT_SHARPNESS_5").orElseThrow().strippedName());
            var neu = assertInstanceOf(ProductNameSource.Neu.class, source);
            assertEquals("SHARPNESS;5", neu.neuId());
        }

        @Test
        void rejectsInvalidProductEntriesDuringJsonRead() {
            var json = """
                {
                  "schemaVersion": 1,
                  "builderVersion": 1,
                  "generatedAt": "now",
                  "products": {
                    "BAD": {
                      "formattedName": "\\u00a77",
                      "source": { "type": "derived" }
                    }
                  }
                }
                """;

            var error = assertThrows(
                RuntimeException.class,
                () -> ConversionLoader.GSON.fromJson(json, ConversionLoader.IndexSnapshot.class)
            );
            var cause = assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertEquals("formattedName must contain a visible name", cause.getMessage());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void rejectsNegativeBuilderVersion() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionIndex(
                    ConversionIndex.SCHEMA_VERSION,
                    -1,
                    "now",
                    null,
                    java.util.Map.of()
                )
            );
        }
    }

    private static ConversionIndex indexWith(String productId, ConversionProductEntry entry) {
        return new ConversionIndex(1, "now", null, java.util.Map.of(productId, entry));
    }
}
