package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;

public interface WidgetView<D, C, A> {
    UIComponent root();

    void update(D data, C config, WidgetSession session, Consumer<A> actions);

    default void close() {}
}
