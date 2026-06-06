package com.github.lutzluca.btrbz.utils.slot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class SlotHookRegistry {

    private static final List<SlotHook> HOOKS = new ArrayList<>();

    private SlotHookRegistry() { }

    public static void register(SlotHook hook) {
        HOOKS.add(hook);
    }

    public static ItemStack getDisplayStack(SlotRenderContext context) {
        for (SlotHook hook : HOOKS) {
            if (!hook.matches(context.view())) {
                continue;
            }

            var displayStack = hook.createDisplayStack(context);
            if (displayStack != null) {
                return displayStack;
            }
        }

        return context.view().rawStack();
    }

    public static boolean handleClick(SlotClickContext context) {
        for (SlotHook hook : HOOKS) {
            if (!hook.matches(context.slot())) {
                continue;
            }

            if (hook.onClick(context) == SlotClickResult.Consume) {
                return true;
            }
        }

        return false;
    }
}