package com.github.lutzluca.btrbz.widgets.framework;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import java.util.Objects;
import java.util.function.Supplier;

/** A direct view over the nine fixed YACL widget fields; it owns no persisted state. */
public final class WidgetStateStore {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;
    private final Supplier<WidgetsConfig> configSupplier;
    private final Runnable saveAction;

    public WidgetStateStore() {
        this(() -> ConfigManager.get().widgets, ConfigManager::save);
    }

    WidgetStateStore(Supplier<WidgetsConfig> configSupplier, Runnable saveAction) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
    }

    public void initializeDefaults(WidgetRegistry registry) {
        // Fixed defaults are constructed by WidgetsConfig. Runtime registration cannot add persistence keys.
    }

    public double globalFineTuneScale() {
        return clampScale(config().globalFineTuneScale);
    }

    public void setGlobalFineTuneScale(double value) {
        config().globalFineTuneScale = clampScale(value);
        this.saveAction.run();
    }

    public WidgetPlacement placement(WidgetDefinition<?, ?> definition, String profile) {
        if (key(definition) == Key.OrderPresets) {
            var state = config().orderPresets;
            return "sign".equals(profile) ? state.signPosition : state.containerPosition;
        }
        return single(definition).position;
    }

    public boolean isActive(WidgetDefinition<?, ?> definition) {
        if (key(definition) == Key.OrderPresets) return config().orderPresets.enabled;
        return single(definition).enabled;
    }

    public void setActive(WidgetDefinition<?, ?> definition, boolean active) {
        if (key(definition) == Key.OrderPresets) config().orderPresets.enabled = active;
        else single(definition).enabled = active;
        this.saveAction.run();
    }

    public void setPlacement(
        WidgetDefinition<?, ?> definition,
        String profile,
        WidgetPlacement placement,
        boolean persist
    ) {
        if (key(definition) == Key.OrderPresets) {
            if ("sign".equals(profile)) config().orderPresets.signPosition = placement;
            else config().orderPresets.containerPosition = placement;
        } else {
            single(definition).position = placement;
        }
        if (persist) this.saveAction.run();
    }

    public void resetPlacement(WidgetDefinition<?, ?> definition, String profile) {
        setPlacement(definition, profile, definition.defaultPlacement(profile), true);
    }

    public double widgetScale(WidgetDefinition<?, ?> definition) {
        if (key(definition) == Key.OrderPresets) return clampScale(config().orderPresets.scale);
        return clampScale(single(definition).scale);
    }

    public void setWidgetScale(WidgetDefinition<?, ?> definition, double value) {
        if (key(definition) == Key.OrderPresets) config().orderPresets.scale = clampScale(value);
        else single(definition).scale = clampScale(value);
        this.saveAction.run();
    }

    public void resetWidgetScale(WidgetDefinition<?, ?> definition) {
        setWidgetScale(definition, 1.0);
    }

    public double requestedScale(WidgetDefinition<?, ?> definition) {
        return WidgetScaleResolver.combineRequestedScale(globalFineTuneScale(), widgetScale(definition));
    }

    public int backgroundColor(WidgetDefinition<?, ?> definition, int fallback) {
        Integer override = key(definition) == Key.OrderPresets
            ? config().orderPresets.background
            : single(definition).background;
        return override == null ? fallback : override;
    }

    public void setBackgroundColor(WidgetDefinition<?, ?> definition, int color) {
        if (key(definition) == Key.OrderPresets) config().orderPresets.background = color;
        else single(definition).background = color;
        this.saveAction.run();
    }

    public void resetBackgroundColor(WidgetDefinition<?, ?> definition) {
        if (key(definition) == Key.OrderPresets) config().orderPresets.background = null;
        else single(definition).background = null;
        this.saveAction.run();
    }

    public void save() {
        this.saveAction.run();
    }

    private WidgetsConfig config() {
        return this.configSupplier.get();
    }

    private WidgetsConfig.SinglePlacementConfig single(WidgetDefinition<?, ?> definition) {
        var widgets = config();
        return switch (key(definition)) {
            case BazaarOrders -> widgets.bazaarOrders;
            case TrackedOrders -> widgets.trackedOrders;
            case OrderValue -> widgets.orderValue;
            case OrderBookScreen -> widgets.orderBookScreen;
            case OrderBookPrice -> widgets.orderBookPrice;
            case Bookmarks -> widgets.bookmarks;
            case OrderLimit -> widgets.orderLimit;
            case PriceDiff -> widgets.priceDiff;
            case OrderPresets -> throw new IllegalArgumentException("Order Presets has two placements");
        };
    }

    private static Key key(WidgetDefinition<?, ?> definition) {
        var identifier = definition.getId().identifier();
        if (!"btrbz".equals(identifier.getNamespace())) {
            throw new IllegalArgumentException("Unsupported widget namespace: " + identifier);
        }
        return switch (identifier.getPath()) {
            case "bazaar_orders" -> Key.BazaarOrders;
            case "tracked_orders_list" -> Key.TrackedOrders;
            case "order_value" -> Key.OrderValue;
            case "order_book_screen" -> Key.OrderBookScreen;
            case "order_book_price" -> Key.OrderBookPrice;
            case "bookmarks" -> Key.Bookmarks;
            case "order_presets" -> Key.OrderPresets;
            case "order_limit" -> Key.OrderLimit;
            case "price_diff" -> Key.PriceDiff;
            default -> throw new IllegalArgumentException("Unsupported widget: " + identifier);
        };
    }

    private static double clampScale(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    private enum Key {
        BazaarOrders,
        TrackedOrders,
        OrderValue,
        OrderBookScreen,
        OrderBookPrice,
        Bookmarks,
        OrderPresets,
        OrderLimit,
        PriceDiff
    }
}
