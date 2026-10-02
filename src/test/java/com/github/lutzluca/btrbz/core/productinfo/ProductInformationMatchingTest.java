package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProductInformationMatchingTest {
    @Test
    void ignoresFormattingButPreservesTheProductName() {
        var product = new IndexedProduct("STOCK_OF_STONKS", "§5Stock of Stonks");
        var menuName = Component.literal("§d  Stock  of Stonks ").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC);

        Assertions.assertTrue(ProductInfoMatching.matchesName(menuName.getString(), product));
        Assertions.assertFalse(ProductInfoMatching.matchesName("Reopen: Stock of Stonks", product));
        Assertions.assertFalse(ProductInfoMatching.matchesName("stock of stonks", product));
        Assertions.assertFalse(ProductInfoMatching.matchesName("Stock of Stonks x2", product));
        Assertions.assertFalse(ProductInfoMatching.matchesName("Giant Killer VI",
            new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII")));
    }

    @Test
    void onlyExemptsTheGenericTitleOfAnIdentifiedBook() {
        var product = new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII");

        Assertions.assertTrue(ProductInfoMatching.matchesDisplayedName("§9Enchanted Book", product, true));
        Assertions.assertFalse(ProductInfoMatching.matchesDisplayedName("Enchanted Book", product, false));
        Assertions.assertFalse(ProductInfoMatching.matchesDisplayedName("Reopen: Giant Killer VII", product, true));
    }

    @Test
    void distinguishesDirectBazaarEntriesFromCategoryNavigation() {
        Assertions.assertTrue(ProductInfoMatching.isBazaarProductEntry(List.of(
            Component.literal("1 product"), Component.literal("§eClick to view product!"))));
        Assertions.assertTrue(ProductInfoMatching.isBazaarProductEntry(List.of(
            Component.literal("Rare commodity"), Component.literal("§eClick to view details!"))));
        Assertions.assertFalse(ProductInfoMatching.isBazaarProductEntry(List.of(
            Component.literal("2 products"), Component.literal("§eClick to view products!"))));
        Assertions.assertFalse(ProductInfoMatching.isBazaarProductEntry(List.of(Component.literal("1 product"))));
    }

    @Test
    void recognizesTheMenusThatProvideAlternativeProductEvidence() {
        Assertions.assertTrue(ProductInfoMatching.isRngMenu("(1/3) Experimentation Table RNG"));
        Assertions.assertTrue(ProductInfoMatching.isSuperpairsMenu("Superpairs (Metaphysical)"));
        Assertions.assertTrue(ProductInfoMatching.isAttributeMenu("(1/12) Attribute Menu"));
        Assertions.assertFalse(ProductInfoMatching.isRngMenu("Chest"));
        Assertions.assertFalse(ProductInfoMatching.isSuperpairsMenu("Experimentation Table"));
        Assertions.assertFalse(ProductInfoMatching.isAttributeMenu("Chest"));
    }

    @Test
    void usesTheSourceShardAcrossAttributeProgressStates() {
        var source = Component.literal("§7Source: §fChill Shard §8(C12)");
        for (var progress : List.of("Syphon 1 shard to unlock!", "Attribute Level: 4", "Attribute Level: 10 (MAX!)")) {
            Assertions.assertEquals("Chill Shard", ProductInfoMatching.attributeShardName(List.of(
                source, Component.literal(progress), Component.literal("Left-Click to open!"))).orElseThrow());
        }
        Assertions.assertEquals("Chill Shard", ProductInfoMatching.attributeShardName(List.of(source)).orElseThrow());
        Assertions.assertEquals("Chill Shard", ProductInfoMatching.attributeShardName(List.of(
            source, Component.literal("Source: Phanflare Shard (C7)"))).orElseThrow());
        Assertions
            .assertTrue(ProductInfoMatching.attributeShardName(List.of(Component.literal("Source: (C12)"))).isEmpty());
        Assertions.assertTrue(
            ProductInfoMatching.attributeShardName(List.of(Component.literal("Source: Chill Shard"))).isEmpty());
        Assertions.assertTrue(
            ProductInfoMatching.attributeShardName(List.of(Component.literal("Attribute Level: 4"))).isEmpty());
    }

    @Test
    void readsSuperpairsEnchantmentNameFromTheThirdLoreLine() {
        List<Component> lore = List.of(Component.literal("Rare Book!"), Component.empty(),
            Component.literal("§9Giant Killer VI"));
        var name = ProductInfoMatching.superpairsEnchantmentName(lore).orElseThrow();

        Assertions.assertEquals("Giant Killer VI", name);
        Assertions.assertTrue(ProductInfoMatching.matchesName(name,
            new IndexedProduct("ENCHANTMENT_GIANT_KILLER_6", "§9Giant Killer VI")));
        Assertions.assertFalse(ProductInfoMatching.matchesName(name,
            new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII")));
        Assertions.assertTrue(ProductInfoMatching.superpairsEnchantmentName(List.of(lore.getFirst())).isEmpty());
        Assertions.assertTrue(ProductInfoMatching.superpairsEnchantmentName(List.of(
            lore.getFirst(), Component.empty(), Component.empty())).isEmpty());
    }

    @Test
    void usesOneShardForAttributeCardsWithoutChangingOwnedShardQuantities() {
        var shard = ProductIdentity.fromIndex(new IndexedProduct("SHARD_CHILL", "§fChill Shard"));
        var book = ProductIdentity.fromIndex(new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII"));

        Assertions.assertEquals(1, ProductInformation.priceCount(4, shard, true));
        Assertions.assertEquals(1, ProductInformation.priceCount(64, shard, true));
        Assertions.assertEquals(64, ProductInformation.priceCount(64, shard, false));
        Assertions.assertEquals(1, ProductInformation.priceCount(7, book, false));
    }
}
