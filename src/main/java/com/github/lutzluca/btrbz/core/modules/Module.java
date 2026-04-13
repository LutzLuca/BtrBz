package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.core.ModuleManager.ModuleContext;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import org.jetbrains.annotations.NotNull;

import lombok.Getter;
import lombok.Setter;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;

public abstract class Module<T> {

    protected T configState;
    private ModuleContext context;

    @Getter
    @Setter
    private boolean displayed = false;

    public void applyConfigState(T state) {
        this.configState = state;
    }

    public void onLoad() { }

    public final void initContext(@NotNull ModuleContext context) {
        if (this.context != null) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " context has already been initialized");
        }

        this.context = context;
    }

    protected final @NotNull ModuleContext context() {
        if (this.context == null) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " context has not been initialized");
        }

        return this.context;
    }

    public abstract boolean shouldDisplay(ScreenInfo info);

    public abstract Optional<DraggableWidget> createWidget(ScreenInfo info);

    protected void updateConfig(Consumer<T> updater) {
        updater.accept(this.configState);
        ModuleManager.getInstance().setDirty(true);
    }

    protected final Optional<Position> loadConfigPosition(Function<T, Integer> xGetter, Function<T, Integer> yGetter) {
        return Utils.zipNullables(xGetter.apply(this.configState), yGetter.apply(this.configState)).map(Position::from);
    }

    protected final void saveConfigPosition(Position position, ObjIntConsumer<T> xSetter, ObjIntConsumer<T> ySetter) {
        this.updateConfig(cfg -> {
            xSetter.accept(cfg, position.x());
            ySetter.accept(cfg, position.y());
        });
    }
}
