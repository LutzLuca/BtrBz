package com.github.lutzluca.btrbz.widgets.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for rendering tooltips with consistent behavior across widgets.
 * <p>
 * Supports both immediate rendering and deferred rendering via setTooltipForNextFrame.
 */
public final class TooltipRenderer {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    
    private TooltipRenderer() {}
    
    /**
     * Render tooltip immediately at the given position.
     */
    public static void renderImmediate(GuiGraphicsExtractor graphics, List<Component> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        
        List<ClientTooltipComponent> components = toClientComponents(lines);
        graphics.tooltip(
            CLIENT.font,
            components,
            mouseX,
            mouseY,
            DefaultTooltipPositioner.INSTANCE,
            null
        );
    }
    
    /**
     * Queue tooltip to be rendered on the next frame.
     * Use this when tooltip should render above other elements.
     */
    public static void renderDeferred(GuiGraphicsExtractor graphics, List<Component> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        
        graphics.setTooltipForNextFrame(CLIENT.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }
    
    /**
     * Convert Component list to ClientTooltipComponent list.
     */
    public static List<ClientTooltipComponent> toClientComponents(List<Component> lines) {
        return lines.stream()
            .map(Component::getVisualOrderText)
            .map(ClientTooltipComponent::create)
            .collect(Collectors.toList());
    }
}
