package com.github.lutzluca.btrbz.core.widgets.alerts;

import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertNotice;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AlertsWidgetData implements WidgetDataSource<AlertsWidgetData.Snapshot> {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());
    private final AlertManager manager;
    private final BazaarData data;
    private final CacheDependencies dependencies;

    public AlertsWidgetData(AlertManager manager, BazaarData data) {
        this.manager = manager;
        this.data = data;
        this.dependencies = CacheDependencies.of(manager.changes(), data.marketChanges(),
            data.indexChanges(),
            UiStyles.appearanceChanges());
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.dependencies;
    }

    @Override
    public boolean sessionSensitive() {
        return false;
    }

    @Override
    public Snapshot snapshot(WidgetSession session) {
        var entries = this.manager.inbox().reversed().stream().map(event -> {
            var product = this.data.refreshIndexedProduct(event.alert().product);
            var name = UiStyles.productName(product.formattedName());
            var tooltip = new java.util.ArrayList<Component>();
            tooltip.add(name);
            tooltip.add(AlertNotice.conditionText(event.alert().condition));
            var observation = this.manager.currentObservation(event.alert());
            tooltip.add(observation.map(value -> AlertNotice.currentText(event.alert().condition, value))
                .orElseGet(() -> Component.literal("Data unavailable").withStyle(UiStyles.muted())));
            tooltip.add(Component.literal("Triggered " + TIME.format(Instant.ofEpochMilli(event.reachedAt())))
                .withStyle(UiStyles.muted()));
            tooltip.add(AlertNotice.from(event, product).observation());
            tooltip.add(Component.literal("Click to open in the Bazaar").withStyle(UiStyles.action()));
            return new Entry(event.id(), product.productId(), name,
                this.data.productStack(product).orElse(ItemStack.EMPTY),
                AlertNotice.conditionText(event.alert().condition), tooltip);
        }).toList();
        return new Snapshot(entries);
    }

    public static Snapshot preview() {
        return new Snapshot(List.of(
            new Entry(UUID.fromString("00000000-0000-0000-0000-000000000001"), "ENCHANTED_DIAMOND",
                Component.literal("Enchanted Diamond").withStyle(ChatFormatting.GREEN), new ItemStack(Items.DIAMOND),
                Component.literal("Buy price ≤ 1,200 coins"),
                List.of(Component.literal("Buy price ≤ 1,200 coins"), Component.literal("Still met: 1,180 coins"))),
            new Entry(UUID.fromString("00000000-0000-0000-0000-000000000002"), "BOOSTER_COOKIE",
                Component.literal("Booster Cookie").withStyle(ChatFormatting.GOLD), new ItemStack(Items.COOKIE),
                Component.literal("Instant buy 16 items at ≤ 9m coins"),
                List.of(Component.literal("Buy 16 items instantly for up to 9m each"),
                    Component.literal("Still met: 24 qualifying items")))));
    }

    public record Snapshot(List<Entry> entries) {
        public Snapshot {
            entries = List.copyOf(entries);
        }
    }

    public record Entry(
        UUID id, String productId, Component name, ItemStack stack, Component condition, List<Component> tooltip
    ) {
        public Entry {
            name = name.copy();
            stack = stack.copy();
            tooltip = tooltip.stream().map(component -> (Component) component.copy()).toList();
        }

        @Override
        public ItemStack stack() {
            return this.stack.copy();
        }

        @Override
        public Component name() {
            return this.name.copy();
        }
    }
}
