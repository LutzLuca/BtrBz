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
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 280);
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
        return panel;
    }
}
