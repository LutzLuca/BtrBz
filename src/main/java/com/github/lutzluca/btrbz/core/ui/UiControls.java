package com.github.lutzluca.btrbz.core.ui;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class UiControls {
    private UiControls() {}

    public static FlowLayout row(UIComponent... children) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        for (var child : children) {
            row.child(child);
        }
        return row;
    }

    public static LabelComponent text(String value, int color) {
        return BazaarUi.text(value, color);
    }

    public static ButtonComponent button(String label, Runnable action) {
        var button = UIComponents.button(Component.literal(label), _ -> action.run());
        button.textShadow(false);
        button.sizing(Sizing.content(8), Sizing.fixed(22));
        button.renderer(buttonRenderer(false, false));
        return button;
    }

    public static ButtonComponent iconButton(String label, ItemStack icon, Runnable action) {
        return new IconTextButton(label, icon, action);
    }

    public static ButtonComponent iconButton(String label, Identifier texture, int sourceSize, Runnable action) {
        return new IconTextButton(label, texture, sourceSize, action);
    }

    public static ButtonComponent.Renderer buttonRenderer(boolean primary, boolean selected) {
        return buttonRenderer(primary, selected, 0xFF89929C);
    }

    public static ButtonComponent.Renderer buttonRenderer(boolean primary, boolean selected, int accent) {
        return (graphics, button, delta) -> {
            boolean hover = button.isHoveredOrFocused();
            int color = primary
                ? (button.active() ? (hover ? 0xFF555C65 : 0xFF3F454C) : (hover ? 0xFF2D3136 : 0xFF22252A))
                : (hover ? 0xFF484D54 : selected ? 0xFF3B3F44 : 0xFF25282C);
            WidgetSurfaces.drawRoundedPanel(graphics, button.getX(), button.getY(), button.getWidth(),
                button.getHeight(),
                color, 3);
            int border = (primary || selected) && button.active() ? accent : hover ? 0xFF707780 : 0xFF45494E;
            graphics.fill(button.getX() + 3, button.getY() + button.getHeight() - 1,
                button.getX() + button.getWidth() - 3, button.getY() + button.getHeight(), border);
        };
    }

    public static ButtonComponent tab(String label, Runnable action, boolean selected) {
        return button(label, action).renderer(tabRenderer(selected));
    }

    public static ButtonComponent.Renderer tabRenderer(boolean selected) {
        return (graphics, button, delta) -> {
            if (button.isHoveredOrFocused()) {
                graphics.fill(button.getX(), button.getY(), button.getX() + button.getWidth(),
                    button.getY() + button.getHeight(), 0x303E3935);
            }
            if (selected) {
                graphics.fill(button.getX() + 3, button.getY() + button.getHeight() - 2,
                    button.getX() + button.getWidth() - 3, button.getY() + button.getHeight(), 0xFFB6A48B);
            }
        };
    }
}
