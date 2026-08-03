package com.github.lutzluca.btrbz.core.widgets.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

public final class BazaarOrderRowComponent extends BaseUIComponent {
    private static final int MINIMUM_LEFT_WIDTH = 24;
    private BazaarRow row;
    private boolean hoverable;
    private boolean reserveScrollbarSpace;
    private boolean hoverSuppressed;

    BazaarOrderRowComponent(
        BazaarRow row,
        boolean hoverable,
        int height,
        boolean reserveScrollbarSpace
    ) {
        this.row = row;
        this.hoverable = hoverable;
        this.reserveScrollbarSpace = reserveScrollbarSpace;
        this.sizing(Sizing.fill(100), Sizing.fixed(height));
        if (!row.tooltip().isEmpty()) this.tooltip(WidgetTooltips.wrapped(row.tooltip()));
    }

    void update(BazaarRow row, boolean hoverable, int height, boolean reserveScrollbarSpace) {
        this.row = row;
        this.hoverable = hoverable;
        this.reserveScrollbarSpace = reserveScrollbarSpace;
        this.verticalSizing(Sizing.fixed(height));
        this.tooltip(WidgetTooltips.wrapped(row.tooltip()));
    }

    @Override public boolean canFocus(FocusSource source) {
        return this.row.clickAction() != null && source == FocusSource.MOUSE_CLICK;
    }

    @Override public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
        if (this.hoverable && this.row.clickAction() != null) {
            this.row.clickAction().accept(click.hasControlDown());
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    void suppressHover(boolean suppress) { this.hoverSuppressed = suppress; }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return !this.hoverSuppressed && super.shouldDrawTooltip(mouseX, mouseY);
    }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.row.backgroundColor() != 0) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, this.row.backgroundColor());
        }
        boolean hovered = this.hoverable && !this.hoverSuppressed && this.isInBoundingBox(mouseX, mouseY);
        if (hovered) graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_HOVER);

        var font = Minecraft.getInstance().font;
        int y = this.y + Math.max(0, (this.height - font.lineHeight) / 2);
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.row.statusColor() != 0) {
            int dot = 4;
            graphics.fill(x, this.y + (this.height - dot) / 2, x + dot, this.y + (this.height + dot) / 2, this.row.statusColor());
            x += dot + 4;
        }

        int trailingInset = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.reserveScrollbarSpace) {
            trailingInset += WidgetLayoutTokens.SCROLLBAR_THICKNESS + WidgetLayoutTokens.SCROLLBAR_CONTENT_GAP;
        }
        int rowEnd = this.x + this.width - trailingInset;
        var rightText = Component.literal(this.row.rightText());
        var prefix = Component.literal(this.row.prefix());

        if (this.row.preservePrefix()) {
            var widths = priorityWidths(
                Math.max(0, rowEnd - x),
                font.width(prefix),
                font.width(rightText)
            );
            graphics.text(font, prefix, x, y, this.row.prefixColor(), false);
            x += widths.prefixWidth();
            int rightWidth = this.row.rightText().isBlank() ? 0 : widths.rightWidth();
            int textLimit = rowEnd - rightWidth - (rightWidth == 0 ? 0 : 3);
            graphics.text(
                font,
                ellipsize(Component.literal(this.row.text()), Math.max(0, textLimit - x)),
                x,
                y,
                BazaarStyles.SECONDARY_TEXT,
                false
            );
            if (rightWidth > 0) {
                graphics.text(
                    font,
                    ellipsize(rightText, rightWidth),
                    rowEnd - rightWidth,
                    y,
                    this.row.rightColor(),
                    false
                );
            }
            return;
        }

        int rightWidth = this.row.rightText().isBlank()
            ? 0
            : Math.min(font.width(rightText), Math.max(0, rowEnd - x - MINIMUM_LEFT_WIDTH - 3));
        int rightX = rowEnd - rightWidth;
        int leftLimit = this.row.rightText().isBlank()
            ? rowEnd
            : rightX - 3;
        int prefixWidth = Math.max(0, leftLimit - x);
        graphics.text(font, ellipsize(prefix, prefixWidth), x, y, this.row.prefixColor(), false);
        x += Math.min(font.width(prefix), prefixWidth);
        int textWidth = Math.max(0, leftLimit - x);
        graphics.text(
            font,
            ellipsize(Component.literal(this.row.text()), textWidth),
            x,
            y,
            BazaarStyles.SECONDARY_TEXT,
            false
        );

        if (!this.row.rightText().isBlank()) {
            graphics.text(font, ellipsize(rightText, rightWidth), rightX, y, this.row.rightColor(), false);
        }
    }

    public static PriorityWidths priorityWidths(int availableWidth, int prefixWidth, int rightWidth) {
        int safeAvailable = Math.max(0, availableWidth);
        int safePrefix = Math.max(0, prefixWidth);
        int metadataSpace = Math.max(0, safeAvailable - safePrefix - 3);
        return new PriorityWidths(safePrefix, Math.min(Math.max(0, rightWidth), metadataSpace));
    }

    public record PriorityWidths(int prefixWidth, int rightWidth) {}

    public record BazaarRow(String id, String prefix, int prefixColor, String text, String rightText, int rightColor,
                       int statusColor, List<Component> tooltip, Consumer<Boolean> clickAction,
                       boolean preservePrefix, int backgroundColor) {
        public BazaarRow(String id, String prefix, int prefixColor, String text, String rightText, int rightColor,
                    int statusColor, List<Component> tooltip, Consumer<Boolean> clickAction) {
            this(id, prefix, prefixColor, text, rightText, rightColor, statusColor, tooltip, clickAction, false, 0);
        }

        public BazaarRow(String id, String prefix, int prefixColor, String text, String rightText, int rightColor,
                    int statusColor, List<Component> tooltip, Consumer<Boolean> clickAction,
                    boolean preservePrefix) {
            this(id, prefix, prefixColor, text, rightText, rightColor, statusColor, tooltip, clickAction, preservePrefix, 0);
        }
    }
}
