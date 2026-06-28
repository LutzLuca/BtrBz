package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Registration order is an implicit coupling. The first matching hook to supply a display stack
 * or handle a click wins. This is acceptable because (currently) hooks don't interfere or share slot targets,
 * so a priority system has no practical benefit. This is a known limitation of the system, where simplicity was chosen
 * over this constraint.
 * A priority mechanism could be added in the future if necessary.
 */

public interface SlotHook {

    boolean matches(SlotView view);

    default @Nullable ItemStack createDisplayStack(SlotRenderContext ctx) {
        return null;
    }

    default SlotClickResult onClick(SlotClickContext ctx) {
        return SlotClickResult.Pass;
    }
}