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
    void keepsOrdinaryBooksIndividualWithoutLimitingShardStacks() {
        var shard = ProductIdentity.fromIndex(new IndexedProduct("SHARD_CHILL", "§fChill Shard"));
        var book = ProductIdentity.fromIndex(new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII"));

        Assertions.assertEquals(1, ProductInfoQuantity.stack(7, book, false).count().orElseThrow());
        Assertions.assertEquals(1, ProductInfoQuantity.stack(7, ProductIdentity.fromName("Enchanted Book"), true)
            .count().orElseThrow());
        Assertions.assertEquals(64, ProductInfoQuantity.stack(64, shard, false).count().orElseThrow());
    }
}
