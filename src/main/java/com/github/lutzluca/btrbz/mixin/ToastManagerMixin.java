package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.BtrBzToast;
import com.github.lutzluca.btrbz.utils.BtrBzToastManager;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin implements BtrBzToastManager {

    @Shadow
    @Final
    private List<?> visibleToasts;

    @Shadow
    @Final
    private BitSet occupiedSlots;

    @Shadow
    @Final
    private Deque<Toast> queued;

    @Override
    public void btrbz$removeToasts() {
        this.queued.removeIf(toast -> toast instanceof BtrBzToast);
        this.visibleToasts.removeIf(instance -> {
            var accessor = (ToastInstanceAccessor) instance;
            if (!(accessor.btrbz$getToast() instanceof BtrBzToast)) {
                return false;
            }
            int first = accessor.btrbz$getFirstSlotIndex();
            this.occupiedSlots.clear(first, first + accessor.btrbz$getOccupiedSlotCount());
            return true;
        });
    }

    // The alert's sound is delivered once through SoundUtil. Leave vanilla toast sounds alone.
    @Redirect(
        method = "lambda$update$0",
        at = @At(value = "INVOKE", target = "Lorg/apache/commons/lang3/mutable/MutableBoolean;isFalse()Z"))
    private boolean btrbz$shouldPlayVisibilitySound(
        MutableBoolean played,
        MutableBoolean ignored,
        @Coerce Object instance
    ) {
        return played.isFalse() && !(((ToastInstanceAccessor) instance).btrbz$getToast() instanceof BtrBzToast);
    }
}
