package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.world.inventory.ClickType;

public record SlotClickContext(
    SlotView slot,
    ClickType actionType,
    int button,
    SlotInputModifiers modifiers
) { }