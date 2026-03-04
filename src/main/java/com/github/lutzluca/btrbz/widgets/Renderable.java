package com.github.lutzluca.btrbz.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Interface for objects that can render themselves.
 *
 * This allows list widgets to manage any renderable object,
 * not just widgets. The renderable handles its own rendering
 * logic, while the container (e.g. list) manages positioning.
 */
public interface Renderable {
    /**
     * Render this item.
     *
     * @param graphics Gui graphics context
     * @param x X position to render at
     * @param y Y position to render at
     * @param width Available width
     * @param height Available height
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Frame time delta
     * @param hovered Whether this item is currently hovered
     */
    void render(
        GuiGraphics graphics,
        int x, int y,
        int width, int height,
        int mouseX, int mouseY,
        float delta,
        boolean hovered
    );

    /**
     * Handle mouse click on this item.
     *
     * @param event Mouse button event with position, button, and modifier information
     * @return true if click was handled
     */
    default boolean mouseClicked(MouseButtonEvent event) {
        return false;
    }

    /**
     * Get tooltip lines to display when this item is hovered.
     * Return null or empty list if no tooltip should be shown.
     *
     * @return List of tooltip lines, or null if no tooltip
     */
    default List<Component> getTooltip() {
        return null;
    }
}
