package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.config.ConfigUi;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public final class AppearanceConfig {
    public UiTheme theme = ThemePreset.BtrBzDark.theme();
    public List<UiTheme> savedThemes = new ArrayList<>();

    public Option<Integer> createHighlightOpacityOption() {
        return Option.<Integer>createBuilder()
            .name(Component.literal("Highlight Opacity"))
            .binding(ThemePreset.BtrBzDark.theme().orderHighlightOpacity(),
                () -> this.theme.orderHighlightOpacity(),
                value -> this.theme = this.theme.withOrderHighlightOpacity(value))
            .description(ConfigUi.createDescription(
                "Set the opacity of order slot backgrounds. 0% is invisible and 100% is fully opaque."))
            .controller(option -> IntegerSliderControllerBuilder.create(option)
                .range(0, 100)
                .step(1)
                .formatValue(value -> Component.literal(value + "%")))
            .build();
    }
}
