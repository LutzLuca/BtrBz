package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface SlotHook {

    boolean matches(SlotView view);

    default @Nullable ItemStack createDisplayStack(SlotRenderContext ctx) {
        return null;
    }

    default SlotClickResult onClick(SlotClickContext ctx) {
        return SlotClickResult.Pass;
    }
}