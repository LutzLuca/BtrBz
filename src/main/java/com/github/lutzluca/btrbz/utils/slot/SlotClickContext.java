package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.world.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

public record SlotClickContext(
    @NotNull SlotView view,
    @NotNull ClickType actionType,
    int button,
    @NotNull SlotInputModifiers modifiers
) { }