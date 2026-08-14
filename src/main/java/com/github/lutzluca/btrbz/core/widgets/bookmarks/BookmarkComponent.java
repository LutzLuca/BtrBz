package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedItem;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.InvalidationReason;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Bookmark storage and semantic operations without presentation ownership. */
public final class BookmarkComponent {
    private static final int PRODUCT_SLOT = 13;

    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;
    private final TrackedOrderManager trackedOrders;

    private final Set<String> buyProducts = new HashSet<>();
    private final Set<String> sellProducts = new HashSet<>();

    private final CacheToken dataChanges = CacheToken.named("bookmarks.data");

    public BookmarkComponent(
        BazaarData bazaarData,
        ProductInfoProvider productInfoProvider,
        TrackedOrderManager trackedOrders
    ) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
        this.trackedOrders = trackedOrders;

        if (items().removeIf(Objects::isNull)) {
            ConfigManager.save();
        }

        this.rebuildOrderCache();

        trackedOrders.addOnOrderAddedListener(_ -> this.rebuildOrderCache());
        trackedOrders.addOnOrderRemovedListener(_ -> this.rebuildOrderCache());
        trackedOrders.addOnOrderUpdatedListener(_ -> this.rebuildOrderCache());
        trackedOrders.addOnOrdersResetListener(this::rebuildOrderCache);

        bazaarData.addIndexChangeListener(this::refreshProducts);
        SlotHookRegistry.register(new BookmarkHook());
    }

    public List<Snapshot> currentBookmarks() {
        return items().stream().map(item -> new Snapshot(
            item.product().productId(),
            item.productName(),
            item.product().formattedName(),
            item.itemStack(),
            this.buyProducts.contains(item.product().productId()),
            this.sellProducts.contains(item.product().productId()))).toList();
    }

    public boolean contains(String productId) {
        return items().stream().anyMatch(item -> item.product().productId().equals(productId));
    }

    public boolean open(String productId) {
        return items().stream()
            .filter(item -> item.product().productId().equals(productId))
            .findFirst()
            .map(item -> {
                GameUtils.runCommand("bz " + item.productName());
                return true;
            })
            .orElse(false);
    }

    public boolean remove(String productId) {
        boolean changed = items().removeIf(item -> item.product().productId().equals(productId));

        if (changed) {
            this.dataChanges.invalidate(InvalidationReason.of("bookmark removed"));
            ConfigManager.save();
        }

        return changed;
    }

    /** Uses a drop-boundary insertion index in {@code 0..size}. */
    public boolean reorder(String productId, int insertionIndex) {
        var items = items();

        if (insertionIndex < 0 || insertionIndex > items.size()) {
            return false;
        }

        int source = -1;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product().productId().equals(productId)) {
                source = i;
                break;
            }
        }

        if (source < 0) {
            return false;
        }

        var item = items.remove(source);
        int target = insertionIndex > source ? insertionIndex - 1 : insertionIndex;

        items.add(Math.min(target, items.size()), item);

        this.dataChanges.invalidate(InvalidationReason.of("bookmarks reordered"));
        ConfigManager.save();

        return true;
    }

    private boolean toggle(ItemStack stack) {
        var product = this.productInfoProvider.getOpenedProduct();

        if (product == null) {
            return false;
        }

        if (this.contains(product.productId())) {
            this.remove(product.productId());
            return false;
        }

        items().add(new BookmarkedItem(product, stack.copy()));

        this.dataChanges.invalidate(InvalidationReason.of("bookmark added"));
        ConfigManager.save();

        return true;
    }

    private void refreshProducts() {
        boolean changed = false;
        var iterator = items().listIterator();

        while (iterator.hasNext()) {
            var item = iterator.next();
            var refreshed = this.bazaarData.refreshIndexedProduct(item.product());

            if (!refreshed.equals(item.product())) {
                iterator.set(new BookmarkedItem(refreshed, item.itemTemplate()));
                changed = true;
            }
        }

        if (changed) {
            this.dataChanges.invalidate(InvalidationReason.of("bookmark products refreshed"));
            ConfigManager.save();
        }
    }

    private void rebuildOrderCache() {
        this.buyProducts.clear();
        this.sellProducts.clear();

        this.trackedOrders.getTrackedOrders().forEach(order -> order.product.bazaarProductId().ifPresent(id -> {
            switch (order.type) {
                case Buy -> this.buyProducts.add(id);
                case Sell -> this.sellProducts.add(id);
            }
        }));

        this.dataChanges.invalidate(InvalidationReason.of("bookmark order indicators rebuilt"));
    }

    private static List<BookmarkedItem> items() {
        return ConfigManager.get().widgets.bookmarks.items;
    }

    public CacheToken dataChanges() {
        return this.dataChanges;
    }

    public record Snapshot(
        String productId,
        String productName,
        String formattedName,
        ItemStack itemStack,
        boolean hasBuyOrder,
        boolean hasSellOffer
    ) {
        public Snapshot {
            itemStack = itemStack.copy();
        }

        @Override
        public ItemStack itemStack() {
            return this.itemStack.copy();
        }
    }

    private final class BookmarkHook implements SlotHook {
        @Override
        public boolean matches(SlotView view) {
            return ConfigManager.get().widgets.bookmarks.frame.enabled
                && view.slotIdx() == PRODUCT_SLOT
                && view.getCurrInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext context) {
            var raw = context.view().getRawStack();
            var product = productInfoProvider.getOpenedProduct();

            if (raw.isEmpty() || context.view().playerInventorySlot() || product == null) {
                return null;
            }

            raw.set(BtrBz.BOOKMARKED, contains(product.productId()));

            return raw;
        }

        @Override
        public SlotClickResult onClick(SlotClickContext context) {
            if (!ConfigManager.get().widgets.bookmarks.frame.enabled) {
                return SlotClickResult.Pass;
            }

            var raw = context.view().getRawStack();

            if (raw.get(BtrBz.BOOKMARKED) == null || productInfoProvider.getOpenedProduct() == null) {
                return SlotClickResult.Pass;
            }

            raw.set(BtrBz.BOOKMARKED, toggle(raw));

            return SlotClickResult.Consume;
        }
    }
}
