package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import java.util.List;
import java.util.Objects;

public final class OrderPresetsWidgetData implements WidgetDataSource<OrderPresetsWidgetData.Snapshot> {
    private final OrderPresetsComponent component;
    private final CacheDependencies dependencies;

    public OrderPresetsWidgetData(
        OrderPresetsComponent component,
        BazaarData market,
        ProductInfoProvider productInfoProvider,
        WidgetConfigHandle<OrderPresetsWidgetConfig> configHandle
    ) {
        this.component = component;
        this.dependencies = CacheDependencies.of(
            component.stateChanges(), component.clipboardTracker().changes(),
            component.purseTracker().changes(), productInfoProvider.changes(),
            market.marketChanges(), configHandle.contentChanges()
        );
    }

    @Override
    public CacheDependencies cacheDependencies() { return this.dependencies; }

    @Override
    public boolean sessionSensitive() { return false; }

    @Override
    public Snapshot snapshot(WidgetSession session) {
        return new Snapshot(this.component.currentPresets().stream().map(state -> {
            var preset = state.preset();
            String label = switch (preset) {
                case OrderPreset.Maximum _ -> "Max";
                case OrderPreset.Clipboard clipboard -> BazaarWidgetViewData.formatInt(clipboard.amount());
                case OrderPreset.Fixed fixed -> BazaarWidgetViewData.formatInt(fixed.amount());
            };
            String tooltip = switch (state) {
                case OrderPresetsComponent.PresetState.Available available ->
                    preset instanceof OrderPreset.Maximum
                        ? BazaarWidgetViewData.formatInt(available.resolvedVolume()) + " items"
                        : preset instanceof OrderPreset.Clipboard ? "Clipboard" : "";
                case OrderPresetsComponent.PresetState.PriceUnavailable _ -> "Price unavailable";
                case OrderPresetsComponent.PresetState.PurseUnavailable _ -> "Purse unavailable";
                case OrderPresetsComponent.PresetState.InsufficientCoins _ -> "Insufficient coins";
                case OrderPresetsComponent.PresetState.CannotAffordSingleItem unavailable ->
                    "Missing " + BazaarWidgetViewData.formatCompact(unavailable.missingCoins()) + " coins";
            };
            return new Preset(
                preset, label, tooltip, state instanceof OrderPresetsComponent.PresetState.Available
            );
        }).toList());
    }

    public static Snapshot preview() {
        return new Snapshot(List.of(
            new Preset(new OrderPreset.Maximum(), "Max", "Use the current maximum", true),
            new Preset(new OrderPreset.Clipboard(320), "320", "Clipboard", true),
            new Preset(new OrderPreset.Fixed(64), "64", "", true),
            new Preset(new OrderPreset.Fixed(1024), "1,024", "", true),
            new Preset(new OrderPreset.Fixed(71680), "71,680", "Insufficient coins", false)
        ));
    }

    public record Preset(OrderPreset preset, String label, String tooltip, boolean available) {
        public Preset {
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tooltip, "tooltip");
        }
    }

    public record Snapshot(List<Preset> presets) {
        public Snapshot {
            presets = List.copyOf(presets);
        }
    }
}
