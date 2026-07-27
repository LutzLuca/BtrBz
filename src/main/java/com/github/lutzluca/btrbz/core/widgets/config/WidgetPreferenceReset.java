package com.github.lutzluca.btrbz.core.widgets.config;

@FunctionalInterface
public interface WidgetPreferenceReset<C> {
    void reset(C current, C defaults);
}
