package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class DailyLimitWidgetView implements WidgetView<DailyLimitWidgetData.Snapshot, DailyLimitWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(
        Sizing.fixed(DailyLimitWidgetDefinition.MINIMUM_CONTENT_WIDTH),
        Sizing.content());

    private final LabelComponent header = text("Daily Limit", UiStyles.palette().primary());
    private final LabelComponent value = text("", UiStyles.palette().primary());

    private String displayedValue = "";

    DailyLimitWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(WidgetLayoutTokens.LINE_GAP);
        this.root.horizontalAlignment(HorizontalAlignment.CENTER);

        this.root.child(this.header);
        this.root.child(this.value);

        this.root.tooltip(WidgetTooltips.wrapped(
            "Estimated from Bazaar transactions observed by the mod. Activity missed while data is unavailable "
                + "may not be included."));
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        DailyLimitWidgetData.Snapshot data,
        DailyLimitWidgetConfig config,
        WidgetSession session,
        Consumer<Void> actions
    ) {
        int percent = (int) Math.round(data.used() * 100.0 / data.limit());
        int color = percent >= 90
            ? UiStyles.palette().error()
            : percent >= 75 ? UiStyles.palette().sell() : UiStyles.palette().buy();
        String display = formattedValue(data, config.numberStyle);

        var value = Component.literal(display).withStyle(UiStyles.money());
        var header = Component.literal("● ").withStyle(UiStyles.color(color))
            .append(Component.literal("Daily Limit").withStyle(UiStyles.heading()));
        this.header.text(header);
        this.value.text(value);

        if (!display.equals(this.displayedValue)) {
            this.displayedValue = display;

            var font = Minecraft.getInstance().font;

            this.root.horizontalSizing(Sizing.fixed(Math.max(
                DailyLimitWidgetDefinition.MINIMUM_CONTENT_WIDTH,
                Math.max(font.width(header), font.width(value)))));
        }

        this.root.clearChildren();
        this.root.child(this.header);
        this.root.child(this.value);
    }

    static String formattedValue(
        DailyLimitWidgetData.Snapshot data,
        WidgetDisplayOptions.NumberStyle style
    ) {
        return number(data.used(), style) + " / " + number(data.limit(), style);
    }

    private static String number(long value, WidgetDisplayOptions.NumberStyle style) {
        return style == WidgetDisplayOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }
}
