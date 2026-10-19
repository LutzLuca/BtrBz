package com.github.lutzluca.btrbz.core.widgets.alerts;

import com.github.lutzluca.btrbz.core.ui.RetainedText;
import com.github.lutzluca.btrbz.core.ui.UiButton;
import com.github.lutzluca.btrbz.Assets;
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
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class AlertsWidgetView implements WidgetView<AlertsWidgetData.Snapshot, AlertsWidgetConfig, Action> {
    private static final int MAX_ROWS = 3;
    private final FlowLayout root = BazaarUi.panel(1);

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
        var label = Component.literal("Alerts " + data.entries().size()).withStyle(UiStyles.primary());
        int headerWidth = 12 + 3 + 7 + 4 + font.width(label) + 6;
        int width = headerWidth;
        if (!config.collapsed) {
            for (var entry : data.entries().stream().limit(MAX_ROWS).toList()) {
                width = Math.max(width, Math.min(Math.max(80, config.contentWidth), font.width(entry.name()) + 32));
            }
        }
        this.root.horizontalSizing(Sizing.fixed(width));
        var header = BazaarUi.line();
        header.child(UiButton.compactIcon(config.collapsed ? Assets.ALERT_EXPAND : Assets.ALERT_COLLAPSE,
            Component.literal(config.collapsed ? "Expand alerts" : "Collapse alerts"),
            () -> actions.accept(new Action.Toggle())));
        var inbox = UiButton.compactIcon(Assets.ALERT_BELL, label, Component.literal("Open Inbox"),
            () -> actions.accept(new Action.Inbox()));
        inbox.active(!session.inSign());
        if (session.inSign()) {
            inbox.tooltip(Component.literal("Finish order input before opening another screen"));
        }
        header.child(inbox);
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
            item.tooltip(session.inSign()
                ? WidgetTooltips.wrapped("Finish order input before opening another screen")
                : WidgetTooltips.wrapped(entry.tooltip()));
            boolean current = session.product().map(product -> product.productId().equals(entry.productId()))
                .orElse(false);
            if (current) {
                item.surface((graphics, component) -> graphics.fill(component.x(), component.y(),
                    component.x() + component.width(), component.y() + component.height(),
                    UiStyles.palette().rowHover()));
            }
            line.child(item);
            var dismiss = UiButton.compactIcon(Assets.ALERT_ARCHIVE,
                Component.literal("Dismiss alert. Move to History and stop reminders."),
                () -> actions.accept(new Action.Dismiss(entry.id())));
            dismiss.tooltip(WidgetTooltips.wrapped(java.util.List.of(Component.literal("Dismiss alert for ")
                .append(entry.name()), entry.condition(),
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
        private final FormattedCharSequence text;
        private final RetainedText retained = new RetainedText();

        Text(Component text, int width) {
            this.text = UiText.ellipsize(text, Math.max(1, width));
            this.sizing(Sizing.fixed(Math.max(1, width)), Sizing.fixed(12));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var font = Minecraft.getInstance().font;
            this.retained.draw(graphics, font, this.text,
                this.x(), this.y() + (this.height() - font.lineHeight) / 2,
                UiStyles.palette().textPrimary(), false);
        }
    }

}
