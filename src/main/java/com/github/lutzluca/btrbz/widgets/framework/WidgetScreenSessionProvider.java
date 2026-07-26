package com.github.lutzluca.btrbz.widgets.framework;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface WidgetScreenSessionProvider {
    WidgetScreenSession current(@Nullable Screen screen);
}
