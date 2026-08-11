package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PriceDifferenceWidgetData implements WidgetDataSource<PriceDifferenceWidgetData.Snapshot> {
    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final BazaarData market;
    private final CacheDependencies dependencies;

    public PriceDifferenceWidgetData(BazaarData market) {
        this.market = market;
        var screens = ScreenInfoHelper.get();

        this.dependencies = CacheDependencies.of(
            screens.inventoryChanges(), market.marketChanges(), market.indexChanges()
        );
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
        var info = ScreenInfoHelper.get().getCurrInfo();
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);

        if (quantity <= 0) {
            return empty();
        }

        var productStack = info.getItemStack(PRODUCT_SLOT);
        if (productStack.isEmpty()) {
            return empty();
        }

        var stack = productStack.orElseThrow();
        var spread = this.market.productSpread(this.market.resolveProduct(stack));
        if (spread.isEmpty()) {
            return empty();
        }

        return new Snapshot(stack.getHoverName(), Optional.of(stack), spread.get(), quantity);
    }

    public static Snapshot preview() {
        return new Snapshot(
            Component.literal("Enchanted Diamond"), Optional.of(new ItemStack(Items.DIAMOND)), 12_450, 640
        );
    }

    private Optional<Integer> listedCount(ItemStack stack) {
        return GameUtils.getLore(stack).stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils.parseUsFormattedNumber(
                line.replace("Inventory:", "").replace("items", "").trim()
            ).toJavaOptional())
            .map(Number::intValue);
    }

    private static Snapshot empty() {
        return new Snapshot(Component.empty(), Optional.empty(), 0, 0);
    }

    public record Snapshot(Component productName, Optional<ItemStack> itemStack, double perItem, int quantity) {
        public Snapshot {
            productName = productName.copy();
            itemStack = itemStack.map(ItemStack::copy);
        }

        @Override
        public Component productName() {
            return this.productName.copy();
        }

        @Override
        public Optional<ItemStack> itemStack() {
            return this.itemStack.map(ItemStack::copy);
        }

        public double total() {
            return this.perItem * this.quantity;
        }
    }
}
