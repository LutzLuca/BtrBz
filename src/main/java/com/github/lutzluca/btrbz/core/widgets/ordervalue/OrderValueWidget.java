package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class OrderValueWidget {
    private OrderValueWidget() {}

    public static UIComponent render(
        BazaarWidgetViewData.OrderValueData value,
        BazaarWidgetOptions.OrderValue options
    ) {
        var layout = UIContainers.verticalFlow(Sizing.fixed(options.contentWidth()), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.LINE_GAP);
        layout.child(label("Bazaar Overview", BazaarStyles.SELL_ACCENT));
        if (options.display() == BazaarWidgetOptions.ValueDisplay.Detailed) {
            if (options.buyLocked()) layout.child(valueLine("Buy Orders (Locked)", value.buyLocked(), semantic(options.colorMode(), BazaarStyles.BUY_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
            if (options.buyItems()) layout.child(valueLine("Buy Orders (Items)", value.buyItems(), semantic(options.colorMode(), BazaarStyles.BUY_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
            if (options.sellClaimable()) layout.child(valueLine("Sell Offers (Claimable)", value.sellClaimable(), semantic(options.colorMode(), BazaarStyles.SELL_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
            if (options.sellPending()) layout.child(valueLine("Sell Offers (Pending)", value.sellPending(), semantic(options.colorMode(), BazaarStyles.SELL_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
        }
        layout.child(valueLine("Total Worth", value.total(), BazaarStyles.PRIMARY_TEXT, true, options.numberStyle(), options.showCoinsSuffix()));
        return layout;
    }

    private static UIComponent valueLine(
        String name,
        long value,
        int color,
        boolean bold,
        BazaarWidgetOptions.NumberStyle format,
        boolean coins
    ) {
        var line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        line.allowOverflow(true);
        line.child(label(name, BazaarStyles.SECONDARY_TEXT));
        line.child(spacer());
        var text = number(value, format) + (coins ? " coins" : "");
        line.child(bold ? boldLabel(text, color) : label(text, color));
        return line;
    }

    private static String number(long value, BazaarWidgetOptions.NumberStyle style) {
        return style == BazaarWidgetOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }

    private static int semantic(BazaarWidgetOptions.ColorMode mode, int semanticColor) {
        return mode == BazaarWidgetOptions.ColorMode.Semantic
            ? semanticColor
            : BazaarStyles.SECONDARY_TEXT;
    }
}
