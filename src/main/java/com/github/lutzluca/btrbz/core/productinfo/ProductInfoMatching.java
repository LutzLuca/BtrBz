package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class ProductInfoMatching {

    private ProductInfoMatching() {}

    static boolean matchesName(String name, IndexedProduct product) {
        return Utils.cleanDisplayName(name).equals(product.strippedName());
    }

    static boolean matchesDisplayedName(String name, IndexedProduct product, boolean singleEnchantmentBook) {
        return singleEnchantmentBook && "Enchanted Book".equals(Utils.cleanDisplayName(name))
            || matchesName(name, product);
    }

    static boolean isOrderStack(ItemStack stack) {
        var name = Utils.cleanDisplayName(stack.getHoverName().getString());
        return name.startsWith("BUY ") || name.startsWith("SELL ");
    }

    static boolean isSuperpairsMenu(String title) {
        return title.startsWith("Superpairs (") && title.endsWith(")");
    }

    static boolean isRngMenu(String title) {
        return title.contains("Experimentation Table RNG");
    }

    static boolean isAttributeMenu(String title) {
        return title.endsWith("Attribute Menu");
    }

    static boolean isBazaarProductEntry(List<Component> lore) {
        for (var line : lore) {
            var text = Utils.cleanDisplayName(line.getString());
            if ("Click to view product!".equals(text) || "Click to view details!".equals(text)) {
                return true;
            }
        }
        return false;
    }

    static Optional<String> attributeShardName(List<Component> lore) {
        // Source: <shard name> (<shard identifier>)
        for (var line : lore) {
            var text = Utils.cleanDisplayName(line.getString());
            if (text.startsWith("Source:")) {
                var identifierStart = text.lastIndexOf(" (");
                if (identifierStart < "Source:".length()) {
                    return Optional.empty();
                }

                var name = text.substring("Source:".length(), identifierStart).trim();
                return name.isEmpty() ? Optional.empty() : Optional.of(name);
            }
        }

        return Optional.empty();
    }

    static Optional<String> superpairsEnchantmentName(List<Component> lore) {
        if (lore.size() < 3) {
            return Optional.empty();
        }
        var name = Utils.cleanDisplayName(lore.get(2).getString());
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }
}
