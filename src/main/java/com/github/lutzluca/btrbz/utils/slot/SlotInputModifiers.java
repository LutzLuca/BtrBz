package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

public record SlotInputModifiers(
    boolean controlDown,
    boolean shiftDown,
    boolean altDown
) {

    public static SlotInputModifiers from(MouseButtonEvent ev) {
        return new SlotInputModifiers(
            ev.hasControlDown(),
            ev.hasShiftDown(),
            ev.hasAltDown()
        );
    }

    public static SlotInputModifiers from(Minecraft client) {
        return new SlotInputModifiers(
            client.hasControlDown(),
            client.hasShiftDown(),
            client.hasAltDown()
        );
    }

    public static SlotInputModifiers none() {
        return new SlotInputModifiers(false, false, false);
    }
}