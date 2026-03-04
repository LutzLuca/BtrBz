package com.github.lutzluca.btrbz.widgets;

import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.base.RenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class LabelWidget extends DraggableWidget {
    private final List<Component> lines = new ArrayList<>();
    private boolean autoSize = true;
    private int padding = 4;
    private Alignment alignment = Alignment.LEFT;
    private int backgroundColor = 0x80000000;
    private boolean backgroundVisible = false;
    private Consumer<LabelWidget> clickCallback;

    private List<FormattedCharSequence> cachedVisualLines;
    private int[] cachedLineWidths;
    private boolean cacheValid = false;

    public enum Alignment {
        LEFT, CENTER, RIGHT
    }

    public LabelWidget(
        int defaultX, int defaultY,
        Component text
    ) {
        this(defaultX, defaultY, Collections.singletonList(text));
    }

    public LabelWidget(
        int defaultX, int defaultY,
        List<Component> lines
    ) {
        super(defaultX, defaultY, 100, 50);
        setLines(lines);
    }

    public LabelWidget setLines(List<Component> newLines) {
        this.lines.clear();
        this.lines.addAll(newLines);
        this.cacheValid = false;
        if (this.autoSize) {
            this.recalculateSize();
        }
        return this;
    }

    public LabelWidget setText(Component text) {
        this.setLines(Collections.singletonList(text));
        return this;
    }

    public List<Component> getLines() {
        return Collections.unmodifiableList(this.lines);
    }

    private void recalculateSize() {
        List<Component> currentLines = this.getLines();
        int lineHeight = this.client.font.lineHeight;
        if (currentLines.isEmpty()) {
            this.width = this.padding * 2 + 50;
            this.height = this.padding * 2 + lineHeight;
            return;
        }

        int maxWidth = 0;
        for (Component line : currentLines) {
            if (line == null) continue;
            int lineWidth = this.client.font.width(line);
            maxWidth = Math.max(maxWidth, lineWidth);
        }

        this.width = maxWidth + this.padding * 2;
        this.height = currentLines.size() * lineHeight + this.padding * 2;
    }

    private void ensureCache() {
        if (this.cacheValid) return;

        this.cachedVisualLines = new ArrayList<>(this.lines.size());
        this.cachedLineWidths = new int[this.lines.size()];

        for (int i = 0; i < this.lines.size(); i++) {
            Component line = this.lines.get(i);
            if (line != null) {
                this.cachedVisualLines.add(line.getVisualOrderText());
                this.cachedLineWidths[i] = this.client.font.width(line);
            } else {
                this.cachedVisualLines.add(FormattedCharSequence.EMPTY);
                this.cachedLineWidths[i] = 0;
            }
        }

        this.cacheValid = true;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
        this.ensureCache();

        int x = this.getX();
        int y = this.getY();
        int lineHeight = this.client.font.lineHeight;

        if (this.backgroundVisible) {
            graphics.fill(x, y, x + this.width, y + this.height, this.backgroundColor);
        }

        int startY = y + this.padding;
        for (int i = 0; i < this.cachedVisualLines.size(); i++) {
            FormattedCharSequence visualLine = this.cachedVisualLines.get(i);
            if (visualLine == FormattedCharSequence.EMPTY) continue;

            int lineY = startY + i * lineHeight;
            int lineWidth = this.cachedLineWidths[i];

            int lineX = switch (this.alignment) {
                case LEFT -> x + this.padding;
                case CENTER -> x + (this.width - lineWidth) / 2;
                case RIGHT -> x + this.width - this.padding - lineWidth;
            };

            graphics.drawString(this.client.font, visualLine, lineX, lineY, 0xFFFFFFFF, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (event.button() == 0 && this.isMouseHovered()) {
            if (this.clickCallback != null) {
                this.clickCallback.accept(this);
                return true;
            }
        }
        return handled;
    }

    public LabelWidget setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
        if (this.autoSize) {
            this.recalculateSize();
        }
        return this;
    }

    public LabelWidget setPadding(int padding) {
        this.padding = padding;
        if (this.autoSize) {
            this.recalculateSize();
        }
        return this;
    }

    public LabelWidget setAlignment(Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    public LabelWidget setBackgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    public LabelWidget setBackgroundVisible(boolean visible) {
        this.backgroundVisible = visible;
        return this;
    }

    public LabelWidget setSize(int width, int height) {
        this.width = width;
        this.height = height;
        this.autoSize = false;
        return this;
    }

    public LabelWidget onClick(Consumer<LabelWidget> callback) {
        this.clickCallback = callback;
        return this;
    }
}
