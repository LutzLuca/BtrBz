package com.github.lutzluca.btrbz.core.widgets.alerts;

import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Observation;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.GameUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Source product text and alert observations, independent of the active theme. */
public final class AlertsWidgetData implements WidgetDataSource<AlertsWidgetData.Snapshot> {
    private final AlertManager manager;
    private final BazaarData data;
    private final CacheDependencies dependencies;

    public AlertsWidgetData(AlertManager manager, BazaarData data) {
        this.manager = manager;
        this.data = data;
        this.dependencies = CacheDependencies.of(manager.changes(), data.marketChanges(), data.indexChanges());
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
            return new Entry(event.id(), product.productId(),
                GameUtils.legacyFormattedComponent(product.formattedName()),
                this.data.productStack(product).orElse(ItemStack.EMPTY),
                event.alert().condition, this.manager.currentObservation(event.alert()),
                event.reachedAt(), event.observation());
        }).toList();
        return new Snapshot(entries);
    }

    public static Snapshot preview() {
        return new Snapshot(List.of(
            new Entry(UUID.fromString("00000000-0000-0000-0000-000000000001"), "ENCHANTED_DIAMOND",
                Component.literal("Enchanted Diamond").withStyle(ChatFormatting.GREEN), new ItemStack(Items.DIAMOND),
                new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), 1_200),
                Optional.of(new Observation.Price(1_180)), 1_000, new Observation.Price(1_180)),
            new Entry(UUID.fromString("00000000-0000-0000-0000-000000000002"), "BOOSTER_COOKIE",
                Component.literal("Booster Cookie").withStyle(ChatFormatting.GOLD), new ItemStack(Items.COOKIE),
                new AlertCondition.Liquidity(AlertCondition.LiquiditySide.SellOffers, 16, 9_000_000),
                Optional.of(new Observation.Liquidity(24)), 1_000, new Observation.Liquidity(24))));
    }

    public record Snapshot(List<Entry> entries) {
        public Snapshot {
            entries = List.copyOf(entries);
        }
    }

    public record Entry(
        UUID id, String productId, Component nativeName, ItemStack stack,
        AlertCondition condition, Optional<Observation> currentObservation, long reachedAt, Observation observation
    ) {
        public Entry {
            nativeName = nativeName.copy();
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return this.stack.copy();
        }

        @Override
        public Component nativeName() {
            return this.nativeName.copy();
        }
    }
}
