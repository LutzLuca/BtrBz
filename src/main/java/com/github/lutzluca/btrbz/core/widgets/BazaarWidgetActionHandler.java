package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.widgets.framework.WidgetActionHandler;
import com.github.lutzluca.btrbz.widgets.framework.WidgetScreenSession;
import net.minecraft.client.Minecraft;

/** One direct semantic switch with action-time session revalidation. */
public final class BazaarWidgetActionHandler implements WidgetActionHandler<BazaarAction> {
    private final TrackedOrderManager trackedOrders;
    private final BookmarkComponent bookmarks;
    private final OrderBookPriceComponent orderBookPrice;
    private final OrderPresetsComponent presets;

    public BazaarWidgetActionHandler(
        TrackedOrderManager trackedOrders,
        BookmarkComponent bookmarks,
        OrderBookPriceComponent orderBookPrice,
        OrderPresetsComponent presets
    ) {
        this.trackedOrders = trackedOrders;
        this.bookmarks = bookmarks;
        this.orderBookPrice = orderBookPrice;
        this.presets = presets;
    }

    @Override
    public void handle(
        BazaarAction action,
        WidgetScreenSession sourceSession,
        WidgetScreenSession currentSession
    ) {
        var source = (BtrBzWidgetSession) sourceSession;
        var current = (BtrBzWidgetSession) currentSession;
        switch (action) {
            case BazaarAction.ReorderTracked reorder -> {
                if (!source.sameWorkflow(current)
                    || source.trackedRevision() != current.trackedRevision()
                    || ConfigManager.get().widgets.trackedOrders.sort
                        != BazaarWidgetOptions.TrackedSort.MANUAL
                    || this.trackedOrders.currentOrders().stream()
                        .noneMatch(order -> order.id().equals(reorder.id()))) return;
                this.trackedOrders.reorder(reorder.id(), reorder.insertionIndex());
            }
            case BazaarAction.SelectPrice select -> {
                if (!validPriceSession(source, current)) return;
                if (source.host() == BtrBzWidgetSession.HostKind.ORDER_BOOK) {
                    if (Minecraft.getInstance().screen instanceof OrderBookScreen screen
                        && source.productId().filter(screen.productId()::equals).isPresent()) {
                        screen.selectPrice(select.price());
                    }
                    return;
                }
                if (source.host() == BtrBzWidgetSession.HostKind.SIGN) {
                    this.orderBookPrice.selectPrice(select.price(), select.copyOnly());
                }
            }
            case BazaarAction.ApplyPreset apply -> {
                boolean eligible = this.presets.eligible(
                        ScreenInfoHelper.get().getCurrInfo(),
                        ScreenInfoHelper.get().getPrevInfo()
                    );
                if (!validPresetSession(source, current, eligible)) return;
                this.presets.apply(apply.preset());
            }
            case BazaarAction.OpenBookmark open -> {
                if (!validBookmarkSession(source, current) || !this.bookmarks.contains(open.productId())) return;
                this.bookmarks.open(open.productId());
            }
            case BazaarAction.RemoveBookmark remove -> {
                if (!validBookmarkSession(source, current) || !this.bookmarks.contains(remove.productId())) return;
                this.bookmarks.remove(remove.productId());
            }
            case BazaarAction.ReorderBookmark reorder -> {
                if (!validBookmarkSession(source, current)
                    || ConfigManager.get().widgets.bookmarks.sort
                        != BazaarWidgetOptions.BookmarkSort.MANUAL
                    || !this.bookmarks.contains(reorder.productId())) return;
                this.bookmarks.reorder(reorder.productId(), reorder.insertionIndex());
            }
        }
    }

    private static boolean validBookmarkSession(
        BtrBzWidgetSession source,
        BtrBzWidgetSession current
    ) {
        return source.sameWorkflow(current)
            && current.host() == BtrBzWidgetSession.HostKind.CONTAINER
            && current.menu().isPresent();
    }

    static boolean validPriceSession(BtrBzWidgetSession source, BtrBzWidgetSession current) {
        return source.sameWorkflow(current)
            && (current.host() == BtrBzWidgetSession.HostKind.SIGN
                || current.host() == BtrBzWidgetSession.HostKind.ORDER_BOOK);
    }

    static boolean validPresetSession(
        BtrBzWidgetSession source,
        BtrBzWidgetSession current,
        boolean eligible
    ) {
        return eligible && source.sameWorkflow(current);
    }
}
