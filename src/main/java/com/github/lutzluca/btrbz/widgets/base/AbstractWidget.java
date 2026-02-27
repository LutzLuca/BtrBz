package com.github.lutzluca.btrbz.widgets.base;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.function.Consumer;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractWidget implements LayoutElement, GuiEventListener, Renderable {
    @Getter
    @Setter
    protected int x, y, width, height;

    @Getter
    @Setter
    protected boolean visible = true;

    @Getter
    @Setter
    protected boolean focused = false;
    
    protected final Minecraft client = Minecraft.getInstance();
    
    public AbstractWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
    }

    @Override
    public void visitWidgets(Consumer<net.minecraft.client.gui.components.AbstractWidget> consumer) {
        // widgets does not extend vanilla AbstractWidget, so we do not pass ourselves
    }
    
    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.x, this.y, this.width, this.height);
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        renderWidget(graphics, mouseX, mouseY, delta);
    }
    
    protected abstract void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta);
    
    protected boolean isMouseHovered() {
        double guiScale = this.client.getWindow().getGuiScale();
        double scaledX = this.client.mouseHandler.xpos() / guiScale;
        double scaledY = this.client.mouseHandler.ypos() / guiScale;
        return this.isMouseOver(scaledX, scaledY);
    }
}
