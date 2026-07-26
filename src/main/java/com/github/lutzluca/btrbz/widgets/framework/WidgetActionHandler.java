package com.github.lutzluca.btrbz.widgets.framework;

@FunctionalInterface
public interface WidgetActionHandler<A> {
    void handle(A action, WidgetScreenSession sourceSession, WidgetScreenSession currentSession);
}
