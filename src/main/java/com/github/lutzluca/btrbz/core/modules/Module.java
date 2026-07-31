package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.Setter;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;

public abstract class Module<T> {

    protected T configState;
    @Getter
    @Setter
    private boolean displayed = false;

    public void applyConfigState(T state) {
        this.configState = state;
    }

    public void onLoad() { }

    public abstract boolean shouldDisplay(ScreenInfo info);

    public abstract Optional<DraggableWidget> createWidget(ScreenInfo info);

    /**
     * Schedules a config save: avoid regular hot-path calls like tick, render, or polling.
     */
    protected void updateConfig(Consumer<T> updater) {
        updater.accept(this.configState);
        ModuleManager.getInstance().setDirty(true);
    }

    /**
     * Schedules a config save only when the updater reports a state change.
     */
    protected boolean updateConfigIfChanged(Predicate<T> updater) {
        boolean changed = updater.test(this.configState);
        if (changed) {
            ModuleManager.getInstance().setDirty(true);
        }
        return changed;
    }
}
