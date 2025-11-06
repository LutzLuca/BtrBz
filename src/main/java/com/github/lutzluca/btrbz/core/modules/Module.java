package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.widget.ClickableWidget;

public abstract class Module<T> {

    protected T configState;

    @Getter
    @Setter
    private boolean displayed = false;

    public void applyConfigState(T state) {
        this.configState = state;
    }

    public void onLoad() { }

    public T serializeConfigState() {
        return this.configState;
    }

    public abstract boolean shouldDisplay(ScreenInfo info);

    public abstract List<ClickableWidget> createWidgets(ScreenInfo info);

    protected void updateConfig(Consumer<T> updater) {
        updater.accept(this.configState);
        ModuleManager.getInstance().setDirty(true);
    }
}
