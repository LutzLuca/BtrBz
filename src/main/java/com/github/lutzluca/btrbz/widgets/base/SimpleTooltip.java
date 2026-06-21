package com.github.lutzluca.btrbz.widgets.base;

import com.github.lutzluca.btrbz.widgets.core.TooltipRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SimpleTooltip implements TooltipProvider {
    private final List<Component> lines;
    private long hoverStartTime = 0;
    private boolean wasHovered = false;
    private final long showDelay;
    
    public SimpleTooltip(List<Component> lines, long showDelay) {
        this.lines = new ArrayList<>(lines);
        this.showDelay = showDelay;
    }
    
    public static SimpleTooltip of(Component... lines) {
        return new SimpleTooltip(List.of(lines), 500);
    }
    
    @Override
    public void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (!this.wasHovered) {
            this.hoverStartTime = now;
            this.wasHovered = true;
        }
        
        long hoverDuration = now - this.hoverStartTime;
        if (hoverDuration < this.showDelay) {
            return;
        }
        
        TooltipRenderer.renderImmediate(graphics, this.lines, mouseX, mouseY);
    }

    @Override
    public void resetHover() {
        this.wasHovered = false;
    }
}
