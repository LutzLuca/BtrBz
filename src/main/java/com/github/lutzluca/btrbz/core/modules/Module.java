package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.ModContext;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;

public abstract class Module<T> {

    protected T configState;
    private ModContext context;

    @Getter
    @Setter
    private boolean displayed = false;

    public void applyConfigState(T state) {
        this.configState = state;
    }

    public void onLoad() { }

    public final void initContext(ModContext context) {
        if (this.context != null) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " context has already been initialized");
        }

        this.context = Objects.requireNonNull(context, "context cannot be null");
    }

    protected final ModContext context() {
        if (this.context == null) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " context has not been initialized");
        }

        return this.context;
    }

    public abstract boolean shouldDisplay(ScreenInfo info);

    public abstract List<DraggableWidget> createWidgets(ScreenInfo info);

    protected void updateConfig(Consumer<T> updater) {
        updater.accept(this.configState);
        ModuleManager.getInstance().setDirty(true);
    }
}
