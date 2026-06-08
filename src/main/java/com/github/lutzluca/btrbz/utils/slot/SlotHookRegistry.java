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

    public static ItemStack getDisplayStack(SlotRenderContext ctx) {
        for (SlotHook hook : HOOKS) {
            if (!hook.matches(ctx.view())) {
                continue;
            }

            var displayStack = hook.createDisplayStack(ctx);
            if (displayStack != null) {
                return displayStack;
            }
        }

        return ctx.view().rawStack();
    }

    public static boolean handleClick(SlotClickContext ctx) {
        for (SlotHook hook : HOOKS) {
            if (!hook.matches(ctx.view())) {
                continue;
            }

            if (hook.onClick(ctx) == SlotClickResult.Consume) {
                return true;
            }
        }

        return false;
    }
}