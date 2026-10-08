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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class OrderHighlightManager {

    private final Map<Integer, TrackedOrder> slotToTrackedOrder = new HashMap<>();
    private final Map<Integer, Integer> inactiveOrderSlots = new HashMap<>();

    public static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top _ -> 0xFF55FF55;
            case OrderStatus.Matched _ -> 0xFF5555FF;
            case OrderStatus.Undercut _ -> 0xFFFF5555;
            case OrderStatus.Unknown _ -> 0xFFAA55FF;
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
                    this.inactiveOrderSlots.put(filled.slotIdx(), 0xFFEFBF04);
                case OrderInfo.ExpiredOrderInfo expired ->
                    this.inactiveOrderSlots.put(expired.slotIdx(), 0xFF888888);
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
        if (marketActive && tracked != null) {
            return Optional.of(colorForStatus(tracked.status));
        }

        return Optional.ofNullable(this.inactiveOrderSlots.get(idx));
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
                .append(Component.literal("Green").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(": best price\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Blue").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(": matched at the best price\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Red").withStyle(ChatFormatting.RED))
                .append(Component.literal(": undercut\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Purple").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(": status unknown\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Gold").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": filled and ready to claim\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Gray").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": expired").withStyle(ChatFormatting.GRAY));
        }
    }
}
