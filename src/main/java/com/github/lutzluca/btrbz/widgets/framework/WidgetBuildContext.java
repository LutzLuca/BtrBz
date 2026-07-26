package com.github.lutzluca.btrbz.widgets.framework;

import java.util.function.Consumer;

public record WidgetBuildContext<A>(
    WidgetLayoutContext layout,
    WidgetInstanceState instanceState,
    Consumer<A> actions,
    boolean interactive
) {}
