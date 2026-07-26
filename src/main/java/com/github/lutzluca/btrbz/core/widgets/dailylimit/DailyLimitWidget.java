package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.core.UIComponent;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class DailyLimitWidget {
    private DailyLimitWidget() {}

    public static UIComponent render(long used, long limit, BazaarWidgetOptions.OrderLimit options) {
        var root = panel(options.contentWidth());
        if (options.showHeader()) root.child(text("Daily Limit", BazaarStyles.PRIMARY_TEXT));
        int percent = (int) Math.round(used * 100.0 / limit);
        int color = percent >= options.criticalThreshold()
            ? BazaarStyles.STATUS_UNDERCUT
            : percent >= options.warningThreshold() ? BazaarStyles.SELL_ACCENT : BazaarStyles.BUY_ACCENT;
        String usedText = number(used, options.numberStyle());
        String limitText = number(limit, options.numberStyle());
        String value = switch (options.display()) {
            case UsedLimit -> usedText + " / " + limitText;
            case Remaining -> number(limit - used, options.numberStyle()) + " remaining";
            case Percentage -> percent + "% used";
            case Compact -> "Limit " + usedText + "/" + limitText;
        };
        root.child(text(value, color));
        return root;
    }

    private static String number(long value, BazaarWidgetOptions.NumberStyle style) {
        return style == BazaarWidgetOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }
}
