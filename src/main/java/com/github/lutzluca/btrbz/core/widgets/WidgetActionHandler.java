package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;

@FunctionalInterface
public interface WidgetActionHandler<A> {
    void handle(A action, WidgetSession sourceSession, WidgetSession currentSession);
}
