package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class RetainedTextRow {
    private final List<RetainedText> slots = new ArrayList<>();
    private int next;

    public void begin() {
        this.next = 0;
    }

    public void draw(
        OwoUIGraphics graphics,
        Font font,
        FormattedCharSequence text,
        int x,
        int y,
        int color,
        boolean dropShadow
    ) {
        if (this.next >= this.slots.size()) {
            this.slots.add(new RetainedText());
        }

        this.slots.get(this.next++).draw(graphics, font, text, x, y, color, dropShadow);
    }
}
