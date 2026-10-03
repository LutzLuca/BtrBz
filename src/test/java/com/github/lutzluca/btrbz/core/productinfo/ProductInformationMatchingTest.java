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
    void matchesExactDisplayedNamesAndOnlyExemptsIdentifiedGenericBooks() {
        var stock = new IndexedProduct("STOCK_OF_STONKS", "§5Stock of Stonks");
        var book = new IndexedProduct("ENCHANTMENT_GIANT_KILLER_7", "§5Giant Killer VII");
        var menuName = Component.literal("§d  Stock  of Stonks ").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC);
        var cases = List.of(
            new NameCase("plain exact name", "Stock of Stonks", stock, false, true),
            new NameCase("formatted exact name", menuName.getString(), stock, false, true),
            new NameCase("reopen prefix", "Reopen: Stock of Stonks", stock, false, false),
            new NameCase("different case", "stock of stonks", stock, false, false),
            new NameCase("quantity suffix", "Stock of Stonks x2", stock, false, false),
            new NameCase("different enchantment level", "Giant Killer VI", book, false, false),
            new NameCase("identified generic book", "§9Enchanted Book", book, true, true),
            new NameCase("generic book without identification", "Enchanted Book", book, false, false),
            new NameCase("unrelated title despite identification", "Reopen: Giant Killer VII", book, true, false));

        for (var example : cases) {
            Assertions.assertEquals(example.expected(), ProductInfoMatching.matchesDisplayedName(
                example.displayedName(), example.product(), example.identifiedBook()), example.description());
        }
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

    private record NameCase(
        String description, String displayedName, IndexedProduct product, boolean identifiedBook,
        boolean expected
    ) {}
}
