package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.Size;
import java.util.function.Consumer;

public interface WidgetView<D, C, A> {
    UIComponent root();

    void update(D data, C config, WidgetSession session, Consumer<A> actions);

    /** Arrange responsive content in the host's available logical space before inflation. */
    default void updateLayout(Size availableSpace) {}

    default void close() {}
}
