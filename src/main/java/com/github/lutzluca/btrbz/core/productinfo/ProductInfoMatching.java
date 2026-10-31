package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

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

    static boolean isSackStack(ItemStack stack) {
        var lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
        if (lore.isEmpty()) {
            return false;
        }
        var header = Utils.cleanDisplayName(lore.getFirst().getString());
        return header.endsWith("Sack") || header.equals("Gemstones");
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

    static boolean isHuntingBoxMenu(String title) {
        return title.equals("Hunting Box") || title.startsWith("(") && title.endsWith(") Hunting Box");
    }

    static Optional<String> essenceShopType(String displayedName) {
        var name = Utils.cleanDisplayName(displayedName);
        var suffix = " Essence Shop";
        return name.endsWith(suffix)
            ? Optional.of(name.substring(0, name.length() - suffix.length())) : Optional.empty();
    }

    static Optional<String> essenceType(String displayedName) {
        var name = Utils.cleanDisplayName(displayedName);
        var suffix = " Essence";
        return name.endsWith(suffix)
            ? Optional.of(name.substring(0, name.length() - suffix.length())) : Optional.empty();
    }

    static boolean allowsMenuProductResolution(List<Component> lore, @Nullable BazaarMenuType menuType) {
        var productEntry = false;
        for (var line : lore) {
            var text = Utils.cleanDisplayName(line.getString());
            if ("Click to view products!".equals(text)) {
                return false;
            }
            if ("Click to view product!".equals(text) || "Click to view details!".equals(text)) {
                productEntry = true;
            }
        }
        return menuType != BazaarMenuType.Main || productEntry;
    }

}
