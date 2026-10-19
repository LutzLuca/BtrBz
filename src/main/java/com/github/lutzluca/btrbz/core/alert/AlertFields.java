package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.ui.UiSurfaces;
import io.vavr.control.Try;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.Minecraft;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Alert fields keep Minecraft editing behavior inside the screen's themed surfaces. */
final class AlertFields {
    private AlertFields() {}

    static TextBoxComponent textBox(String hint) {
        var field = UIComponents.textBox(Sizing.fill(100));
        configure(field, hint);
        return field;
    }

    static TextBoxComponent quantity() {
        var field = new TextBoxComponent(Sizing.fill(100)) {
            @Override
            public void setFocused(boolean focused) {
                boolean wasFocused = this.isFocused();
                super.setFocused(focused);
                if (wasFocused && !focused) {
                    Try.of(() -> Long.parseLong(this.getValue().trim().replace(",", "")))
                        .filter(value -> value > 0)
                        .onSuccess(value -> this.setValue(NumberFormat.getIntegerInstance(Locale.US).format(value)));
                }
            }
        };
        configure(field, "e.g. 1,200");
        return field;
    }

    private static void configure(TextBoxComponent field, String hint) {
        field.setBordered(false);
        field.verticalSizing(Sizing.fixed(10));
        field.setTextColor(UiStyles.palette().textPrimary());
        field.setHint(Component.literal(hint).withStyle(UiStyles.label()));
    }

    static FlowLayout frame(TextBoxComponent field) {
        var frame = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        frame.padding(Insets.of(6));
        frame.surface((graphics, component) -> {
            int border = field.isFocused() ? UiStyles.palette().accent() : UiStyles.palette().outline();
            UiSurfaces.drawRoundedPanel(graphics, component.x(), component.y(), component.width(), component.height(),
                border, 3);
            UiSurfaces.drawRoundedPanel(graphics, component.x() + 1, component.y() + 1,
                component.width() - 2, component.height() - 2, UiStyles.palette().surface(), 2);
        });
        frame.child(field);
        return frame;
    }
}
