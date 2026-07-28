package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class DailyLimitWidgetView implements WidgetView<DailyLimitWidgetData.Snapshot, DailyLimitWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final LabelComponent header = text("Daily Limit", BazaarStyles.PRIMARY_TEXT);
    private final LabelComponent value = text("", BazaarStyles.BUY_ACCENT);

    DailyLimitWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(WidgetLayoutTokens.LINE_GAP);
        this.root.child(this.header);
        this.root.child(this.value);
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
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));
        int percent = (int) Math.round(data.used() * 100.0 / data.limit());
        int color = percent >= config.criticalThreshold
            ? BazaarStyles.STATUS_UNDERCUT
            : percent >= config.warningThreshold ? BazaarStyles.SELL_ACCENT : BazaarStyles.BUY_ACCENT;
        String used = number(data.used(), config.numberStyle);
        String limit = number(data.limit(), config.numberStyle);
        String display = switch (config.display) {
            case UsedLimit -> used + " / " + limit;
            case Remaining -> number(data.limit() - data.used(), config.numberStyle) + " remaining";
            case Percentage -> percent + "% used";
            case Compact -> "Limit " + used + "/" + limit;
        };
        this.value.text(Component.literal(display));
        this.value.color(BazaarStyles.color(color));
        this.root.clearChildren();
        if (config.showHeader) this.root.child(this.header);
        this.root.child(this.value);
    }

    private static String number(long value, WidgetDisplayOptions.NumberStyle style) {
        return style == WidgetDisplayOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }
}
