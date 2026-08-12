package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPreset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Deterministic manager fixtures using the same immutable records and component factories. */
public final class BazaarWidgetPreviewData {
    public BazaarWidgetViewData.OrdersData orders() {
        return new BazaarWidgetViewData.OrdersData(List.of(
            order("cookie", BazaarWidgetViewData.OrderSide.Buy, "Booster Cookie", Items.COOKIE, 9_825_000, 4, 1, BazaarWidgetViewData.OrderStatus.Matched),
            order("diamond", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Diamond", Items.DIAMOND, 1_234, 640, 0, BazaarWidgetViewData.OrderStatus.Top),
            order("gold", BazaarWidgetViewData.OrderSide.Buy, "Enchanted Gold Block", Items.GOLD_BLOCK, 182_400, 32, 18, BazaarWidgetViewData.OrderStatus.Matched),
            order("pearl", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Ender Pearl", Items.ENDER_PEARL, 1_840, 2_048, 512, BazaarWidgetViewData.OrderStatus.Undercut),
            order("blaze", BazaarWidgetViewData.OrderSide.Buy, "Enchanted Blaze Rod", Items.BLAZE_ROD, 1_950_000, 8, 0, BazaarWidgetViewData.OrderStatus.Unknown),
            order("quartz", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Quartz", Items.QUARTZ, 2_175, 1_280, 960, BazaarWidgetViewData.OrderStatus.Top),
            order("emerald", BazaarWidgetViewData.OrderSide.Buy, "Enchanted Emerald", Items.EMERALD, 1_118, 3_584, 1_792, BazaarWidgetViewData.OrderStatus.Matched),
            order("cane", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Sugar Cane", Items.SUGAR_CANE, 85_500, 96, 24, BazaarWidgetViewData.OrderStatus.Undercut)
        ), 3);
    }

    public BazaarWidgetViewData.OrderValueData orderValue() {
        return new BazaarWidgetViewData.OrderValueData(24_700_000, 8_400_000, 11_200_000, 6_800_000, 51_100_000);
    }

    public BazaarWidgetViewData.OrderBookData orderBook() {
        return new BazaarWidgetViewData.OrderBookData(
            "Booster Cookie",
            new ItemStack(Items.COOKIE),
            levels(BazaarWidgetViewData.OrderSide.Buy, 9_811_000.1),
            levels(BazaarWidgetViewData.OrderSide.Sell, 9_835_000.0),
            Optional.of(BazaarWidgetViewData.OrderSide.Sell)
        );
    }

    public BazaarWidgetViewData.BookmarksData bookmarks() {
        return new BazaarWidgetViewData.BookmarksData(List.of(
            bookmark("BOOSTER_COOKIE", "Booster Cookie", Items.COOKIE, true, false),
            bookmark("ENCHANTED_DIAMOND", "Enchanted Diamond", Items.DIAMOND, true, true),
            bookmark("ENCHANTED_GOLD", "Enchanted Gold Block", Items.GOLD_BLOCK, false, true),
            bookmark("ENCHANTED_ENDER_PEARL", "Enchanted Ender Pearl", Items.ENDER_PEARL, false, false),
            bookmark("ENCHANTED_BLAZE_ROD", "Enchanted Blaze Rod", Items.BLAZE_ROD, true, false),
            bookmark("ENCHANTED_EMERALD", "Enchanted Emerald", Items.EMERALD, false, true)
        ));
    }

    public BazaarWidgetViewData.PresetsData presets() {
        return new BazaarWidgetViewData.PresetsData(List.of(
            new BazaarWidgetViewData.Preset(new OrderPreset.Maximum(), "Maximum", "Use the current maximum", true),
            new BazaarWidgetViewData.Preset(new OrderPreset.Clipboard(320), "Clipboard", "From Clipboard", true),
            new BazaarWidgetViewData.Preset(new OrderPreset.Fixed(64), "64", "", true),
            new BazaarWidgetViewData.Preset(new OrderPreset.Fixed(1024), "1,024", "", true),
            new BazaarWidgetViewData.Preset(new OrderPreset.Fixed(71680), "71,680", "Insufficient coins", false)
        ));
    }

    public BazaarWidgetViewData.DailyLimitData dailyLimit() {
        return new BazaarWidgetViewData.DailyLimitData(11_250_000_000L, 15_000_000_000L);
    }

    public BazaarWidgetViewData.PriceDifferenceData priceDifference() {
        return new BazaarWidgetViewData.PriceDifferenceData(
            "Enchanted Diamond", new ItemStack(Items.DIAMOND), 12_450, 640
        );
    }

    private static BazaarWidgetViewData.Order order(
        String key,
        BazaarWidgetViewData.OrderSide side,
        String name,
        Item item,
        long price,
        int total,
        int filled,
        BazaarWidgetViewData.OrderStatus status
    ) {
        var id = new TrackedOrderId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        List<Component> tooltip = List.of(
            Component.literal(status.label()),
            Component.literal("Volume: " + BazaarWidgetViewData.formatInt(total)),
            Component.literal("Price each: " + BazaarWidgetViewData.formatPrice(price))
        );
        Optional<BazaarWidgetViewData.MarketInfo> market = switch (status) {
            case Undercut -> Optional.of(BazaarWidgetViewData.MarketInfo.bestPriceAndQueue(price + 0.1, 0.1, 1, total));
            case Matched -> Optional.of(BazaarWidgetViewData.MarketInfo.queue(3, total * 18L));
            case Top, Unknown -> Optional.empty();
        };
        return new BazaarWidgetViewData.Order(
            id, side, name, styled(name, item), new ItemStack(item), price, total,
            Optional.of(new BazaarWidgetViewData.FillProgress(filled, total)), status, market, tooltip
        );
    }

    private static BazaarWidgetViewData.Bookmark bookmark(
        String id,
        String name,
        Item item,
        boolean buy,
        boolean sell
    ) {
        return new BazaarWidgetViewData.Bookmark(id, name, styled(name, item), new ItemStack(item), buy, sell);
    }

    private static List<BazaarWidgetViewData.OrderBookEntry> levels(BazaarWidgetViewData.OrderSide side, double start) {
        var values = new ArrayList<BazaarWidgetViewData.OrderBookEntry>();
        for (int i = 0; i < 6; i++) {
            values.add(new BazaarWidgetViewData.OrderBookEntry(side, start + (side == BazaarWidgetViewData.OrderSide.Buy ? -i : i) * 12_500, 18 + i * 23, 5 + i * 4));
        }
        return values;
    }

    private static Component styled(String name, Item item) {
        var component = Component.literal(name);
        if (item == Items.COOKIE) return component.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (item == Items.DIAMOND) return component.withStyle(ChatFormatting.AQUA);
        if (item == Items.EMERALD) return component.withStyle(ChatFormatting.GREEN);
        return component.withStyle(ChatFormatting.GRAY);
    }
}
