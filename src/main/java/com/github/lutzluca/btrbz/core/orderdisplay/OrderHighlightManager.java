package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.core.config.ConfigUi;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.appearance.UiPalette;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import net.minecraft.network.chat.Component;

public class OrderHighlightManager {

    private final Map<Integer, TrackedOrder> slotToTrackedOrder = new HashMap<>();
    private final Map<Integer, UiColor> inactiveOrderSlots = new HashMap<>();

    public static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top _ -> UiStyles.palette().best();
            case OrderStatus.Matched _ -> UiStyles.palette().matched();
            case OrderStatus.Undercut _ -> UiStyles.palette().undercut();
            case OrderStatus.Unknown _ -> UiStyles.palette().unknown();
        };
    }

    public void sync(
        List<TrackedOrder> trackedOrders,
        List<OrderInfo> snapshot
    ) {
        this.slotToTrackedOrder.clear();
        this.inactiveOrderSlots.clear();

        trackedOrders
            .stream()
            .filter(order -> order.slot != -1)
            .forEach(order -> this.slotToTrackedOrder.put(order.slot, order));

        snapshot.forEach(order -> {
            switch (order) {
                case OrderInfo.FilledOrderInfo filled ->
                    this.inactiveOrderSlots.put(filled.slotIdx(), UiColor.Filled);
                case OrderInfo.ExpiredOrderInfo expired ->
                    this.inactiveOrderSlots.put(expired.slotIdx(), UiColor.Expired);
                case OrderInfo.UnfilledOrderInfo _ -> {
                }
            }
        });
    }

    public Optional<Integer> getHighlight(int idx, boolean marketActive) {
        if (!ConfigStore.get().config().orderHighlight.enabled) {
            return Optional.empty();
        }

        var tracked = this.slotToTrackedOrder.get(idx);
        var color = marketActive && tracked != null
            ? Optional.of(colorForStatus(tracked.status))
            : Optional.ofNullable(this.inactiveOrderSlots.get(idx)).map(role -> role.color(UiStyles.palette()));
        return color.map(value -> UiPalette.alpha(value, 0.55));
    }

    public void clear() {
        this.slotToTrackedOrder.clear();
        this.inactiveOrderSlots.clear();
    }

    public TrackedOrder getTrackedOrder(int slotIdx) {
        return this.slotToTrackedOrder.get(slotIdx);
    }

    public static class HighlightConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Order Highlighting"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(ConfigUi.createDescription(
                    "Draw status-colored backgrounds behind your orders on the Bazaar Orders page."))
                .controller(ConfigUi::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Highlighting"))
                .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                    ConfigUi.text(
                        "Color-code your orders on the Bazaar Orders page so their current status is easy to scan."),
                    highlightLegend()),
                    ConfigImages.OrderStatus))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }

        private static Component highlightLegend() {
            return Component.empty()
                .append(Component.literal("Best: best price\n").withStyle(UiStyles.color(UiColor.Best)))
                .append(Component.literal("Matched: at the best price\n")
                    .withStyle(UiStyles.color(UiColor.Matched)))
                .append(Component.literal("Undercut: a better price exists\n")
                    .withStyle(UiStyles.color(UiColor.Undercut)))
                .append(Component.literal("Unknown: market status unavailable\n")
                    .withStyle(UiStyles.color(UiColor.Unknown)))
                .append(Component.literal("Filled: ready to claim\n")
                    .withStyle(UiStyles.color(UiColor.Filled)))
                .append(Component.literal("Expired").withStyle(UiStyles.color(UiColor.Expired)));
        }
    }
}
