package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Slf4j
public class ItemOverrideManager {

    private static final List<ItemOverrideRule> RULES = new ArrayList<>();


    public static void register(ItemOverrideRule rule) {
        RULES.add(rule);
    }

    public static ItemStack apply(ScreenInfo info, Slot slot, ItemStack original) {
        for (ItemOverrideRule rule : RULES) {
            var replacement = Try.of(() -> rule.replace(info, slot, original))
                .onFailure(err -> log.error(
                    "Item override rule '{}' failed for screen '{}' and slot '{}'",
                    rule.getClass().getName(),
                    info.containerName().orElse("<no-container>"),
                    slot == null ? "<null>" : slot.getContainerSlot(),
                    err
                ))
                .getOrElse(Optional.empty());

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
