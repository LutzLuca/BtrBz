package com.github.lutzluca.btrbz.utils.slot;

import org.jetbrains.annotations.NotNull;
import net.minecraft.world.inventory.ContainerInput;

/**
 * @param actionType the resolved client click action. Vanilla or client mods may rewrite
 *        physical input before BtrBz receives it, so hooks should not treat this as pristine
 *        mouse-button intent.
 */
public record SlotClickContext(
    @NotNull SlotView view,
    @NotNull ContainerInput actionType,
    int button,
    @NotNull SlotInputModifiers modifiers) {}
