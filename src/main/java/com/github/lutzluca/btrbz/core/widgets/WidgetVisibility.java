package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;

@FunctionalInterface
public interface WidgetVisibility<D, C> {
    boolean test(D data, C config, WidgetSession session);
}
