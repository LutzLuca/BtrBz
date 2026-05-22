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

    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    public static void register(ItemOverrideRule rule) {
        RULES.add(rule);
    }

    public static ItemStack apply(ScreenInfo info, Slot slot, ItemStack original) {
        if (APPLYING.get()) {
            return original;
        }

        try {
            APPLYING.set(true);

            for (ItemOverrideRule rule : RULES) {
                var replacement = Try.of(() -> rule.replace(info, slot, original))
                    .onFailure(err -> log.error(
                        "Item override rule '{}' failed for screen '{}' and slot '{}'",
                        rule.getClass().getName(),
                        info.containerName().orElse("<unknown>"),
                        slot == null ? "<null>" : slot.getContainerSlot(),
                        err
                    ))
                    .getOrElse(Optional.empty());

                if (replacement.isPresent()) {
                    return replacement.get();
                }
            }

            return original;
        } finally {
            APPLYING.set(false);
        }
    }

    public interface ItemOverrideRule {
        Optional<ItemStack> replace(ScreenInfo info, Slot slot, ItemStack original);
    }
}