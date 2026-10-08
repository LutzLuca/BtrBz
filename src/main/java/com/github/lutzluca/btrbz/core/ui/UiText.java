package com.github.lutzluca.btrbz.core.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Shared text measurement and fitting, preserving component styles. */
public final class UiText {
    private UiText() {}

    public static Component firstFittingText(List<Component> candidates, int maximumWidth) {
        var font = Minecraft.getInstance().font;
        int availableWidth = Math.max(0, maximumWidth);

        for (var candidate : candidates) {
            if (font.width(candidate) <= availableWidth) {
                return candidate;
            }
        }

        return Component.empty();
    }

    public static FormattedCharSequence ellipsize(Component text, int maxWidth) {
        var font = Minecraft.getInstance().font;

        if (maxWidth <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }

        var ellipsis = FormattedText.of("…", text.getStyle());
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return FormattedCharSequence.EMPTY;
        }

        var trimmed = font.substrByWidth(text, maxWidth - ellipsisWidth);
        return Language.getInstance().getVisualOrder(FormattedText.composite(trimmed, ellipsis));
    }
}
