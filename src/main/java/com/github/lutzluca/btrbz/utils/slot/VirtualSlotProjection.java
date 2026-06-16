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

    public static boolean isProjectionSuppressed() {
        return SUPPRESSION_DEPTH.get() > 0;
    }

    public static <T> T withProjectionSuppressed(Supplier<T> supplier) {
        int prevDepth = SUPPRESSION_DEPTH.get();
        SUPPRESSION_DEPTH.set(prevDepth + 1);

        try {
            return supplier.get();
        } finally {
            if (prevDepth == 0) {
                SUPPRESSION_DEPTH.remove();
            } else {
                SUPPRESSION_DEPTH.set(prevDepth);
            }
        }
    }

    public static ItemStack project(Slot slot, ItemStack raw) {
        if (VirtualSlotProjection.isProjectionSuppressed()) {
            return raw;
        }

        var proj = VirtualSlotProjection.withProjectionSuppressed(() -> {
            var view = VirtualSlotProjection.createSlotView(slot, raw);
            return SlotHookRegistry.getDisplayStack(new SlotRenderContext(view));
        });

        return proj == raw ? raw : CatharsisSupport.disableCatharsisModifications(proj);
    }

    public static @NotNull SlotView createSlotView(Slot slot, ItemStack rawStack) {
        var helper = ScreenInfoHelper.get();
        
        return new SlotView(
            helper.getCurrInfo(),
            helper.getPrevInfo(),
            slot,
            rawStack
        );
    }
}
