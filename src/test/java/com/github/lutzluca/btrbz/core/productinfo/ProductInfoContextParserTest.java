package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProductInfoContextParserTest {

    @Test
    void usesTheSourceShardAcrossAttributeProgressStates() {
        var source = Component.literal("§7Source: §fChill Shard §8(C12)");
        for (var progress : List.of("Syphon 1 shard to unlock!", "Attribute Level: 4", "Attribute Level: 10 (MAX!)")) {
            Assertions.assertEquals("Chill Shard", ProductInfoContextParser.attributeShardName(List.of(
                source, Component.literal(progress), Component.literal("Left-Click to open!"))).orElseThrow());
        }
        Assertions.assertEquals("Chill Shard",
            ProductInfoContextParser.attributeShardName(List.of(source)).orElseThrow());
        Assertions.assertEquals("Chill Shard", ProductInfoContextParser.attributeShardName(List.of(
            source, Component.literal("Source: Phanflare Shard (C7)"))).orElseThrow());
        Assertions
            .assertTrue(
                ProductInfoContextParser.attributeShardName(List.of(Component.literal("Source: (C12)"))).isEmpty());
        Assertions.assertTrue(
            ProductInfoContextParser.attributeShardName(List.of(Component.literal("Source: Chill Shard"))).isEmpty());
        Assertions.assertTrue(
            ProductInfoContextParser.attributeShardName(List.of(Component.literal("Attribute Level: 4"))).isEmpty());
    }

    @Test
    void readsSuperpairsEnchantmentNameFromTheThirdLoreLine() {
        List<Component> lore = List.of(Component.literal("Rare Book!"), Component.empty(),
            Component.literal("§9Giant Killer VI"));
        var name = ProductInfoContextParser.superpairsEnchantmentName(lore).orElseThrow();

        Assertions.assertEquals("Giant Killer VI", name);
        Assertions.assertTrue(ProductInfoMatching.matchesName(name,
            new IndexedProduct("ENCHANTMENT_GIANT_KILLER_6", "§9Giant Killer VI")));
        Assertions.assertFalse(ProductInfoMatching.matchesName(name,
            new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII")));
        Assertions.assertTrue(ProductInfoContextParser.superpairsEnchantmentName(List.of(lore.getFirst())).isEmpty());
        Assertions.assertTrue(ProductInfoContextParser.superpairsEnchantmentName(List.of(
            lore.getFirst(), Component.empty(), Component.empty())).isEmpty());
    }

    @Test
    void separatesTheFullStashQuantityFromTheProductName() {
        var entry = ProductInfoContextParser.stashEntry("§fDiamond §7x15,904");

        Assertions.assertEquals(15_904, entry.count().orElseThrow());
        Assertions
            .assertTrue(ProductInfoMatching.matchesName(entry.productName(), new IndexedProduct("DIAMOND", "Diamond")));

        var book = ProductInfoContextParser.stashEntry("§9Enchanted Book");
        Assertions.assertEquals("Enchanted Book", book.productName());
        Assertions.assertFalse(book.hasQuantitySuffix());
        Assertions.assertTrue(book.count().isEmpty());

        var invalid = ProductInfoContextParser.stashEntry("Diamond x???");
        Assertions.assertEquals("Diamond", invalid.productName());
        Assertions.assertTrue(invalid.hasQuantitySuffix());
        Assertions.assertTrue(invalid.count().isEmpty());
    }

    @Test
    void readsOwnedShardsInsteadOfSyphonOrAttributeProgress() {
        var examples = Map.of("§7Owned: §b2 Shards", 2, "Owned: 1 Shard", 1, "Owned: 12,345 Shards", 12_345);
        for (var example : examples.entrySet()) {
            var count = ProductInfoContextParser.ownedShards(List.of(
                Component.literal(example.getKey()), Component.literal("Syphon 1 more to level up!"),
                Component.literal("Attribute Level: 4")));
            Assertions.assertEquals(example.getValue(), count.orElseThrow());
        }
        Assertions.assertTrue(ProductInfoContextParser.ownedShards(List.of(
            Component.literal("Syphon 1 more to level up!"))).isEmpty());
        Assertions.assertTrue(ProductInfoContextParser.ownedShards(List.of(
            Component.literal("Owned: ??? Shards"))).isEmpty());
    }

    @Test
    void readsAvailableCompostInsteadOfTheProductionTimer() {
        var count = ProductInfoContextParser.availableCompost(List.of(
            Component.literal("§7Compost Available: §a2"), Component.literal("Next Compost: 00:04:30")));

        Assertions.assertEquals(2, count.orElseThrow());
        Assertions.assertEquals(0, ProductInfoContextParser.availableCompost(List.of(
            Component.literal("Compost Available: 0"))).orElseThrow());
        Assertions.assertTrue(ProductInfoContextParser.availableCompost(List.of(
            Component.literal("Next Compost: 00:04:30"))).isEmpty());
    }

    @Test
    void usesTheGemstoneTierAmountInsteadOfTheSharedStoredCount() {
        var quantity = ProductInfoContextParser.sackQuantity(List.of(
            Component.literal("§7Gemstones"), Component.empty(),
            Component.literal(" §7Amount: §a125"),
            Component.literal("§7Stored: §648,555§7/2.6M")));
        Assertions.assertEquals(125, quantity.orElseThrow());

        var emptyTier = ProductInfoContextParser.sackQuantity(List.of(
            Component.literal("Gemstones"), Component.literal("Stored: 48,555/2.6M"),
            Component.literal("Amount: 0")));
        Assertions.assertEquals(0, emptyTier.orElseThrow());
    }

    @Test
    void usesTheExactStoredCountInOrdinarySacks() {
        var quantity = ProductInfoContextParser.sackQuantity(List.of(
            Component.literal("Enchanted Agronomy Sack"),
            Component.literal("§7Stored: §61,234,567§7/2.6M")));
        Assertions.assertEquals(1_234_567, quantity.orElseThrow());

        var emptySack = ProductInfoContextParser.sackQuantity(List.of(
            Component.literal("Mining Sack"), Component.literal("Stored: 0/20k")));
        Assertions.assertEquals(0, emptySack.orElseThrow());
    }

    @Test
    void neverUsesTheSharedStoredCountWhenTheGemstoneTierAmountIsUnavailable() {
        for (var amount : List.of("", "Amount: ???", "Amount: -1")) {
            var quantity = ProductInfoContextParser.sackQuantity(List.of(
                Component.literal("Gemstones"), Component.literal("Stored: 48,555/2.6M"),
                Component.literal(amount)));
            Assertions.assertTrue(quantity.isEmpty());
        }
    }
}
