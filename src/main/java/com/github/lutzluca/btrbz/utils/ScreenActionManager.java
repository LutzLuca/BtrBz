package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.inventory.Slot;

@Slf4j
public class ScreenActionManager {

    private static final List<ScreenClickRule> RULES = new ArrayList<>();

    public static void register(ScreenClickRule rule) {
        RULES.add(rule);
    }

    public static boolean handleClick(ScreenInfo info, Slot slot, int button) {

        if (slot == null) {
            return false;
        }

        for (ScreenClickRule rule : RULES) {
            var applies = Try.of(() -> rule.applies(info, slot, button))
                .onFailure(err -> log.error(
                    "Screen click rule '{}' failed while matching screen '{}' slot '{}' button '{}'",
                    rule.getClass().getName(),
                    info.containerName().orElse("<unknown>"),
                    slot.getContainerSlot(),
                    button,
                    err
                ))
                .getOrElse(false);

            if (!applies) {
                continue;
            }

            return Try.of(() -> rule.onClick(info, slot, button))
                .onFailure(err -> log.error(
                    "Screen click rule '{}' failed while handling screen '{}' slot '{}' button '{}'",
                    rule.getClass().getName(),
                    info.containerName().orElse("<unknown>"),
                    slot.getContainerSlot(),
                    button,
                    err
                ))
                .getOrElse(false);
        }

        return false;
    }

    public interface ScreenClickRule {

        boolean applies(ScreenInfo info, Slot slot, int button);

        boolean onClick(ScreenInfo info, Slot slot, int button);
    }
}
