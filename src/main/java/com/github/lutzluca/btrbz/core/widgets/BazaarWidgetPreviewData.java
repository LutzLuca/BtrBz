package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.widgets.framework.WidgetRenderContext;
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
public final class BazaarWidgetPreviewData implements BazaarDataProvider {
    @Override
    public BazaarData.OrdersData orders(WidgetRenderContext context) {
        return new BazaarData.OrdersData(List.of(
            order("cookie", BazaarData.OrderSide.BUY, "Booster Cookie", Items.COOKIE, 9_825_000, 4, 1, BazaarData.OrderStatus.MATCHED),
            order("diamond", BazaarData.OrderSide.SELL, "Enchanted Diamond", Items.DIAMOND, 1_234, 640, 0, BazaarData.OrderStatus.TOP),
            order("gold", BazaarData.OrderSide.BUY, "Enchanted Gold Block", Items.GOLD_BLOCK, 182_400, 32, 18, BazaarData.OrderStatus.MATCHED),
            order("pearl", BazaarData.OrderSide.SELL, "Enchanted Ender Pearl", Items.ENDER_PEARL, 1_840, 2_048, 512, BazaarData.OrderStatus.UNDERCUT),
            order("blaze", BazaarData.OrderSide.BUY, "Enchanted Blaze Rod", Items.BLAZE_ROD, 1_950_000, 8, 0, BazaarData.OrderStatus.UNKNOWN),
            order("quartz", BazaarData.OrderSide.SELL, "Enchanted Quartz", Items.QUARTZ, 2_175, 1_280, 960, BazaarData.OrderStatus.TOP),
            order("emerald", BazaarData.OrderSide.BUY, "Enchanted Emerald", Items.EMERALD, 1_118, 3_584, 1_792, BazaarData.OrderStatus.MATCHED),
            order("cane", BazaarData.OrderSide.SELL, "Enchanted Sugar Cane", Items.SUGAR_CANE, 85_500, 96, 24, BazaarData.OrderStatus.UNDERCUT)
        ), 3);
    }

    @Override
    public BazaarData.OrderValueData orderValue(WidgetRenderContext context) {
        return new BazaarData.OrderValueData(24_700_000, 8_400_000, 11_200_000, 6_800_000, 51_100_000);
    }

    @Override
    public BazaarData.OrderBookData orderBook(WidgetRenderContext context) {
        return new BazaarData.OrderBookData(
            "Booster Cookie",
            new ItemStack(Items.COOKIE),
            levels(BazaarData.OrderSide.BUY, 9_811_000.1),
            levels(BazaarData.OrderSide.SELL, 9_835_000.0)
        );
    }

    @Override
    public BazaarData.BookmarksData bookmarks(WidgetRenderContext context) {
        return new BazaarData.BookmarksData(List.of(
            bookmark("BOOSTER_COOKIE", "Booster Cookie", Items.COOKIE, true, false),
            bookmark("ENCHANTED_DIAMOND", "Enchanted Diamond", Items.DIAMOND, true, true),
            bookmark("ENCHANTED_GOLD", "Enchanted Gold Block", Items.GOLD_BLOCK, false, true),
            bookmark("ENCHANTED_ENDER_PEARL", "Enchanted Ender Pearl", Items.ENDER_PEARL, false, false),
            bookmark("ENCHANTED_BLAZE_ROD", "Enchanted Blaze Rod", Items.BLAZE_ROD, true, false),
            bookmark("ENCHANTED_EMERALD", "Enchanted Emerald", Items.EMERALD, false, true)
        ));
    }

    @Override
    public BazaarData.PresetsData presets(WidgetRenderContext context) {
        return new BazaarData.PresetsData(List.of(
            new BazaarData.Preset(new OrderPreset.Maximum(), "Maximum", "Use the current maximum", true),
            new BazaarData.Preset(new OrderPreset.Clipboard(320), "Clipboard", "From Clipboard", true),
            new BazaarData.Preset(new OrderPreset.Fixed(64), "64", "", true),
            new BazaarData.Preset(new OrderPreset.Fixed(1024), "1,024", "", true),
            new BazaarData.Preset(new OrderPreset.Fixed(71680), "71,680", "Insufficient coins", false)
        ));
    }

    @Override
    public BazaarData.DailyLimitData dailyLimit(WidgetRenderContext context) {
        return new BazaarData.DailyLimitData(11_250_000_000L, 15_000_000_000L);
    }

    @Override
    public BazaarData.PriceDifferenceData priceDifference(WidgetRenderContext context) {
        return new BazaarData.PriceDifferenceData(
            "Enchanted Diamond", new ItemStack(Items.DIAMOND), 12_450, 640
        );
    }

    private static BazaarData.Order order(
        String key,
        BazaarData.OrderSide side,
        String name,
        Item item,
        long price,
        int total,
        int filled,
        BazaarData.OrderStatus status
    ) {
        var id = new TrackedOrderId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        List<Component> tooltip = List.of(
            Component.literal(status.label()),
            Component.literal("Volume: " + BazaarData.formatInt(total)),
            Component.literal("Price each: " + BazaarData.formatPrice(price))
        );
        Optional<BazaarData.MarketInfo> market = switch (status) {
            case UNDERCUT -> Optional.of(BazaarData.MarketInfo.bestPriceAndQueue(price + 0.1, 0.1, 1, total));
            case MATCHED -> Optional.of(BazaarData.MarketInfo.queue(3, total * 18L));
            case TOP, UNKNOWN -> Optional.empty();
        };
        return new BazaarData.Order(
            id, side, name, styled(name, item), new ItemStack(item), price, total,
            Optional.of(new BazaarData.FillProgress(filled, total)), status, market, tooltip
        );
    }

    private static BazaarData.Bookmark bookmark(
        String id,
        String name,
        Item item,
        boolean buy,
        boolean sell
    ) {
        return new BazaarData.Bookmark(id, name, styled(name, item), new ItemStack(item), buy, sell);
    }

    private static List<BazaarData.OrderBookEntry> levels(BazaarData.OrderSide side, double start) {
        var values = new ArrayList<BazaarData.OrderBookEntry>();
        for (int i = 0; i < 6; i++) {
            values.add(new BazaarData.OrderBookEntry(side, start + (side == BazaarData.OrderSide.BUY ? -i : i) * 12_500, 18 + i * 23, 5 + i * 4));
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
