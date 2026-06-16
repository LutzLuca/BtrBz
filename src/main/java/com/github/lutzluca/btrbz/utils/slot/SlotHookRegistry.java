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
        var view = ctx.view();
        
        for (SlotHook hook : HOOKS) {
            if (!hook.matches(view)) {
                continue;
            }

            var display = hook.createDisplayStack(ctx);
            if (display != null) {
                return display;
            }
        }

        return view.rawStack();
    }

    public static boolean handleClick(SlotClickContext ctx) {
        var view = ctx.view();

        for (SlotHook hook : HOOKS) {
            if (!hook.matches(view)) {
                continue;
            }

            if (hook.onClick(ctx) == SlotClickResult.Consume) {
                return true;
            }
        }

        return false;
    }
}