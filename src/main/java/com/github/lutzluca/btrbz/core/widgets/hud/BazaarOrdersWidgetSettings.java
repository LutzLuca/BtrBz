package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;

import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class BazaarOrdersWidgetSettings {
    private BazaarOrdersWidgetSettings() {
    }

    public static UIComponent create(WidgetConfigBinding<BazaarOrdersWidgetConfig> binding) {
        var panel = panel();
        populate(panel, binding);

        return panel;
    }

    private static void populate(
        io.wispforest.owo.ui.container.FlowLayout panel,
        WidgetConfigBinding<BazaarOrdersWidgetConfig> binding
    ) {
        panel.clearChildren();

        enumeration(panel, "Display mode", binding, c -> c.mode, (c, v) -> c.mode = v,
            "Detailed shows individual orders. Status counts provides a smaller overview.",
            () -> panel.queue(() -> populate(panel, binding)));

        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 320,
            "Controls horizontal space without changing the widget's text or icon scale.");

        if (binding.current().mode != BazaarOrdersWidgetConfig.HudMode.Detailed) {
            return;
        }

        integer(
            panel,
            "Visible orders",
            binding,
            c -> c.visibleOrders,
            (c, v) -> c.visibleOrders = v,
            BazaarOrdersWidgetConfig.MIN_VISIBLE_ORDERS,
            BazaarOrdersWidgetConfig.MAX_VISIBLE_ORDERS,
            "Maximum number of active orders shown before the overflow summary."
        );

        bool(panel, "Abbreviate Enchanted", binding, c -> c.abbreviateEnchanted,
            (c, v) -> c.abbreviateEnchanted = v,
            "Shortens names beginning with Enchanted when horizontal space is limited.");

        bool(panel, "Show queue information", binding, c -> c.showQueue, (c, v) -> c.showQueue = v,
            "Shows orders and items ahead as [orders/items] when the data is available.");

        bool(panel, "Show undercut gap", binding, c -> c.showUndercutGap, (c, v) -> c.showUndercutGap = v,
            "Shows how far an undercut order is from the current best price. Wider widgets are recommended.");
    }
}
