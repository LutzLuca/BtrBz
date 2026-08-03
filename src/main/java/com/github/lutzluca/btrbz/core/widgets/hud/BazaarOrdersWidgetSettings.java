package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class BazaarOrdersWidgetSettings {
    private BazaarOrdersWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<BazaarOrdersWidgetConfig> binding) {
        var panel = panel();
        enumeration(panel, "Display mode", binding, c -> c.mode, (c, v) -> c.mode = v);
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 320);
        if (binding.current().mode != BazaarOrdersWidgetConfig.HudMode.Detailed) return panel;
        integer(
            panel,
            "Visible orders",
            binding,
            c -> c.visibleOrders,
            (c, v) -> c.visibleOrders = v,
            BazaarOrdersWidgetConfig.MIN_VISIBLE_ORDERS,
            BazaarOrdersWidgetConfig.MAX_VISIBLE_ORDERS
        );
        bool(panel, "Abbreviate Enchanted", binding, c -> c.abbreviateEnchanted, (c, v) -> c.abbreviateEnchanted = v);
        bool(panel, "Show queue information", binding, c -> c.showQueue, (c, v) -> c.showQueue = v);
        bool(panel, "Show undercut gap", binding, c -> c.showUndercutGap, (c, v) -> c.showUndercutGap = v);
        return panel;
    }
}
