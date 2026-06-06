package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.client.Minecraft;

public record SlotInputModifiers(boolean controlDown, boolean shiftDown, boolean altDown) {

    public static SlotInputModifiers from(Minecraft client) {
        if (client == null) {
            return new SlotInputModifiers(false, false, false);
        }

        return new SlotInputModifiers(
            client.hasControlDown(),
            client.hasShiftDown(),
            client.hasAltDown()
        );
    }
}