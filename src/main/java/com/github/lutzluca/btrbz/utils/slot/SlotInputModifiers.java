package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

public record SlotInputModifiers(
    boolean controlDown,
    boolean shiftDown,
    boolean altDown
) {

    public static SlotInputModifiers from(MouseButtonEvent event) {
        return new SlotInputModifiers(
            event.hasControlDown(),
            event.hasShiftDown(),
            event.hasAltDown()
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