package com.github.lutzluca.btrbz.core.widgets.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class WidgetTooltips {
    public static final int MAXIMUM_WIDTH = 200;

    private WidgetTooltips() {}

    public static List<ClientTooltipComponent> wrapped(String text) {
        return wrapped(List.of(Component.literal(text)));
    }

    public static List<ClientTooltipComponent> wrapped(Collection<Component> lines) {
        var font = Minecraft.getInstance().font;
        var tooltip = new ArrayList<ClientTooltipComponent>();
        for (var line : lines) {
            var wrappedLines = font.split(line, MAXIMUM_WIDTH);
            if (wrappedLines.isEmpty()) {
                tooltip.add(ClientTooltipComponent.create(FormattedCharSequence.EMPTY));
            }
            for (var wrapped : wrappedLines) {
                tooltip.add(ClientTooltipComponent.create(wrapped));
            }
        }
        return List.copyOf(tooltip);
    }
}
