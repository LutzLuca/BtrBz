package com.github.lutzluca.btrbz.core.bazaariteminfo;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.gui.Font;

/** Pixel-aligned Order Book text with matching measurement helpers. */
final class BazaarItemInfoOrderText {
    private BazaarItemInfoOrderText() {}

    static int width(Font font, String text) {
        return font.width(text);
    }

    static int lineHeight(int fontLineHeight) {
        return Math.max(1, fontLineHeight);
    }

    static void draw(OwoUIGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }
}
