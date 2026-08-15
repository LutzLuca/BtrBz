package com.github.lutzluca.btrbz.core.widgets.session;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface WidgetSessionProvider {
    WidgetSession current(@Nullable Screen screen);
}
