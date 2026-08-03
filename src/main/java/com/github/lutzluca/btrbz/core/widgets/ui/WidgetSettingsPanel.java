package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.ScrollSafeDiscreteSliderComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WidgetSettingsPanel {
    private WidgetSettingsPanel() {}

    public static FlowLayout panel() {
        var panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        panel.gap(5);
        return panel;
    }

    public static <C> void integer(
        FlowLayout panel,
        String label,
        WidgetConfigBinding<C> binding,
        ToIntFunction<C> getter,
        BiConsumer<C, Integer> setter,
        int minimum,
        int maximum,
        String description
    ) {
        var slider = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), minimum, maximum);
        slider.decimalPlaces(0);
        slider.setFromDiscreteValue(getter.applyAsInt(binding.current()));
        slider.message(value -> Component.literal(label + " " + value));
        slider.onChanged().subscribe(value -> binding.mutate(config ->
            setter.accept(config, (int) Math.round(value))
        ));
        slider.tooltip(WidgetTooltips.wrapped(description));
        panel.child(slider);
    }

    public static <C> void bool(
        FlowLayout panel,
        String label,
        WidgetConfigBinding<C> binding,
        Function<C, Boolean> getter,
        BiConsumer<C, Boolean> setter,
        String description
    ) {
        var checkbox = UIComponents.smallCheckbox(Component.literal(label));
        checkbox.checked(getter.apply(binding.current()));
        checkbox.onChanged().subscribe(value -> binding.mutate(config -> setter.accept(config, value)));
        checkbox.tooltip(WidgetTooltips.wrapped(description));
        panel.child(checkbox);
    }

    public static <C, E extends Enum<E>> void enumeration(
        FlowLayout panel,
        String label,
        WidgetConfigBinding<C> binding,
        Function<C, E> getter,
        BiConsumer<C, E> setter,
        String description
    ) {
        enumeration(panel, label, binding, getter, setter, description, () -> {});
    }

    public static <C, E extends Enum<E>> void enumeration(
        FlowLayout panel,
        String label,
        WidgetConfigBinding<C> binding,
        Function<C, E> getter,
        BiConsumer<C, E> setter,
        String description,
        Runnable afterChange
    ) {
        var control = UIComponents.button(enumMessage(label, getter.apply(binding.current())), button -> {
            var screen = Minecraft.getInstance().screen;
            if (screen == null || !(button.root() instanceof FlowLayout root)) return;
            var current = getter.apply(binding.current());
            io.wispforest.owo.ui.component.DropdownComponent.openContextMenu(
                screen,
                root,
                FlowLayout::child,
                button.x(),
                button.y() + button.height(),
                dropdown -> {
                    for (var value : current.getDeclaringClass().getEnumConstants()) {
                        dropdown.button(Component.literal(enumLabel(value)), menu -> {
                            binding.mutate(config -> setter.accept(config, value));
                            button.setMessage(enumMessage(label, value));
                            menu.remove();
                            afterChange.run();
                        });
                    }
                }
            );
        });
        control.renderer(ButtonComponent.Renderer.flat(0xFF2C3340, 0xFF384252, 0xFF20242D));
        control.textShadow(false);
        control.sizing(Sizing.fill(100), Sizing.fixed(20));
        control.tooltip(WidgetTooltips.wrapped(description));
        panel.child(control);
    }

    private static Component enumMessage(String label, Enum<?> value) {
        return Component.literal(label + ": " + enumLabel(value));
    }

    static String enumLabel(Enum<?> value) {
        var name = value.name();
        var display = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (index > 0 && Character.isUpperCase(character)) display.append(' ');
            display.append(index == 0 ? Character.toUpperCase(character) : Character.toLowerCase(character));
        }
        return display.toString();
    }
}
