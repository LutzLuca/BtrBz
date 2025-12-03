package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemOverrideManager {

    private static final List<ItemOverrideRule> RULES = new ArrayList<>();


    public static void register(ItemOverrideRule rule) {
        RULES.add(rule);
    }

    public static ItemStack apply(ScreenInfo info, Slot slot, ItemStack original) {
        for (ItemOverrideRule rule : RULES) {
            var replacement = rule.replace(info, slot, original);
            if (replacement.isPresent()) {
                return replacement.get();
            }
        }

        return original;
    }

    public interface ItemOverrideRule {

        Optional<ItemStack> replace(ScreenInfo info, Slot slot, ItemStack original);
    }
}
