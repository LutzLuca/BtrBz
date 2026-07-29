package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class DailyLimitWidgetSettings {
    private DailyLimitWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<DailyLimitWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 140, 280);
        bool(panel, "Fit width to content", binding, c -> c.fitToContent, (c, v) -> c.fitToContent = v);
        enumeration(panel, "Display", binding, c -> c.display, (c, v) -> c.display = v);
        enumeration(panel, "Number format", binding, c -> c.numberStyle, (c, v) -> c.numberStyle = v);
        bool(panel, "Show header", binding, c -> c.showHeader, (c, v) -> c.showHeader = v);
        panel.child(UIComponents.label(Component.literal("Daily coin limit")));
        var limit = UIComponents.textBox(Sizing.fill(100));
        limit.setMaxLength(18);
        limit.setFilter(text -> text.matches("[0-9]*"));
        limit.text(Long.toString(Math.round(binding.current().dailyLimit)));
        limit.onChanged().subscribe(text -> {
            if (text.isBlank()) return;
            try {
                long parsed = Long.parseLong(text);
                if (parsed > 0) binding.mutate(config -> config.dailyLimit = parsed);
            } catch (NumberFormatException _) { }
        });
        panel.child(limit);
        integer(panel, "Warning threshold", binding, c -> c.warningThreshold, (c, value) -> {
            c.warningThreshold = value;
            if (c.criticalThreshold < value) c.criticalThreshold = value;
        }, 1, 100);
        integer(panel, "Critical threshold", binding, c -> c.criticalThreshold, (c, value) -> {
            c.criticalThreshold = value;
            if (c.warningThreshold > value) c.warningThreshold = value;
        }, 1, 100);
        return panel;
    }
}
