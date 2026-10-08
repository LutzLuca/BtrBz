package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared retained labels and item presentation without feature-specific layout. */
public final class UiComponents {
    private UiComponents() {}

    public static LabelComponent label(String value, int color) {
        var label = new RetainedLabelComponent(Component.literal(value));
        label.color(Color.ofArgb(color));
        label.shadow(false);
        return label;
    }

    public static LabelComponent boldLabel(String value, int color) {
        var label = label(value, color);
        label.text(Component.literal(value).withStyle(ChatFormatting.BOLD));
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
}
