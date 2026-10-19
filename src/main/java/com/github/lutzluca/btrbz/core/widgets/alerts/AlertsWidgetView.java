package com.github.lutzluca.btrbz.core.widgets.alerts;

import com.github.lutzluca.btrbz.core.ui.RetainedText;
import com.github.lutzluca.btrbz.core.ui.TextRenderRevision;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.ui.UiText;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.alerts.AlertsWidgetDefinition.Action;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class AlertsWidgetView implements WidgetView<AlertsWidgetData.Snapshot, AlertsWidgetConfig, Action> {
    private static final int MAX_ROWS = 3;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());
    private final FlowLayout root = BazaarUi.panel(1);
    private final BooleanSupplier remindersEnabled;

    AlertsWidgetView(BooleanSupplier remindersEnabled) {
        this.remindersEnabled = remindersEnabled;
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        AlertsWidgetData.Snapshot data,
        AlertsWidgetConfig config,
        WidgetSession session,
        Consumer<Action> actions
    ) {
        this.root.clearChildren();
        var font = Minecraft.getInstance().font;
        var count = Component.literal(Integer.toString(data.entries().size())).withStyle(UiStyles.primary());
        int countWidth = font.width(count) + 2;
        int headerWidth = 12 * 3 + countWidth + 12;
        int width = headerWidth;
        if (!config.collapsed) {
            for (var entry : data.entries().stream().limit(MAX_ROWS).toList()) {
                width = Math.max(width, Math.min(Math.max(80, config.contentWidth), font.width(entry.name()) + 32));
            }
        }
        this.root.horizontalSizing(Sizing.fixed(width));
        var header = BazaarUi.line();
        header.child(new Control(config.collapsed ? Glyph.Expand : Glyph.Collapse,
            config.collapsed ? "Expand alerts" : "Collapse alerts", () -> actions.accept(new Action.Toggle())));
        var inbox = new Control(Glyph.Bell, "Open alert inbox", () -> actions.accept(new Action.Inbox()));
        inbox.enabled(!session.inSign());
        header.child(inbox);
        var counter = new Link(countWidth, () -> actions.accept(new Action.Inbox()));
        counter.enabled(!session.inSign());
        counter.child(new Text(count, countWidth));
        counter.tooltip(Component.literal(data.entries().size() + " pending alerts. Open Inbox"));
        header.child(counter).child(UiComponents.spacer());
        String snooze = "Remind me in 10 minutes";
        if (data.snoozedUntil() > System.currentTimeMillis()) {
            snooze += "\nReminders postponed until " + TIME.format(Instant.ofEpochMilli(data.snoozedUntil()));
        }
        var clock = new Control(Glyph.Clock, snooze, () -> actions.accept(new Action.Snooze()));
        clock.availability(this.remindersEnabled, "Pending alert reminders are off. Enable them in Alerts settings.");
        header.child(clock);
        this.root.child(header);
        if (config.collapsed) {
            return;
        }

        for (var entry : data.entries().stream().limit(MAX_ROWS).toList()) {
            var line = BazaarUi.line();
            var item = new Link(width - 15, () -> actions.accept(new Action.Open(entry.id())));
            item.enabled(!session.inSign());
            item.gap(3);
            item.child(UiComponents.item(entry.stack(), 12));
            item.child(new Text(UiStyles.productName(entry.name(), false), width - 30));
            item.tooltip(WidgetTooltips.wrapped(entry.tooltip()));
            boolean current = session.product().map(product -> product.productId().equals(entry.productId()))
                .orElse(false);
            if (current) {
                item.surface((graphics, component) -> graphics.fill(component.x(), component.y(),
                    component.x() + component.width(), component.y() + component.height(),
                    UiStyles.palette().rowHover()));
            }
            line.child(item);
            var dismiss = new Control(Glyph.Archive, "Dismiss alert. Move to History and stop reminders.",
                () -> actions.accept(new Action.Dismiss(entry.id())));
            dismiss.tooltip(WidgetTooltips.wrapped(java.util.List.of(Component.literal("Dismiss alert for ")
                .append(entry.name()), entry.tooltip().get(1),
                Component.literal("Move to History and stop reminders"))));
            line.child(dismiss);
            this.root.child(line);
        }
        if (data.entries().size() > MAX_ROWS) {
            var more = new Link(width, () -> actions.accept(new Action.Inbox()));
            more.enabled(!session.inSign());
            more.child(new Text(Component.literal("+" + (data.entries().size() - MAX_ROWS) + " more")
                .withStyle(UiStyles.muted()), width));
            more.tooltip(Component.literal("Open all pending alerts in Inbox"));
            this.root.child(more);
        }
    }

    private static class Link extends FlowLayout {
        private final Runnable action;
        private boolean enabled = true;

        Link(int width, Runnable action) {
            super(Sizing.fixed(width), Sizing.fixed(12), Algorithm.HORIZONTAL);
            this.action = action;
            this.verticalAlignment(VerticalAlignment.CENTER);
            this.cursorStyle(CursorStyle.HAND);
            this.allowOverflow(true);
        }

        void enabled(boolean enabled) {
            this.enabled = enabled;
            this.cursorStyle(enabled ? CursorStyle.HAND : CursorStyle.POINTER);
            if (!enabled) {
                this.tooltip(Component.literal("Finish order input before opening another screen"));
            }
        }

        @Override
        public boolean canFocus(FocusSource source) {
            return this.enabled;
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent event, boolean doubled) {
            if (this.enabled && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                this.action.run();
                return true;
            }
            return super.onMouseDown(event, doubled);
        }

        @Override
        public boolean onKeyPress(KeyEvent event) {
            if (this.enabled && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE
                || event.key() == InputConstants.KEY_NUMPADENTER)) {
                this.action.run();
                return true;
            }
            return super.onKeyPress(event);
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            if (this.isInBoundingBox(mouseX, mouseY) || this.focusHandler().focused() == this) {
                graphics.fill(this.x(), this.y(), this.x() + this.width(), this.y() + this.height(),
                    UiStyles.palette().rowHover());
            }
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }
    }

    private static final class Text extends BaseUIComponent {
        private final Component text;
        private final RetainedText retained = new RetainedText();
        private int fittedWidth = -1;
        private long fittedRevision = -1;
        private FormattedCharSequence fitted = FormattedCharSequence.EMPTY;

        Text(Component text, int width) {
            this.text = text;
            this.sizing(Sizing.fixed(Math.max(1, width)), Sizing.fixed(12));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var font = Minecraft.getInstance().font;
            long revision = TextRenderRevision.current();
            if (this.fittedWidth != this.width() || this.fittedRevision != revision) {
                this.fitted = UiText.ellipsize(this.text, this.width());
                this.fittedWidth = this.width();
                this.fittedRevision = revision;
            }
            this.retained.draw(graphics, font, this.fitted,
                this.x(), this.y() + (this.height() - font.lineHeight) / 2,
                UiStyles.palette().textPrimary(), false);
        }
    }

    private static final class Control extends Link {
        private final Glyph glyph;
        private final Component description;
        private BooleanSupplier availability = () -> true;
        private Component unavailable = Component.empty();
        private boolean available = true;

        Control(Glyph glyph, String description, Runnable action) {
            super(12, action);
            this.glyph = glyph;
            this.description = Component.literal(description);
            this.tooltip(this.description);
        }

        void availability(BooleanSupplier availability, String unavailable) {
            this.availability = availability;
            this.unavailable = Component.literal(unavailable);
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean available = this.availability.getAsBoolean();
            if (this.available != available) {
                this.available = available;
                this.enabled(available);
                this.tooltip(available ? this.description : this.unavailable);
            }
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
            int color = !this.available
                ? UiStyles.palette().textMuted()
                : this.isInBoundingBox(mouseX, mouseY)
                    ? UiStyles.palette().textPrimary() : UiStyles.palette().textSecondary();
            for (int row = 0; row < this.glyph.pixels.length; row++) {
                for (int column = 0; column < 7; column++) {
                    if ((this.glyph.pixels[row] & (1 << (6 - column))) != 0) {
                        int x = this.x() + 2 + column;
                        int y = this.y() + 2 + row;
                        graphics.fill(x, y, x + 1, y + 1, color);
                    }
                }
            }
        }
    }

    private enum Glyph {
        Bell(8, 28, 34, 34, 34, 127, 8),
        Clock(28, 34, 73, 73, 69, 34, 28),
        Archive(127, 65, 127, 34, 42, 34, 62),
        Expand(16, 8, 4, 2, 4, 8, 16),
        Collapse(0, 65, 34, 20, 8, 0, 0);

        private final int[] pixels;

        Glyph(int... pixels) {
            this.pixels = pixels;
        }
    }
}
