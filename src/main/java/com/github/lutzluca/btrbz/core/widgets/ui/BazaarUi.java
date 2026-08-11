package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class BazaarUi {
    private BazaarUi() {
    }

    public static RetainedFlowLayout panel(int width) {
        var panel = RetainedFlowLayout.vertical(Sizing.fixed(width), Sizing.content());
        panel.allowOverflow(true);
        panel.gap(WidgetLayoutTokens.LINE_GAP);
        return panel;
    }

    public static RetainedFlowLayout line(UIComponent... components) {
        var line = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        line.verticalAlignment(VerticalAlignment.CENTER);
        line.gap(3);

        for (var component : components) {
            if (component != null) {
                line.child(component);
            }
        }

        return line;
    }

    public static LabelComponent label(String value, int color) {
        var label = new BazaarLabelComponent(Component.literal(value));
        label.color(BazaarStyles.color(color));
        label.shadow(false);
        return label;
    }

    public static LabelComponent text(String value, int color) {
        return label(value, color);
    }

    public static LabelComponent boldLabel(String value, int color) {
        var label = new BazaarLabelComponent(Component.literal(value).withStyle(ChatFormatting.BOLD));
        label.color(BazaarStyles.color(color));
        label.shadow(false);
        return label;
    }

    public static UIComponent spacer() {
        var spacer = UIComponents.spacer();
        spacer.horizontalSizing(Sizing.expand(100));
        spacer.verticalSizing(Sizing.fixed(0));
        return spacer;
    }

    public static ItemComponent icon(ItemStack stack) {
        return item(stack, 16);
    }

    public static ItemComponent item(ItemStack stack, int size) {
        var item = UIComponents.item(stack);
        item.sizing(Sizing.fixed(size), Sizing.fixed(size));
        item.showOverlay(false);
        item.setTooltipFromStack(false);
        return item;
    }

    public static @Nullable ItemComponent reconcileItem(
        @Nullable ItemComponent current,
        Optional<ItemStack> next,
        int size
    ) {
        if (next.isEmpty()) {
            if (current != null && current.hasParent()) {
                current.dismount(UIComponent.DismountReason.REMOVED);
            }

            return null;
        }

        if (current == null) {
            return item(next.orElseThrow(), size);
        }

        current.stack(next.orElseThrow());

        return current;
    }

    public static String firstFittingText(List<String> candidates, int maximumWidth) {
        var font = Minecraft.getInstance().font;
        int availableWidth = Math.max(0, maximumWidth);

        for (var candidate : candidates) {
            if (font.width(candidate) <= availableWidth) {
                return candidate;
            }
        }

        return "";
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

    public static String truncate(String value, int maxWidth) {
        var font = Minecraft.getInstance().font;

        if (font.width(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "…";
        int target = Math.max(0, maxWidth - font.width(ellipsis));

        return font.plainSubstrByWidth(value, target).stripTrailing() + ellipsis;
    }
}
