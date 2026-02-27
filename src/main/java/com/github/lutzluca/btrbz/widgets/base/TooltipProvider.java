package com.github.lutzluca.btrbz.widgets.base;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Provides tooltip rendering for widgets.
 */
public interface TooltipProvider {
    /**
     * Render tooltip at the given mouse position.
     * 
     * @param graphics Gui graphics context
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     */
    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY);

    /**
     * Reset hover state when mouse leaves widget.
     */
    void resetHover();
}

