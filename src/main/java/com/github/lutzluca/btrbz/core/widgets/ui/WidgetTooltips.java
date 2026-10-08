package com.github.lutzluca.btrbz.core.widgets.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public final class WidgetTooltips {
    public static final int MAXIMUM_WIDTH = 200;

    private WidgetTooltips() {}

    public static List<ClientTooltipComponent> wrapped(String text) {
        return wrapped(List.of(Component.literal(text)));
    }

    public static List<ClientTooltipComponent> wrapped(Collection<Component> lines) {
        var client = Minecraft.getInstance();
        var font = client.font;
        int width = Math.min(MAXIMUM_WIDTH, Math.max(1, client.getWindow().getGuiScaledWidth() - 16));
        var tooltip = new ArrayList<ClientTooltipComponent>();
        for (var line : lines) {
            for (var wrapped : font.split(line, width)) {
                tooltip.add(ClientTooltipComponent.create(wrapped));
            }
        }
        return List.copyOf(tooltip);
    }
}
