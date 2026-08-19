package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.utils.Notifier;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Persists the Bazaar HUD hint lifecycle and emits its one-time chat message. */
public final class BazaarHudHintController {
    private final WidgetConfigHandle<BazaarOrdersWidgetConfig> config;
    private final Supplier<Component> toggleKeyLabel;
    private final Runnable saveAction;
    private final Consumer<Component> notification;

    public BazaarHudHintController(
        WidgetConfigHandle<BazaarOrdersWidgetConfig> config,
        Supplier<Component> toggleKeyLabel,
        Runnable saveAction
    ) {
        this(config, toggleKeyLabel, saveAction, message -> Notifier.notifyPlayer(message));
    }

    BazaarHudHintController(
        WidgetConfigHandle<BazaarOrdersWidgetConfig> config,
        Supplier<Component> toggleKeyLabel,
        Runnable saveAction,
        Consumer<Component> notification
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.toggleKeyLabel = Objects.requireNonNull(toggleKeyLabel, "toggleKeyLabel");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
        this.notification = Objects.requireNonNull(notification, "notification");
    }

    public void onWidgetRendered(WidgetId id) {
        if (!BazaarOrdersWidgetDefinition.ID.equals(id)
            || this.config.current().supportedToggleHintState() != BazaarOrdersWidgetConfig.ToggleHintState.Unseen) {
            return;
        }

        this.config.mutate("Bazaar HUD toggle hint shown",
            value -> value.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Shown);
        this.saveAction.run();
        this.notification.accept(Notifier.prefix()
            .append(Component.translatable(
                "message.btrbz.bazaar_orders_hud_hint",
                this.toggleKeyLabel.get()).withStyle(ChatFormatting.GRAY)));
    }

    public boolean dismiss() {
        if (this.config.current().supportedToggleHintState() != BazaarOrdersWidgetConfig.ToggleHintState.Shown) {
            return false;
        }

        this.config.mutate("Bazaar HUD toggle hint dismissed",
            value -> value.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Dismissed);
        return true;
    }
}
