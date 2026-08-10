package com.github.lutzluca.btrbz.core.widgets.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

public final class BazaarOrderRowComponent extends BaseUIComponent {
    private static final int MINIMUM_LEFT_WIDTH = 24;

    private BazaarRow row;

    private boolean hoverable;
    private boolean reserveScrollbarSpace;
    private boolean hoverSuppressed;

    private @Nullable BazaarRow.Appearance lastAppearance;
    private @Nullable DrawLayout drawLayout;
    private int drawLayoutWidth = -1;

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

        if (!row.tooltip().isEmpty()) {
            this.tooltip(WidgetTooltips.wrapped(row.tooltip()));
        }
    }

    void update(BazaarRow row, boolean hoverable, int height, boolean reserveScrollbarSpace) {
        var appearance = row.appearance();
        boolean changed = !appearance.equals(this.lastAppearance) || this.reserveScrollbarSpace != reserveScrollbarSpace;

        this.row = row;

        this.hoverable = hoverable;
        this.reserveScrollbarSpace = reserveScrollbarSpace;

        this.verticalSizing(Sizing.fixed(height));

        if (changed) {
            this.lastAppearance = appearance;
            this.drawLayout = null;
            this.tooltip(WidgetTooltips.wrapped(row.tooltip()));
        }
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.row.clickAction() != null && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.onMouseDown(click, doubled);
        }

        if (this.hoverable && this.row.clickAction() != null) {
            this.row.clickAction().accept(click.hasControlDown());
            return true;
        }

        return super.onMouseDown(click, doubled);
    }

    void suppressHover(boolean suppress) {
        this.hoverSuppressed = suppress;
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return !this.hoverSuppressed && super.shouldDrawTooltip(mouseX, mouseY);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.row.backgroundColor() != 0) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, this.row.backgroundColor());
        }

        boolean hovered = this.hoverable && !this.hoverSuppressed && this.isInBoundingBox(mouseX, mouseY);

        if (hovered) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_HOVER);
        }

        if (this.drawLayout == null || this.drawLayoutWidth != this.width) {
            this.drawLayout = this.computeDrawLayout();
            this.drawLayoutWidth = this.width;
        }

        var layout = this.drawLayout;
        var font = Minecraft.getInstance().font;
        int y = this.y + Math.max(0, (this.height - font.lineHeight) / 2);

        if (layout.dotX() >= 0) {
            int dot = 4;
            int dotScreenX = this.x + layout.dotX();
            graphics.fill(dotScreenX, this.y + (this.height - dot) / 2,
                dotScreenX + dot, this.y + (this.height + dot) / 2, this.row.statusColor());
        }

        graphics.text(font, layout.prefix(), this.x + layout.prefixX(), y, this.row.prefixColor(), false);
        graphics.text(font, layout.text(), this.x + layout.textX(), y, BazaarStyles.SECONDARY_TEXT, false);

        if (layout.rightText() != null) {
            graphics.text(font, layout.rightText(), this.x + layout.rightX(), y, this.row.rightColor(), false);
        }
    }

    private DrawLayout computeDrawLayout() {
        var font = Minecraft.getInstance().font;
        int x = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int dotX = -1;

        if (this.row.statusColor() != 0) {
            dotX = x;
            x += 4 + 4;
        }

        int trailingInset = WidgetLayoutTokens.rowTrailingInset(this.reserveScrollbarSpace);
        int rowEnd = this.width - trailingInset;
        var rightText = Component.literal(this.row.rightText());
        var prefix = Component.literal(this.row.prefix());
        boolean blankRight = this.row.rightText().isBlank();

        if (this.row.preservePrefix()) {
            var widths = priorityWidths(
                Math.max(0, rowEnd - x),
                font.width(prefix),
                font.width(rightText)
            );
            int prefixX = x;
            x += widths.prefixWidth();

            int rightWidth = blankRight ? 0 : widths.rightWidth();
            int textLimit = rowEnd - rightWidth - (rightWidth == 0 ? 0 : 3);

            return new DrawLayout(
                prefix.getVisualOrderText(), prefixX,
                ellipsize(Component.literal(this.row.text()), Math.max(0, textLimit - x)), x,
                rightWidth > 0 ? ellipsize(rightText, rightWidth) : null,
                rowEnd - rightWidth,
                dotX
            );
        }

        int rightWidth = blankRight
            ? 0
            : Math.min(font.width(rightText), Math.max(0, rowEnd - x - MINIMUM_LEFT_WIDTH - 3));
        int rightX = rowEnd - rightWidth;
        int leftLimit = blankRight ? rowEnd : rightX - 3;
        int prefixWidth = Math.max(0, leftLimit - x);
        int prefixX = x;
        var prefixSequence = ellipsize(prefix, prefixWidth);

        x += Math.min(font.width(prefix), prefixWidth);

        int textWidth = Math.max(0, leftLimit - x);

        return new DrawLayout(
            prefixSequence, prefixX,
            ellipsize(Component.literal(this.row.text()), textWidth), x,
            blankRight ? null : ellipsize(rightText, rightWidth), rightX,
            dotX
        );
    }

    public static PriorityWidths priorityWidths(int availableWidth, int prefixWidth, int rightWidth) {
        int safeAvailable = Math.max(0, availableWidth);
        int safePrefix = Math.max(0, prefixWidth);
        int metadataSpace = Math.max(0, safeAvailable - safePrefix - 3);
        return new PriorityWidths(safePrefix, Math.min(Math.max(0, rightWidth), metadataSpace));
    }

    private record DrawLayout(
        FormattedCharSequence prefix, int prefixX,
        FormattedCharSequence text, int textX,
        @Nullable FormattedCharSequence rightText, int rightX,
        int dotX
    ) {}

    public record PriorityWidths(int prefixWidth, int rightWidth) {}

    public record BazaarRow(
        String id,
        String prefix,
        int prefixColor,
        String text,
        String rightText,
        int rightColor,
        int statusColor,
        List<Component> tooltip,
        Consumer<Boolean> clickAction,
        boolean preservePrefix,
        int backgroundColor
    ) {

        public Appearance appearance() {
            return new Appearance(
                this.id, this.prefix, this.prefixColor, this.text, this.rightText,
                this.rightColor, this.statusColor, this.tooltip,
                this.preservePrefix, this.backgroundColor
            );
        }

        public record Appearance(
            String id, String prefix, int prefixColor, String text, String rightText,
            int rightColor, int statusColor, List<Component> tooltip,
            boolean preservePrefix, int backgroundColor
        ) {}

        public BazaarRow(
            String id,
            String prefix,
            int prefixColor,
            String text,
            String rightText,
            int rightColor,
            int statusColor,
            List<Component> tooltip,
            Consumer<Boolean> clickAction
        ) {
            this(id, prefix, prefixColor, text, rightText, rightColor, statusColor, tooltip, clickAction, false, 0);
        }

        public BazaarRow(
            String id,
            String prefix,
            int prefixColor,
            String text,
            String rightText,
            int rightColor,
            int statusColor,
            List<Component> tooltip,
            Consumer<Boolean> clickAction,
            boolean preservePrefix
        ) {
            this(id, prefix, prefixColor, text, rightText, rightColor, statusColor, tooltip, clickAction, preservePrefix, 0);
        }
    }
}
