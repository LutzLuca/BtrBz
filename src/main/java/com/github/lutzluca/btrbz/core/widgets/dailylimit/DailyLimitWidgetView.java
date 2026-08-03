package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class DailyLimitWidgetView implements WidgetView<DailyLimitWidgetData.Snapshot, DailyLimitWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final LabelComponent header = text("Estimated Daily Limit", BazaarStyles.PRIMARY_TEXT);
    private final LabelComponent value = text("", BazaarStyles.BUY_ACCENT);

    DailyLimitWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(WidgetLayoutTokens.LINE_GAP);
        this.root.horizontalAlignment(HorizontalAlignment.CENTER);
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
        int percent = (int) Math.round(data.used() * 100.0 / data.limit());
        int color = percent >= 90
            ? BazaarStyles.STATUS_UNDERCUT
            : percent >= 75 ? BazaarStyles.SELL_ACCENT : BazaarStyles.BUY_ACCENT;
        String display = BazaarWidgetViewData.formatInt(data.used())
            + " / " + BazaarWidgetViewData.formatInt(data.limit());
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));
        this.value.text(Component.literal(display));
        this.value.color(BazaarStyles.color(color));
        this.root.clearChildren();
        this.root.child(this.header);
        this.root.child(this.value);
    }
}
