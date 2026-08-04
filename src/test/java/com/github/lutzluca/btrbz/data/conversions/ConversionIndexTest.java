package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.utils.Utils;
import com.mojang.serialization.Dynamic;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;
import net.azureaaron.legacyitemdfu.LegacyItemStackFixer;
import net.azureaaron.legacyitemdfu.TypeReferences;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

            var index = new ConversionIndex(ConversionIndex.SCHEMA_VERSION, "now", null, products);

            assertTrue(index.uniqueProductByName("Duplicate").isEmpty());
            assertTrue(index.hasAmbiguousName("duplicate"));
        }
    }

    @Nested
    @DisplayName("bundled seed")
    class BundledSeed {

        @Test
        void carriesAStackSourceForEveryBazaarProduct() throws Exception {
            var stream = ConversionIndexTest.class.getClassLoader()
                .getResourceAsStream("assets/btrbz/conversion-index.json");
            assertNotNull(stream);

            ConversionLoader.IndexSnapshot snapshot;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                snapshot = ConversionLoader.GSON.fromJson(reader, ConversionLoader.IndexSnapshot.class);
            }
            var index = snapshot.toIndex();
            var stackCount = index.products().values().stream()
                .filter(entry -> entry.itemStack() != null)
                .count();
            var stackSourceCount = index.products().values().stream()
                .filter(entry -> entry.itemStack() != null || entry.legacyItemStack() != null)
                .count();

            assertEquals(ConversionIndex.SCHEMA_VERSION, index.schemaVersion());
            assertEquals(RemoteNeuConversionIndexBuilder.BUILDER_VERSION, index.builderVersion());
            assertTrue(index.size() >= 1_900);
            assertTrue(stackCount >= 1_300);
            assertEquals(index.size(), stackSourceCount);
            var enchantedDiamond = index.products().get("ENCHANTED_DIAMOND");
            assertNotNull(enchantedDiamond);
            assertNotNull(enchantedDiamond.legacyItemStack());
            assertEquals("minecraft:diamond", enchantedDiamond.legacyItemStack().itemId());
            var fixedDiamond = assertDoesNotThrow(() -> fixLegacy(enchantedDiamond.legacyItemStack()));
            assertTrue(fixedDiamond.getValue().toString().contains("minecraft:diamond"));

            var warpedStone = index.products().get("AOTE_STONE");
            assertNotNull(warpedStone);
            assertNotNull(warpedStone.legacyItemStack());
            var fixedWarpedStone = assertDoesNotThrow(() -> fixLegacy(warpedStone.legacyItemStack()));
            assertTrue(fixedWarpedStone.getValue().toString().contains("minecraft:player_head"));
            assertTrue(fixedWarpedStone.getValue().toString().contains("minecraft:profile"));
            index.products().forEach((productId, entry) -> {
                if (entry.itemStack() != null) {
                    assertTrue(
                        entry.itemStack().dataVersion()
                            <= RemoteNeuConversionIndexBuilder.BASELINE_ITEM_DATA_VERSION,
                        productId
                    );
                    assertDoesNotThrow(
                        () -> TagParser.parseCompoundFully(entry.itemStack().stackSnbt()),
                        productId
                    );
                }
                if (entry.legacyItemStack() != null) {
                    assertDoesNotThrow(
                        () -> LegacyNbtParser.parse(entry.legacyItemStack().nbtTag()),
                        productId
                    );
                }
            });
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

            var counts = new ConversionIndex(
                ConversionIndex.SCHEMA_VERSION,
                "now",
                null,
                products
            ).sourceCounts();

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
        void roundTripsMissingProductMarkers() throws Exception {
            var index = new ConversionIndex(
                ConversionIndex.SCHEMA_VERSION,
                7,
                "now",
                "abc",
                java.util.Map.of(
                    "KNOWN",
                    new ConversionProductEntry("Known", new ProductNameSource.Neu("KNOWN"))
                ),
                Set.of("MISSING")
            );

            var json = ConversionLoader.GSON.toJson(ConversionLoader.IndexSnapshot.fromIndex(index));
            var parsed = ConversionLoader.GSON.fromJson(json, ConversionLoader.IndexSnapshot.class).toIndex();

            assertFalse(parsed.isComplete());
            assertEquals(Set.of("MISSING"), parsed.missingProductIds());
        }

        @Test
        void treatsSnapshotsWithoutMissingProductMarkersAsComplete() throws Exception {
            var json = """
                {
                  "schemaVersion": 2,
                  "builderVersion": 1,
                  "generatedAt": "now",
                  "products": {
                    "KNOWN": {
                      "formattedName": "Known",
                      "source": { "type": "neu", "neuId": "KNOWN" }
                    }
                  }
                }
                """;

            var parsed = ConversionLoader.GSON.fromJson(json, ConversionLoader.IndexSnapshot.class).toIndex();

            assertTrue(parsed.isComplete());
        }

        @Test
        void roundTripsModernProductStackData() throws Exception {
            var stackData = new ProductStackData(
                4671,
                "{count:1,id:\"minecraft:diamond\",components:{\"minecraft:enchantment_glint_override\":1b}}"
            );
            var index = indexWith(
                "ENCHANTED_DIAMOND",
                new ConversionProductEntry(
                    "Â§aEnchanted Diamond",
                    new ProductNameSource.Neu("ENCHANTED_DIAMOND"),
                    stackData
                )
            );

            var json = ConversionLoader.GSON.toJson(ConversionLoader.IndexSnapshot.fromIndex(index));
            var parsed = ConversionLoader.GSON.fromJson(json, ConversionLoader.IndexSnapshot.class).toIndex();

            assertEquals(stackData, parsed.productStackData("ENCHANTED_DIAMOND").orElseThrow());
        }

        @Test
        void roundTripsLegacyProductStackData() throws Exception {
            var stackData = new LegacyProductStackData(
                "minecraft:diamond",
                0,
                "{ExtraAttributes:{id:\"ENCHANTED_DIAMOND\"},display:{Lore:[0:\"Collection Item\"]}}"
            );
            var index = indexWith(
                "ENCHANTED_DIAMOND",
                new ConversionProductEntry(
                    "Â§aEnchanted Diamond",
                    new ProductNameSource.Neu("ENCHANTED_DIAMOND"),
                    null,
                    stackData
                )
            );

            var json = ConversionLoader.GSON.toJson(ConversionLoader.IndexSnapshot.fromIndex(index));
            var parsed = ConversionLoader.GSON.fromJson(json, ConversionLoader.IndexSnapshot.class).toIndex();

            assertEquals(
                stackData,
                parsed.legacyProductStackData("ENCHANTED_DIAMOND").orElseThrow()
            );
        }

        @Test
        void rejectsInvalidProductEntriesDuringJsonRead() {
            var json = """
                {
                  "schemaVersion": 2,
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

        @Test
        void rejectsBlankProductStackData() {
            assertThrows(IllegalArgumentException.class, () -> new ProductStackData(4671, " "));
        }

        @Test
        void convertsLegacyNeuDataThroughTheBundledDataFixer() {
            var fixed = assertDoesNotThrow(() -> fixLegacy(new LegacyProductStackData(
                "minecraft:diamond",
                0,
                "{ExtraAttributes:{id:\"ENCHANTED_DIAMOND\"},"
                    + "display:{Lore:[0:\"Collection Item\"],Name:\"Â§aEnchanted Diamond\"}}"
            )));

            var serialized = fixed.getValue().toString();
            assertTrue(serialized.contains("minecraft:diamond"));
            assertTrue(serialized.contains("minecraft:custom_data"));
        }

        @Test
        void normalizesTheSkyBlockDataNestedByTheLegacyFixer() {
            var fixed = assertDoesNotThrow(() -> fixLegacy(new LegacyProductStackData(
                "minecraft:diamond",
                0,
                "{ExtraAttributes:{id:\"ENCHANTED_DIAMOND\"},"
                    + "display:{Lore:[0:\"§7Collection Item\"],Name:\"§aEnchanted Diamond\"}}"
            )));
            var item = assertInstanceOf(CompoundTag.class, fixed.getValue());
            var customData = item
                .getCompoundOrEmpty("components")
                .getCompoundOrEmpty("minecraft:custom_data");

            var normalized = LegacyStackNormalizer.extraAttributes(customData);

            assertEquals("ENCHANTED_DIAMOND", normalized.getString("id").orElseThrow());
            assertFalse(normalized.contains("ExtraAttributes"));
        }

        @Test
        void recoversTheNeuItemModelNestedByTheLegacyFixer() {
            var fixed = assertDoesNotThrow(() -> fixLegacy(new LegacyProductStackData(
                "minecraft:paper",
                0,
                "{ExtraAttributes:{id:\"FINE_TOPAZ_GEM\"},"
                    + "ItemModel:\"hypixel_skyblock:item/collections/gemstone/topaz/fine_topaz_gem\"}"
            )));
            var item = assertInstanceOf(CompoundTag.class, fixed.getValue());
            var customData = item
                .getCompoundOrEmpty("components")
                .getCompoundOrEmpty("minecraft:custom_data");

            assertEquals(
                Identifier.parse("hypixel_skyblock:item/collections/gemstone/topaz/fine_topaz_gem"),
                LegacyStackNormalizer.itemModel(customData).orElseThrow()
            );
        }

        @Test
        void ignoresMissingOrMalformedNeuItemModels() {
            var customData = new CompoundTag();

            assertTrue(LegacyStackNormalizer.itemModel(customData).isEmpty());

            customData.putString("ItemModel", "not a valid identifier");

            assertTrue(LegacyStackNormalizer.itemModel(customData).isEmpty());
        }

        @Test
        void restoresLegacyFormattingLostByTheDataFixer() {
            var normalized = LegacyStackNormalizer.formattedText(Component.literal("§a§lEnchanted Diamond"));

            assertEquals("Enchanted Diamond", normalized.getString());
            assertEquals("§a§lEnchanted Diamond", Utils.legacyFormattedText(normalized));
        }
    }

    private static ConversionIndex indexWith(String productId, ConversionProductEntry entry) {
        return new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION,
            "now",
            null,
            java.util.Map.of(productId, entry)
        );
    }

    private static Dynamic<Tag> fixLegacy(LegacyProductStackData stackData) throws Exception {
        var item = new CompoundTag();
        item.put("tag", LegacyNbtParser.parse(stackData.nbtTag()));
        item.putString("id", stackData.itemId());
        item.putShort("Damage", (short) stackData.damage());
        item.putInt("Count", 1);
        return LegacyItemStackFixer.getFixer().update(
            TypeReferences.LEGACY_ITEM_STACK,
            new Dynamic<Tag>(NbtOps.INSTANCE, item),
            LegacyItemStackFixer.getFirstVersion(),
            LegacyItemStackFixer.getLatestVersion()
        );
    }
}
