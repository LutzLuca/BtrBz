package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.text.Text;

@Slf4j
public class OrderHighlightManager {

    private final Map<Integer, TrackedOrder> slotToTrackedOrder = new HashMap<>();
    private final Map<Integer, Integer> filledOrderSlots = new HashMap<>();
    private Integer overrideSlotIdx = null;
    private Integer overrideColor = null;

    public static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top ignored -> 0xFF55FF55;
            case OrderStatus.Matched ignored -> 0xFF5555FF;
            case OrderStatus.Undercut ignored -> 0xFFFF5555;
            case OrderStatus.Unknown ignored -> 0xFFAA55FF;
        };
    }

    public void sync(
        List<TrackedOrder> trackedOrders,
        List<OrderInfo.FilledOrderInfo> filledOrders
    ) {
        log.debug("Synchronizing highlights from ui orders");
        this.slotToTrackedOrder.clear();
        this.filledOrderSlots.clear();

        trackedOrders
            .stream()
            .filter(order -> order.slot != -1)
            .forEach(order -> this.slotToTrackedOrder.put(order.slot, order));

        filledOrders.forEach(order -> this.filledOrderSlots.put(order.slotIdx(), 0xFFEFBF04));
    }

    public Optional<Integer> getHighlight(int idx) {
        if (this.overrideSlotIdx != null && idx == this.overrideSlotIdx) {
            return Optional.of(this.overrideColor);
        }

        if (!ConfigManager.get().orderHighlight.enabled) {
            return Optional.empty();
        }

        var tracked = this.slotToTrackedOrder.get(idx);
        if (tracked != null) {
            return Optional.of(colorForStatus(tracked.status));
        }

        return Optional.ofNullable(this.filledOrderSlots.get(idx));
    }

    public void setHighlightOverride(int slotIdx, int color) {
        this.overrideSlotIdx = slotIdx;
        this.overrideColor = color;
    }

    public void clearHighlightOverride() {
        this.overrideSlotIdx = null;
        this.overrideColor = null;
    }

    public static class HighlightConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Highlighting"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable order highlights in the Order screen")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Highlighting"))
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable order highlights in the Order screen")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}