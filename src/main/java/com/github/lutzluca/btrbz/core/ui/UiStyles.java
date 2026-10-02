package com.github.lutzluca.btrbz.core.ui;

import com.github.lutzluca.btrbz.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Shared text roles and formatting, separate from the palette's colors. */
public final class UiStyles {
    private UiStyles() {}

    public static UiPalette palette() {
        return UiPalette.DEFAULT_PALETTE;
    }

    public static Style color(int argb) {
        return Style.EMPTY.withColor(argb & 0xFFFFFF).withBold(false).withUnderlined(false);
    }

    public static Style primary() {
        return color(palette().primary());
    }

    public static Style label() {
        return color(palette().label());
    }

    public static Style muted() {
        return color(palette().muted());
    }

    public static Style quantity() {
        return color(palette().quantity());
    }

    public static Style heading() {
        return primary().withBold(true);
    }

    public static Style key() {
        return primary().withBold(true);
    }

    public static Style action() {
        return color(palette().action());
    }

    public static Style money() {
        return Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(false).withUnderlined(false);
    }

    public static MutableComponent coins(double value) {
        return Component.literal(Utils.formatDecimal(value, 1, true) + " coins").withStyle(money());
    }
}
