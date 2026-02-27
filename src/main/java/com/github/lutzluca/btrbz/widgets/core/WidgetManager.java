package com.github.lutzluca.btrbz.widgets.core;

import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.base.RenderContext;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class WidgetManager {
    protected final List<DraggableWidget> widgets;
    protected final Minecraft client;
    protected final RenderContext renderContext;

    public WidgetManager() {
        this.widgets = new ArrayList<>();
        this.client = Minecraft.getInstance();
        this.renderContext = new RenderContext();
    }

    public WidgetManager(List<DraggableWidget> widgets) {
        this();
        this.widgets.addAll(widgets);
    }

    public void addWidget(DraggableWidget widget) {
        this.widgets.add(widget);
    }

    public void setWidgets(List<DraggableWidget> widgets) {
        this.widgets.clear();
        this.widgets.addAll(widgets);
    }

    public void init() {
        log.debug("Initializing {} widgets", this.widgets.size());
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        boolean anyWidgetDragging = this.isAnyWidgetDragging();
        DraggableWidget topWidget = this.findTopmostWidgetAt(mouseX, mouseY);

        for (DraggableWidget widget : this.widgets) {
            if (!widget.isVisible()) {
                continue;
            }

            boolean isTop = (widget == topWidget);
            this.renderContext.update(isTop, anyWidgetDragging);
            widget.renderWidget(graphics, mouseX, mouseY, delta, this.renderContext);
        }
    }

    protected boolean isAnyWidgetDragging() {
        for (DraggableWidget widget : this.widgets) {
            if (widget.isDragging()) {
                return true;
            }
        }
        return false;
    }

    protected @Nullable DraggableWidget findTopmostWidgetAt(double x, double y) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            DraggableWidget widget = this.widgets.get(i);
            if (widget.isVisible() && widget.containsPoint(x, y)) {
                return widget;
            }
        }
        return null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            DraggableWidget widget = this.widgets.get(i);
            if (widget.isVisible() && widget.isMouseOver(mouseX, mouseY)) {
                if (widget.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            this.widgets.get(i).mouseReleased(mouseX, mouseY, button);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            DraggableWidget widget = this.widgets.get(i);
            if (widget.isDragging()) {
                return widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
        }

        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            DraggableWidget widget = this.widgets.get(i);
            if (widget.isVisible() && widget.isMouseOver(mouseX, mouseY)) {
                if (widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            DraggableWidget widget = this.widgets.get(i);
            if (widget.isVisible() && widget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            for (DraggableWidget widget : this.widgets) {
                if (widget.isDragging()) {
                    widget.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
            }
        }
        
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }
    
    public void cleanup() {
        log.debug("Cleaning up {} widgets", this.widgets.size());
        this.widgets.clear();
    }

    public List<DraggableWidget> getWidgets() {
        return new ArrayList<>(this.widgets);
    }
}
