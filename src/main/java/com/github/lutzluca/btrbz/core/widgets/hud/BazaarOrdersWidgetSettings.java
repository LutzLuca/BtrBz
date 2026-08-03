package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class BazaarOrdersWidgetSettings {
    private BazaarOrdersWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<BazaarOrdersWidgetConfig> binding) {
        var panel = panel();
        enumeration(panel, "Display mode", binding, c -> c.mode, (c, v) -> c.mode = v);
        integer(
            panel,
            "Visible orders",
            binding,
            c -> c.visibleOrders,
            (c, v) -> c.visibleOrders = v,
            BazaarOrdersWidgetConfig.MIN_VISIBLE_ORDERS,
            BazaarOrdersWidgetConfig.MAX_VISIBLE_ORDERS
        );
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 320);
        bool(panel, "Abbreviate Enchanted", binding, c -> c.abbreviateEnchanted, (c, v) -> c.abbreviateEnchanted = v);
        bool(panel, "Hide when empty", binding, c -> c.hideWhenEmpty, (c, v) -> c.hideWhenEmpty = v);
        bool(panel, "Show ItemStacks", binding, c -> c.showItem, (c, v) -> c.showItem = v);
        bool(panel, "Show volume", binding, c -> c.showVolume, (c, v) -> c.showVolume = v);
        enumeration(panel, "Price display", binding, c -> c.priceDisplay, (c, v) -> c.priceDisplay = v);
        bool(panel, "Show queue", binding, c -> c.showQueue, (c, v) -> c.showQueue = v);
        bool(panel, "Show undercut gap", binding, c -> c.showUndercutGap, (c, v) -> c.showUndercutGap = v);
        return panel;
    }
}
