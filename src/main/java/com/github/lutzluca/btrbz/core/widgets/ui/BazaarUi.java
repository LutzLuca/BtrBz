package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.ui.UiComponents;

import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class BazaarUi {
    private BazaarUi() {}

    public static RetainedFlowLayout panel(int width) {
        var panel = RetainedFlowLayout.vertical(Sizing.fixed(width), Sizing.content());
        panel.allowOverflow(true);
        panel.gap(WidgetLayoutTokens.LINE_GAP);
        return panel;
    }

    public static RetainedFlowLayout line(UIComponent... components) {
        var line = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        line.verticalAlignment(VerticalAlignment.CENTER);
        line.gap(3);

        for (var component : components) {
            if (component != null) {
                line.child(component);
            }
        }

        return line;
    }

    public static @Nullable ItemComponent reconcileItem(
        @Nullable ItemComponent current,
        Optional<ItemStack> next,
        int size
    ) {
        if (next.isEmpty()) {
            if (current != null && current.hasParent()) {
                current.dismount(UIComponent.DismountReason.REMOVED);
            }

            return null;
        }

        if (current == null) {
            return UiComponents.item(next.orElseThrow(), size);
        }

        current.stack(next.orElseThrow());

        return current;
    }

}
