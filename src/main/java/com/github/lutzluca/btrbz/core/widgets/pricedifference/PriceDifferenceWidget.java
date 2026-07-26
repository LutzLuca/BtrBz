package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.UIComponent;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class PriceDifferenceWidget {
    private PriceDifferenceWidget() {}

    public static UIComponent render(
        BazaarWidgetViewData.PriceDifferenceData difference,
        BazaarWidgetOptions.PriceDiff options
    ) {
        var root = panel(options.contentWidth());
        if (options.showProduct()) root.child(line(
            options.showItems() ? icon(difference.iconCopy()) : null,
            text(difference.productName(), BazaarStyles.PRIMARY_TEXT)
        ));
        int color = difference.total() >= 0 ? BazaarStyles.BUY_ACCENT : BazaarStyles.STATUS_UNDERCUT;
        if (options.display() != BazaarWidgetOptions.DiffDisplay.Total) {
            root.child(valueLine("Per item", signed(difference.perItem(), options.numberStyle()), color, 0));
        }
        if (options.display() != BazaarWidgetOptions.DiffDisplay.PerItem) {
            root.child(valueLine(
                "Total (" + BazaarWidgetViewData.formatInt(difference.quantity()) + " items)",
                signed(difference.total(), options.numberStyle()),
                color,
                1
            ));
        }
        return root;
    }

    private static FlowLayout valueLine(String label, String value, int color, int labelInset) {
        var labelComponent = text(label, BazaarStyles.SECONDARY_TEXT);
        labelComponent.margins(Insets.left(labelInset));
        var line = line(labelComponent);
        line.child(spacer());
        line.child(text(value, color));
        return line;
    }

    private static String number(long value, BazaarWidgetOptions.NumberStyle style) {
        return style == BazaarWidgetOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }

    private static String signed(long value, BazaarWidgetOptions.NumberStyle style) {
        return (value >= 0 ? "+" : "") + number(value, style);
    }
}
