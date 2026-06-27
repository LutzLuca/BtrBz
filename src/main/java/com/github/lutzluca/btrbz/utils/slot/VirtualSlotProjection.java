package com.github.lutzluca.btrbz.utils.slot;

import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.compat.CatharsisSupport;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;

public final class VirtualSlotProjection {

    // Thread-local reentrancy guard for slot projection
    private static final ThreadLocal<Integer> SUPPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private VirtualSlotProjection() { }

    public static <T> T withProjectionSuppressed(Supplier<T> supplier) {
        int prevDepth = SUPPRESSION_DEPTH.get();
        SUPPRESSION_DEPTH.set(prevDepth + 1);

        try {
            return supplier.get();
        } finally {
            SUPPRESSION_DEPTH.set(prevDepth);
        }
    }

    public static ItemStack project(Slot slot, ItemStack raw, SlotView sharedView, SlotRenderContext sharedContext) {
        if (SUPPRESSION_DEPTH.get() > 0) {
            return raw;
        }

        int prevDepth = SUPPRESSION_DEPTH.get();
        SUPPRESSION_DEPTH.set(prevDepth + 1);

        try {
            var helper = ScreenInfoHelper.get();
            sharedView.update(helper.getCurrInfo(), helper.getPrevInfo(), slot, raw);
            var proj = SlotHookRegistry.getDisplayStack(sharedContext);
            return proj == raw ? raw : CatharsisSupport.disableCatharsisModifications(proj);
        } finally {
            SUPPRESSION_DEPTH.set(prevDepth);
        }
    }
}