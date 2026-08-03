package com.github.lutzluca.btrbz.core.widgets.manager;

public interface WidgetManagerLauncherOwner {
    WidgetManagerLauncher btrbz$managerLauncher();

    default void btrbz$prepareManagerTransition() {}
}
