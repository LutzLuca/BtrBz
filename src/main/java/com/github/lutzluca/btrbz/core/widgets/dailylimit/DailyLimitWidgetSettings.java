package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

public final class DailyLimitWidgetSettings {
    private DailyLimitWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<DailyLimitWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.enumeration(panel, "Number format", binding, c -> c.numberStyle,
            (c, v) -> c.numberStyle = v,
            "Compact abbreviates large values. Exact keeps the full comma-separated coin amounts.");

        panel.child(UIComponents.label(Component.literal("Daily coin limit")));

        var limit = UIComponents.textBox(Sizing.fill(100));
        limit.setMaxLength(18);
        limit.setFilter(text -> text.matches("[0-9]*"));
        limit.text(Long.toString(Math.round(binding.current().dailyLimit)));
        limit.onChanged().subscribe(text -> {
            if (text.isBlank()) {
                return;
            }

            try {
                long parsed = Long.parseLong(text);

                if (parsed > 0) {
                    binding.mutate(config -> config.dailyLimit = parsed);
                }
            } catch (NumberFormatException _) {
            }
        });
        limit.tooltip(com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips.wrapped(
            "Your personal Bazaar coin limit. The used value is estimated from transactions observed by the mod."));

        panel.child(limit);

        return panel;
    }
}
