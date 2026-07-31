package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Applies the SkyBlock-specific cleanup which Minecraft's legacy data fixer cannot infer.
 * Adapted from Skyblocker's LGPL-3.0 LegacyItemStackFixer.
 */
final class LegacyStackNormalizer {

    private LegacyStackNormalizer() { }

    static ItemStack normalize(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            stack.set(DataComponents.CUSTOM_NAME, formattedText(stack.get(DataComponents.CUSTOM_NAME)));
        }

        if (stack.has(DataComponents.LORE)) {
            stack.set(DataComponents.LORE, new ItemLore(formattedLore(stack.get(DataComponents.LORE).lines())));
        }

        if (stack.has(DataComponents.CUSTOM_DATA)) {
            var customData = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            itemModel(customData).ifPresent(model -> stack.set(DataComponents.ITEM_MODEL, model));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(extraAttributes(customData)));
        }

        var tooltipDisplay = stack
            .getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
            .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
            .withHidden(DataComponents.ENCHANTMENTS, true);
        stack.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);
        return stack;
    }

    static Component formattedText(Component component) {
        return Utils.legacyFormattedComponent(component.getString());
    }

    static List<Component> formattedLore(List<Component> lore) {
        return lore.stream().map(LegacyStackNormalizer::formattedText).toList();
    }

    static CompoundTag extraAttributes(CompoundTag customData) {
        return customData.getCompoundOrEmpty("ExtraAttributes").copy();
    }

    static Optional<Identifier> itemModel(CompoundTag customData) {
        return customData
            .getString("ItemModel")
            .flatMap(value -> Optional.ofNullable(Identifier.tryParse(value)));
    }
}
